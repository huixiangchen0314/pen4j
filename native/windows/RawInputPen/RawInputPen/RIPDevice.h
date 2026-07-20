#pragma once
/**
 * RIPDevice.h - 单支笔的 HID 解析器（轻量，可移动，不可拷贝）
 *
 * 提供：
 *  1. 从 HID 报告提取 RIPEvent
 *  2. 查询设备静态属性（名称、UID、VID/PID、压力范围等）
 *
 * 内部缓存 preparsed data 和用途映射，但不拥有事件队列。
 */
#include <windows.h>
#include <hidsdi.h>
#include <hidpi.h>
#include <string>
#include <vector>
#include <cstdint>
#include "RIPApi.h"

class RIPDevice {
public:
    RIPDevice();
    ~RIPDevice();

    // 禁止拷贝
    RIPDevice(const RIPDevice&) = delete;
    RIPDevice& operator=(const RIPDevice&) = delete;

    // 允许移动（放入 std::vector<std::unique_ptr<RIPDevice>> 或作为临时对象）
    RIPDevice(RIPDevice&& other) noexcept;
    RIPDevice& operator=(RIPDevice&& other) noexcept;

    // 初始化：加载 preparsed data 并解析用途映射、逻辑范围、按钮等
    bool Initialize(HANDLE hDevice);

    // 从 HID 原始数据解析笔事件。成功返回 true，并填充 out
    bool ExtractEvent(const BYTE* rawData, DWORD size, RIPEvent& out);

    // 判断设备是否具有笔的特征（笔尖开关或压力），用于过滤虚拟触摸设备
    bool IsPhysicalPen() const;

    // ── 设备静态信息查询 ──
    const char* GetDeviceName()  const { return m_deviceName.c_str(); }
    const char* GetDeviceUid()   const { return m_uid.c_str(); }
    uint16_t GetDeviceVid()      const { return m_vid; }
    uint16_t GetDevicePid()      const { return m_pid; }
    void GetPressureRange(uint32_t& min, uint32_t& max) const;
    void GetLogicalRange(uint32_t& maxX, uint32_t& maxY) const;
    uint32_t GetButtonCount()    const;

    // 一次性填充 RIPDeviceInfo 结构体（用于设备枚举）
    void FillDeviceInfo(RIPDeviceInfo& info) const;

private:
    void Clear();                     // 释放 preparsed data，重置状态
    void ExtractDeviceInfo(HANDLE hDevice); // 获取设备路径、VID/PID、生成名称/UID

    HANDLE               m_hDevice = nullptr;
    PHIDP_PREPARSED_DATA m_preparsedData = nullptr;
    HIDP_CAPS            m_caps = {};

    // 用途映射
    USAGE m_usageX = 0, m_usageY = 0, m_usagePressure = 0,
        m_usageTiltX = 0, m_usageTiltY = 0, m_usageTip = 0,
        m_usageSerial = 0;
    USAGE m_pageX = 0, m_pageY = 0, m_pagePressure = 0,
        m_pageTiltX = 0, m_pageTiltY = 0, m_pageTip = 0,
        m_pageSerial = 0;
    bool  m_hasX = false, m_hasY = false, m_hasPressure = false;
    bool  m_hasTiltX = false, m_hasTiltY = false, m_hasTip = false;
    bool  m_hasSerial = false;

    // 逻辑范围
    LONG m_logicalMinX = 0, m_logicalMaxX = 65535;
    LONG m_logicalMinY = 0, m_logicalMaxY = 65535;
    LONG m_logicalMinPressure = 0, m_logicalMaxPressure = 1023;

    // 按钮描述（按 Usage 值排序，位 0 对应第一个按钮）
    struct ButtonDesc { USAGE page; USAGE usage; };
    std::vector<ButtonDesc> m_buttonUsages;

    // 设备标识
    std::string  m_deviceName;
    std::string  m_uid;
    std::wstring m_devicePath;
    USHORT       m_vid = 0, m_pid = 0;
    bool         m_uidFinalized = false;

    // 是否已成功初始化
    bool m_initialized = false;
};