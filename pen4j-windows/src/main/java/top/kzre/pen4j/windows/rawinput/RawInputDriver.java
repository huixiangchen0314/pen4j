package top.kzre.pen4j.windows.rawinput;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import lombok.extern.slf4j.Slf4j;
import top.kzre.pen4j.api.*;
import top.kzre.pen4j.core.BasePenDevice;
import top.kzre.pen4j.core.DefaultPenEvent;
import top.kzre.pen4j.core.PenPlatformDriver;
import top.kzre.pen4j.windows.common.PenVendorVIDTable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class RawInputDriver implements PenPlatformDriver {

    private final AtomicReference<PenListener> listenerRef = new AtomicReference<>();
    private final List<PenDevice> devices = new ArrayList<>();
    private final RawInputDevice currentDevice;

    private volatile Pointer ripContext;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private Thread pollThread;

    public RawInputDriver() {
        currentDevice = new RawInputDevice();
        devices.add(currentDevice);
    }

    @Override
    public boolean isAvailable() {
        return true;   // 假设 DLL 已正确加载
    }

    @Override
    public List<PenDevice> getDevices() {
        return Collections.unmodifiableList(devices);
    }

    @Override
    public void start(PenListener listener) {
        Objects.requireNonNull(listener);
        if (started.getAndSet(true)) {
            log.warn("RawInputDriver already started");
            return;
        }
        listenerRef.set(listener);
        listener.onDeviceAdded(currentDevice);

        // 1. 创建 RIP 上下文
        ripContext = RIPApi.INSTANCE.RIPCreate();
        if (ripContext == null) {
            throw new RuntimeException("RIPCreate failed");
        }

        // 2. 启动监听（不使用回调，用轮询模式）
        int status = RIPApi.INSTANCE.RIPStart(ripContext, null);
        if (status != 0) {
            String err = RIPApi.INSTANCE.RIPGetLastError(ripContext);
            RIPApi.INSTANCE.RIPDestroy(ripContext);
            ripContext = null;
            throw new RuntimeException("RIPStart failed: " + err);
        }

        // 3. 动态查询并更新设备信息
        queryAndUpdateDeviceInfo();

        // 4. 启动轮询线程
        pollThread = new Thread(this::pollLoop, "RawInput-Poll");
        pollThread.setDaemon(true);
        pollThread.start();

        log.info("RawInputDriver started (RIP)");
    }

    @Override
    public void stop() {
        if (!started.getAndSet(false)) return;

        if (pollThread != null) {
            pollThread.interrupt();
            try {
                pollThread.join(1000);
            } catch (InterruptedException ignored) {}
            pollThread = null;
        }

        if (ripContext != null) {
            RIPApi.INSTANCE.RIPStop(ripContext);
            RIPApi.INSTANCE.RIPDestroy(ripContext);
            ripContext = null;
        }

        listenerRef.set(null);
        log.info("RawInputDriver stopped");
    }

    private void queryAndUpdateDeviceInfo() {
        if (ripContext == null) return;

        // 压力范围
        IntByReference minP = new IntByReference();
        IntByReference maxP = new IntByReference();
        if (RIPApi.INSTANCE.RIPGetPressureRange(ripContext, minP, maxP) == 0) {
            currentDevice.setMaxPressure(maxP.getValue());
        }

        // 坐标范围
        IntByReference maxX = new IntByReference();
        IntByReference maxY = new IntByReference();
        if (RIPApi.INSTANCE.RIPGetLogicalRange(ripContext, maxX, maxY) == 0) {
            currentDevice.setMaxX(maxX.getValue());
            currentDevice.setMaxY(maxY.getValue());
        }

        // 按钮数量
        IntByReference btnCount = new IntByReference();
        if (RIPApi.INSTANCE.RIPGetButtonCount(ripContext, btnCount) == 0) {
            currentDevice.setSideButtonCount(btnCount.getValue());
        }

        // 设备名称
        String name = RIPApi.INSTANCE.RIPGetDeviceName(ripContext);
        if (name != null && !name.isEmpty()) {
            currentDevice.setName(name);
        }

        // VID / PID 并映射厂商名（使用公共表）
        IntByReference vidRef = new IntByReference();
        if (RIPApi.INSTANCE.RIPGetDeviceVid(ripContext, vidRef) == 0) {
            int vid = vidRef.getValue();
            currentDevice.setVid(vid);
            String vendor = PenVendorVIDTable.getVendorName(vid);
            currentDevice.setVendor(vendor);
        }
        IntByReference pidRef = new IntByReference();
        if (RIPApi.INSTANCE.RIPGetDevicePid(ripContext, pidRef) == 0) {
            currentDevice.setPid(pidRef.getValue());
        }

        // 唯一标识符
        String uid = RIPApi.INSTANCE.RIPGetDeviceUid(ripContext);
        if (uid != null && !uid.isEmpty()) {
            currentDevice.setUid(uid);
        }
    }

    private void pollLoop() {
        RIPEvent.ByReference event = new RIPEvent.ByReference();
        while (started.get() && ripContext != null) {
            int got = RIPApi.INSTANCE.RIPPollEvent(ripContext, event);
            if (got == 1) {
                PenListener listener = listenerRef.get();
                if (listener != null) {
                    int buttonsMask = event.buttons & 0xFFFF;

                    PenState state = PenState.builder()
                            .x((int) event.x)
                            .y((int) event.y)
                            .pressure(event.pressure)
                            .tiltX(event.tiltX)
                            .tiltY(event.tiltY)
                            .near(true)
                            .tipPressed(event.tip != 0)
                            .buttons(buttonsMask)
                            .eraserPressed(false)
                            .cursorType(PenCursorType.PEN)
                            .build();

                    PenEvent penEvent = new DefaultPenEvent(currentDevice,
                            System.currentTimeMillis() * 1000, state);
                    listener.onPenData(penEvent);
                }
            } else {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private static class RawInputDevice extends BasePenDevice {
        {
            setName("Raw Input Pen");
            setCaps(EnumSet.of(PenCapability.PRESSURE, PenCapability.TILT));
            setSupportedCursorTypes(EnumSet.of(PenCursorType.PEN));
        }
    }
}