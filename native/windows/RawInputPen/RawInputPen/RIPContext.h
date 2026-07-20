#pragma once
/**
 * RIPContext.h - 笔输入上下文（多设备，无内部设备缓存，纯轮询）
 *
 * 维护最轻量的内部状态：
 *  1. 消息窗口 + 后台线程（接收 WM_INPUT）
 *  2. 设备句柄 -> UID 映射（用于填充事件中的 deviceUid）
 *  3. 统一扩展事件队列（FIFO，最大容量 1024）
 *
 * 设备信息查询（GetDeviceCount / GetDeviceInfo）实时通过系统 API 获取，
 * 不依赖任何历史缓存。HID 解析借助临时 RIPDevice 对象完成。
 */
#ifndef RIP_CONTEXT_H
#define RIP_CONTEXT_H

#include <windows.h>
#include <atomic>
#include <queue>
#include <mutex>
#include <string>
#include <unordered_map>
#include <memory>       // unique_ptr（如果缓存解析器则使用）
#include "RIPApi.h"     // 引入 RIPExtendedEvent, RIPDeviceInfo 等

#define MAX_RIP_EVENT_QUEUE  1024

class RIPDevice;  // 前置声明，解析器类

class RIPContext {
public:
    RIPContext();
    ~RIPContext();

    // 启动消息窗口和后台线程（注册设备、开启消息循环）
    RIPStatus Start();
    void Stop();

    // 取出一个带设备标识的事件（非阻塞）
    int PollEventEx(RIPExtendedEvent* event);
    const char* GetLastError() const { return m_lastError; }

    // 实时设备枚举（每次调用都直接查询系统）
    RIPStatus GetDeviceCount(uint32_t* count);
    RIPStatus GetDeviceInfo(uint32_t index, RIPDeviceInfo* info);

private:
    // 禁止拷贝
    RIPContext(const RIPContext&) = delete;
    RIPContext& operator=(const RIPContext&) = delete;

    // 窗口线程与过程
    static DWORD WINAPI ThreadProc(LPVOID param);
    static LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam);

    bool CreateMessageWindow();
    bool RegisterRawInputDevices();
    void UnregisterRawInputDevices();

    // 事件处理
    void HandleRawInput(HRAWINPUT hRawInput);
    void HandleDeviceChange(WPARAM wParam, HANDLE hDevice);
    void PushEvent(const RIPExtendedEvent& ev);

    // 辅助：从设备句柄获取稳定 UID
    std::string GetOrCreateDeviceUid(HANDLE hDevice);

    // 线程与窗口
    std::atomic<bool> m_running{ false };
    HWND               m_hwnd = nullptr;
    HANDLE             m_hThread = nullptr;

    // 初始化同步
    HANDLE m_hInitEvent = nullptr;
    bool   m_initSuccess = false;

    // 设备句柄 -> UID 映射（仅用于事件填充，极轻量）
    std::unordered_map<HANDLE, std::string> m_deviceUidMap;
    std::mutex m_mapMutex;

    // 统一事件队列（扩展事件，包含 deviceUid）
    std::queue<RIPExtendedEvent> m_eventQueue;
    std::mutex m_queueMutex;

    // 错误信息
    char m_lastError[256] = { 0 };
};

#endif // RIP_CONTEXT_H