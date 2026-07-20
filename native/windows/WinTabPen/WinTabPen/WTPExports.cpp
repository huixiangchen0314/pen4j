/**
 * WTPExports.cpp - WinTab 胶水层导出包装
 * 所有函数仅做参数校验并转发到 WTPContext 成员方法，不包含业务逻辑。
 */
#include "WTPContext.h"

 // ── 上下文生命周期 ──
WTPContext* WTPCreate(void) {
    return new (std::nothrow) WTPContext();
}

void WTPDestroy(WTPContext* ctx) {
    if (ctx) {
        ctx->Stop();
        delete ctx;
    }
}

WTPStatus WTPStart(WTPContext* ctx) {
    return ctx ? ctx->Start() : WTP_ERR_UNKNOWN;
}

void WTPStop(WTPContext* ctx) {
    if (ctx) ctx->Stop();
}

// ── 事件轮询（多设备，带 deviceUid） ──
int WTPPollEventEx(WTPContext* ctx, WTPExtendedEvent* event) {
    return ctx ? ctx->PollEventEx(event) : 0;
}

// ── 设备实时枚举 ──
WTPStatus WTPGetDeviceCount(WTPContext* ctx, uint32_t* count) {
    if (!ctx || !count) return WTP_ERR_UNKNOWN;
    return ctx->GetDeviceCount(count);
}

WTPStatus WTPGetDeviceInfo(WTPContext* ctx, uint32_t index, WTPDeviceInfo* info) {
    if (!ctx || !info) return WTP_ERR_UNKNOWN;
    return ctx->GetDeviceInfo(index, info);
}

// ── 错误信息 ──
const char* WTPGetLastError(WTPContext* ctx) {
    return ctx ? ctx->GetLastError() : "Invalid context";
}