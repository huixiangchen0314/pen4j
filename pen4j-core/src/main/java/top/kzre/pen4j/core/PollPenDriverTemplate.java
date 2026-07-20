package top.kzre.pen4j.core;

import lombok.extern.slf4j.Slf4j;
import top.kzre.pen4j.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 轮询式笔驱动模板，实现 {@link PenPlatformDriver}。
 * <p>
 * 构造时注入一个 {@link PollPenDriver} 实现，负责：
 * <ol>
 *   <li>向全局 {@link SmartTaskLoop} 注册设备探测和事件轮询任务</li>
 *   <li>维护设备 UID → {@link PenDevice} 映射</li>
 *   <li>调用 {@link PenListener} 通知设备增删和笔数据</li>
 * </ol>
 * 该模板为 final，确保所有平台驱动的生命周期管理完全一致。
 */
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

    /**
     * @param driver 具体平台驱动实例（已创建，未启动）
     */
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

        // 1. 平台驱动自身的初始化
        try {
            log.info("Starting platform driver...");
            driver.onStart();
            log.info("Platform driver started successfully");
        } catch (Exception e) {
            started.set(false);
            log.error("Failed to start platform driver", e);
            throw new RuntimeException("driver.onStart failed", e);
        }

        // 2. 立即执行一次设备枚举，填充初始列表
        probeDevices();

        // 3. 向全局循环注册任务（若循环未运行会自动启动）
        TaskLoops.deviceProbe().register(probeTask);
        TaskLoops.eventPoll().register(pollTask);
        log.info("Registered probe and poll tasks to global loops");
    }

    @Override
    public void stop() {
        if (!started.compareAndSet(true, false)) {
            return; // 已经停止
        }

        log.info("Stopping platform driver...");
        // 1. 先停止事件轮询（避免后续空指针）
        TaskLoops.eventPoll().unregister(pollTask);
        // 2. 停止设备探测
        TaskLoops.deviceProbe().unregister(probeTask);

        // 3. 停止平台驱动
        try {
            driver.onStop();
            log.info("Platform driver stopped successfully");
        } catch (Exception e) {
            log.error("Error stopping platform driver", e);
        }

        // 4. 清理资源
        listener = null;
        devices.clear();
        uidToDevice.clear();
    }

    // ── 设备探测逻辑（由探测线程和主动调用触发） ──

    /**
     * 调用驱动的 {@link PollPenDriver#enumerateDevices()} 获取最新设备列表，
     * 与内部映射比较，触发设备增删回调。
     */
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
                    existingUids.remove(uid);   // 设备仍然存在
                } else {
                    // 新设备
                    uidToDevice.put(uid, dev);
                    devices.add(dev);
                    log.info("Device added: {} (UID: {})", dev.getName(), uid);
                    if (listener != null) {
                        listener.onDeviceAdded(dev);
                    }
                }
            }

            // 移除已消失的设备
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

    // ── 事件轮询及分发逻辑（由轮询线程调用） ──

    /**
     * 从驱动拉取一个笔事件，根据设备 UID 找到对应设备对象，
     * 并通过监听器分发给上层。若设备未知，立即触发一次同步探测。
     */
    private void pollAndDispatch() {
        if (!started.get()) return;
        try {
            PenEvent event = driver.pollEvent();
            if (event == null) return;

            String uid = event.getDevice().getUid();
            PenDevice device = uidToDevice.get(uid);
            if (device == null) {
                log.debug("Received event from unknown device UID: {}. Triggering immediate probe.", uid);
                // 未知设备 → 立刻触发一次探测（同步，保证设备发现）
                probeDevices();
                device = uidToDevice.get(uid);
                if (device == null) {
                    log.warn("Still unable to find device for UID: {}. Dropping event.", uid);
                    return;
                }
            }

            // 确保事件携带正确的设备对象（防御性拷贝）
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