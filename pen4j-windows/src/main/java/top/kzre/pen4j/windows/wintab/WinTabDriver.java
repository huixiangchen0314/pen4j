package top.kzre.pen4j.windows.wintab;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import lombok.extern.slf4j.Slf4j;
import top.kzre.pen4j.api.*;
import top.kzre.pen4j.core.DefaultPenEvent;
import top.kzre.pen4j.core.PenPlatformDriver;
import top.kzre.pen4j.windows.common.PenVendorVIDTable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class WinTabDriver implements PenPlatformDriver {

    private final List<WTPDevice> devices = new ArrayList<>();
    private final AtomicReference<PenListener> listenerRef = new AtomicReference<>();

    private volatile Pointer wtpContext;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private Thread pollThread;

    @Override
    public boolean isAvailable() {
        // 简单尝试加载 DLL 并检查能否创建上下文（不实际启动）
        Pointer testCtx = WTPApi.INSTANCE.WTPCreate();
        if (testCtx == null) return false;
        WTPApi.INSTANCE.WTPDestroy(testCtx);
        return true;
    }

    @Override
    public List<PenDevice> getDevices() {
        return Collections.unmodifiableList(new ArrayList<>(devices));
    }

    @Override
    public void start(PenListener listener) {
        if (started.getAndSet(true)) {
            log.warn("WTPDriver already started");
            return;
        }
        Objects.requireNonNull(listener);
        listenerRef.set(listener);

        // 1. 创建胶水层上下文
        wtpContext = WTPApi.INSTANCE.WTPCreate();
        if (wtpContext == null) {
            throw new RuntimeException("WTPCreate failed");
        }

        // 2. 启动轮询（回调为 null，使用轮询模式）
        int status = WTPApi.INSTANCE.WTPStart(wtpContext, null);
        if (status != 0) {
            String err = WTPApi.INSTANCE.WTPGetLastError(wtpContext);
            WTPApi.INSTANCE.WTPDestroy(wtpContext);
            wtpContext = null;
            throw new RuntimeException("WTPStart failed: " + err);
        }

        // 3. 从胶水层获取设备信息，构建唯一设备对象
        WTPDevice device = buildDeviceFromGlue();
        devices.add(device);
        listener.onDeviceAdded(device);

        // 4. 启动轮询线程
        pollThread = new Thread(this::pollLoop, "WTP-Poll");
        pollThread.setDaemon(true);
        pollThread.start();

        log.info("WTPDriver started (pure glue, no JNA WinTab calls)");
    }

    @Override
    public void stop() {
        if (!started.getAndSet(false)) return;

        if (pollThread != null) {
            pollThread.interrupt();
            try { pollThread.join(2000); } catch (InterruptedException ignored) {}
            pollThread = null;
        }

        if (wtpContext != null) {
            WTPApi.INSTANCE.WTPStop(wtpContext);
            WTPApi.INSTANCE.WTPDestroy(wtpContext);
            wtpContext = null;
        }

        devices.clear();
        listenerRef.set(null);
        log.info("WTPDriver stopped");
    }

    private WTPDevice buildDeviceFromGlue() {
        WTPDevice device = new WTPDevice();

        // 压力范围
        IntByReference min = new IntByReference();
        IntByReference max = new IntByReference();
        if (WTPApi.INSTANCE.WTPGetPressureRange(wtpContext, min, max) == 0) {
            device.maxPressure = max.getValue();
        }

        // 坐标范围
        IntByReference maxX = new IntByReference();
        IntByReference maxY = new IntByReference();
        if (WTPApi.INSTANCE.WTPGetLogicalRange(wtpContext, maxX, maxY) == 0) {
            device.maxX = maxX.getValue();
            device.maxY = maxY.getValue();
        }

        // 按钮数量
        IntByReference btnCount = new IntByReference();
        if (WTPApi.INSTANCE.WTPGetButtonCount(wtpContext, btnCount) == 0) {
            device.sideButtonCount = btnCount.getValue();
        }

        // 设备名称
        String name = WTPApi.INSTANCE.WTPGetDeviceName(wtpContext);
        if (name != null && !name.isEmpty()) {
            device.name = name;
        }

        // VID/PID 和厂商
        IntByReference vidRef = new IntByReference();
        IntByReference pidRef = new IntByReference();
        if (WTPApi.INSTANCE.WTPGetDeviceVid(wtpContext, vidRef) == 0) {
            int vid = vidRef.getValue();
            device.vid = vid;
            device.vendor = PenVendorVIDTable.getVendorName(vid);
        }
        if (WTPApi.INSTANCE.WTPGetDevicePid(wtpContext, pidRef) == 0) {
            device.pid = pidRef.getValue();
        }

        // 唯一标识
        String uid = WTPApi.INSTANCE.WTPGetDeviceUid(wtpContext);
        if (uid != null && !uid.isEmpty()) {
            device.uid = uid;
        }

        return device;
    }

    private void pollLoop() {
        WTPEvent.ByReference event = new WTPEvent.ByReference();
        while (started.get() && wtpContext != null) {
            int got = WTPApi.INSTANCE.WTPPollEvent(wtpContext, event);
            if (got == 1) {
                PenListener listener = listenerRef.get();
                if (listener == null) continue;

                WTPDevice device = devices.isEmpty() ? null : devices.get(0);
                if (device == null) continue;

                int buttonsMask = event.buttons & 0xFFFF;
                PenState state = PenState.builder()
                        .x(event.x)
                        .y(event.y)
                        .pressure(event.pressure)
                        .tangentialPressure(event.tangentialPressure)
                        .tiltX(event.tiltX)
                        .tiltY(event.tiltY)
                        .azimuth(event.azimuth)
                        .altitude(event.altitude)
                        .twist(event.twist)
                        .roll(event.roll)
                        .pitch(event.pitch)
                        .yaw(event.yaw)
                        .near(event.proximity != 0)
                        .tipPressed(event.tip != 0)
                        .eraserPressed(event.eraser != 0)
                        .buttons(buttonsMask)
                        .cursorType(event.eraser != 0 ? PenCursorType.ERASER : PenCursorType.PEN)
                        .build();

                PenEvent penEvent = new DefaultPenEvent(device, System.currentTimeMillis() * 1000, state);
                listener.onPenData(penEvent);
            } else {
                try { Thread.sleep(1); } catch (InterruptedException e) { break; }
            }
        }
    }

    // 内部设备类，不再依赖 WintabPenDevice
    private static class WTPDevice implements PenDevice {
        String name = "WinTab Pen";
        String vendor = "Unknown";
        String uid = "wintab-pen-0";
        int vid = 0;
        int pid = 0;
        int maxX = 65535, maxY = 65535;
        int maxPressure = 1023;
        int sideButtonCount = 2;
        final Set<PenCapability> caps = EnumSet.of(
                PenCapability.PRESSURE, PenCapability.TILT, PenCapability.PROXIMITY,
                PenCapability.SIDE_BUTTON, PenCapability.ABSOLUTE_MODE);
        final Set<PenCursorType> cursorTypes = EnumSet.of(PenCursorType.PEN);

        @Override public String getName() { return name; }
        @Override public String getVendor() { return vendor; }
        @Override public String getUid() { return uid; }
        @Override public int getVid() { return vid; }
        @Override public int getMaxX() { return maxX; }
        @Override public int getMaxY() { return maxY; }
        @Override public int getMaxPressure() { return maxPressure; }
        @Override public int getMaxProximity() { return 0; }
        @Override public int getSideButtonCount() { return sideButtonCount; }
        @Override public boolean supports(PenCapability cap) { return caps.contains(cap); }
        @Override public Set<PenCursorType> getSupportedCursorTypes() { return cursorTypes; }
    }
}