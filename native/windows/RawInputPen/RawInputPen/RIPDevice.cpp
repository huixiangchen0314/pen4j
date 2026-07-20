/**
 * RIPDevice.cpp - 单支笔的 HID 解析器实现（含移动语义与笔特征判断）
 */
#include "RIPDevice.h"
#include "RIPApi.h"          // RIPEvent, RIPDeviceInfo
#include <algorithm>
#include <cstring>

 // ── 构造 / 析构 / 移动 ──
RIPDevice::RIPDevice() = default;

RIPDevice::~RIPDevice() {
    Clear();
}

RIPDevice::RIPDevice(RIPDevice&& other) noexcept {
    *this = std::move(other);
}

RIPDevice& RIPDevice::operator=(RIPDevice&& other) noexcept {
    if (this != &other) {
        Clear();
        // 交换关键资源（指针、句柄）
        std::swap(m_hDevice, other.m_hDevice);
        std::swap(m_preparsedData, other.m_preparsedData);
        std::swap(m_caps, other.m_caps);

        // 用途映射
        std::swap(m_usageX, other.m_usageX);
        std::swap(m_usageY, other.m_usageY);
        std::swap(m_usagePressure, other.m_usagePressure);
        std::swap(m_usageTiltX, other.m_usageTiltX);
        std::swap(m_usageTiltY, other.m_usageTiltY);
        std::swap(m_usageTip, other.m_usageTip);
        std::swap(m_usageSerial, other.m_usageSerial);

        std::swap(m_pageX, other.m_pageX);
        std::swap(m_pageY, other.m_pageY);
        std::swap(m_pagePressure, other.m_pagePressure);
        std::swap(m_pageTiltX, other.m_pageTiltX);
        std::swap(m_pageTiltY, other.m_pageTiltY);
        std::swap(m_pageTip, other.m_pageTip);
        std::swap(m_pageSerial, other.m_pageSerial);

        std::swap(m_hasX, other.m_hasX);
        std::swap(m_hasY, other.m_hasY);
        std::swap(m_hasPressure, other.m_hasPressure);
        std::swap(m_hasTiltX, other.m_hasTiltX);
        std::swap(m_hasTiltY, other.m_hasTiltY);
        std::swap(m_hasTip, other.m_hasTip);
        std::swap(m_hasSerial, other.m_hasSerial);

        // 逻辑范围
        std::swap(m_logicalMinX, other.m_logicalMinX);
        std::swap(m_logicalMaxX, other.m_logicalMaxX);
        std::swap(m_logicalMinY, other.m_logicalMinY);
        std::swap(m_logicalMaxY, other.m_logicalMaxY);
        std::swap(m_logicalMinPressure, other.m_logicalMinPressure);
        std::swap(m_logicalMaxPressure, other.m_logicalMaxPressure);

        // 按钮列表
        std::swap(m_buttonUsages, other.m_buttonUsages);

        // 设备标识（使用移动，避免多余拷贝）
        m_deviceName = std::move(other.m_deviceName);
        m_uid = std::move(other.m_uid);
        m_devicePath = std::move(other.m_devicePath);
        std::swap(m_vid, other.m_vid);
        std::swap(m_pid, other.m_pid);
        std::swap(m_uidFinalized, other.m_uidFinalized);

        // 初始化标志
        std::swap(m_initialized, other.m_initialized);
    }
    return *this;
}

void RIPDevice::Clear() {
    if (m_preparsedData) {
        free(m_preparsedData);
        m_preparsedData = nullptr;
    }
    m_hDevice = nullptr;
    m_initialized = false;

    // 用途映射
    m_hasX = m_hasY = m_hasPressure = false;
    m_hasTiltX = m_hasTiltY = m_hasTip = false;
    m_hasSerial = false;
    m_usageX = m_usageY = m_usagePressure = m_usageTiltX = m_usageTiltY = m_usageTip = m_usageSerial = 0;
    m_pageX = m_pageY = m_pagePressure = m_pageTiltX = m_pageTiltY = m_pageTip = m_pageSerial = 0;

    // 逻辑范围设为默认值
    m_logicalMinX = 0;          m_logicalMaxX = 65535;
    m_logicalMinY = 0;          m_logicalMaxY = 65535;
    m_logicalMinPressure = 0;   m_logicalMaxPressure = 1023;

    m_buttonUsages.clear();
    m_deviceName.clear();
    m_uid.clear();
    m_devicePath.clear();
    m_vid = m_pid = 0;
    m_uidFinalized = false;
}

// ── 笔特征判断（用于过滤虚拟触摸设备） ──
bool RIPDevice::IsPhysicalPen() const {
    //  必须同时拥有 X 和 Y 坐标
    if (!m_hasX || !m_hasY) return false;

    //  必须至少有笔尖或压力之一
    if (!m_hasTip && !m_hasPressure) return false;


    //  排除设备路径中包含 "ROOT" 或 "VIRTUAL" 的虚拟设备（大小写不敏感）
    std::wstring upperPath = m_devicePath;
    std::transform(upperPath.begin(), upperPath.end(), upperPath.begin(), ::towupper);
    if (upperPath.find(L"ROOT") != std::wstring::npos ||
        upperPath.find(L"VIRTUAL") != std::wstring::npos) {
        return false;
    }

    return true;
}

// ── 初始化 ──
bool RIPDevice::Initialize(HANDLE hDevice) {
    Clear();
    m_hDevice = hDevice;
    ExtractDeviceInfo(hDevice);   // 填充设备名称/UID等

    UINT size = 0;
    GetRawInputDeviceInfo(hDevice, RIDI_PREPARSEDDATA, nullptr, &size);
    if (!size) return false;

    m_preparsedData = (PHIDP_PREPARSED_DATA)malloc(size);
    if (!m_preparsedData) return false;

    if (GetRawInputDeviceInfo(hDevice, RIDI_PREPARSEDDATA, m_preparsedData, &size) != size) {
        free(m_preparsedData);
        m_preparsedData = nullptr;
        return false;
    }

    if (HidP_GetCaps(m_preparsedData, &m_caps) != HIDP_STATUS_SUCCESS) {
        free(m_preparsedData);
        m_preparsedData = nullptr;
        return false;
    }

    USHORT numCaps = m_caps.NumberInputValueCaps;
    if (!numCaps) { m_initialized = true; return true; }

    HIDP_VALUE_CAPS* vc = (HIDP_VALUE_CAPS*)malloc(sizeof(HIDP_VALUE_CAPS) * numCaps);
    if (!vc) {
        free(m_preparsedData);
        m_preparsedData = nullptr;
        return false;
    }

    if (HidP_GetValueCaps(HidP_Input, vc, &numCaps, m_preparsedData) != HIDP_STATUS_SUCCESS) {
        free(vc);
        free(m_preparsedData);
        m_preparsedData = nullptr;
        return false;
    }

    std::vector<std::pair<USAGE, USAGE>> tempButtons;

    for (USHORT i = 0; i < numCaps; ++i) {
        HIDP_VALUE_CAPS& cap = vc[i];
        USAGE page = cap.UsagePage;
        USAGE usage;
        if (cap.IsRange) {
            if (cap.Range.UsageMin != cap.Range.UsageMax) continue;
            usage = cap.Range.UsageMin;
        }
        else {
            usage = cap.NotRange.Usage;
        }

        if (page == 0x01) {
            if (usage == 0x30) {
                m_pageX = page; m_usageX = usage;
                m_logicalMinX = cap.LogicalMin; m_logicalMaxX = cap.LogicalMax;
                m_hasX = true;
            }
            else if (usage == 0x31) {
                m_pageY = page; m_usageY = usage;
                m_logicalMinY = cap.LogicalMin; m_logicalMaxY = cap.LogicalMax;
                m_hasY = true;
            }
        }
        else if (page == 0x0D) {
            switch (usage) {
            case 0x30: m_pagePressure = page; m_usagePressure = usage;
                m_logicalMinPressure = cap.LogicalMin; m_logicalMaxPressure = cap.LogicalMax;
                m_hasPressure = true; break;
            case 0x3D: m_pageTiltX = page; m_usageTiltX = usage; m_hasTiltX = true; break;
            case 0x3E: m_pageTiltY = page; m_usageTiltY = usage; m_hasTiltY = true; break;
            case 0x42: m_pageTip = page; m_usageTip = usage; m_hasTip = true; break;
            case 0x5B: m_pageSerial = page; m_usageSerial = usage; m_hasSerial = true; break;
            }
        }
        else if (page == 0x09) {
            tempButtons.emplace_back(page, usage);
        }
    }
    free(vc);

    // 按钮排序去重
    std::sort(tempButtons.begin(), tempButtons.end(),
        [](const auto& a, const auto& b) { return a.second < b.second; });
    tempButtons.erase(std::unique(tempButtons.begin(), tempButtons.end(),
        [](const auto& a, const auto& b) { return a.second == b.second; }), tempButtons.end());

    m_buttonUsages.clear();
    for (const auto& btn : tempButtons) {
        m_buttonUsages.push_back({ btn.first, btn.second });
    }

    m_initialized = true;
    return true;
}

// ── 事件提取 ──
bool RIPDevice::ExtractEvent(const BYTE* rawData, DWORD size, RIPEvent& out) {
    memset(&out, 0, sizeof(out));
    out.timestamp = (uint32_t)GetTickCount();
    if (!m_initialized || !m_preparsedData) return false;

    ULONG ulVal = 0;

#define GET_USAGE_VAL(page, usage, hasFlag) \
    ((hasFlag) ? (HidP_GetUsageValue(HidP_Input, page, 0, usage, &ulVal, m_preparsedData, (PCHAR)rawData, size) == HIDP_STATUS_SUCCESS ? (LONG)ulVal : 0L) : 0L)

    LONG rawX = GET_USAGE_VAL(m_pageX, m_usageX, m_hasX);
    LONG rawY = GET_USAGE_VAL(m_pageY, m_usageY, m_hasY);
    LONG rawP = GET_USAGE_VAL(m_pagePressure, m_usagePressure, m_hasPressure);
    LONG tiltX = GET_USAGE_VAL(m_pageTiltX, m_usageTiltX, m_hasTiltX);
    LONG tiltY = GET_USAGE_VAL(m_pageTiltY, m_usageTiltY, m_hasTiltY);
    LONG tip = GET_USAGE_VAL(m_pageTip, m_usageTip, m_hasTip);

    // 按钮位掩码
    uint16_t buttonMask = 0;
    for (size_t i = 0; i < m_buttonUsages.size(); ++i) {
        if (HidP_GetUsageValue(HidP_Input, m_buttonUsages[i].page, 0, m_buttonUsages[i].usage,
            &ulVal, m_preparsedData, (PCHAR)rawData, size) == HIDP_STATUS_SUCCESS) {
            if (ulVal) buttonMask |= (1 << i);
        }
    }
    out.buttons = buttonMask;

    // 序列号处理
    if (m_hasSerial && !m_uidFinalized) {
        LONG serial = GET_USAGE_VAL(m_pageSerial, m_usageSerial, m_hasSerial);
        if (serial != 0) {
            char uidBuf[256];
            sprintf_s(uidBuf, "VID_%04X&PID_%04X_Serial_%ld", m_vid, m_pid, serial);
            m_uid = uidBuf;
            m_uidFinalized = true;
        }
    }

#undef GET_USAGE_VAL

    // 坐标映射
    int screenW = GetSystemMetrics(SM_CXSCREEN);
    int screenH = GetSystemMetrics(SM_CYSCREEN);
    LONG rangeX = m_logicalMaxX - m_logicalMinX;
    LONG rangeY = m_logicalMaxY - m_logicalMinY;
    out.x = (rangeX > 0) ? (float)((rawX - m_logicalMinX) * screenW / rangeX) : (float)rawX;
    out.y = (rangeY > 0) ? (float)((rawY - m_logicalMinY) * screenH / rangeY) : (float)rawY;
    out.pressure = (float)rawP;
    out.tiltX = (float)tiltX;
    out.tiltY = (float)tiltY;
    out.tip = tip ? 1 : 0;
    out.reserved = 0;
    return true;
}

// ── 设备信息提取（内部） ──
void RIPDevice::ExtractDeviceInfo(HANDLE hDevice) {
    UINT size = 0;
    GetRawInputDeviceInfo(hDevice, RIDI_DEVICENAME, nullptr, &size);
    if (size > 0) {
        std::vector<wchar_t> pathBuf(size);
        if (GetRawInputDeviceInfo(hDevice, RIDI_DEVICENAME, pathBuf.data(), &size) > 0) {
            m_devicePath = pathBuf.data();
            const std::wstring path(m_devicePath);
            size_t vidPos = path.find(L"VID_");
            size_t pidPos = path.find(L"PID_");
            if (vidPos != std::wstring::npos && pidPos != std::wstring::npos) {
                try {
                    m_vid = (USHORT)std::stoul(path.substr(vidPos + 4, 4), nullptr, 16);
                    m_pid = (USHORT)std::stoul(path.substr(pidPos + 4, 4), nullptr, 16);
                }
                catch (...) { m_vid = m_pid = 0; }
            }
        }
    }

    if (m_devicePath.empty()) {
        RID_DEVICE_INFO info = { sizeof(info) };
        UINT infoSize = sizeof(info);
        if (GetRawInputDeviceInfo(hDevice, RIDI_DEVICEINFO, &info, &infoSize) == infoSize) {
            m_vid = info.hid.dwVendorId;
            m_pid = info.hid.dwProductId;
        }
    }

    char nameBuf[128];
    sprintf_s(nameBuf, "Pen (VID_%04X&PID_%04X)", m_vid, m_pid);
    m_deviceName = nameBuf;

    if (!m_devicePath.empty()) {
        m_uid = std::string(m_devicePath.begin(), m_devicePath.end());
    }
    else {
        char uidBuf[64];
        sprintf_s(uidBuf, "VID_%04X&PID_%04X", m_vid, m_pid);
        m_uid = uidBuf;
    }
    m_uidFinalized = false;
}

// ── 信息查询 ──
void RIPDevice::GetPressureRange(uint32_t& min, uint32_t& max) const {
    min = (uint32_t)m_logicalMinPressure;
    max = (uint32_t)m_logicalMaxPressure;
}

void RIPDevice::GetLogicalRange(uint32_t& maxX, uint32_t& maxY) const {
    maxX = (uint32_t)m_logicalMaxX;
    maxY = (uint32_t)m_logicalMaxY;
}

uint32_t RIPDevice::GetButtonCount() const {
    return (uint32_t)m_buttonUsages.size();
}

void RIPDevice::FillDeviceInfo(RIPDeviceInfo& info) const {
    memset(&info, 0, sizeof(info));
    strncpy_s(info.deviceName, m_deviceName.c_str(), sizeof(info.deviceName) - 1);
    strncpy_s(info.uid, m_uid.c_str(), sizeof(info.uid) - 1);
    info.vid = m_vid;
    info.pid = m_pid;
    uint32_t minP, maxP;
    GetPressureRange(minP, maxP);
    info.maxPressure = maxP;
    uint32_t maxX, maxY;
    GetLogicalRange(maxX, maxY);
    info.maxLogicalX = maxX;
    info.maxLogicalY = maxY;
    info.buttonCount = GetButtonCount();
    info.reserved = 0;
}