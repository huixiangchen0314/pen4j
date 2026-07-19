package top.kzre.pen4j.windows.rawinput;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

public interface RIPApi extends Library {
    RIPApi INSTANCE = Native.load("RawInputPen", RIPApi.class);

    Pointer RIPCreate();
    void RIPDestroy(Pointer ctx);
    int RIPStart(Pointer ctx, RIPEventCallback callback);  // 返回 RIPStatus
    void RIPStop(Pointer ctx);
    int RIPPollEvent(Pointer ctx, RIPEvent event);         // 返回 1 表示有新事件
    String RIPGetLastError(Pointer ctx);

    // 压力范围
    int RIPGetPressureRange(Pointer ctx, IntByReference min, IntByReference max);

    // 坐标逻辑范围
    int RIPGetLogicalRange(Pointer ctx, IntByReference maxX, IntByReference maxY);

    // 按钮数量（不包括笔尖）
    int RIPGetButtonCount(Pointer ctx, IntByReference count);

    // 设备名称（如 "Pen (VID_056A&PID_0087)"）
    String RIPGetDeviceName(Pointer ctx);

    // 获取 VID
    int RIPGetDeviceVid(Pointer ctx, IntByReference vid);

    // 获取 PID
    int RIPGetDevicePid(Pointer ctx, IntByReference pid);

    // 获取唯一标识符（序列号优先，否则设备路径）
    String RIPGetDeviceUid(Pointer ctx);
}