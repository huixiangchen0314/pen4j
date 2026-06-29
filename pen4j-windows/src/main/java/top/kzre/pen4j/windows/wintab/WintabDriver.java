package top.kzre.pen4j.windows.wintab;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser.MSG;
import lombok.extern.slf4j.Slf4j;
import top.kzre.pen4j.api.*;
import top.kzre.pen4j.core.DefaultPenEvent;
import top.kzre.pen4j.core.spi.PenPlatformDriver;
import top.kzre.pen4j.windows.common.BOOLS;
import top.kzre.pen4j.windows.common.UINTS;
import top.kzre.pen4j.windows.common.LONGS;
import top.kzre.pen4j.windows.common.WinConstants;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class WintabDriver implements PenPlatformDriver {

    private static final int MAX_PACKETS_PER_POLL = 32;

    private final HWND hwnd;
    private final List<WintabPenDevice> devices = new CopyOnWriteArrayList<>();
    private final AtomicReference<PenListener> listenerRef = new AtomicReference<>();

    private HANDLE hCtx;
    private volatile boolean running = false;
    private Thread pollThread;
    private long pktDataMask;

    public WintabDriver(long nativeWindowHandle) {
        this.hwnd = new HWND(Pointer.createConstant(nativeWindowHandle));
        // JVM 关闭钩子：确保即使异常退出也能释放 WinTab 上下文
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (hCtx != null) {
                log.warn("Shutdown hook triggered, forcing WinTab cleanup.");
                cleanup();
            }
        }, "wintab-shutdown-hook"));
    }

    @Override
    public boolean isAvailable() {
        try {
            WintabLibrary lib = WintabLibrary.INSTANCE;
            Memory buf = new Memory(128);
            return lib.WTInfoW(new UINT(WintabConst.WTI_INTERFACE),
                    new UINT(WintabConst.IFC_WINTABID), buf).intValue() != 0;
        } catch (UnsatisfiedLinkError | Exception e) {
            return false;
        }
    }

    @Override
    public List<PenDevice> getDevices() {
        return Collections.unmodifiableList(new ArrayList<>(devices));
    }

    @Override
    public void start(PenListener listener) {
        if (running) {
            log.warn("WintabDriver already started");
            return;
        }
        Objects.requireNonNull(listener);
        listenerRef.set(listener);

        if (!User32.INSTANCE.IsWindowVisible(hwnd)) {
            log.warn("Window (HWND={}) is not visible. WinTab may not work.", hwnd);
        }

        // 确保窗口获得焦点（部分驱动要求）
        User32.INSTANCE.SetForegroundWindow(hwnd);
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}

        enumerateDevices();
        devices.forEach(listener::onDeviceAdded);

        // 1. 先启动消息泵线程（确保窗口消息循环就绪）
        running = true;
        CountDownLatch pumpStarted = new CountDownLatch(1);
        pollThread = new Thread(() -> {
            MSG msg = new MSG();
            pumpStarted.countDown();
            while (running) {
                // 处理窗口消息
                while (User32.INSTANCE.PeekMessage(msg, hwnd, 0, 0, WinConstants.PM_REMOVE)) {
                    User32.INSTANCE.TranslateMessage(msg);
                    User32.INSTANCE.DispatchMessage(msg);
                }
                // 上下文就绪后才轮询数据包
                if (hCtx != null && Pointer.nativeValue(hCtx.getPointer()) != 0) {
                    pollOnce();
                }
                try {
                    Thread.sleep(8);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "pen4j-wintab-poll");
        pollThread.setDaemon(true);
        pollThread.start();

        try {
            pumpStarted.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
            return;
        }

        // 2. 打开 WinTab 上下文
        LOGCONTEXTW ctx = buildContext(hwnd);
        hCtx = WintabLibrary.INSTANCE.WTOpenW(hwnd, ctx, BOOLS.TRUE);
        if (hCtx == null || Pointer.nativeValue(hCtx.getPointer()) == 0) {
            log.error("WTOpenW failed. Options=0x{}, Status=0x{}, InExt={}/{}",
                    ctx.lcOptions != null ? Long.toHexString(ctx.lcOptions.longValue()) : "null",
                    ctx.lcStatus != null ? Long.toHexString(ctx.lcStatus.longValue()) : "null",
                    ctx.lcInExtX, ctx.lcInExtY);
            cleanup(); // 清理已启动的线程等
            throw new RuntimeException("WTOpenW failed – ensure tablet driver is running and window is valid");
        }
        WintabLibrary.INSTANCE.WTEnable(hCtx, BOOLS.TRUE);
        log.info("Wintab context opened, HCTX={}", hCtx);
    }

    @Override
    public void stop() {
        running = false;
        cleanup();
    }

    /**
     * 释放所有资源（幂等，线程安全）。
     * 由 stop() 或 JVM 关闭钩子调用。
     */
    private synchronized void cleanup() {
        if (hCtx == null) {
            return; // 已经释放
        }

        // 停止轮询线程
        if (pollThread != null) {
            pollThread.interrupt();
            try {
                pollThread.join(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        // 关闭 WinTab 上下文
        try {
            if (Pointer.nativeValue(hCtx.getPointer()) != 0) {
                WintabLibrary.INSTANCE.WTEnable(hCtx, BOOLS.FALSE);
                if (!WintabLibrary.INSTANCE.WTClose(hCtx).booleanValue()) {
                    log.warn("WTClose returned FALSE");
                }
            }
        } catch (Exception e) {
            log.error("Error closing WinTab context", e);
        } finally {
            hCtx = null;   // 置空后，再次调用 cleanup() 会直接返回
        }

        devices.clear();
        log.info("WintabDriver resources cleaned up.");
    }

    private void enumerateDevices() {
        devices.clear();
        devices.addAll(WintabPenProbe.probeAll());
    }

    private LOGCONTEXTW buildContext(HWND hwnd) {
        LOGCONTEXTW ctx = new LOGCONTEXTW();
        UINT ret = WintabLibrary.INSTANCE.WTInfoW(
                new UINT(WintabConst.WTI_DEFCONTEXT), new UINT(0), ctx.getPointer());
        if (ret.intValue() == 0) throw new RuntimeException("WTInfoW failed");
        ctx.read();

        // 直接赋值（已验证可用）
        ctx.lcOptions = new UINT(WintabConst.CXO_SYSTEM | WintabConst.CXO_PEN);

        // 最小数据掩码（保证稳定）
        long mask = WintabConst.PK_TIME | WintabConst.PK_X | WintabConst.PK_Y |
                WintabConst.PK_NORMALPRESSURE | WintabConst.PK_STATUS |
                WintabConst.PK_BUTTONS | WintabConst.PK_CHANGED;
        ctx.lcPktData = new UINT(mask);
        pktDataMask = mask;

        ctx.lcPktMode = UINTS.ZERO;
        ctx.lcMoveMask = UINTS.ZERO;
        ctx.lcBtnDnMask = UINTS.ZERO;
        ctx.lcBtnUpMask = UINTS.ZERO;

        int sw = User32.INSTANCE.GetSystemMetrics(WinUser.SM_CXSCREEN);
        int sh = User32.INSTANCE.GetSystemMetrics(WinUser.SM_CYSCREEN);
        if (ctx.lcInExtX == null || ctx.lcInExtX.longValue() == 0) ctx.lcInExtX = new LONG(sw);
        if (ctx.lcInExtY == null || ctx.lcInExtY.longValue() == 0) ctx.lcInExtY = new LONG(sh);
        if (ctx.lcInExtZ == null || ctx.lcInExtZ.longValue() == 0) ctx.lcInExtZ = new LONG(1);

        ctx.lcOutOrgX = LONGS.ZERO; ctx.lcOutOrgY = LONGS.ZERO; ctx.lcOutOrgZ = LONGS.ZERO;
        ctx.lcOutExtX = new LONG(sw); ctx.lcOutExtY = new LONG(sh);
        if (ctx.lcOutExtZ == null || ctx.lcOutExtZ.longValue() == 0) ctx.lcOutExtZ = LONGS.ONE;

        ctx.lcSensX = LONGS.FIX32_ONE; ctx.lcSensY = LONGS.FIX32_ONE; ctx.lcSensZ = LONGS.FIX32_ONE;
        ctx.write();
        return ctx;
    }

    private void pollOnce() {
        if (hCtx == null || Pointer.nativeValue(hCtx.getPointer()) == 0) return;
        PenListener listener = listenerRef.get();
        if (listener == null) return;

        int packetSize = calcPacketSize(pktDataMask);
        Memory buf = new Memory((long) MAX_PACKETS_PER_POLL * packetSize);
        int count = WintabLibrary.INSTANCE.WTPacketsGet(hCtx, MAX_PACKETS_PER_POLL, buf);
        if (count <= 0) return;

        for (int i = 0; i < count; i++) {
            byte[] raw = new byte[packetSize];
            buf.read((long) i * packetSize, raw, 0, packetSize);
            PACKET pkt = new PACKET(raw, pktDataMask);
            WintabPenDevice dev = devices.isEmpty() ? null : devices.get(0);
            if (dev == null) continue;

            PenState state = buildPenState(pkt, dev);
            int pkTime = (int) pkt.getPkTime();
            PenEvent event = new DefaultPenEvent(dev, pkTime * 1000L, state);
            listener.onPenData(event);
        }
    }

    private int calcPacketSize(long mask) {
        int size = 0;
        if ((mask & WintabConst.PK_CONTEXT) != 0) size += 8;
        if ((mask & WintabConst.PK_STATUS) != 0) size += 4;
        if ((mask & WintabConst.PK_TIME) != 0) size += 4;
        if ((mask & WintabConst.PK_CHANGED) != 0) size += 4;
        if ((mask & WintabConst.PK_SERIALNUMBER) != 0) size += 4;
        if ((mask & WintabConst.PK_CURSOR) != 0) size += 4;
        if ((mask & WintabConst.PK_BUTTONS) != 0) size += 4;
        if ((mask & WintabConst.PK_X) != 0) size += 4;
        if ((mask & WintabConst.PK_Y) != 0) size += 4;
        if ((mask & WintabConst.PK_Z) != 0) size += 4;
        if ((mask & WintabConst.PK_NORMALPRESSURE) != 0) size += 4;
        if ((mask & WintabConst.PK_TANGENTPRESSURE) != 0) size += 4;
        if ((mask & WintabConst.PK_ORIENTATION) != 0) size += 12;
        if ((mask & WintabConst.PK_ROTATION) != 0) size += 12;
        return size;
    }

    private PenState buildPenState(PACKET pkt, WintabPenDevice dev) {
        long status = pkt.getPkStatus();
        long cursor = pkt.getPkCursor();
        int buttons = (int) pkt.getPkButtons();

        double normalPressure = (double) pkt.getPkNormalPressure();
        double tangentPressure = (pktDataMask & WintabConst.PK_TANGENTPRESSURE) != 0 ?
                (double) pkt.getPkTangentPressure() : 0.0;
        double z = (pktDataMask & WintabConst.PK_Z) != 0 ? (double) pkt.getPkZ() : 0.0;

        double tiltX = 0, tiltY = 0;
        double azimuth = Double.NaN, altitude = Double.NaN, twist = Double.NaN;
        if ((pktDataMask & WintabConst.PK_ORIENTATION) != 0 && dev.supports(PenCapability.TILT)) {
            long az = pkt.getOrientationAzimuth();
            long al = pkt.getOrientationAltitude();
            long tw = pkt.getOrientationTwist();
            if (az >= 0 && al >= 0) {
                double azRad = Math.toRadians(az / 10.0);
                double alRad = Math.toRadians(al / 10.0);
                tiltX = Math.sin(azRad) * Math.sin(alRad);
                tiltY = Math.cos(azRad) * Math.sin(alRad);
                azimuth = az / 10.0;
                altitude = al / 10.0;
                twist = tw / 10.0;
            }
        }

        double roll = Double.NaN, pitch = Double.NaN, yaw = Double.NaN;
        // 旋转（PK_ROTATION）暂未启用，预留

        PenCursorType cursorType = mapCursorType((int) cursor);

        return PenState.builder()
                .x((double) pkt.getPkX())
                .y((double) pkt.getPkY())
                .z(z)
                .pressure(normalPressure)
                .tangentialPressure(tangentPressure)
                .tiltX(tiltX)
                .tiltY(tiltY)
                .azimuth(azimuth)
                .altitude(altitude)
                .twist(twist)
                .roll(roll)
                .pitch(pitch)
                .yaw(yaw)
                .cursorType(cursorType)
                .serialNumber(pkt.getPkSerialNumber())
                .buttons(buttons)
                .near((status & WintabConst.PK_PENNEAR) != 0)
                .tipPressed((status & WintabConst.PK_PENCONTACT) != 0)
                .button1Pressed((status & WintabConst.PK_PENBARREL0) != 0)
                .button2Pressed((status & WintabConst.PK_PENBARREL1) != 0)
                .eraserPressed((status & WintabConst.PK_PENERASER) != 0)
                .build();
    }

    private PenCursorType mapCursorType(int csrType) {
        if ((csrType & WintabConst.CSR_TYPE_ERASER) != 0) return PenCursorType.ERASER;
        if ((csrType & WintabConst.CSR_TYPE_AIRBRUSH) != 0) return PenCursorType.AIRBRUSH;
        if ((csrType & WintabConst.CSR_TYPE_LENS) != 0) return PenCursorType.LENS_CURSOR;
        if ((csrType & WintabConst.CSR_TYPE_CURSOR) != 0) return PenCursorType.CURSOR;
        return PenCursorType.PEN;
    }
}