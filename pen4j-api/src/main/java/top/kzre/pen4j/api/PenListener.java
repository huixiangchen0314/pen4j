package top.kzre.pen4j.api;

/**
 * 笔事件监听器。
 * 实现此接口并注册到 PenContext 以接收实时笔数据。
 */
public interface PenListener {

    /**
     * 当笔状态更新时调用。
     * 注意：此方法可能在非 UI 线程中调用，实现者需注意线程安全。
     */
    void onPenData(PenEvent event);

    /**
     * 新设备连接时调用（可选实现）。
     */
    default void onDeviceAdded(PenDevice device) {}

    /**
     * 设备断开时调用（可选实现）。
     */
    default void onDeviceRemoved(PenDevice device) {}
}