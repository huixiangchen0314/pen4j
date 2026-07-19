#pragma once

#include <windows.h>
#include <atomic>
#include <queue>
#include <mutex>
#include <string>
#include <vector>
#include "WTPApi.h"
#include "wintab.h"   // 结构定义仍需要

#define MAX_WTP_EVENT_QUEUE 1024

class WTPContext {
public:
    WTPContext();
    ~WTPContext();

    WTPStatus Start(WTPEventCallback callback);
    void Stop();
    int PollEvent(WTPEvent* event);
    const char* GetLastError() const { return m_lastError; }

    void GetPressureRange(uint32_t& outMin, uint32_t& outMax) const;
    void GetLogicalRange(uint32_t& outMaxX, uint32_t& outMaxY) const;
    uint32_t GetButtonCount() const { return (uint32_t)m_buttonCount; }
    const char* GetDeviceName() const { return m_deviceName.c_str(); }
    uint16_t GetDeviceVid() const { return m_vid; }
    uint16_t GetDevicePid() const { return m_pid; }
    const char* GetDeviceUid() const { return m_uid.c_str(); }

private:
    bool LoadWinTabFunctions();
    void FreeWinTabFunctions();
    bool CreateContext();
    void DestroyContext();
    void ExtractPacket(const BYTE* raw, WTPEvent& out);
    void PushEvent(const WTPEvent& ev);
    static DWORD WINAPI ThreadProc(LPVOID param);

    // 动态函数指针
    HINSTANCE m_hModule = nullptr;
    // 声明所有需要的函数指针类型
    typedef UINT(WINAPI* WTInfoW_t)(UINT, UINT, LPVOID);
    typedef HCTX(WINAPI* WTOpenW_t)(HWND, LPLOGCONTEXTW, BOOL);
    typedef BOOL(WINAPI* WTClose_t)(HCTX);
    typedef BOOL(WINAPI* WTEnable_t)(HCTX, BOOL);
    typedef int  (WINAPI* WTPacketsGet_t)(HCTX, int, LPVOID);

    WTInfoW_t       pWTInfoW = nullptr;
    WTOpenW_t       pWTOpenW = nullptr;
    WTClose_t       pWTClose = nullptr;
    WTEnable_t      pWTEnable = nullptr;
    WTPacketsGet_t  pWTPacketsGet = nullptr;

    HCTX m_hCtx = nullptr;
    LOGCONTEXTW m_lc = {};
    int m_packetSize = 0;
    DWORD m_pktDataMask = 0;
    int m_queueSize = 32;

    LONG m_orientMax[3] = { 3600, 900, 3600 };
    LONG m_rotationMax[3] = { 3600, 3600, 3600 };

    std::atomic<bool> m_running{ false };
    HANDLE m_hThread = nullptr;
    std::queue<WTPEvent> m_queue;
    std::mutex m_queueMutex;
    WTPEventCallback m_callback = nullptr;

    std::string m_deviceName;
    std::string m_uid;
    std::wstring m_devicePath;
    uint16_t m_vid = 0, m_pid = 0;
    uint32_t m_maxX = 65535, m_maxY = 65535;
    uint32_t m_pressureMin = 0, m_pressureMax = 1023;
    int m_buttonCount = 2;

    char m_lastError[256] = { 0 };
};