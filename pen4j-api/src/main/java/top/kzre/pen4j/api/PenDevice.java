package top.kzre.pen4j.api;

import java.util.Set;

/**
 * 表示一个数位板或笔设备。
 * 在多笔 / 多工具场景下，每个逻辑设备对应一个 PenDevice 实例。
 */
public interface PenDevice {

    /** 设备名称，如 "Wacom Intuos Pro S" */
    String getName();

    /** 厂商名称，如 "Wacom"、"Huion" */
    String getVendor();

    /** 逻辑坐标系最大 X 值 */
    int getMaxX();

    /** 逻辑坐标系最大 Y 值 */
    int getMaxY();

    /** 最大压力原始值 */
    int getMaxPressure();

    /** 最大悬停距离（物理值），若不支持悬停则返回 0 */
    int getMaxProximity();

    /** 侧键数量（通常为 0、1、2） */
    int getSideButtonCount();

    /** 设备唯一标识（用于区分多支笔 / 多工具） */
    String getUid();

    /** 查询是否支持给定的能力 */
    boolean supports(PenCapability capability);

    /** 该设备支持的全部笔光标类型 */
    Set<PenCursorType> getSupportedCursorTypes();
}