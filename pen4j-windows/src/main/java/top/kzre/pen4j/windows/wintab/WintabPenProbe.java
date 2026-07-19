package top.kzre.pen4j.windows.wintab;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.WinDef.UINT;
import lombok.extern.slf4j.Slf4j;
import top.kzre.pen4j.api.PenCapability;
import top.kzre.pen4j.api.PenCursorType;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WinTab 笔设备探测器（单例）。
 * 提供设备枚举、缓存，以及基于弱引用的设备变更监听（自动启停后台轮询）。
 */
@Slf4j
public final class WintabPenProbe {

    // ───────── 设备变更监听器接口 ─────────
    public interface DeviceChangeListener {
        void onDeviceAdded(WintabPenDevice device);
        void onDeviceRemoved(WintabPenDevice device);
    }

    // ───────── 单例 ─────────
    private static final WintabPenProbe INSTANCE = new WintabPenProbe();

    private WintabPenProbe() {
        refreshDevices(); // 初始化设备列表
    }

    public static WintabPenProbe getInstance() {
        return INSTANCE;
    }

    // ───────── 设备变更监听（弱引用） ─────────
    private final ScheduledPoll poller = new ScheduledPoll();

    public void addDeviceListener(DeviceChangeListener listener) {
        poller.addListener(listener);
    }

    public void removeDeviceListener(DeviceChangeListener listener) {
        poller.removeListener(listener);
    }

    // ───────── 设备缓存 ─────────
    private final Object cacheLock = new Object();
    private volatile List<WintabPenDevice> cachedDevices = Collections.emptyList();

    /**
     * 获取当前缓存的设备列表（不可变快照）。
     */
    public List<WintabPenDevice> getDevices() {
        synchronized (cacheLock) {
            return new ArrayList<>(cachedDevices);
        }
    }

    /**
     * 强制刷新设备列表（主动探测）。
     * 内部调用原始探测逻辑，并更新缓存。
     */
    public void refreshDevices() {
        List<WintabPenDevice> fresh = doProbeAll();
        synchronized (cacheLock) {
            cachedDevices = Collections.unmodifiableList(fresh);
        }
        log.debug("Device cache refreshed, {} devices found", fresh.size());
    }

    // ───────── 公开静态方法（兼容旧调用） ─────────
    /**
     * 静态探测方法，保持原有 JNA 调用逻辑不变。
     * 该方法每次都会触发完整的设备枚举，但不会影响单例缓存。
     * @return 探测到的设备列表
     */
    public static List<WintabPenDevice> probeAll() {
        // 直接调用原始实现，确保行为与之前完全一致
        return doProbeAll();
    }

    // ───────── 内部轮询器 ─────────
    private class ScheduledPoll {
        private final List<WeakReference<DeviceChangeListener>> listeners = new ArrayList<>();
        private final Object listenersLock = new Object();

        private Thread pollThread;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private List<WintabPenDevice> lastSnapshot = Collections.emptyList();

        void addListener(DeviceChangeListener listener) {
            synchronized (listenersLock) {
                // 清理已释放的弱引用
                listeners.removeIf(ref -> ref.get() == null);
                // 避免重复添加
                for (WeakReference<DeviceChangeListener> ref : listeners) {
                    if (ref.get() == listener) return;
                }
                listeners.add(new WeakReference<>(listener));
                updatePollingState();
            }
        }

        void removeListener(DeviceChangeListener listener) {
            synchronized (listenersLock) {
                listeners.removeIf(ref -> {
                    DeviceChangeListener l = ref.get();
                    return l == null || l == listener;
                });
                updatePollingState();
            }
        }

        private int countActiveListeners() {
            synchronized (listenersLock) {
                listeners.removeIf(ref -> ref.get() == null);
                return listeners.size();
            }
        }

        private void updatePollingState() {
            boolean hasListeners = countActiveListeners() > 0;
            if (hasListeners && running.compareAndSet(false, true)) {
                startPolling();
            } else if (!hasListeners && running.compareAndSet(true, false)) {
                stopPolling();
            }
        }

        private void startPolling() {
            lastSnapshot = getDevices();   // 使用外部类的缓存快照
            pollThread = new Thread(this::pollLoop, "Wintab-Device-Poller");
            pollThread.setDaemon(true);
            pollThread.start();
            log.debug("Wintab device polling started");
        }

        private void stopPolling() {
            if (pollThread != null) {
                pollThread.interrupt();
                pollThread = null;
            }
            log.debug("Wintab device polling stopped");
        }

        private void pollLoop() {
            while (running.get()) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (countActiveListeners() == 0) {
                    running.set(false);
                    break;
                }
                checkForDeviceChanges();
            }
            running.set(false);
        }

        private void checkForDeviceChanges() {
            // 主动刷新设备列表（这会调用 doProbeAll 并更新缓存）
            refreshDevices();
            List<WintabPenDevice> current = getDevices();

            Set<String> oldUids = new HashSet<>();
            for (WintabPenDevice d : lastSnapshot) oldUids.add(d.getUid());
            Set<String> newUids = new HashSet<>();
            for (WintabPenDevice d : current) newUids.add(d.getUid());

            List<DeviceChangeListener> activeListeners = new ArrayList<>();
            synchronized (listenersLock) {
                listeners.removeIf(ref -> ref.get() == null);
                for (WeakReference<DeviceChangeListener> ref : listeners) {
                    DeviceChangeListener l = ref.get();
                    if (l != null) activeListeners.add(l);
                }
            }

            for (WintabPenDevice d : current) {
                if (!oldUids.contains(d.getUid())) {
                    for (DeviceChangeListener l : activeListeners) {
                        l.onDeviceAdded(d);
                    }
                }
            }
            for (WintabPenDevice d : lastSnapshot) {
                if (!newUids.contains(d.getUid())) {
                    for (DeviceChangeListener l : activeListeners) {
                        l.onDeviceRemoved(d);
                    }
                }
            }
            lastSnapshot = current;
        }
    }

    // ───────── 原始探测逻辑（私有，保持与之前完全一致） ─────────
    /**
     * 核心设备探测，保持原有 JNA 调用不变。
     */
    private static List<WintabPenDevice> doProbeAll() {
        List<WintabPenDevice> list = new ArrayList<>();
        try {
            int deviceIdx = 0;
            WintabPenDevice pen = probeCursor(deviceIdx, 0);
            if (pen != null) list.add(pen);
            WintabPenDevice eraser = probeCursor(deviceIdx, 1);
            if (eraser != null) list.add(eraser);
        } catch (Exception e) {
            log.error("Failed to probe WinTab devices", e);
        }
        if (list.isEmpty()) {
            log.warn("No WinTab devices found, using fallback defaults");
            list.add(createFallbackPen());
            list.add(createFallbackEraser());
        }
        return list;
    }

    // ───────── 以下全部是原有私有方法，未做任何更改 ─────────
    private static WintabPenDevice probeCursor(int deviceIdx, int cursorIdx) {
        try {
            // 1. 检查光标是否存在（CSR_NAME）
            Memory tmp = new Memory(4);
            UINT ret = WintabLibrary.INSTANCE.WTInfoW(
                    new UINT(WintabConst.WTI_CURSORS + cursorIdx),
                    new UINT(WintabConst.CSR_NAME), tmp);
            if (ret.intValue() == 0) {
                log.debug("Cursor {}/{} not available", deviceIdx, cursorIdx);
                return null;
            }

            // 2. 设备名
            Memory devNameBuf = new Memory(256);
            WintabLibrary.INSTANCE.WTInfoW(
                    new UINT(WintabConst.WTI_DEVICES + deviceIdx),
                    new UINT(WintabConst.DVC_NAME), devNameBuf);
            String devName = devNameBuf.getWideString(0);

            // 3. 光标类型
            Memory csrTypeBuf = new Memory(4);
            WintabLibrary.INSTANCE.WTInfoW(
                    new UINT(WintabConst.WTI_CURSORS + cursorIdx),
                    new UINT(WintabConst.CSR_TYPE), csrTypeBuf);
            int csrType = csrTypeBuf.getInt(0);

            // 4. 压力范围 (DVC_NPRESSURE)
            Memory prBuf = new Memory(8);
            UINT prRet = WintabLibrary.INSTANCE.WTInfoW(
                    new UINT(WintabConst.WTI_DEVICES + deviceIdx),
                    new UINT(WintabConst.DVC_NPRESSURE), prBuf);
            int maxPressure = 8192; // 默认
            if (prRet.intValue() >= 8) {
                int minPr = prBuf.getInt(0);
                int maxPr = prBuf.getInt(4);
                maxPressure = Math.max(maxPr, 1);
            }

            // 5. 从默认上下文获取坐标范围
            LOGCONTEXTW defCtx = new LOGCONTEXTW();
            UINT defRet = WintabLibrary.INSTANCE.WTInfoW(
                    new UINT(WintabConst.WTI_DEFCONTEXT),
                    new UINT(0), defCtx.getPointer());
            defCtx.read();
            int inOrgX = (int) defCtx.lcInOrgX.longValue();
            int inOrgY = (int) defCtx.lcInOrgY.longValue();
            int inExtX = (int) defCtx.lcInExtX.longValue();
            int inExtY = (int) defCtx.lcInExtY.longValue();
            if (inExtX <= 0) inExtX = 20000;
            if (inExtY <= 0) inExtY = 15000;
            int maxX = inExtX - 1;
            int maxY = inExtY - 1;

            // 6. 能力判断
            Set<PenCapability> caps = EnumSet.of(
                    PenCapability.PRESSURE, PenCapability.PROXIMITY,
                    PenCapability.SIDE_BUTTON, PenCapability.ABSOLUTE_MODE);

            // 倾斜 (DVC_ORIENTATION)
            Memory orBuf = new Memory(12);
            UINT orRet = WintabLibrary.INSTANCE.WTInfoW(
                    new UINT(WintabConst.WTI_DEVICES + deviceIdx),
                    new UINT(WintabConst.DVC_ORIENTATION), orBuf);
            if (orRet.intValue() > 0) {
                caps.add(PenCapability.TILT);
            }

            // 切向压力 (DVC_TPRESSURE)
            Memory tpBuf = new Memory(8);
            UINT tpRet = WintabLibrary.INSTANCE.WTInfoW(
                    new UINT(WintabConst.WTI_DEVICES + deviceIdx),
                    new UINT(WintabConst.DVC_TPRESSURE), tpBuf);
            if (tpRet.intValue() > 0) {
                caps.add(PenCapability.TANGENTIAL_PRESSURE);
            }

            // 橡皮擦
            if ((csrType & WintabConst.CSR_TYPE_ERASER) != 0) {
                caps.add(PenCapability.ERASER);
            }

            // 多笔区分（若驱动支持）
            caps.add(PenCapability.MULTIPEN);

            // 旋转 (PK_ROTATION) 通过在打开上下文后检测，这里先不加

            // 7. 光标类型映射
            Set<PenCursorType> cursors = EnumSet.noneOf(PenCursorType.class);
            if ((csrType & WintabConst.CSR_TYPE_PEN) != 0) cursors.add(PenCursorType.PEN);
            if ((csrType & WintabConst.CSR_TYPE_AIRBRUSH) != 0) cursors.add(PenCursorType.AIRBRUSH);
            if ((csrType & WintabConst.CSR_TYPE_ERASER) != 0) cursors.add(PenCursorType.ERASER);
            if ((csrType & WintabConst.CSR_TYPE_LENS) != 0) cursors.add(PenCursorType.LENS_CURSOR);
            if ((csrType & WintabConst.CSR_TYPE_CURSOR) != 0) cursors.add(PenCursorType.CURSOR);
            if (cursors.isEmpty()) cursors.add(PenCursorType.PEN);

            return new WintabPenDevice(
                    deviceIdx, cursorIdx, devName,
                    maxX, maxY, maxPressure,
                    inOrgX, inOrgY, inExtX, inExtY,
                    caps, cursors);
        } catch (Exception e) {
            log.warn("Failed to probe cursor {}/{}: {}", deviceIdx, cursorIdx, e.getMessage());
            return null;
        }
    }

    private static WintabPenDevice createFallbackPen() {
        return new WintabPenDevice(
                0, 0, "Pen Tablet",
                19999, 14999, 8192,
                0, 0, 20000, 15000,
                EnumSet.of(PenCapability.PRESSURE, PenCapability.TILT, PenCapability.PROXIMITY),
                EnumSet.of(PenCursorType.PEN));
    }

    private static WintabPenDevice createFallbackEraser() {
        return new WintabPenDevice(
                0, 1, "Pen Tablet Eraser",
                19999, 14999, 8192,
                0, 0, 20000, 15000,
                EnumSet.of(PenCapability.PRESSURE, PenCapability.ERASER),
                EnumSet.of(PenCursorType.ERASER));
    }
}