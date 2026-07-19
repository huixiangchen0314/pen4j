package top.kzre.pen4j.windows.wintab;

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
public class WinTabDriver implements PenPlatformDriver {

    private final List<WinTabDevice> devices = new ArrayList<>();
    private final AtomicReference<PenListener> listenerRef = new AtomicReference<>();

    private volatile Pointer wtpContext;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private Thread pollThread;

    @Override
    public boolean isAvailable() {
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
            log.warn("WinTabDriver already started");
            return;
        }
        Objects.requireNonNull(listener);
        listenerRef.set(listener);

        wtpContext = WTPApi.INSTANCE.WTPCreate();
        if (wtpContext == null) {
            throw new RuntimeException("WTPCreate failed");
        }

        int status = WTPApi.INSTANCE.WTPStart(wtpContext, null);
        if (status != 0) {
            String err = WTPApi.INSTANCE.WTPGetLastError(wtpContext);
            WTPApi.INSTANCE.WTPDestroy(wtpContext);
            wtpContext = null;
            throw new RuntimeException("WTPStart failed: " + err);
        }

        WinTabDevice device = buildDeviceFromGlue();
        devices.add(device);
        listener.onDeviceAdded(device);

        pollThread = new Thread(this::pollLoop, "WinTab-Poll");
        pollThread.setDaemon(true);
        pollThread.start();

        log.info("WinTabDriver started (pure glue)");
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
        log.info("WinTabDriver stopped");
    }

    private WinTabDevice buildDeviceFromGlue() {
        WinTabDevice device = new WinTabDevice();

        IntByReference min = new IntByReference();
        IntByReference max = new IntByReference();
        if (WTPApi.INSTANCE.WTPGetPressureRange(wtpContext, min, max) == 0) {
            device.setMaxPressure(max.getValue());
        }

        IntByReference maxX = new IntByReference();
        IntByReference maxY = new IntByReference();
        if (WTPApi.INSTANCE.WTPGetLogicalRange(wtpContext, maxX, maxY) == 0) {
            device.setMaxX(maxX.getValue());
            device.setMaxY(maxY.getValue());
        }

        IntByReference btnCount = new IntByReference();
        if (WTPApi.INSTANCE.WTPGetButtonCount(wtpContext, btnCount) == 0) {
            device.setSideButtonCount(btnCount.getValue());
        }

        String name = WTPApi.INSTANCE.WTPGetDeviceName(wtpContext);
        if (name != null && !name.isEmpty()) {
            device.setName(name);
        }

        IntByReference vidRef = new IntByReference();
        IntByReference pidRef = new IntByReference();
        if (WTPApi.INSTANCE.WTPGetDeviceVid(wtpContext, vidRef) == 0) {
            int vid = vidRef.getValue();
            device.setVid(vid);
            device.setVendor(PenVendorVIDTable.getVendorName(vid));
        }
        if (WTPApi.INSTANCE.WTPGetDevicePid(wtpContext, pidRef) == 0) {
            device.setPid(pidRef.getValue());
        }

        String uid = WTPApi.INSTANCE.WTPGetDeviceUid(wtpContext);
        if (uid != null && !uid.isEmpty()) {
            device.setUid(uid);
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

                WinTabDevice device = devices.isEmpty() ? null : devices.get(0);
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

    private static class WinTabDevice extends BasePenDevice {
        {
            setName("WinTab Pen");
            setCaps(EnumSet.of(
                    PenCapability.PRESSURE, PenCapability.TILT, PenCapability.PROXIMITY,
                    PenCapability.SIDE_BUTTON, PenCapability.ABSOLUTE_MODE));
            setSupportedCursorTypes(EnumSet.of(PenCursorType.PEN));
        }
    }
}