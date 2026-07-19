package top.kzre.pen4j.windows.wintab;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import lombok.extern.slf4j.Slf4j;
import top.kzre.pen4j.api.*;
import top.kzre.pen4j.core.DefaultPenEvent;
import top.kzre.pen4j.core.PenPlatformDriver;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class WintabDriver implements PenPlatformDriver {

    private static final int MAX_PACKETS = 32;
    private static final int POLL_INTERVAL_MS = 8;

    private final HWND hwnd;
    private final List<WintabPenDevice> devices = new CopyOnWriteArrayList<>();
    private final AtomicReference<PenListener> listenerRef = new AtomicReference<>();

    private WintabContext context;
    private WintabPacketParser parser;
    private volatile boolean running;
    private Thread pollThread;

    // 设备变更监听器，保持强引用
    private final WintabPenProbe.DeviceChangeListener deviceChangeListener =
            new WintabPenProbe.DeviceChangeListener() {
                @Override
                public void onDeviceAdded(WintabPenDevice device) {
                    devices.add(device);
                    PenListener listener = listenerRef.get();
                    if (listener != null) {
                        listener.onDeviceAdded(device);
                    }
                }

                @Override
                public void onDeviceRemoved(WintabPenDevice device) {
                    devices.remove(device);
                    PenListener listener = listenerRef.get();
                    if (listener != null) {
                        listener.onDeviceRemoved(device);
                    }
                }
            };

    public WintabDriver(long nativeWindowHandle) {
        this.hwnd = new HWND(Pointer.createConstant(nativeWindowHandle));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (context != null) cleanup();
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

        // 使用单例探测器刷新并加载当前设备列表
        WintabPenProbe probe = WintabPenProbe.getInstance();
        probe.refreshDevices();
        List<WintabPenDevice> initialDevices = probe.getDevices();
        devices.clear();
        devices.addAll(initialDevices);
        initialDevices.forEach(listener::onDeviceAdded);

        // 注册设备热插拔监听
        probe.addDeviceListener(deviceChangeListener);

        // 数据掩码（与原有逻辑一致）
        long mask = WintabConst.PK_TIME | WintabConst.PK_X | WintabConst.PK_Y |
                WintabConst.PK_NORMALPRESSURE | WintabConst.PK_STATUS |
                WintabConst.PK_BUTTONS | WintabConst.PK_CHANGED |
                WintabConst.PK_ORIENTATION | WintabConst.PK_ROTATION;

        // 创建上下文（轮询模式）
        context = new WintabContext(hwnd, mask);

        // 打开上下文
        User32.INSTANCE.SetForegroundWindow(hwnd);
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}

        try {
            context.open();
        } catch (RuntimeException e) {
            cleanup();
            throw new RuntimeException("WTOpenW failed", e);
        }

        parser = new WintabPacketParser(context.getPktDataMask());

        // 启动纯轮询线程（数据包解析部分完全不变）
        running = true;
        pollThread = new Thread(() -> {
            while (running) {
                if (context != null && context.getHandle() != null) {
                    pollPackets();
                }
                try { Thread.sleep(POLL_INTERVAL_MS); } catch (InterruptedException e) { break; }
            }
        }, "pen4j-wintab-poll");
        pollThread.setDaemon(true);
        pollThread.start();

        log.info("WintabDriver started (poll-only thread, with device change listener)");
    }

    @Override
    public void stop() {
        running = false;
        cleanup();
    }

    private synchronized void cleanup() {
        // 移除设备变更监听
        WintabPenProbe.getInstance().removeDeviceListener(deviceChangeListener);

        if (pollThread != null) {
            pollThread.interrupt();
            try { pollThread.join(2000); } catch (InterruptedException ignored) {}
            pollThread = null;
        }
        if (context != null) {
            context.close();
            context = null;
        }
        parser = null;
        devices.clear();
        log.info("WintabDriver cleaned up.");
    }

    // ─── 以下方法完全不变 ───
    private void pollPackets() {
        HANDLE hCtx = context.getHandle();
        if (hCtx == null || Pointer.nativeValue(hCtx.getPointer()) == 0) return;

        PenListener listener = listenerRef.get();
        if (listener == null || parser == null) return;

        int packetSize = parser.calcPacketSize();
        Memory buf = new Memory((long) MAX_PACKETS * packetSize);
        int count = WintabLibrary.INSTANCE.WTPacketsGet(hCtx, MAX_PACKETS, buf);
        if (count <= 0) return;

        for (int i = 0; i < count; i++) {
            byte[] raw = new byte[packetSize];
            buf.read((long) i * packetSize, raw, 0, packetSize);
            PACKET pkt = new PACKET(raw, context.getPktDataMask());
            WintabPenDevice dev = devices.isEmpty() ? null : devices.get(0);
            if (dev == null) continue;

            PenState state = parser.parse(pkt, dev);
            int pkTime = (int) pkt.getPkTime();
            PenEvent event = new DefaultPenEvent(dev, pkTime * 1000L, state);
            listener.onPenData(event);
        }
    }
}