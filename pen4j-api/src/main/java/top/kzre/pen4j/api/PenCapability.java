package top.kzre.pen4j.api;

public enum PenCapability {
    PRESSURE,
    TILT,
    TWIST,
    ROTATION,           // 3D 旋转 (roll/pitch/yaw)
    PROXIMITY,
    SIDE_BUTTON,
    ERASER,
    TANGENTIAL_PRESSURE,
    WHEEL,
    MULTIPEN,           // 多支笔区分
    ABSOLUTE_MODE,
    RELATIVE_MODE
}