package top.kzre.pen4j.windows.rawinput;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

public interface RIPApi extends Library {
    RIPApi INSTANCE = Native.load("RawInputPen", RIPApi.class);

    // 上下文生命周期
    Pointer RIPCreate();
    void RIPDestroy(Pointer ctx);
    int RIPStart(Pointer ctx);                     // 不再接受回调参数
    void RIPStop(Pointer ctx);

    // 轮询（多设备，带 UID）
    int RIPPollEventEx(Pointer ctx, RIPExtendedEvent event);

    // 实时枚举设备
    int RIPGetDeviceCount(Pointer ctx, IntByReference count);
    int RIPGetDeviceInfo(Pointer ctx, int index, RIPDeviceInfo info);

    // 错误信息
    String RIPGetLastError(Pointer ctx);
}