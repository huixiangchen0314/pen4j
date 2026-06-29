package top.kzre.pen4j.windows.ink;

import com.sun.jna.*;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinUser.*;
import com.sun.jna.win32.W32APIOptions;

public interface WindowsInkJNA extends User32 {

    WindowsInkJNA INSTANCE = Native.load("user32", WindowsInkJNA.class, W32APIOptions.DEFAULT_OPTIONS);

    int WM_POINTERENTER = 0x0249;
    int WM_POINTERLEAVE = 0x024A;
    int WM_POINTERUPDATE = 0x0245;
    int WM_POINTERDOWN = 0x0246;
    int WM_POINTERUP = 0x0247;

    int PT_PEN = 0x00000003;
    int PEN_MASK_PRESSURE = 0x00000001;
    int PEN_MASK_ROTATION = 0x00000002;
    int PEN_MASK_TILT_X = 0x00000004;
    int PEN_MASK_TILT_Y = 0x00000008;

    int PEN_FLAG_BARREL = 0x00000001;
    int PEN_FLAG_INVERTED = 0x00000002;
    int PEN_FLAG_ERASER = 0x00000004;

    BOOL GetPointerInfo(int pointerId, POINTER_INFO.ByReference pPointerInfo);
    BOOL GetPointerPenInfo(int pointerId, POINTER_PEN_INFO.ByReference penInfo);
    BOOL RegisterPointerInputTarget(HWND hwnd, int pointerType, int flags);

    @Structure.FieldOrder({
            "pointerType", "pointerId", "frameId", "pointerFlags",
            "sourceDevice", "hwndTarget", "ptPixelLocation", "ptHimetricLocation",
            "ptPixelLocationRaw", "ptHimetricLocationRaw", "dwTime",
            "historyCount", "inputData", "dwKeyStates", "PerformanceCount", "ButtonChangeType"
    })
    class POINTER_INFO extends Structure {
        public int pointerType;
        public int pointerId;
        public int frameId;
        public int pointerFlags;
        public HANDLE sourceDevice;
        public HWND hwndTarget;
        public POINT ptPixelLocation;
        public POINT ptHimetricLocation;
        public POINT ptPixelLocationRaw;
        public POINT ptHimetricLocationRaw;
        public int dwTime;
        public int historyCount;
        public int inputData;
        public int dwKeyStates;
        public long PerformanceCount;
        public int ButtonChangeType;

        public POINTER_INFO() {
            super();
        }

        public POINTER_INFO(Pointer p) {
            super(p);
            read();
        }

        public static class ByReference extends POINTER_INFO implements Structure.ByReference {}
    }

    @Structure.FieldOrder({
            "penFlags", "penMask", "pressure", "rotation", "tiltX", "tiltY"
    })
    class POINTER_PEN_INFO extends Structure {
        public int penFlags;
        public int penMask;
        public int pressure;
        public int rotation;
        public int tiltX;
        public int tiltY;

        public POINTER_PEN_INFO() {
            super();
        }

        public POINTER_PEN_INFO(Pointer p) {
            super(p);
            read();
        }

        public static class ByReference extends POINTER_PEN_INFO implements Structure.ByReference {}
    }
}