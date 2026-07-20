#pragma once

#include <windows.h>
#include <atomic>
#include <queue>
#include <mutex>
#include <string>
#include <vector>
#include <unordered_map>
#include "WTPApi.h"
#include "wintab.h"

#define MAX_WTP_EVENT_QUEUE 1024

class WTPContext {
public:
    WTPContext();
    ~WTPContext();

    WTPStatus Start();
    void Stop();
    int PollEventEx(WTPExtendedEvent* event);
    const char* GetLastError() const { return m_lastError; }

    // 实时枚举（每次调用都直接查询系统，不依赖内部状态）
    WTPStatus GetDeviceCount(uint32_t* count);
    WTPStatus GetDeviceInfo(uint32_t index, WTPDeviceInfo* info);

private:
    bool LoadWinTabFunctions();
    void FreeWinTabFunctions();
    bool CreateContext();
    void DestroyContext();
    void ExtractPacket(const BYTE* raw, WTPEvent& out);
    void PushEvent(const WTPExtendedEvent& ev);
    static DWORD WINAPI ThreadProc(LPVOID param);

    // 填充当前设备信息到内部字符串
    void QueryCurrentDeviceInfo();

    // WinTab 函数指针
    HINSTANCE m_hModule = nullptr;
    typedef UINT(WINAPI* WTInfoW_t)(UINT, UINT, LPVOID);
    typedef HCTX(WINAPI* WTOpenW_t)(HWND, LPLOGCONTEXTW, BOOL);
    typedef BOOL(WINAPI* WTClose_t)(HCTX);
    typedef BOOL(WINAPI* WTEnable_t)(HCTX, BOOL);
    typedef int   (WINAPI* WTPacketsGet_t)(HCTX, int, LPVOID);

    WTInfoW_t      pWTInfoW = nullptr;
    WTOpenW_t      pWTOpenW = nullptr;
    WTClose_t      pWTClose = nullptr;
    WTEnable_t     pWTEnable = nullptr;
    WTPacketsGet_t pWTPacketsGet = nullptr;

    HCTX m_hCtx = nullptr;
    LOGCONTEXTW m_lc = {};
    int m_packetSize = 0;
    DWORD m_pktDataMask = 0;
    int m_queueSize = 32;

    LONG m_orientMax[3] = { 3600, 900, 3600 };
    LONG m_rotationMax[3] = { 3600, 3600, 3600 };

    std::atomic<bool> m_running{ false };
    HANDLE m_hThread = nullptr;
    std::queue<WTPExtendedEvent> m_queue;
    std::mutex m_queueMutex;

    // 当前设备信息（用于事件填充）
    std::string m_currentDeviceUid;
    std::string m_currentDeviceName;
    uint16_t m_currentVid = 0, m_currentPid = 0;
    uint32_t m_currentMaxPressure = 0;
    uint32_t m_currentMaxX = 0, m_currentMaxY = 0;
    uint32_t m_currentButtonCount = 0;

    char m_lastError[256] = { 0 };
};