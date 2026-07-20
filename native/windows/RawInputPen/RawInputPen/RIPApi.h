#pragma once
/**
 * RIPApi.h - Raw Input Pen 胶水层公开接口
 * 1. 多设备管理
 * 2. 设备发现事件
 * 3. 轮询优于通知
 */
#ifndef RIP_API_H
#define RIP_API_H

 // 导出宏
#ifdef RAWINPUTPEN_EXPORTS
#define RIPAPI __declspec(dllexport)
#else
#define RIPAPI __declspec(dllimport)
#endif

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>

    /* 不透明上下文句柄（C++ 中为 class，C 中为 struct） */
#ifdef __cplusplus
    class RIPContext;
#else
    typedef struct RIPContext RIPContext;
#endif

    /* 笔事件数据 */
    typedef struct {
        uint32_t timestamp;   /**< 毫秒时间戳 */
        float    x;           /**< 逻辑 X 坐标（屏幕像素） */
        float    y;           /**< 逻辑 Y 坐标（屏幕像素） */
        float    pressure;    /**< 设备原始压力值（未归一化） */
        float    tiltX;       /**< 倾斜 X（度） */
        float    tiltY;       /**< 倾斜 Y（度） */
        uint8_t  tip;         /**< 笔尖接触 (0/1) */
        uint8_t  reserved;    /**< 保留字节，对齐用 */
        uint16_t buttons;     /**< 按钮位掩码：bit0=button1, bit1=button2, ... */
    } RIPEvent;

    /* 回调函数类型 */
    typedef void (*RIPEventCallback)(const RIPEvent* event);

    /* 状态码 */
    typedef enum {
        RIP_OK = 0,
        RIP_ERR_ALREADY_STARTED,
        RIP_ERR_WINDOW_CREATE_FAILED,
        RIP_ERR_REGISTER_DEVICES_FAILED,
        RIP_ERR_UNKNOWN
    } RIPStatus;

    /**
     * 创建笔输入上下文。
     * @return 新上下文指针，失败返回 NULL。
     */
    RIPAPI RIPContext* RIPCreate(void);

    /**
     * 销毁上下文（自动停止监听并释放资源）。
     */
    RIPAPI void RIPDestroy(RIPContext* ctx);

    /**
     * 启动后台监听。
     * @param ctx      上下文
     * @param callback 事件回调（可为 NULL，仅使用轮询）
     */
    RIPAPI RIPStatus RIPStart(RIPContext* ctx, RIPEventCallback callback);

    /**
     * 停止监听，但保留上下文对象可再次启动。
     */
    RIPAPI void RIPStop(RIPContext* ctx);

    /**
     * 非阻塞轮询一个笔事件。
     * @param ctx   上下文
     * @param event 输出事件
     * @return 1=取到事件，0=队列空
     */
    RIPAPI int RIPPollEvent(RIPContext* ctx, RIPEvent* event);

    /**
     * 获取指定上下文的最后一次错误信息。
     * @return 静态字符串，上下文销毁后失效
     */
    RIPAPI const char* RIPGetLastError(RIPContext* ctx);

    /**
     * 获取压力逻辑范围（设备原始值）。
     * @param ctx   上下文
     * @param min   输出：最小压力（通常为 0）
     * @param max   输出：最大压力（例如 2047）
     * @return 状态码
     */
    RIPAPI RIPStatus RIPGetPressureRange(RIPContext* ctx, uint32_t* min, uint32_t* max);

    /**
     * 获取坐标逻辑范围（设备原始值）。
     * @param ctx   上下文
     * @param maxX  输出：X 轴最大逻辑值
     * @param maxY  输出：Y 轴最大逻辑值
     * @return 状态码
     */
    RIPAPI RIPStatus RIPGetLogicalRange(RIPContext* ctx, uint32_t* maxX, uint32_t* maxY);

    /**
     * 获取可用的笔杆按钮数量。
     * @param ctx   上下文
     * @param count 输出：按钮数量（不包括笔尖）
     * @return 状态码
     */
    RIPAPI RIPStatus RIPGetButtonCount(RIPContext* ctx, uint32_t* count);

    /**
     * 获取设备名称（基于 VID/PID 组合的默认描述）。
     * @param ctx 上下文
     * @return UTF-8 字符串，静态缓冲，下次调用可能覆盖，上下文销毁后失效。
     *         格式示例："Pen (VID_056A&PID_0087)"
     */
    RIPAPI const char* RIPGetDeviceName(RIPContext* ctx);

    /**
     * 获取设备的 USB VID（vendor ID）。
     * @param ctx 上下文
     * @param vid 输出：vendor ID
     * @return 状态码
     */
    RIPAPI RIPStatus RIPGetDeviceVid(RIPContext* ctx, uint16_t* vid);

    /**
     * 获取设备的 USB PID（product ID）。
     * @param ctx 上下文
     * @param pid 输出：product ID
     * @return 状态码
     */
    RIPAPI RIPStatus RIPGetDevicePid(RIPContext* ctx, uint16_t* pid);

    /**
     * 获取设备唯一标识符。
     * 优先使用笔的物理序列号（若支持），否则使用设备路径。
     * @param ctx 上下文
     * @return UTF-8 字符串，静态缓冲，下次调用可能覆盖，上下文销毁后失效。
     *         失败返回空字符串。
     */
    RIPAPI const char* RIPGetDeviceUid(RIPContext* ctx);

#ifdef __cplusplus
}
#endif

#endif /* RIP_API_H */