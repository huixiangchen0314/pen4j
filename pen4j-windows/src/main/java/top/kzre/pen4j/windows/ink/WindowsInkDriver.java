package top.kzre.pen4j.windows.ink;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinUser.*;
import lombok.extern.slf4j.Slf4j;
import top.kzre.pen4j.api.*;
import top.kzre.pen4j.core.DefaultPenEvent;
import top.kzre.pen4j.core.spi.PenPlatformDriver;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class WindowsInkDriver implements PenPlatformDriver {
    private static final int CS_HREDRAW = 0x0002;
    private static final int CS_VREDRAW = 0x0001;

    private static final String WINDOW_CLASS = "Pen4JInkWindow_" + System.currentTimeMillis();
    private static final int WS_EX_TOOLWINDOW = 0x00000080;
    private static final int WS_EX_LAYERED = 0x00080000;
    private static final int WS_EX_NOACTIVATE = 0x08000000;
    private static final int LWA_ALPHA = 0x00000002;

    private final List<PenDevice> devices = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<PenListener> listenerRef = new AtomicReference<>();
    private final WindowsInkJNA ink = WindowsInkJNA.INSTANCE;

    private Thread messageThread;
    private HWND hwnd;
    private HMODULE hInstance;

    @Override
    public boolean isAvailable() {
        try {
            String osName = System.getProperty("os.name").toLowerCase();
            return osName.contains("win");
        } catch (Throwable e) {
            log.debug("Windows Ink not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<PenDevice> getDevices() {
        return new ArrayList<>(devices);
    }

    @Override
    public void start(PenListener listener) {
        if (!running.compareAndSet(false, true)) return;
        listenerRef.set(listener);

        int screenWidth = User32.INSTANCE.GetSystemMetrics(WinUser.SM_CXSCREEN);
        int screenHeight = User32.INSTANCE.GetSystemMetrics(WinUser.SM_CYSCREEN);

        PenDevice device = new InkDevice(
                "Windows Ink Digitizer",
                "Microsoft",
                screenWidth,
                screenHeight,
                1024,
                0,
                2,
                "ink-digitizer-1"
        );
        devices.add(device);

        messageThread = new Thread(this::messageLoop, "pen4j-ink-message");
        messageThread.setDaemon(true);
        messageThread.start();

        log.info("Windows Ink driver started");
    }

    private void messageLoop() {
        try {
            hInstance = Kernel32.INSTANCE.GetModuleHandle(null);

            WNDCLASSEX.ByReference wc = new WNDCLASSEX.ByReference();
            wc.cbSize = wc.size();

            // 使用匿名内部类实现WindowProc
            wc.lpfnWndProc = new WindowProc() {
                @Override
                public LRESULT callback(HWND hWnd, int uMsg, WPARAM wParam, LPARAM lParam) {
                    return windowProc(hWnd, uMsg, wParam, lParam);
                }
            };

            wc.hInstance = hInstance;
            wc.lpszClassName = WINDOW_CLASS;
            wc.hbrBackground = null;
            wc.style = CS_HREDRAW | CS_VREDRAW;

            if (User32.INSTANCE.RegisterClassEx(wc) == null) {
                log.error("Failed to register window class, error: {}", Native.getLastError());
                return;
            }

            hwnd = User32.INSTANCE.CreateWindowEx(
                    WS_EX_TOOLWINDOW | WS_EX_LAYERED | WS_EX_NOACTIVATE,
                    WINDOW_CLASS,
                    "Pen4J Ink Capture",
                    WinUser.WS_POPUP,
                    0, 0, 1, 1,
                    null, null, hInstance, null
            );

            if (hwnd == null) {
                log.error("Failed to create ink window, error: {}", Native.getLastError());
                return;
            }

            User32.INSTANCE.SetLayeredWindowAttributes(hwnd, 0, (byte) 0, LWA_ALPHA);
            User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_SHOWNOACTIVATE);

            ink.RegisterPointerInputTarget(hwnd, WindowsInkJNA.PT_PEN, 0);

            MSG msg = new MSG();
            while (running.get()) {
                int result = User32.INSTANCE.GetMessage(msg, hwnd, 0, 0);
                if (result == 0 || result == -1) {
                    break;
                }
                User32.INSTANCE.TranslateMessage(msg);
                User32.INSTANCE.DispatchMessage(msg);
            }
        } catch (Exception e) {
            log.error("Error in ink message loop", e);
        } finally {
            cleanup();
        }
    }

    private LRESULT windowProc(HWND hWnd, int uMsg, WPARAM wParam, LPARAM lParam) {
        PenListener currentListener = listenerRef.get();
        PenDevice device = devices.isEmpty() ? null : devices.get(0);

        if (currentListener == null || device == null) {
            return User32.INSTANCE.DefWindowProc(hWnd, uMsg, wParam, lParam);
        }

        switch (uMsg) {
            case WindowsInkJNA.WM_POINTERUPDATE:
            case WindowsInkJNA.WM_POINTERDOWN:
            case WindowsInkJNA.WM_POINTERUP:
            case WindowsInkJNA.WM_POINTERENTER:
            case WindowsInkJNA.WM_POINTERLEAVE:
                handlePointerEvent(uMsg, wParam, lParam, device, currentListener);
                return new LRESULT(0);
        }

        return User32.INSTANCE.DefWindowProc(hWnd, uMsg, wParam, lParam);
    }


    private void handlePointerEvent(int msg, WPARAM wParam, LPARAM lParam,
                                    PenDevice device, PenListener listener) {
        int pointerId = wParam.intValue() & 0xFFFF;

        WindowsInkJNA.POINTER_INFO.ByReference pInfo = new WindowsInkJNA.POINTER_INFO.ByReference();
        if (!ink.GetPointerInfo(pointerId, pInfo).booleanValue()) {
            return;
        }

        if (pInfo.pointerType != WindowsInkJNA.PT_PEN) {
            return;
        }

        WindowsInkJNA.POINTER_PEN_INFO.ByReference penInfo = new WindowsInkJNA.POINTER_PEN_INFO.ByReference();
        if (!ink.GetPointerPenInfo(pointerId, penInfo).booleanValue()) {
            return;
        }

        double x = (double) pInfo.ptPixelLocation.x / device.getMaxX();
        double y = (double) pInfo.ptPixelLocation.y / device.getMaxY();
        double pressure = Math.min(1.0, (double) penInfo.pressure / 1024.0);
        double tiltX = Math.max(-1.0, Math.min(1.0, (double) penInfo.tiltX / 90.0));
        double tiltY = Math.max(-1.0, Math.min(1.0, (double) penInfo.tiltY / 90.0));
        double twist = (double) penInfo.rotation;

        boolean tipPressed = (msg == WindowsInkJNA.WM_POINTERDOWN) ||
                ((msg == WindowsInkJNA.WM_POINTERUPDATE) &&
                        (penInfo.penFlags & WindowsInkJNA.PEN_FLAG_INVERTED) == 0);
        boolean barrelPressed = (penInfo.penFlags & WindowsInkJNA.PEN_FLAG_BARREL) != 0;
        boolean eraserPressed = (penInfo.penFlags & WindowsInkJNA.PEN_FLAG_ERASER) != 0;
        boolean near = (msg != WindowsInkJNA.WM_POINTERLEAVE);

        PenState state = PenState.builder()
                .x(x).y(y)
                .pressure(pressure)
                .tiltX(tiltX).tiltY(tiltY)
                .twist(twist)
                .near(near)
                .tipPressed(tipPressed)
                .button1Pressed(barrelPressed)
                .button2Pressed(false)
                .eraserPressed(eraserPressed)
                .build();

        listener.onPenData(new DefaultPenEvent(device, pInfo.dwTime * 1000L, state));
    }

    private void cleanup() {
        if (hwnd != null) {
            User32.INSTANCE.DestroyWindow(hwnd);
            hwnd = null;
        }
        if (hInstance != null) {
            User32.INSTANCE.UnregisterClass(WINDOW_CLASS, hInstance);
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (hwnd != null) {
                User32.INSTANCE.PostMessage(hwnd, WinUser.WM_QUIT, new WPARAM(0), new LPARAM(0));
            }

            if (messageThread != null) {
                try {
                    messageThread.join(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            devices.clear();
            listenerRef.set(null);
            log.info("Windows Ink driver stopped");
        }
    }

    private static class InkDevice implements PenDevice {
        private final String name, vendor;
        private final int maxX, maxY, maxPressure, maxProximity, sideButtonCount;
        private final String uid;

        InkDevice(String name, String vendor, int maxX, int maxY, int maxPressure,
                  int maxProximity, int sideButtonCount, String uid) {
            this.name = name;
            this.vendor = vendor;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxPressure = maxPressure;
            this.maxProximity = maxProximity;
            this.sideButtonCount = sideButtonCount;
            this.uid = uid;
        }

        @Override public String getName() { return name; }
        @Override public String getVendor() { return vendor; }
        @Override public int getMaxX() { return maxX; }
        @Override public int getMaxY() { return maxY; }
        @Override public int getMaxPressure() { return maxPressure; }
        @Override public int getMaxProximity() { return maxProximity; }
        @Override public int getSideButtonCount() { return sideButtonCount; }
        @Override public String getUid() { return uid; }

        @Override
        public boolean supports(PenCapability capability) {
            switch (capability) {
                case PRESSURE: return true;
                case TILT: return true;
                case TWIST: return true;
                case PROXIMITY: return true;
                case SIDE_BUTTON: return sideButtonCount > 0;
                case ERASER: return true;
                default: return false;
            }
        }

        @Override
        public Set<PenCursorType> getSupportedCursorTypes() {
            return EnumSet.of(PenCursorType.PEN, PenCursorType.ERASER);
        }
    }
}