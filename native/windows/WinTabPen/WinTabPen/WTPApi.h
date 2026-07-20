#pragma once
/**
 * WTPApi.h - WinTab Pen 胶水层公开接口（多设备，无内部缓存）
 */
#ifndef WTP_API_H
#define WTP_API_H

#ifdef WIN_TAB_PEN_EXPORTS
#define WTPAPI __declspec(dllexport)
#else
#define WTPAPI __declspec(dllimport)
#endif

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>

#ifdef __cplusplus
    class WTPContext;
#else
    typedef struct WTPContext WTPContext;
#endif

    /* 笔事件数据（内部使用，不直接暴露） */
    typedef struct {
        uint32_t timestamp;
        float    x, y;
        float    pressure;
        float    tangentialPressure;
        float    tiltX, tiltY;
        float    azimuth;
        float    altitude;
        float    twist;
        float    roll, pitch, yaw;
        uint8_t  tip;
        uint8_t  proximity;
        uint8_t  eraser;
        uint8_t  reserved;
        uint16_t buttons;
    } WTPEvent;

    /* 设备信息（枚举时返回） */
    typedef struct {
        char     deviceName[128];
        char     uid[256];
        uint16_t vid;
        uint16_t pid;
        uint32_t maxPressure;
        uint32_t maxLogicalX;
        uint32_t maxLogicalY;
        uint32_t buttonCount;
        uint32_t reserved;
    } WTPDeviceInfo;

    /* 扩展笔事件（携带设备 UID） */
    typedef struct {
        char     deviceUid[256];
        WTPEvent event;
    } WTPExtendedEvent;

    typedef enum {
        WTP_OK = 0,
        WTP_ERR_ALREADY_STARTED,
        WTP_ERR_CREATE_CONTEXT,
        WTP_ERR_UNKNOWN
    } WTPStatus;

    /* ── 上下文生命周期 ── */
    WTPAPI WTPContext* WTPCreate(void);
    WTPAPI void        WTPDestroy(WTPContext* ctx);
    WTPAPI WTPStatus   WTPStart(WTPContext* ctx);   /* 无回调，仅轮询模式 */
    WTPAPI void        WTPStop(WTPContext* ctx);

    /* ── 事件轮询（多设备） ── */
    WTPAPI int WTPPollEventEx(WTPContext* ctx, WTPExtendedEvent* event);

    /* ── 设备实时枚举 ── */
    WTPAPI WTPStatus WTPGetDeviceCount(WTPContext* ctx, uint32_t* count);
    WTPAPI WTPStatus WTPGetDeviceInfo(WTPContext* ctx, uint32_t index, WTPDeviceInfo* info);

    /* ── 错误信息 ── */
    WTPAPI const char* WTPGetLastError(WTPContext* ctx);

#ifdef __cplusplus
}
#endif

#endif /* WTP_API_H */