package top.kzre.pen4j.windows.wintab;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

public interface WTPApi extends Library {
    WTPApi INSTANCE = Native.load("WinTabPen", WTPApi.class);

    Pointer WTPCreate();
    void WTPDestroy(Pointer ctx);
    int WTPStart(Pointer ctx, WTPEventCallback callback);  // 使用回调接口
    void WTPStop(Pointer ctx);
    int WTPPollEvent(Pointer ctx, WTPEvent event);
    String WTPGetLastError(Pointer ctx);

    int WTPGetPressureRange(Pointer ctx, IntByReference min, IntByReference max);
    int WTPGetLogicalRange(Pointer ctx, IntByReference maxX, IntByReference maxY);
    int WTPGetButtonCount(Pointer ctx, IntByReference count);
    String WTPGetDeviceName(Pointer ctx);
    int WTPGetDeviceVid(Pointer ctx, IntByReference vid);
    int WTPGetDevicePid(Pointer ctx, IntByReference pid);
    String WTPGetDeviceUid(Pointer ctx);
}