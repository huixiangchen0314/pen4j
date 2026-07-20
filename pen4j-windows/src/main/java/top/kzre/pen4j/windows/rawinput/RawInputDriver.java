package top.kzre.pen4j.windows.rawinput;

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
public class RawInputDriver extends PollPenDriver {

    private volatile Pointer ripContext;
    // UID -> 设备对象缓存，用于事件快速查找和枚举时复用对象
    private final Map<String, PenDevice> uidToDeviceCache = new ConcurrentHashMap<>();

    public RawInputDriver() {
    }

    @Override
    public boolean isAvailable() {
        return true;   // 可在此检查 DLL 是否存在
    }

    @Override
    public void onStart() {
        log.debug("Creating RIP context...");
        ripContext = RIPApi.INSTANCE.RIPCreate();
        if (ripContext == null) {
            throw new RuntimeException("RIPCreate failed");
        }
        int status = RIPApi.INSTANCE.RIPStart(ripContext);
        if (status != 0) {
            String err = RIPApi.INSTANCE.RIPGetLastError(ripContext);
            log.error("RIPStart failed: {}", err);
            RIPApi.INSTANCE.RIPDestroy(ripContext);
            ripContext = null;
            throw new RuntimeException("RIPStart failed: " + err);
        }
        log.info("RawInputDriver started successfully");
    }

    @Override
    public void onStop() {
        if (ripContext != null) {
            RIPApi.INSTANCE.RIPStop(ripContext);
            RIPApi.INSTANCE.RIPDestroy(ripContext);
            ripContext = null;
        }
        uidToDeviceCache.clear();
        log.info("RawInputDriver stopped");
    }

    @Override
    public List<PenDevice> enumerateDevices() {
        log.debug("Enumerating pen devices...");
        List<PenDevice> devices = new ArrayList<>();
        if (ripContext == null) {
            return devices;
        }

        IntByReference countRef = new IntByReference();
        int ret = RIPApi.INSTANCE.RIPGetDeviceCount(ripContext, countRef);
        if (ret != 0) {
            log.error("Failed to get device count: {}", RIPApi.INSTANCE.RIPGetLastError(ripContext));
            return devices;
        }
        int count = countRef.getValue();
        log.debug("Found {} pen device(s)", count);

        for (int i = 0; i < count; i++) {
            RIPDeviceInfo info = new RIPDeviceInfo();
            ret = RIPApi.INSTANCE.RIPGetDeviceInfo(ripContext, i, info);
            if (ret != 0) {
                log.warn("Failed to get device info for index {}: {}", i, RIPApi.INSTANCE.RIPGetLastError(ripContext));
                continue;
            }
            String uid = info.getUid();
            // 尝试复用已有的设备对象，保持引用稳定性
            RawInputDevice device = (RawInputDevice) uidToDeviceCache.get(uid);
            if (device == null) {
                device = new RawInputDevice();
                device.setUid(uid);
                device.setName(info.getDeviceName());
                device.setVid(info.vid & 0xFFFF);
                device.setPid(info.pid & 0xFFFF);
                device.setVendor(PenVendorVIDTable.getVendorName(device.getVid()));
                device.setMaxPressure(info.maxPressure);
                device.setMaxX(info.maxLogicalX);
                device.setMaxY(info.maxLogicalY);
                device.setSideButtonCount(info.buttonCount);
                device.setCaps(EnumSet.of(PenCapability.PRESSURE, PenCapability.TILT));
                device.setSupportedCursorTypes(EnumSet.of(PenCursorType.PEN));
                uidToDeviceCache.put(uid, device);
                log.info("Device added: {} (UID: {})", device.getName(), uid);
            }
            devices.add(device);
        }

        // 清理缓存中已经不存在的设备
        Set<String> currentUids = new HashSet<>();
        for (PenDevice d : devices) {
            currentUids.add(d.getUid());
        }
        uidToDeviceCache.keySet().removeIf(uid -> {
            if (!currentUids.contains(uid)) {
                log.info("Device removed: {}", uid);
                return true;
            }
            return false;
        });

        return devices;
    }

    @Override
    public PenEvent pollEvent() {
        if (ripContext == null) {
            return null;
        }

        RIPExtendedEvent.ByReference extEvent = new RIPExtendedEvent.ByReference();
        int got = RIPApi.INSTANCE.RIPPollEventEx(ripContext, extEvent);
        if (got != 1) {
            return null;
        }

        String uid = extEvent.getDeviceUid();
        PenDevice device = uidToDeviceCache.get(uid);
        if (device == null) {
            // 设备未知，将在外部由模板触发即时探测，此处记录警告
            log.warn("Received event from unknown device UID: {}, dropping event", uid);
            return null;
        }

        RIPEvent event = extEvent.event;
        int buttonsMask = event.buttons & 0xFFFF;

        // 少量调试日志，便于追踪事件流（生产环境可关闭）
        log.debug("Pen event: UID={}, x={}, y={}, pressure={}, tip={}, buttons={}",
                uid, event.x, event.y, event.pressure, event.tip, buttonsMask);

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

        return new DefaultPenEvent(device, event.timestamp * 1000L, state);
    }

    // 内部设备类，仅作为数据容器
    private static class RawInputDevice extends BasePenDevice {
    }
}