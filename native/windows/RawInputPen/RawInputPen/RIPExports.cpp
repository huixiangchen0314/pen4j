/**
 * RIPExports.cpp - 导出包装（仅包含新 API，无内部逻辑）
 */
#include "RIPApi.h"
#include "RIPContext.h"

 // ── 上下文生命周期 ──
RIPContext* RIPCreate(void) {
    return new (std::nothrow) RIPContext();
}

void RIPDestroy(RIPContext* ctx) {
    if (ctx) {
        ctx->Stop();
        delete ctx;
    }
}

RIPStatus RIPStart(RIPContext* ctx) {
    return ctx ? ctx->Start() : RIP_ERR_UNKNOWN;
}

void RIPStop(RIPContext* ctx) {
    if (ctx) ctx->Stop();
}

// ── 事件轮询（多设备，带 deviceUid） ──
int RIPPollEventEx(RIPContext* ctx, RIPExtendedEvent* event) {
    return ctx ? ctx->PollEventEx(event) : 0;
}

// ── 设备实时枚举 ──
RIPStatus RIPGetDeviceCount(RIPContext* ctx, uint32_t* count) {
    if (!ctx || !count) return RIP_ERR_UNKNOWN;
    return ctx->GetDeviceCount(count);
}

RIPStatus RIPGetDeviceInfo(RIPContext* ctx, uint32_t index, RIPDeviceInfo* info) {
    if (!ctx || !info) return RIP_ERR_UNKNOWN;
    return ctx->GetDeviceInfo(index, info);
}

// ── 错误信息 ──
const char* RIPGetLastError(RIPContext* ctx) {
    return ctx ? ctx->GetLastError() : "Invalid context";
}