package top.kzre.pen4j.windows.wintab;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

public interface WTPApi extends Library {
    WTPApi INSTANCE = Native.load("WinTabPen", WTPApi.class);

    Pointer WTPCreate();
    void WTPDestroy(Pointer ctx);
    int WTPStart(Pointer ctx);                          // 无回调参数
    void WTPStop(Pointer ctx);
    int WTPPollEventEx(Pointer ctx, WTPExtendedEvent event); // 新轮询接口
    String WTPGetLastError(Pointer ctx);

    int WTPGetDeviceCount(Pointer ctx, IntByReference count);
    int WTPGetDeviceInfo(Pointer ctx, int index, WTPDeviceInfo info);
}