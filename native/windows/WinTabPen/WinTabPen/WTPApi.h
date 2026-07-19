/**
 * WTPApi.h - WinTab Pen 胶水层公开接口
 */
#pragma once

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

    /* 前置声明为 C++ class，消除类型不一致警告 */
#ifdef __cplusplus
    class WTPContext;
#else
    typedef struct WTPContext WTPContext;
#endif

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

    typedef void (*WTPEventCallback)(const WTPEvent* event);

    typedef enum {
        WTP_OK = 0,
        WTP_ERR_ALREADY_STARTED,
        WTP_ERR_CREATE_CONTEXT,
        WTP_ERR_UNKNOWN
    } WTPStatus;

    WTPAPI WTPContext* WTPCreate(void);
    WTPAPI void WTPDestroy(WTPContext* ctx);
    WTPAPI WTPStatus WTPStart(WTPContext* ctx, WTPEventCallback callback);
    WTPAPI void WTPStop(WTPContext* ctx);
    WTPAPI int WTPPollEvent(WTPContext* ctx, WTPEvent* event);
    WTPAPI const char* WTPGetLastError(WTPContext* ctx);

    WTPAPI WTPStatus WTPGetPressureRange(WTPContext* ctx, uint32_t* min, uint32_t* max);
    WTPAPI WTPStatus WTPGetLogicalRange(WTPContext* ctx, uint32_t* maxX, uint32_t* maxY);
    WTPAPI WTPStatus WTPGetButtonCount(WTPContext* ctx, uint32_t* count);
    WTPAPI const char* WTPGetDeviceName(WTPContext* ctx);
    WTPAPI WTPStatus WTPGetDeviceVid(WTPContext* ctx, uint16_t* vid);
    WTPAPI WTPStatus WTPGetDevicePid(WTPContext* ctx, uint16_t* pid);
    WTPAPI const char* WTPGetDeviceUid(WTPContext* ctx);

#ifdef __cplusplus
}
#endif

#endif