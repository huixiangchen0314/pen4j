#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <cstring>
#include <string>
#include <vector>
#include <algorithm>
#include "WTPContext.h"

static std::string WcharToUtf8(const WCHAR* wstr) {
    if (!wstr) return {};
    int len = WideCharToMultiByte(CP_UTF8, 0, wstr, -1, nullptr, 0, nullptr, nullptr);
    if (len <= 0) return {};
    std::vector<char> buf(len);
    WideCharToMultiByte(CP_UTF8, 0, wstr, -1, buf.data(), len, nullptr, nullptr);
    return std::string(buf.data());
}

static int ExtractVidFromPnpId(const std::wstring& pnpId) {
    if (pnpId.empty()) return 0;
    std::wstring upper = pnpId;
    std::transform(upper.begin(), upper.end(), upper.begin(), ::towupper);
    size_t pos = upper.find(L"VID_");
    if (pos != std::wstring::npos && pos + 8 <= upper.size()) {
        try { return std::stoi(upper.substr(pos + 4, 4), nullptr, 16); }
        catch (...) {}
    }
    return 0;
}

// ── 导出包装 ──
WTPContext* WTPCreate(void) { return new (std::nothrow) WTPContext(); }
void WTPDestroy(WTPContext* ctx) { if (ctx) { ctx->Stop(); delete ctx; } }
WTPStatus WTPStart(WTPContext* ctx, WTPEventCallback cb) { return ctx ? ctx->Start(cb) : WTP_ERR_UNKNOWN; }
void WTPStop(WTPContext* ctx) { if (ctx) ctx->Stop(); }
int WTPPollEvent(WTPContext* ctx, WTPEvent* ev) { return ctx ? ctx->PollEvent(ev) : 0; }
const char* WTPGetLastError(WTPContext* ctx) { return ctx ? ctx->GetLastError() : "Invalid context"; }

WTPStatus WTPGetPressureRange(WTPContext* ctx, uint32_t* min, uint32_t* max) {
    if (!ctx || !min || !max) return WTP_ERR_UNKNOWN;
    ctx->GetPressureRange(*min, *max);
    return WTP_OK;
}

WTPStatus WTPGetLogicalRange(WTPContext* ctx, uint32_t* maxX, uint32_t* maxY) {
    if (!ctx || !maxX || !maxY) return WTP_ERR_UNKNOWN;
    ctx->GetLogicalRange(*maxX, *maxY);
    return WTP_OK;
}

WTPStatus WTPGetButtonCount(WTPContext* ctx, uint32_t* count) {
    if (!ctx || !count) return WTP_ERR_UNKNOWN;
    *count = ctx->GetButtonCount();
    return WTP_OK;
}

const char* WTPGetDeviceName(WTPContext* ctx) { return ctx ? ctx->GetDeviceName() : ""; }

WTPStatus WTPGetDeviceVid(WTPContext* ctx, uint16_t* vid) {
    if (!ctx || !vid) return WTP_ERR_UNKNOWN;
    *vid = ctx->GetDeviceVid();
    return WTP_OK;
}

WTPStatus WTPGetDevicePid(WTPContext* ctx, uint16_t* pid) {
    if (!ctx || !pid) return WTP_ERR_UNKNOWN;
    *pid = ctx->GetDevicePid();
    return WTP_OK;
}

const char* WTPGetDeviceUid(WTPContext* ctx) { return ctx ? ctx->GetDeviceUid() : ""; }

// ── WTPContext 实现 ──
WTPContext::WTPContext() {
    LoadWinTabFunctions();
}

WTPContext::~WTPContext() {
    Stop();
    FreeWinTabFunctions();
}

bool WTPContext::LoadWinTabFunctions() {
    if (m_hModule) return true;
    m_hModule = LoadLibraryW(L"wintab32.dll");
    if (!m_hModule) {
        strncpy_s(m_lastError, "Cannot load wintab32.dll", sizeof(m_lastError));
        return false;
    }

    pWTInfoW = (WTInfoW_t)GetProcAddress(m_hModule, "WTInfoW");
    pWTOpenW = (WTOpenW_t)GetProcAddress(m_hModule, "WTOpenW");
    pWTClose = (WTClose_t)GetProcAddress(m_hModule, "WTClose");
    pWTEnable = (WTEnable_t)GetProcAddress(m_hModule, "WTEnable");
    pWTPacketsGet = (WTPacketsGet_t)GetProcAddress(m_hModule, "WTPacketsGet");

    if (!pWTInfoW || !pWTOpenW || !pWTClose || !pWTEnable || !pWTPacketsGet) {
        strncpy_s(m_lastError, "Failed to resolve WinTab functions", sizeof(m_lastError));
        FreeWinTabFunctions();
        return false;
    }
    return true;
}

void WTPContext::FreeWinTabFunctions() {
    if (m_hModule) {
        FreeLibrary(m_hModule);
        m_hModule = nullptr;
    }
    pWTInfoW = nullptr;
    pWTOpenW = nullptr;
    pWTClose = nullptr;
    pWTEnable = nullptr;
    pWTPacketsGet = nullptr;
}

WTPStatus WTPContext::Start(WTPEventCallback callback) {
    if (m_running) {
        strncpy_s(m_lastError, "Already started", sizeof(m_lastError));
        return WTP_ERR_ALREADY_STARTED;
    }
    if (!pWTInfoW) {
        strncpy_s(m_lastError, "WinTab functions not loaded", sizeof(m_lastError));
        return WTP_ERR_CREATE_CONTEXT;
    }
    m_callback = callback;
    if (!CreateContext()) {
        strncpy_s(m_lastError, "Failed to create WinTab context", sizeof(m_lastError));
        return WTP_ERR_CREATE_CONTEXT;
    }
    m_running = true;
    m_hThread = CreateThread(nullptr, 0, ThreadProc, this, 0, nullptr);
    if (!m_hThread) {
        DestroyContext();
        m_running = false;
        strncpy_s(m_lastError, "Failed to create thread", sizeof(m_lastError));
        return WTP_ERR_UNKNOWN;
    }
    Sleep(50);
    return WTP_OK;
}

void WTPContext::Stop() {
    if (!m_running) return;
    m_running = false;
    if (m_hCtx && pWTEnable) pWTEnable(m_hCtx, FALSE);
    if (m_hThread) {
        WaitForSingleObject(m_hThread, 5000);
        CloseHandle(m_hThread);
        m_hThread = nullptr;
    }
    DestroyContext();
    m_callback = nullptr;
    std::lock_guard<std::mutex> lock(m_queueMutex);
    while (!m_queue.empty()) m_queue.pop();
}

int WTPContext::PollEvent(WTPEvent* event) {
    std::lock_guard<std::mutex> lock(m_queueMutex);
    if (m_queue.empty()) return 0;
    *event = m_queue.front();
    m_queue.pop();
    return 1;
}

void WTPContext::GetPressureRange(uint32_t& outMin, uint32_t& outMax) const {
    outMin = m_pressureMin; outMax = m_pressureMax;
}

void WTPContext::GetLogicalRange(uint32_t& outMaxX, uint32_t& outMaxY) const {
    outMaxX = m_maxX; outMaxY = m_maxY;
}

DWORD WINAPI WTPContext::ThreadProc(LPVOID param) {
    auto* self = static_cast<WTPContext*>(param);
    int bufSize = self->m_packetSize * self->m_queueSize;
    std::vector<BYTE> buffer(bufSize);

    while (self->m_running) {
        if (!self->pWTPacketsGet) break;
        int count = self->pWTPacketsGet(self->m_hCtx, self->m_queueSize, buffer.data());
        if (count <= 0) { Sleep(1); continue; }
        for (int i = 0; i < count; ++i) {
            WTPEvent event;
            self->ExtractPacket(buffer.data() + i * self->m_packetSize, event);
            if (self->m_callback) self->m_callback(&event);
            self->PushEvent(event);
        }
    }
    return 0;
}

bool WTPContext::CreateContext() {
    ZeroMemory(&m_lc, sizeof(m_lc));
    if (!pWTInfoW || pWTInfoW(WTI_DEFCONTEXT, 0, &m_lc) == 0) {
        strncpy_s(m_lastError, "WTInfoW failed", sizeof(m_lastError));
        return false;
    }

    m_pktDataMask = PK_X | PK_Y | PK_NORMAL_PRESSURE | PK_TANGENT_PRESSURE |
        PK_BUTTONS | PK_STATUS | PK_TIME | PK_ORIENTATION | PK_ROTATION |
        PK_CHANGED | PK_CURSOR;
    m_lc.lcPktData = m_pktDataMask;
    m_lc.lcPktMode = 0;
    m_lc.lcOptions |= CXO_PEN;
    m_lc.lcOptions &= ~CXO_MESSAGES;
    m_lc.lcMoveMask = m_pktDataMask;
    m_lc.lcBtnDnMask = m_lc.lcBtnUpMask = 0;

    int sw = GetSystemMetrics(SM_CXSCREEN);
    int sh = GetSystemMetrics(SM_CYSCREEN);
    m_lc.lcOutOrgX = m_lc.lcOutOrgY = 0;
    m_lc.lcOutExtX = sw;
    m_lc.lcOutExtY = sh;
    m_lc.lcSensX = m_lc.lcSensY = m_lc.lcSensZ = 0x00010000;

    HWND hWnd = GetDesktopWindow();
    m_hCtx = pWTOpenW(hWnd, &m_lc, TRUE);
    if (!m_hCtx) {
        strncpy_s(m_lastError, "WTOpenW failed", sizeof(m_lastError));
        return false;
    }

    // 计算包大小
    m_packetSize = 0;
    if (m_pktDataMask & PK_CONTEXT)         m_packetSize += sizeof(HCTX);
    if (m_pktDataMask & PK_STATUS)          m_packetSize += sizeof(UINT);
    if (m_pktDataMask & PK_TIME)            m_packetSize += sizeof(DWORD);
    if (m_pktDataMask & PK_CHANGED)         m_packetSize += sizeof(WTPKT);
    if (m_pktDataMask & PK_SERIAL_NUMBER)   m_packetSize += sizeof(UINT);
    if (m_pktDataMask & PK_CURSOR)          m_packetSize += sizeof(UINT);
    if (m_pktDataMask & PK_BUTTONS)         m_packetSize += sizeof(DWORD);
    if (m_pktDataMask & PK_X)              m_packetSize += sizeof(LONG);
    if (m_pktDataMask & PK_Y)              m_packetSize += sizeof(LONG);
    if (m_pktDataMask & PK_Z)              m_packetSize += sizeof(LONG);
    if (m_pktDataMask & PK_NORMAL_PRESSURE) m_packetSize += sizeof(UINT);
    if (m_pktDataMask & PK_TANGENT_PRESSURE) m_packetSize += sizeof(UINT);
    if (m_pktDataMask & PK_ORIENTATION)    m_packetSize += sizeof(ORIENTATION);
    if (m_pktDataMask & PK_ROTATION)       m_packetSize += sizeof(ROTATION);

    // 查询设备信息
    WCHAR nameBuf[256] = { 0 };
    pWTInfoW(WTI_DEVICES + m_lc.lcDevice, DVC_NAME, nameBuf);
    m_deviceName = WcharToUtf8(nameBuf);
    if (m_deviceName.empty()) m_deviceName = "WinTab Pen";

    AXIS pressureAxis;
    if (pWTInfoW(WTI_DEVICES + m_lc.lcDevice, DVC_NPRESSURE, &pressureAxis)) {
        m_pressureMin = pressureAxis.axMin;
        m_pressureMax = pressureAxis.axMax;
    }

    m_maxX = m_lc.lcOutExtX ? m_lc.lcOutExtX : 65535;
    m_maxY = m_lc.lcOutExtY ? m_lc.lcOutExtY : 65535;

    BYTE btnCount = 0;
    pWTInfoW(WTI_CURSORS + 0, CSR_BUTTONS, &btnCount);
    m_buttonCount = max(btnCount, 0);

    WCHAR pnpBuf[256] = { 0 };
    if (pWTInfoW(WTI_DEVICES + m_lc.lcDevice, DVC_PNPID, pnpBuf)) {
        m_devicePath = pnpBuf;
        m_vid = ExtractVidFromPnpId(m_devicePath);
        m_pid = 0;
    }

    AXIS orientAxis[3];
    if (pWTInfoW(WTI_DEVICES + m_lc.lcDevice, DVC_ORIENTATION, orientAxis)) {
        for (int i = 0; i < 3; ++i) m_orientMax[i] = orientAxis[i].axMax;
    }
    AXIS rotAxis[3];
    if (pWTInfoW(WTI_DEVICES + m_lc.lcDevice, DVC_ROTATION, rotAxis)) {
        for (int i = 0; i < 3; ++i) m_rotationMax[i] = rotAxis[i].axMax;
    }

    m_uid = m_deviceName + "-" + std::to_string(m_vid);
    return true;
}

void WTPContext::DestroyContext() {
    if (m_hCtx && pWTClose) { pWTClose(m_hCtx); m_hCtx = nullptr; }
}

void WTPContext::ExtractPacket(const BYTE* raw, WTPEvent& out) {
    memset(&out, 0, sizeof(out));
    const BYTE* ptr = raw;
    DWORD mask = m_pktDataMask;

    if (mask & PK_CONTEXT)         ptr += sizeof(HCTX);

    if (mask & PK_STATUS) {
        UINT status = *(UINT*)ptr;
        out.proximity = (status & TPS_PROXIMITY) ? 1 : 0;
        out.eraser = (status & TPS_INVERT) ? 1 : 0;
        ptr += sizeof(UINT);
    }

    if (mask & PK_TIME) { out.timestamp = *(DWORD*)ptr; ptr += sizeof(DWORD); }
    if (mask & PK_CHANGED)         ptr += sizeof(WTPKT);
    if (mask & PK_SERIAL_NUMBER)   ptr += sizeof(UINT);
    if (mask & PK_CURSOR)          ptr += sizeof(UINT);

    if (mask & PK_BUTTONS) {
        DWORD btns = *(DWORD*)ptr;
        out.buttons = (uint16_t)(btns & 0xFFFF);
        out.tip = (btns & 0x01) ? 1 : 0;
        ptr += sizeof(DWORD);
    }

    if (mask & PK_X) { out.x = (float)*(LONG*)ptr; ptr += sizeof(LONG); }
    if (mask & PK_Y) { out.y = (float)*(LONG*)ptr; ptr += sizeof(LONG); }
    if (mask & PK_Z)               ptr += sizeof(LONG);

    if (mask & PK_NORMAL_PRESSURE) { out.pressure = (float)*(UINT*)ptr; ptr += sizeof(UINT); }
    if (mask & PK_TANGENT_PRESSURE) { out.tangentialPressure = (float)*(UINT*)ptr; ptr += sizeof(UINT); }

    if (mask & PK_ORIENTATION) {
        ORIENTATION* ori = (ORIENTATION*)ptr;
        int azimuth = ori->orAzimuth;
        int altitude = ori->orAltitude;
        int twist = ori->orTwist;

        out.azimuth = azimuth / 10.0f;
        out.altitude = altitude / 10.0f;
        out.twist = twist / 10.0f;

        if (altitude >= 0) {
            double altMax = (double)m_orientMax[1];
            double azMax = (double)m_orientMax[0];
            if (altMax > 0 && azMax > 0) {
                double rangedAlt = altitude / altMax;
                if (rangedAlt > 1.0) rangedAlt = 1.0;
                double betha = rangedAlt * 1.5707963267948966;
                double theta = (azimuth / azMax) * 6.283185307179586 - 1.5707963267948966;
                out.tiltX = (float)atan(cos(theta) / tan(betha));
                out.tiltY = (float)atan(sin(theta) / tan(betha));
            }
            else {
                out.tiltX = (float)azimuth / 3600.0f;
                out.tiltY = (float)altitude / 900.0f;
            }
        }
        else {
            out.tiltX = out.tiltY = 0.0f;
        }
        ptr += sizeof(ORIENTATION);
    }

    if (mask & PK_ROTATION) {
        ROTATION* rot = (ROTATION*)ptr;
        out.roll = rot->roRoll / 10.0f;
        out.pitch = rot->roPitch / 10.0f;
        out.yaw = rot->roYaw / 10.0f;
        ptr += sizeof(ROTATION);
    }
}

void WTPContext::PushEvent(const WTPEvent& ev) {
    std::lock_guard<std::mutex> lock(m_queueMutex);
    if (m_queue.size() >= MAX_WTP_EVENT_QUEUE) m_queue.pop();
    m_queue.push(ev);
}