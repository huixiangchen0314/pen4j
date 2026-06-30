package top.kzre.pen4j.windows.ink;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR;   // 新增
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import lombok.extern.slf4j.Slf4j;
import top.kzre.pen4j.api.*;
import top.kzre.pen4j.core.DefaultPenEvent;
import top.kzre.pen4j.core.PenPlatformDriver;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class WindowsInkDriver implements PenPlatformDriver {

    private final HWND hwnd;
    private final AtomicReference<PenListener> listenerRef = new AtomicReference<>();
    private final List<PenDevice> devices = new ArrayList<>();
    private final WindowsInkParser parser = new WindowsInkParser();

    private boolean started = false;
    private ComCtl32.SUBCLASSPROC subClassProc;
    private final UINT_PTR subClassId = new UINT_PTR(1L); // 修正：用 long 构造

    public WindowsInkDriver(long nativeWindowHandle) {
        this.hwnd = new HWND(Pointer.createConstant(nativeWindowHandle));
        devices.add(new WindowsInkDevice());
    }

    @Override
    public boolean isAvailable() {
        // 实际使用时可以调用 VerSetConditionMask + VerifyVersionInfo 检查 Windows 版本
        // 这里简单返回 true
        return true;
    }

    @Override
    public List<PenDevice> getDevices() {
        return Collections.unmodifiableList(devices);
    }

    @Override
    public void start(PenListener listener) {
        if (started) {
            log.warn("WindowsInkDriver already started");
            return;
        }
        Objects.requireNonNull(listener);
        listenerRef.set(listener);

        // 通知设备
        listener.onDeviceAdded(devices.get(0));

        // 安装子类化回调
        subClassProc = new ComCtl32.SUBCLASSPROC() {
            @Override
            public LRESULT callback(HWND hWnd, int uMsg, WPARAM wParam, LPARAM lParam,
                                    UINT_PTR uIdSubclass, ULONG_PTR dwRefData) {
                return handleSubclassMessage(hWnd, uMsg, wParam, lParam, uIdSubclass, dwRefData);
            }
        };
        // 修正：ULONG_PTR 构造参数使用 Pointer 或 long
        if (!ComCtl32.INSTANCE.SetWindowSubclass(hwnd, subClassProc, subClassId,
                new ULONG_PTR(0L))) {
            throw new RuntimeException("SetWindowSubclass failed");
        }

        started = true;
        log.info("WindowsInkDriver started on HWND={}", hwnd);
    }

    @Override
    public void stop() {
        if (!started) return;
        started = false;

        if (subClassProc != null) {
            ComCtl32.INSTANCE.RemoveWindowSubclass(hwnd, subClassProc, subClassId);
            subClassProc = null;
        }
        devices.clear();
        log.info("WindowsInkDriver stopped");
    }

    private LRESULT handleSubclassMessage(HWND hWnd, int msg, WPARAM wParam, LPARAM lParam,
                                          UINT_PTR uIdSubclass, ULONG_PTR dwRefData) {
        switch (msg) {
            case PointerConstants.WM_POINTERDOWN:
            case PointerConstants.WM_POINTERUP:
            case PointerConstants.WM_POINTERUPDATE:
                handlePointerMessage(msg, wParam);
                break;
        }
        return ComCtl32.INSTANCE.DefSubclassProc(hWnd, msg, wParam, lParam);
    }

    private void handlePointerMessage(int msg, WPARAM wParam) {
        int pointerId = wParam.intValue() & 0xFFFF;
        IntByReference pointerType = new IntByReference();
        if (!User32Ex.INSTANCE.GetPointerType(pointerId, pointerType)) return;
        if (pointerType.getValue() != PointerConstants.PT_PEN) return;

        PointerPenInfo.ByReference penInfo = new PointerPenInfo.ByReference();
        if (!User32Ex.INSTANCE.GetPointerPenInfo(pointerId, penInfo)) return;

        PenListener listener = listenerRef.get();
        if (listener == null) return;
        PenDevice dev = devices.get(0);

        PenState state = parser.parse(penInfo, msg);
        PenEvent event = new DefaultPenEvent(dev, System.currentTimeMillis() * 1000, state);
        listener.onPenData(event);
    }

    // 内部设备
    private static class WindowsInkDevice implements PenDevice {
        @Override
        public String getName() {
            return "Windows Ink Pen";
        }

        @Override
        public String getVendor() {
            return "Microsoft";
        }

        @Override
        public int getMaxX() {
            return User32.INSTANCE.GetSystemMetrics(WinUser.SM_CXSCREEN);
        }

        @Override
        public int getMaxY() {
            return User32.INSTANCE.GetSystemMetrics(WinUser.SM_CYSCREEN);
        }

        @Override
        public int getMaxPressure() {
            return 1024; // Windows Ink 压力值范围 0-1024
        }

        @Override
        public int getMaxProximity() {
            return 0;    // Windows Ink 不提供悬停距离
        }

        @Override
        public int getSideButtonCount() {
            return 1;    // 默认至少一个笔杆按钮
        }

        @Override
        public String getUid() {
            return "windows-ink-pen-0";
        }

        @Override
        public boolean supports(PenCapability capability) {
            // 只声明已知可用的能力，避免不存在的枚举值
            switch (capability) {
                case PRESSURE:
                case TILT:
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public Set<PenCursorType> getSupportedCursorTypes() {
            return Collections.singleton(PenCursorType.PEN);
        }
    }
}