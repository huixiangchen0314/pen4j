package top.kzre.pen4j.core;

import top.kzre.pen4j.api.*;
import java.util.*;

/**
 * 平台驱动抽象类：只定义轮询驱动必须实现的两个底层操作。
 * 线程管理、设备列表、事件分发全部由 {@link PollPenDriverTemplate} 处理。
 */
public abstract class PollPenDriver {
    /**
     * 平台相关的启动逻辑（如打开设备、创建上下文）。
     * 由模板在 {@link PollPenDriverTemplate#start(PenListener)} 中调用。
     */
    public abstract void onStart() throws Exception;

    /**
     * 平台相关的停止逻辑（如释放资源）。
     */
    public abstract void onStop() throws Exception;

    /**
     * 实时枚举当前连接的所有笔设备。
     * 由模板每 2 秒自动调用一次。
     */
    public abstract List<PenDevice> enumerateDevices();

    /**
     * 非阻塞拉取一个笔事件。
     * @return 事件对象（必须包含设备 UID），没有事件时返回 null。
     *         由模板每毫秒调用一次。
     */
    public abstract PenEvent pollEvent();

    public boolean isAvailable() { return true; }   // 默认可用，子类可覆写
}