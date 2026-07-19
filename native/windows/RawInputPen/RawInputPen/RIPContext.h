#pragma once
/**
 * RIPContext.h - 单个笔输入上下文实现类（支持动态按钮、设备信息查询）
 */
#ifndef RIP_CONTEXT_H
#define RIP_CONTEXT_H

#include <windows.h>
#include <hidsdi.h>
#include <hidpi.h>
#include <atomic>
#include <queue>
#include <mutex>
#include <string>
#include <vector>
#include <utility>
#include "RIPApi.h"

#define MAX_RIP_EVENT_QUEUE  1024

class RIPContext {
public:
    RIPContext();
    ~RIPContext();

    RIPStatus Start(RIPEventCallback callback);
    void Stop();
    int PollEvent(RIPEvent* event);
    const char* GetLastError() const { return m_lastError; }

    // 信息查询接口
    void GetPressureRange(uint32_t& outMin, uint32_t& outMax) const {
        outMin = (uint32_t)m_logicalMinPressure;
        outMax = (uint32_t)m_logicalMaxPressure;
    }
    void GetLogicalRange(uint32_t& outMaxX, uint32_t& outMaxY) const {
        outMaxX = (uint32_t)m_logicalMaxX;
        outMaxY = (uint32_t)m_logicalMaxY;
    }
    uint32_t GetButtonCount() const {
        return m_buttonUsages.empty() ? 0 : (uint32_t)m_buttonUsages.size();
    }
    const char* GetDeviceName() const { return m_deviceName.c_str(); }
    uint16_t GetDeviceVid() const { return m_vid; }
    uint16_t GetDevicePid() const { return m_pid; }
    const char* GetDeviceUid() const { return m_uid.c_str(); }

private:
    RIPContext(const RIPContext&) = delete;
    RIPContext& operator=(const RIPContext&) = delete;

    static DWORD WINAPI ThreadProc(LPVOID param);
    static LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam);

    bool CreateMessageWindow();
    bool RegisterRawInputDevices();
    void UnregisterRawInputDevices();

    // HID 解析
    bool InitializeDeviceCache(HANDLE hDevice);
    void ClearDeviceCache();
    bool ExtractPenEvent(const BYTE* rawData, DWORD size, RIPEvent& out);
    void PushEvent(const RIPEvent& ev);
    void HandleRawInput(HRAWINPUT hRawInput);

    // 设备信息提取
    void ExtractDeviceInfo(HANDLE hDevice);

    // 按钮存储：按顺序按钮1对应索引0，按钮2对应索引1，...
    struct ButtonDesc {
        USAGE page;
        USAGE usage;
    };
    std::vector<ButtonDesc> m_buttonUsages;  // 索引 0 = button 1

    // 状态变量
    std::atomic<bool> m_running{ false };
    HWND m_hwnd = nullptr;
    HANDLE m_hThread = nullptr;

    PHIDP_PREPARSED_DATA m_preparsedData = nullptr;
    HIDP_CAPS m_caps = { 0 };
    bool m_deviceCached = false;

    // 用途记录
    USAGE m_usageX = 0, m_usageY = 0, m_usagePressure = 0, m_usageTiltX = 0, m_usageTiltY = 0;
    USAGE m_usageTip = 0;
    USAGE m_usageSerial = 0;
    USAGE m_pageX = 0, m_pageY = 0, m_pagePressure = 0, m_pageTiltX = 0, m_pageTiltY = 0;
    USAGE m_pageTip = 0;
    USAGE m_pageSerial = 0;
    bool m_hasX = false, m_hasY = false, m_hasPressure = false;
    bool m_hasTiltX = false, m_hasTiltY = false, m_hasTip = false;
    bool m_hasSerial = false;

    LONG m_logicalMinX = 0, m_logicalMaxX = 65535;
    LONG m_logicalMinY = 0, m_logicalMaxY = 65535;
    LONG m_logicalMinPressure = 0, m_logicalMaxPressure = 1023;

    // 设备标识
    std::string m_deviceName;
    std::string m_uid;
    std::wstring m_devicePath;
    USHORT m_vid = 0, m_pid = 0;
    bool m_uidFinalized = false;

    std::queue<RIPEvent> m_queue;
    std::mutex m_queueMutex;
    RIPEventCallback m_callback = nullptr;

    char m_lastError[256] = { 0 };
};

#endif // RIP_CONTEXT_H