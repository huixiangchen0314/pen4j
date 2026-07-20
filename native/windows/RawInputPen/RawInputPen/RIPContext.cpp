/**
 * RIPContext.cpp - 多设备、无内部缓存、轻量映射实现
 *
 * 依赖：
 *   RIPDevice.h / RIPDevice.cpp  提供临时 HID 解析器
 *   RIPApi.h                     提供公开接口与结构体
 */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <hidsdi.h>
#include <hidpi.h>
#include <cstring>
#include <string>
#include <vector>
#include <algorithm>
#include <unordered_map>
#include <memory>
#include "RIPContext.h"
#include "RIPDevice.h"  
#include "RIPApi.h"

 // ── 笔设备注册模板 ──
#define RAW_INPUT_DEVICE_COUNT 3

struct RawInputDevicesRequest {
    RAWINPUTDEVICE devices[RAW_INPUT_DEVICE_COUNT];
    int count = RAW_INPUT_DEVICE_COUNT;
};

static void s_BuildRawInputDesc(HWND hwnd, DWORD dwFlags, RawInputDevicesRequest* desc) {
    USHORT usages[] = { 0x02, 0x01, 0x03 };   // 笔、触摸屏、笔(备选)
    for (int i = 0; i < RAW_INPUT_DEVICE_COUNT; i++) {
        desc->devices[i].usUsagePage = 0x000D;
        desc->devices[i].usUsage = usages[i];
        desc->devices[i].dwFlags = dwFlags;
        desc->devices[i].hwndTarget = hwnd;
    }
}

// ────────── RIPContext 构造 / 析构 ──────────
RIPContext::RIPContext() {}
RIPContext::~RIPContext() { Stop(); }

// ────────── 启动 / 停止 ──────────
RIPStatus RIPContext::Start() {
    if (m_running) {
        strncpy_s(m_lastError, "Already started", sizeof(m_lastError));
        return RIP_ERR_ALREADY_STARTED;
    }

    m_running = true;
    m_hInitEvent = CreateEventW(nullptr, TRUE, FALSE, nullptr);
    if (!m_hInitEvent) {
        m_running = false;
        strncpy_s(m_lastError, "CreateEvent failed", sizeof(m_lastError));
        return RIP_ERR_UNKNOWN;
    }

    m_hThread = CreateThread(nullptr, 0, ThreadProc, this, 0, nullptr);
    if (!m_hThread) {
        m_running = false;
        CloseHandle(m_hInitEvent);
        m_hInitEvent = nullptr;
        strncpy_s(m_lastError, "Failed to create thread", sizeof(m_lastError));
        return RIP_ERR_UNKNOWN;
    }

    DWORD waitResult = WaitForSingleObject(m_hInitEvent, 5000);
    CloseHandle(m_hInitEvent);
    m_hInitEvent = nullptr;

    if (waitResult != WAIT_OBJECT_0 || !m_initSuccess) {
        m_running = false;
        if (m_hwnd) PostMessage(m_hwnd, WM_QUIT, 0, 0);
        WaitForSingleObject(m_hThread, INFINITE);
        CloseHandle(m_hThread);
        m_hThread = nullptr;

        if (!m_initSuccess) {
            strncpy_s(m_lastError, "RegisterRawInputDevices failed", sizeof(m_lastError));
            return RIP_ERR_REGISTER_DEVICES_FAILED;
        }
        else {
            strncpy_s(m_lastError, "Thread initialization timed out", sizeof(m_lastError));
            return RIP_ERR_WINDOW_CREATE_FAILED;
        }
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

    // 清空映射与事件队列
    {
        std::lock_guard<std::mutex> lock(m_mapMutex);
        m_deviceUidMap.clear();
    }
    {
        std::lock_guard<std::mutex> lock(m_queueMutex);
        while (!m_eventQueue.empty()) m_eventQueue.pop();
    }
}

// ────────── 事件轮询（新接口） ──────────
int RIPContext::PollEventEx(RIPExtendedEvent* event) {
    std::lock_guard<std::mutex> lock(m_queueMutex);
    if (m_eventQueue.empty()) return 0;
    *event = m_eventQueue.front();
    m_eventQueue.pop();
    return 1;
}

// ────────── 设备实时枚举 ──────────
RIPStatus RIPContext::GetDeviceCount(uint32_t* count) {
    *count = 0;
    UINT nDevices = 0;
    if (GetRawInputDeviceList(nullptr, &nDevices, sizeof(RAWINPUTDEVICELIST)) == UINT(-1))
        return RIP_ERR_UNKNOWN;
    if (nDevices == 0) return RIP_OK;

    std::vector<RAWINPUTDEVICELIST> list(nDevices);
    if (GetRawInputDeviceList(list.data(), &nDevices, sizeof(RAWINPUTDEVICELIST)) == UINT(-1))
        return RIP_ERR_UNKNOWN;

    uint32_t penCount = 0;
    for (UINT i = 0; i < nDevices; ++i) {
        if (list[i].dwType != RIM_TYPEHID) continue;
        RID_DEVICE_INFO info = { sizeof(info) };
        UINT infoSize = sizeof(info);
        if (GetRawInputDeviceInfo(list[i].hDevice, RIDI_DEVICEINFO, &info, &infoSize) != sizeof(info))
            continue;
        if (!(info.hid.usUsagePage == 0x000D &&
            (info.hid.usUsage == 0x02 || info.hid.usUsage == 0x01 || info.hid.usUsage == 0x03)))
            continue;

        // 关键过滤：临时创建解析器，检查是否有笔尖或压力
        RIPDevice parser;
        if (parser.Initialize(list[i].hDevice) && parser.IsPhysicalPen()) {
            ++penCount;
        }
    }
    *count = penCount;
    return RIP_OK;
}

RIPStatus RIPContext::GetDeviceInfo(uint32_t index, RIPDeviceInfo* info) {
    UINT nDevices = 0;
    if (GetRawInputDeviceList(nullptr, &nDevices, sizeof(RAWINPUTDEVICELIST)) == UINT(-1))
        return RIP_ERR_UNKNOWN;

    std::vector<RAWINPUTDEVICELIST> list(nDevices);
    if (GetRawInputDeviceList(list.data(), &nDevices, sizeof(RAWINPUTDEVICELIST)) == UINT(-1))
        return RIP_ERR_UNKNOWN;

    uint32_t penFound = 0;
    for (UINT i = 0; i < nDevices; ++i) {
        if (list[i].dwType != RIM_TYPEHID) continue;
        RID_DEVICE_INFO devInfo = { sizeof(devInfo) };
        UINT infoSize = sizeof(devInfo);
        if (GetRawInputDeviceInfo(list[i].hDevice, RIDI_DEVICEINFO, &devInfo, &infoSize) != sizeof(devInfo))
            continue;
        if (!(devInfo.hid.usUsagePage == 0x000D &&
            (devInfo.hid.usUsage == 0x02 || devInfo.hid.usUsage == 0x01 || devInfo.hid.usUsage == 0x03)))
            continue;

        RIPDevice parser;
        if (!parser.Initialize(list[i].hDevice) || !parser.IsPhysicalPen()) {
            // 不是真正的笔设备，跳过，不增加 penFound
            continue;
        }

        if (penFound == index) {
            parser.FillDeviceInfo(*info);
            return RIP_OK;
        }
        ++penFound;
    }
    return RIP_ERR_UNKNOWN; // index 超出范围
}

// ────────── 线程与窗口过程 ──────────
DWORD WINAPI RIPContext::ThreadProc(LPVOID param) {
    auto* self = static_cast<RIPContext*>(param);
    self->m_initSuccess = false;

    if (!self->CreateMessageWindow()) {
        SetEvent(self->m_hInitEvent);
        return 1;
    }
    if (!self->RegisterRawInputDevices()) {
        SetEvent(self->m_hInitEvent);
        return 2;
    }

    self->m_initSuccess = true;
    SetEvent(self->m_hInitEvent);

    MSG msg;
    while (self->m_running) {
        BOOL bRet = GetMessage(&msg, nullptr, 0, 0);
        if (bRet <= 0) break;  // 0 = WM_QUIT, -1 = error
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    if (self->m_hwnd) {
        DestroyWindow(self->m_hwnd);
        self->m_hwnd = nullptr;
    }
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

    switch (msg) {
    case WM_INPUT:
        self->HandleRawInput(reinterpret_cast<HRAWINPUT>(lParam));
        break;
    case WM_INPUT_DEVICE_CHANGE:
        self->HandleDeviceChange(wParam, reinterpret_cast<HANDLE>(lParam));
        break;
    case WM_DESTROY:
        PostQuitMessage(0);
        break;
    default:
        return DefWindowProc(hwnd, msg, wParam, lParam);
    }
    return 0;
}

// ────────── 窗口创建与设备注册 ──────────
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
    RawInputDevicesRequest req;
    s_BuildRawInputDesc(m_hwnd, RIDEV_INPUTSINK | RIDEV_DEVNOTIFY, &req);
    if (!::RegisterRawInputDevices(req.devices, req.count, sizeof(RAWINPUTDEVICE))) {
        strncpy_s(m_lastError, "RegisterRawInputDevices failed", sizeof(m_lastError));
        return false;
    }
    return true;
}

void RIPContext::UnregisterRawInputDevices() {
    RawInputDevicesRequest req;
    s_BuildRawInputDesc(nullptr, RIDEV_REMOVE, &req);
    ::RegisterRawInputDevices(req.devices, req.count, sizeof(RAWINPUTDEVICE));
}

// ────────── 事件处理（核心变更）──────────
std::string RIPContext::GetOrCreateDeviceUid(HANDLE hDevice) {
    std::lock_guard<std::mutex> lock(m_mapMutex);
    auto it = m_deviceUidMap.find(hDevice);
    if (it != m_deviceUidMap.end())
        return it->second;

    // 通过 RIPDevice 获取统一的 UID
    std::string uid;
    RIPDevice parser;
    if (parser.Initialize(hDevice)) {
        uid = parser.GetDeviceUid();      // 返回 const char*，即 m_uid
    }
    if (uid.empty())
        uid = "unknown";

    m_deviceUidMap[hDevice] = uid;
    return uid;
}

void RIPContext::HandleRawInput(HRAWINPUT hRawInput) {
    UINT size = 0;
    GetRawInputData(hRawInput, RID_INPUT, nullptr, &size, sizeof(RAWINPUTHEADER));
    if (!size) return;

    std::vector<BYTE> buf(size);
    if (GetRawInputData(hRawInput, RID_INPUT, buf.data(), &size, sizeof(RAWINPUTHEADER)) != size)
        return;

    RAWINPUT* raw = reinterpret_cast<RAWINPUT*>(buf.data());
    if (raw->header.dwType != RIM_TYPEHID) return;

    // 获取设备 UID
    std::string uid = GetOrCreateDeviceUid(raw->header.hDevice);

    // 使用临时解析器提取事件
    RIPDevice parser;
    if (!parser.Initialize(raw->header.hDevice)) return;   // 解析失败则丢弃

    RIPEvent event;
    if (parser.ExtractEvent(raw->data.hid.bRawData, raw->data.hid.dwSizeHid, event)) {
        RIPExtendedEvent ext;
        strncpy_s(ext.deviceUid, uid.c_str(), sizeof(ext.deviceUid) - 1);
        ext.deviceUid[sizeof(ext.deviceUid) - 1] = '\0';
        ext.event = event;
        PushEvent(ext);
    }
}

void RIPContext::HandleDeviceChange(WPARAM wParam, HANDLE hDevice) {
    if (wParam == GIDC_REMOVAL) {
        std::lock_guard<std::mutex> lock(m_mapMutex);
        m_deviceUidMap.erase(hDevice);
    }
    // 插入事件由 HandleRawInput 自动发现并添加 UID，无需额外处理
}

void RIPContext::PushEvent(const RIPExtendedEvent& ev) {
    std::lock_guard<std::mutex> lock(m_queueMutex);
    if (m_eventQueue.size() >= MAX_RIP_EVENT_QUEUE)
        m_eventQueue.pop();
    m_eventQueue.push(ev);
}