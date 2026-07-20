package top.kzre.pen4j.core;

/**
 * 全局 SmartTaskLoop 容器，提供设备探测与事件轮询的共享循环。
 * 线程随任务自动启停，无需手动管理。
 */
public final class TaskLoops {

    private static final SmartTaskLoop DEVICE_PROBE = new SmartTaskLoop("pen4j-DeviceProbeLoop", 2000);
    private static final SmartTaskLoop EVENT_POLL   = new SmartTaskLoop("pen4j-EventPollLoop", 1);

    private TaskLoops() {}

    /** 设备探测循环（每2秒执行一次注册的探测任务） */
    public static SmartTaskLoop deviceProbe() {
        return DEVICE_PROBE;
    }

    /** 事件轮询循环（约1ms执行一次注册的轮询任务） */
    public static SmartTaskLoop eventPoll() {
        return EVENT_POLL;
    }
}