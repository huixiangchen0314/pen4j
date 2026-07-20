#pragma once
/**
 * RIPApi.h - Raw Input Pen 胶水层公开接口（多设备，无内部缓存）
 *
 * 设计原则：
 *  1. 多设备管理 - 实时枚举系统连接的笔设备，不做内部缓存
 *  2. 轮询驱动   - 通过 RIPPollEventEx 获取带设备标识的事件
 *  3. 轻量映射   - 事件到来时仅维护设备句柄到路径的映射（用于填充 deviceUid）
 *  4. 无热插拔通知 - Java 层通过定时枚举或事件中发现新 UID 来处理设备变更
 */
#ifndef RIP_API_H
#define RIP_API_H

#ifdef RAWINPUTPEN_EXPORTS
#define RIPAPI __declspec(dllexport)
#else
#define RIPAPI __declspec(dllimport)
#endif

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>

#ifdef __cplusplus
    class RIPContext;
#else
    typedef struct RIPContext RIPContext;
#endif

    /* ── 笔事件数据（不变） ── */
    typedef struct {
        uint32_t timestamp;   /* 毫秒时间戳 */
        float    x;           /* 逻辑 X 坐标（屏幕像素） */
        float    y;           /* 逻辑 Y 坐标（屏幕像素） */
        float    pressure;    /* 设备原始压力值（未归一化） */
        float    tiltX;       /* 倾斜 X（度） */
        float    tiltY;       /* 倾斜 Y（度） */
        uint8_t  tip;         /* 笔尖接触 (0/1) */
        uint8_t  reserved;    /* 保留 */
        uint16_t buttons;     /* 按钮位掩码 */
    } RIPEvent;

    /* ── 设备信息（枚举时返回） ── */
    typedef struct {
        char     deviceName[128];  /* UTF-8 设备名称 */
        char     uid[256];         /* 唯一标识符（设备路径或序列号） */
        uint16_t vid;
        uint16_t pid;
        uint32_t maxPressure;
        uint32_t maxLogicalX;
        uint32_t maxLogicalY;
        uint32_t buttonCount;      /* 笔身按钮数量 */
        uint32_t reserved;
    } RIPDeviceInfo;

    /* ── 扩展笔事件（携带设备 UID） ── */
    typedef struct {
        char     deviceUid[256];   /* 产生事件的设备唯一标识 */
        RIPEvent event;
    } RIPExtendedEvent;

    typedef enum {
        RIP_OK = 0,
        RIP_ERR_ALREADY_STARTED,
        RIP_ERR_WINDOW_CREATE_FAILED,
        RIP_ERR_REGISTER_DEVICES_FAILED,
        RIP_ERR_UNKNOWN
    } RIPStatus;

    /* ── 上下文生命周期 ── */
    RIPAPI RIPContext* RIPCreate(void);
    RIPAPI void        RIPDestroy(RIPContext* ctx);
    RIPAPI RIPStatus   RIPStart(RIPContext* ctx);   /* 不再接受回调，仅轮询模式 */
    RIPAPI void        RIPStop(RIPContext* ctx);

    /* ── 事件轮询（多设备） ── */
    /**
     * 非阻塞轮询一个带设备标识的笔事件。
     * @return 1=有事件，0=队列空
     */
    RIPAPI int RIPPollEventEx(RIPContext* ctx, RIPExtendedEvent* event);

    /* ── 设备实时枚举（每次调用均查询系统） ── */
    /**
     * 获取当前已连接的笔设备数量。
     */
    RIPAPI RIPStatus RIPGetDeviceCount(RIPContext* ctx, uint32_t* count);

    /**
     * 获取指定索引的设备信息。
     * @param index 从 0 到 count-1
     */
    RIPAPI RIPStatus RIPGetDeviceInfo(RIPContext* ctx, uint32_t index, RIPDeviceInfo* info);

    /* ── 错误信息 ── */
    RIPAPI const char* RIPGetLastError(RIPContext* ctx);

#ifdef __cplusplus
}
#endif

#endif /* RIP_API_H */