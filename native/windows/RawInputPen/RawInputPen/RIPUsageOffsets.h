#pragma once
/**
 * RIPUsageOffsets.h - 笔相关 HID 用途的位偏移缓存
 */
#ifndef RIP_USAGE_OFFSETS_H
#define RIP_USAGE_OFFSETS_H

#include <windows.h>

 /** 存储从设备描述符解析出的各字段位偏移信息 */
struct RIPUsageOffsets {
    USHORT ReportByteLength = 0;   // 输入报告总长度（字节）

    // 坐标
    USHORT XByteOffset = 0, XBitOffset = 0, XBitSize = 0;
    USHORT YByteOffset = 0, YBitOffset = 0, YBitSize = 0;

    // 压力
    USHORT PressureByteOffset = 0, PressureBitOffset = 0, PressureBitSize = 0;

    // 倾斜
    USHORT TiltXByteOffset = 0, TiltXBitOffset = 0, TiltXBitSize = 0;
    USHORT TiltYByteOffset = 0, TiltYBitOffset = 0, TiltYBitSize = 0;

    // 笔尖开关
    USHORT TipSwitchByteOffset = 0, TipSwitchBitOffset = 0, TipSwitchBitSize = 0;

    // 按钮 1、2
    USHORT Button1ByteOffset = 0, Button1BitOffset = 0, Button1BitSize = 0;
    USHORT Button2ByteOffset = 0, Button2BitOffset = 0, Button2BitSize = 0;

    bool Valid = false;             // 是否包含必需的 X/Y 字段
};

#endif // RIP_USAGE_OFFSETS_H