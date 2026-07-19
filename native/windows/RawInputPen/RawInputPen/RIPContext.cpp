/**
 * RIPContext.cpp - 增强版，支持设备信息查询、多按钮、序列号
 */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <hidsdi.h>
#include <hidpi.h>
#include <cstring>
#include <string>
#include <vector>
#include <algorithm>
#include "RIPContext.h"

 // ────────── 导出包装 ──────────
RIPContext* RIPCreate(void) { return new (std::nothrow) RIPContext(); }
void RIPDestroy(RIPContext* ctx) { if (ctx) { ctx->Stop(); delete ctx; } }
RIPStatus RIPStart(RIPContext* ctx, RIPEventCallback cb) { return ctx ? ctx->Start(cb) : RIP_ERR_UNKNOWN; }
void RIPStop(RIPContext* ctx) { if (ctx) ctx->Stop(); }
int RIPPollEvent(RIPContext* ctx, RIPEvent* ev) { return ctx ? ctx->PollEvent(ev) : 0; }
const char* RIPGetLastError(RIPContext* ctx) { return ctx ? ctx->GetLastError() : "Invalid context"; }

RIPStatus RIPGetPressureRange(RIPContext* ctx, uint32_t* min, uint32_t* max) {
    if (!ctx || !min || !max) return RIP_ERR_UNKNOWN;
    ctx->GetPressureRange(*min, *max);
    return RIP_OK;
}

RIPStatus RIPGetLogicalRange(RIPContext* ctx, uint32_t* maxX, uint32_t* maxY) {
    if (!ctx || !maxX || !maxY) return RIP_ERR_UNKNOWN;
    ctx->GetLogicalRange(*maxX, *maxY);
    return RIP_OK;
}

RIPStatus RIPGetButtonCount(RIPContext* ctx, uint32_t* count) {
    if (!ctx || !count) return RIP_ERR_UNKNOWN;
    *count = ctx->GetButtonCount();
    return RIP_OK;
}

const char* RIPGetDeviceName(RIPContext* ctx) {
    return ctx ? ctx->GetDeviceName() : "";
}

RIPStatus RIPGetDeviceVid(RIPContext* ctx, uint16_t* vid) {
    if (!ctx || !vid) return RIP_ERR_UNKNOWN;
    *vid = ctx->GetDeviceVid();
    return RIP_OK;
}

RIPStatus RIPGetDevicePid(RIPContext* ctx, uint16_t* pid) {
    if (!ctx || !pid) return RIP_ERR_UNKNOWN;
    *pid = ctx->GetDevicePid();
    return RIP_OK;
}

const char* RIPGetDeviceUid(RIPContext* ctx) {
    return ctx ? ctx->GetDeviceUid() : "";
}

// ────────── RIPContext 实现 ──────────
RIPContext::RIPContext() {}
RIPContext::~RIPContext() { Stop(); }

RIPStatus RIPContext::Start(RIPEventCallback callback) {
    if (m_running) {
        strncpy_s(m_lastError, "Already started", sizeof(m_lastError));
        return RIP_ERR_ALREADY_STARTED;
    }
    m_callback = callback;
    m_running = true;
    m_hThread = CreateThread(nullptr, 0, ThreadProc, this, 0, nullptr);
    if (!m_hThread) {
        m_running = false;
        strncpy_s(m_lastError, "Failed to create thread", sizeof(m_lastError));
        return RIP_ERR_UNKNOWN;
    }
    for (int i = 0; i < 200 && !m_hwnd; ++i) Sleep(10);
    if (!m_hwnd) {
        m_running = false;
        WaitForSingleObject(m_hThread, 2000);
        CloseHandle(m_hThread);
        m_hThread = nullptr;
        strncpy_s(m_lastError, "Message window not created", sizeof(m_lastError));
        return RIP_ERR_WINDOW_CREATE_FAILED;
    }
    return RIP_OK;
}

void RIPContext::Stop() {
    if (!m_running) return;
    m_running = false;
    if (m_hwnd) PostMessage(m_hwnd, WM_QUIT, 0, 0);
    if (m_hThread) {
        WaitForSingleObject(m_hThread, 5000);
        CloseHandle(m_hThread);
        m_hThread = nullptr;
    }
    UnregisterRawInputDevices();
    if (m_hwnd) { DestroyWindow(m_hwnd); m_hwnd = nullptr; }
    ClearDeviceCache();
    m_callback = nullptr;
    std::lock_guard<std::mutex> lock(m_queueMutex);
    while (!m_queue.empty()) m_queue.pop();
}

int RIPContext::PollEvent(RIPEvent* event) {
    std::lock_guard<std::mutex> lock(m_queueMutex);
    if (m_queue.empty()) return 0;
    *event = m_queue.front();
    m_queue.pop();
    return 1;
}

DWORD WINAPI RIPContext::ThreadProc(LPVOID param) {
    auto* self = static_cast<RIPContext*>(param);
    if (!self->CreateMessageWindow()) return 1;
    if (!self->RegisterRawInputDevices()) return 2;
    MSG msg;
    while (self->m_running && GetMessage(&msg, nullptr, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }
    self->UnregisterRawInputDevices();
    if (self->m_hwnd) { DestroyWindow(self->m_hwnd); self->m_hwnd = nullptr; }
    return 0;
}

LRESULT CALLBACK RIPContext::WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    if (msg == WM_CREATE) {
        CREATESTRUCT* cs = reinterpret_cast<CREATESTRUCT*>(lParam);
        SetWindowLongPtr(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(cs->lpCreateParams));
        return 0;
    }
    auto* self = reinterpret_cast<RIPContext*>(GetWindowLongPtr(hwnd, GWLP_USERDATA));
    if (!self) return DefWindowProc(hwnd, msg, wParam, lParam);
    if (msg == WM_INPUT) {
        self->HandleRawInput(reinterpret_cast<HRAWINPUT>(lParam));
    }
    else if (msg == WM_DESTROY) {
        PostQuitMessage(0);
    }
    else {
        return DefWindowProc(hwnd, msg, wParam, lParam);
    }
    return 0;
}

bool RIPContext::CreateMessageWindow() {
    WNDCLASSEXW wc = { sizeof(wc) };
    wc.lpfnWndProc = WndProc;
    wc.hInstance = GetModuleHandleW(nullptr);
    wc.lpszClassName = L"RIPRawInputMsgWindow";
    if (!RegisterClassExW(&wc)) {
        if (::GetLastError() != ERROR_CLASS_ALREADY_EXISTS) {
            strncpy_s(m_lastError, "RegisterClassEx failed", sizeof(m_lastError));
            return false;
        }
    }
    m_hwnd = CreateWindowExW(0, wc.lpszClassName, nullptr, 0, 0, 0, 0, 0,
        HWND_MESSAGE, nullptr, GetModuleHandleW(nullptr), this);
    if (!m_hwnd) {
        strncpy_s(m_lastError, "CreateWindowEx failed", sizeof(m_lastError));
        return false;
    }
    return true;
}

bool RIPContext::RegisterRawInputDevices() {
    RAWINPUTDEVICE rid;
    rid.usUsagePage = 0x000D;
    rid.usUsage = 0x02;
    rid.dwFlags = RIDEV_INPUTSINK;
    rid.hwndTarget = m_hwnd;
    if (!::RegisterRawInputDevices(&rid, 1, sizeof(rid))) {
        strncpy_s(m_lastError, "RegisterRawInputDevices failed", sizeof(m_lastError));
        return false;
    }
    return true;
}

void RIPContext::UnregisterRawInputDevices() {
    RAWINPUTDEVICE rid;
    rid.usUsagePage = 0x000D;
    rid.usUsage = 0x02;
    rid.dwFlags = RIDEV_REMOVE;
    rid.hwndTarget = nullptr;
    ::RegisterRawInputDevices(&rid, 1, sizeof(rid));
}

// ── 设备信息提取 ──
void RIPContext::ExtractDeviceInfo(HANDLE hDevice) {
    UINT size = 0;
    GetRawInputDeviceInfo(hDevice, RIDI_DEVICENAME, nullptr, &size);
    if (size == 0) return;
    std::vector<wchar_t> pathBuf(size);
    if (GetRawInputDeviceInfo(hDevice, RIDI_DEVICENAME, pathBuf.data(), &size) == 0) return;
    m_devicePath = pathBuf.data();

    const std::wstring path(m_devicePath);
    size_t vidPos = path.find(L"VID_");
    size_t pidPos = path.find(L"PID_");
    if (vidPos != std::wstring::npos && pidPos != std::wstring::npos) {
        try {
            m_vid = (USHORT)std::stoul(path.substr(vidPos + 4, 4), nullptr, 16);
            m_pid = (USHORT)std::stoul(path.substr(pidPos + 4, 4), nullptr, 16);
        }
        catch (...) {
            m_vid = m_pid = 0;
        }
    }

    char nameBuf[128];
    sprintf_s(nameBuf, "Pen (VID_%04X&PID_%04X)", m_vid, m_pid);
    m_deviceName = nameBuf;

    m_uid = std::string(m_devicePath.begin(), m_devicePath.end());
    m_uidFinalized = false;
}

// ── HID 缓存：记录用途和逻辑范围，以及按钮 ──
bool RIPContext::InitializeDeviceCache(HANDLE hDevice) {
    ExtractDeviceInfo(hDevice);

    UINT size = 0;
    GetRawInputDeviceInfo(hDevice, RIDI_PREPARSEDDATA, nullptr, &size);
    if (!size) { strncpy_s(m_lastError, "Get preparsed size failed", sizeof(m_lastError)); return false; }
    m_preparsedData = (PHIDP_PREPARSED_DATA)malloc(size);
    if (!m_preparsedData) return false;
    if (GetRawInputDeviceInfo(hDevice, RIDI_PREPARSEDDATA, m_preparsedData, &size) != size) {
        free(m_preparsedData); m_preparsedData = nullptr; return false;
    }
    if (HidP_GetCaps(m_preparsedData, &m_caps) != HIDP_STATUS_SUCCESS) {
        free(m_preparsedData); m_preparsedData = nullptr; return false;
    }

    USHORT numCaps = m_caps.NumberInputValueCaps;
    if (!numCaps) { m_deviceCached = true; return true; }

    HIDP_VALUE_CAPS* vc = (HIDP_VALUE_CAPS*)malloc(sizeof(HIDP_VALUE_CAPS) * numCaps);
    if (!vc) { free(m_preparsedData); m_preparsedData = nullptr; return false; }
    if (HidP_GetValueCaps(HidP_Input, vc, &numCaps, m_preparsedData) != HIDP_STATUS_SUCCESS) {
        free(vc); free(m_preparsedData); m_preparsedData = nullptr; return false;
    }

    // 临时存储按钮 (page, usage)，用于排序
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

        if (page == 0x01) { // Generic Desktop
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
        else if (page == 0x0D) { // Digitizers
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
        else if (page == 0x09) { // Button
            tempButtons.emplace_back(page, usage);
        }
    }
    free(vc);

    // 排序按钮用途，确保索引对应按钮编号（usage 值通常为 1,2,3...）
    std::sort(tempButtons.begin(), tempButtons.end(),
        [](const auto& a, const auto& b) { return a.second < b.second; });
    // 去重（若同一用途出现多次）
    tempButtons.erase(std::unique(tempButtons.begin(), tempButtons.end(),
        [](const auto& a, const auto& b) { return a.second == b.second; }), tempButtons.end());

    m_buttonUsages.clear();
    for (const auto& btn : tempButtons) {
        m_buttonUsages.push_back({ btn.first, btn.second });
    }

    m_deviceCached = true;
    return true;
}

void RIPContext::ClearDeviceCache() {
    if (m_preparsedData) { free(m_preparsedData); m_preparsedData = nullptr; }
    m_deviceCached = false;
    m_hasX = m_hasY = m_hasPressure = false;
    m_hasTiltX = m_hasTiltY = m_hasTip = false;
    m_hasSerial = false;
    m_buttonUsages.clear();
}

// ── 使用 HidP_GetUsageValue 提取数据 ──
bool RIPContext::ExtractPenEvent(const BYTE* rawData, DWORD size, RIPEvent& out) {
    memset(&out, 0, sizeof(out));
    out.timestamp = (uint32_t)GetTickCount();
    if (!m_deviceCached) return false;

    ULONG ulVal = 0;

#define GET_USAGE_VAL(page, usage, hasFlag) \
        ( (hasFlag) ? ( HidP_GetUsageValue(HidP_Input, page, 0, usage, &ulVal, m_preparsedData, (PCHAR)rawData, size) == HIDP_STATUS_SUCCESS ? (LONG)ulVal : 0L ) : 0L )

    LONG rawX = GET_USAGE_VAL(m_pageX, m_usageX, m_hasX);
    LONG rawY = GET_USAGE_VAL(m_pageY, m_usageY, m_hasY);
    LONG rawP = GET_USAGE_VAL(m_pagePressure, m_usagePressure, m_hasPressure);
    LONG tiltX = GET_USAGE_VAL(m_pageTiltX, m_usageTiltX, m_hasTiltX);
    LONG tiltY = GET_USAGE_VAL(m_pageTiltY, m_usageTiltY, m_hasTiltY);
    LONG tip = GET_USAGE_VAL(m_pageTip, m_usageTip, m_hasTip);

    // 提取按钮状态到位掩码
    uint16_t buttonMask = 0;
    for (size_t i = 0; i < m_buttonUsages.size(); ++i) {
        const auto& btn = m_buttonUsages[i];
        if (HidP_GetUsageValue(HidP_Input, btn.page, 0, btn.usage, &ulVal,
            m_preparsedData, (PCHAR)rawData, size) == HIDP_STATUS_SUCCESS) {
            if (ulVal) {
                buttonMask |= (1 << i);  // 位 0 对应 button1
            }
        }
    }
    out.buttons = buttonMask;

    // 序列号提取（如果存在且未最终确定UID）
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

    // 坐标映射到屏幕像素
    int screenW = GetSystemMetrics(SM_CXSCREEN);
    int screenH = GetSystemMetrics(SM_CYSCREEN);
    LONG rangeX = m_logicalMaxX - m_logicalMinX;
    LONG rangeY = m_logicalMaxY - m_logicalMinY;
    out.x = (rangeX > 0) ? (float)((rawX - m_logicalMinX) * screenW / rangeX) : (float)rawX;
    out.y = (rangeY > 0) ? (float)((rawY - m_logicalMinY) * screenH / rangeY) : (float)rawY;

    // 压力：原始值（不归一化）
    out.pressure = (float)rawP;

    // 倾斜值
    out.tiltX = (float)tiltX;
    out.tiltY = (float)tiltY;

    // 笔尖状态
    out.tip = tip ? 1 : 0;
    out.reserved = 0; // 保留字段置零

    return true;
}

void RIPContext::PushEvent(const RIPEvent& ev) {
    std::lock_guard<std::mutex> lock(m_queueMutex);
    if (m_queue.size() >= MAX_RIP_EVENT_QUEUE) m_queue.pop();
    m_queue.push(ev);
}

void RIPContext::HandleRawInput(HRAWINPUT hRawInput) {
    UINT size = 0;
    GetRawInputData(hRawInput, RID_INPUT, nullptr, &size, sizeof(RAWINPUTHEADER));
    if (!size) return;

    BYTE* buf = new BYTE[size];
    if (GetRawInputData(hRawInput, RID_INPUT, buf, &size, sizeof(RAWINPUTHEADER)) != size) {
        delete[] buf; return;
    }

    RAWINPUT* raw = (RAWINPUT*)buf;
    if (raw->header.dwType != RIM_TYPEHID) { delete[] buf; return; }

    if (!m_deviceCached) InitializeDeviceCache(raw->header.hDevice);

    RIPEvent event;
    if (ExtractPenEvent(raw->data.hid.bRawData, raw->data.hid.dwSizeHid, event)) {
        if (m_callback) m_callback(&event);
        PushEvent(event);
    }
    delete[] buf;
}