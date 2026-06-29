package top.kzre.pen4j.core;

import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import top.kzre.pen4j.api.PenDevice;
import top.kzre.pen4j.api.PenEvent;
import top.kzre.pen4j.api.PenState;

/**
 * PenEvent 的基础实现，平台驱动通过此对象传递事件。
 */
@Value
@ToString
public class DefaultPenEvent implements PenEvent {
    @NonNull PenDevice device;
    long timestampMicros;
    @NonNull PenState state;

}