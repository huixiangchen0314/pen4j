package top.kzre.pen4j.windows.ink;

import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import java.util.Arrays;
import java.util.List;

/**
 * 对应 POINTER_INFO
 */
@Structure.FieldOrder({"pointerType", "pointerId", "frameId", "pointerFlags",
        "sourceDevice", "hwndTarget", "ptPixelLocation", "ptHimetricLocation",
        "ptPixelLocationRaw", "ptHimetricLocationRaw", "dwTime", "historyCount",
        "InputData", "dwKeyStates", "dwPerformanceCount", "ButtonChangeType"})
public class PointerInfo extends Structure {
    public static class ByReference extends PointerInfo implements Structure.ByReference {}

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
    public int InputData;
    public int dwKeyStates;
    public long dwPerformanceCount;
    public int ButtonChangeType;
}

