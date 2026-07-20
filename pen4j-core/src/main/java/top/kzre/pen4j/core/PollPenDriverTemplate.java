package top.kzre.pen4j.core;

import lombok.extern.slf4j.Slf4j;
import top.kzre.pen4j.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public final class PollPenDriverTemplate implements PenPlatformDriver {

    private final PollPenDriver driver;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private PenListener listener;

    // 设备列表与映射（线程安全）
    private final List<PenDevice> devices = new CopyOnWriteArrayList<>();
    private final Map<String, PenDevice> uidToDevice = new ConcurrentHashMap<>();

    // 注册到 SmartTaskLoop 的两个任务
    private final Runnable probeTask = this::probeDevices;
    private final Runnable pollTask  = this::pollAndDispatch;

    // JVM 关闭钩子
    private final Thread shutdownHook = new Thread(this::onShutdown, "PenDriverShutdownHook");

    public PollPenDriverTemplate(PollPenDriver driver) {
        this.driver = Objects.requireNonNull(driver, "driver must not be null");
    }

    // ── PenPlatformDriver 接口实现 ──

    @Override
    public boolean isAvailable() {
        return driver.isAvailable();
    }

    @Override
    public List<PenDevice> getDevices() {
        return Collections.unmodifiableList(devices);
    }

    @Override
    public void start(PenListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("already started");
        }
        this.listener = listener;

        try {
            log.info("Starting platform driver...");
            driver.onStart();
            log.info("Platform driver started successfully");
        } catch (Exception e) {
            started.set(false);
            log.error("Failed to start platform driver", e);
            throw new RuntimeException("driver.onStart failed", e);
        }

        // 立即执行一次设备枚举
        probeDevices();

        // 注册全局任务
        TaskLoops.deviceProbe().register(probeTask);
        TaskLoops.eventPoll().register(pollTask);
        log.info("Registered probe and poll tasks to global loops");

        // 注册 JVM 关闭钩子
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            log.debug("Registered shutdown hook");
        } catch (IllegalStateException e) {
            // JVM 已经在关闭过程中，无法注册钩子
            log.warn("Could not register shutdown hook, JVM may be shutting down", e);
        }
    }

    @Override
    public void stop() {
        if (!started.compareAndSet(true, false)) {
            return; // 已停止或未启动
        }

        log.info("Stopping platform driver...");

        // 1. 移除 JVM 关闭钩子（防止重复调用）
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
            log.debug("Removed shutdown hook");
        } catch (IllegalStateException e) {
            // JVM 在关闭时可能无法移除，忽略
        }

        // 2. 停止事件轮询任务
        TaskLoops.eventPoll().unregister(pollTask);
        // 3. 停止设备探测任务
        TaskLoops.deviceProbe().unregister(probeTask);

        // 4. 停止平台驱动
        try {
            driver.onStop();
            log.info("Platform driver stopped successfully");
        } catch (Exception e) {
            log.error("Error stopping platform driver", e);
        }

        // 5. 清理资源
        listener = null;
        devices.clear();
        uidToDevice.clear();
    }

    // ── JVM 关闭钩子回调 ──
    private void onShutdown() {
        log.info("Shutdown hook triggered, stopping driver...");
        stop(); // stop() 是幂等的，多次调用无害
    }

    // ── 设备探测逻辑 ──

    private void probeDevices() {
        if (!started.get()) return;
        try {
            log.debug("Starting device probe...");
            List<PenDevice> latest = driver.enumerateDevices();
            Set<String> existingUids = new HashSet<>(uidToDevice.keySet());

            for (PenDevice dev : latest) {
                String uid = dev.getUid();
                if (uid == null || uid.isEmpty()) continue;
                if (uidToDevice.containsKey(uid)) {
                    existingUids.remove(uid);
                } else {
                    uidToDevice.put(uid, dev);
                    devices.add(dev);
                    log.info("Device added: {} (UID: {})", dev.getName(), uid);
                    if (listener != null) {
                        listener.onDeviceAdded(dev);
                    }
                }
            }

            for (String oldUid : existingUids) {
                PenDevice removed = uidToDevice.remove(oldUid);
                if (removed != null) {
                    devices.remove(removed);
                    log.info("Device removed: {}", oldUid);
                    if (listener != null) {
                        listener.onDeviceRemoved(removed);
                    }
                }
            }
            log.debug("Device probe finished. Current device count: {}", devices.size());
        } catch (Exception e) {
            log.warn("Device probe failed", e);
        }
    }

    // ── 事件轮询及分发逻辑 ──

    private void pollAndDispatch() {
        if (!started.get()) return;
        try {
            PenEvent event = driver.pollEvent();
            if (event == null) return;

            String uid = event.getDevice().getUid();
            PenDevice device = uidToDevice.get(uid);
            if (device == null) {
                log.debug("Received event from unknown device UID: {}. Triggering immediate probe.", uid);
                probeDevices();
                device = uidToDevice.get(uid);
                if (device == null) {
                    log.warn("Still unable to find device for UID: {}. Dropping event.", uid);
                    return;
                }
            }

            if (event.getDevice() != device) {
                event = new DefaultPenEvent(device, event.getTimestampMicros(), event.getState());
            }

            if (listener != null) {
                listener.onPenData(event);
            }
        } catch (Exception e) {
            log.warn("Error in poll and dispatch cycle", e);
        }
    }
}