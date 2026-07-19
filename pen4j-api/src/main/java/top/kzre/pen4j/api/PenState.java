package top.kzre.pen4j.api;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;

@Value
@Builder
@ToString
public class PenState {
    /** 屏幕绝对坐标（原始像素值） */
    double x, y;
    /** 悬停高度（原始值） */
    double z;
    /** 原始压力值 */
    double pressure;
    /** 切向压力（喷枪指轮，原始值） */
    double tangentialPressure;
    /** 倾斜分量 X，范围 [-1,1]，正负表示方向 */
    double tiltX;
    /** 倾斜分量 Y，范围 [-1,1] */
    double tiltY;
    /** 方位角（度，0~360），NaN 表示不支持 */
    double azimuth;
    /** 仰角（度，0~90），NaN 表示不支持 */
    double altitude;
    /** 笔身旋转（度，0~360），NaN 表示不支持 */
    double twist;
    /** 3D 旋转 roll（度），NaN 表示不支持 */
    double roll;
    /** 3D 旋转 pitch（度），NaN 表示不支持 */
    double pitch;
    /** 3D 旋转 yaw（度），NaN 表示不支持 */
    double yaw;
    /** 当前光标类型 */
    PenCursorType cursorType;
    /** 笔硬件序列号 */
    long serialNumber;
    /** 原始按钮位掩码，bit0 = 按钮1，bit1 = 按钮2，…… */
    int buttons;
    /** 笔是否处于感应范围内 */
    boolean near;
    /** 笔尖是否接触表面 */
    boolean tipPressed;
    /** 是否为橡皮擦模式（由光标类型或硬件标志决定） */
    boolean eraserPressed;

    /**
     * 便捷方法：检测第 index 个按钮是否按下（index 从 1 开始）。
     * @param index 按钮编号（1-based）
     * @return true 表示按下
     */
    public boolean isButtonPressed(int index) {
        if (index < 1) return false;
        return ((buttons >> (index - 1)) & 1) != 0;
    }
}