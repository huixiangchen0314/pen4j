package top.kzre.pen4j.windows.wintab;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import lombok.extern.slf4j.Slf4j;
import top.kzre.pen4j.api.*;
import top.kzre.pen4j.core.BasePenDevice;
import top.kzre.pen4j.core.DefaultPenEvent;
import top.kzre.pen4j.core.PollPenDriver;
import top.kzre.pen4j.windows.common.PenVendorVIDTable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class WinTabDriver extends PollPenDriver {

    private volatile Pointer wtpContext;
    // UID -> 设备对象缓存
    private final Map<String, PenDevice> uidToDeviceCache = new ConcurrentHashMap<>();

    @Override
    public boolean isAvailable() {
        Pointer testCtx = WTPApi.INSTANCE.WTPCreate();
        if (testCtx == null) return false;
        WTPApi.INSTANCE.WTPDestroy(testCtx);
        return true;
    }

    @Override
    public void onStart() {
        log.debug("Creating WTP context...");
        wtpContext = WTPApi.INSTANCE.WTPCreate();
        if (wtpContext == null) {
            throw new RuntimeException("WTPCreate failed");
        }
        int status = WTPApi.INSTANCE.WTPStart(wtpContext);
        if (status != 0) {
            String err = WTPApi.INSTANCE.WTPGetLastError(wtpContext);
            WTPApi.INSTANCE.WTPDestroy(wtpContext);
            wtpContext = null;
            throw new RuntimeException("WTPStart failed: " + err);
        }
        log.info("WinTabDriver started successfully");
    }

    @Override
    public void onStop() {
        if (wtpContext != null) {
            WTPApi.INSTANCE.WTPStop(wtpContext);
            WTPApi.INSTANCE.WTPDestroy(wtpContext);
            wtpContext = null;
        }
        uidToDeviceCache.clear();
        log.info("WinTabDriver stopped");
    }

    @Override
    public List<PenDevice> enumerateDevices() {
        log.debug("Enumerating WinTab devices...");
        List<PenDevice> devices = new ArrayList<>();
        if (wtpContext == null) return devices;

        IntByReference countRef = new IntByReference();
        int ret = WTPApi.INSTANCE.WTPGetDeviceCount(wtpContext, countRef);
        if (ret != 0) {
            log.error("Failed to get device count: {}", WTPApi.INSTANCE.WTPGetLastError(wtpContext));
            return devices;
        }
        int count = countRef.getValue();
        log.info("Found {} WinTab device(s)", count);

        for (int i = 0; i < count; i++) {
            WTPDeviceInfo info = new WTPDeviceInfo();
            ret = WTPApi.INSTANCE.WTPGetDeviceInfo(wtpContext, i, info);
            if (ret != 0) {
                log.warn("Failed to get device info for index {}: {}", i, WTPApi.INSTANCE.WTPGetLastError(wtpContext));
                continue;
            }
            String uid = info.getUid();
            WinTabDevice device = (WinTabDevice) uidToDeviceCache.get(uid);
            if (device == null) {
                device = new WinTabDevice();
                device.setUid(uid);
                device.setName(info.getDeviceName());
                device.setVid(info.vid & 0xFFFF);
                device.setPid(info.pid & 0xFFFF);
                device.setVendor(PenVendorVIDTable.getVendorName(device.getVid()));
                device.setMaxPressure(info.maxPressure);
                device.setMaxX(info.maxLogicalX);
                device.setMaxY(info.maxLogicalY);
                device.setSideButtonCount(info.buttonCount);
                device.setCaps(EnumSet.of(
                        PenCapability.PRESSURE, PenCapability.TILT, PenCapability.PROXIMITY,
                        PenCapability.SIDE_BUTTON, PenCapability.ABSOLUTE_MODE));
                device.setSupportedCursorTypes(EnumSet.of(PenCursorType.PEN, PenCursorType.ERASER));
                uidToDeviceCache.put(uid, device);
                log.info("Device added: {} (UID: {})", device.getName(), uid);
            }
            devices.add(device);
        }

        // 清理缓存中不存在的设备
        Set<String> currentUids = new HashSet<>();
        for (PenDevice d : devices) currentUids.add(d.getUid());
        uidToDeviceCache.keySet().removeIf(uid -> {
            if (!currentUids.contains(uid)) {
                log.info("Device removed from cache: {}", uid);
                return true;
            }
            return false;
        });
        return devices;
    }

    @Override
    public PenEvent pollEvent() {
        if (wtpContext == null) return null;

        WTPExtendedEvent.ByReference extEvent = new WTPExtendedEvent.ByReference();
        int got = WTPApi.INSTANCE.WTPPollEventEx(wtpContext, extEvent);
        if (got != 1) return null;

        String uid = extEvent.getDeviceUid();
        PenDevice device = uidToDeviceCache.get(uid);
        if (device == null) {
            log.warn("Received event from unknown device UID: {}, dropping event", uid);
            return null;
        }

        WTPEvent event = extEvent.event;
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

        log.debug("Pen event: UID={}, x={}, y={}, pressure={}, tip={}, eraser={}",
                uid, event.x, event.y, event.pressure, event.tip, event.eraser);

        return new DefaultPenEvent(device, event.timestamp * 1000L, state);
    }

    // 内部设备类
    private static class WinTabDevice extends BasePenDevice {
        {
            setName("WinTab Pen");
            setCaps(EnumSet.of(
                    PenCapability.PRESSURE, PenCapability.TILT, PenCapability.PROXIMITY,
                    PenCapability.SIDE_BUTTON, PenCapability.ABSOLUTE_MODE));
            setSupportedCursorTypes(EnumSet.of(PenCursorType.PEN, PenCursorType.ERASER));
        }
    }
}