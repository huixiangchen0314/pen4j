package top.kzre.pen4j.core;

import lombok.extern.slf4j.Slf4j;
import top.kzre.pen4j.api.PenDevice;
import top.kzre.pen4j.api.PenEvent;
import top.kzre.pen4j.api.PenListener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 数位板笔上下文的统一入口。
 * 用户必须显式提供平台驱动实例。
 */
@Slf4j
public class PenContext implements AutoCloseable {

    private final PenPlatformDriver driver;
    private final List<PenListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Executor dispatchExecutor;
    private final ConcurrentHashMap<String, PenEvent> latestEventMap = new ConcurrentHashMap<>();
    private final Object pollLock = new Object();

    public enum DispatchMode { DIRECT, SINGLE_THREAD }

    // ---------- 工厂方法（只接受驱动实例） ----------

    public static PenContext create(PenPlatformDriver driver) {
        return create(driver, DispatchMode.SINGLE_THREAD);
    }

    public static PenContext create(PenPlatformDriver driver, DispatchMode mode) {
        return create(driver, mode, null);
    }

    public static PenContext create(PenPlatformDriver driver, DispatchMode mode, Executor executor) {
        return new PenContext(Objects.requireNonNull(driver), mode, executor);
    }

    private PenContext(PenPlatformDriver driver, DispatchMode mode, Executor executor) {
        this.driver = driver;
        if (executor != null) {
            this.dispatchExecutor = executor;
        } else {
            if (mode == DispatchMode.DIRECT) {
                this.dispatchExecutor = Runnable::run;
            } else {
                this.dispatchExecutor = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "pen4j-event-dispatch");
                    t.setDaemon(true);
                    return t;
                });
            }
        }
    }

    // ---------- 生命周期 ----------

    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("PenContext starting with driver: {}", driver.getClass().getSimpleName());
            driver.start(new PenListener() {
                @Override
                public void onPenData(PenEvent event) {
                    latestEventMap.put(event.getDevice().getUid(), event);
                    for (PenListener listener : listeners) {
                        try {
                            dispatchExecutor.execute(() -> listener.onPenData(event));
                        } catch (Exception e) {
                            log.warn("Error dispatching pen event", e);
                        }
                    }
                }

                @Override
                public void onDeviceAdded(PenDevice device) {
                    log.info("Device added: {}", device.getName());
                    for (PenListener listener : listeners) {
                        try {
                            dispatchExecutor.execute(() -> listener.onDeviceAdded(device));
                        } catch (Exception e) {
                            log.warn("Error dispatching device added", e);
                        }
                    }
                }

                @Override
                public void onDeviceRemoved(PenDevice device) {
                    log.info("Device removed: {}", device.getName());
                    latestEventMap.remove(device.getUid());
                    for (PenListener listener : listeners) {
                        try {
                            dispatchExecutor.execute(() -> listener.onDeviceRemoved(device));
                        } catch (Exception e) {
                            log.warn("Error dispatching device removed", e);
                        }
                    }
                }
            });
        } else {
            log.debug("PenContext already running, start() ignored");
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("PenContext stopping");
            driver.stop();
        }
    }

    // ---------- 监听器管理 ----------

    public void addListener(PenListener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    public void removeListener(PenListener listener) {
        listeners.remove(listener);
    }

    // ---------- 设备与轮询 ----------

    public List<PenDevice> getDevices() {
        return driver.getDevices();
    }

    public PenEvent poll(PenDevice device) {
        synchronized (pollLock) {
            return latestEventMap.get(device.getUid());
        }
    }

    @Override
    public void close() {
        stop();
        listeners.clear();
        if (dispatchExecutor instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dispatchExecutor).close();
            } catch (Exception ignore) {
                log.debug("Error closing dispatch executor", ignore);
            }
        }
    }
}