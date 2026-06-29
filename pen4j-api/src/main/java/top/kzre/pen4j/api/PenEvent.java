package top.kzre.pen4j.api;

/**
 * 数位板笔事件，每次硬件数据更新时产生。
 * 携带发生时间、来源设备以及完整的笔状态快照。
 */
public interface PenEvent {

    /** 产生事件的设备 */
    PenDevice getDevice();

    /** 事件发生时间（微秒，从某个固定起点开始计，用于计算增量） */
    long getTimestampMicros();

    /** 此时笔的完整状态（不可变快照） */
    PenState getState();
}