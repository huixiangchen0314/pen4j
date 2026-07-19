package top.kzre.pen4j.windows.ink;

import top.kzre.pen4j.api.PenCursorType;
import top.kzre.pen4j.api.PenState;

public class WindowsInkParser {

    public PenState parse(PointerPenInfo penInfo, int msg) {
        int x = penInfo.pointerInfo.ptPixelLocation.x;
        int y = penInfo.pointerInfo.ptPixelLocation.y;

        double pressure = ((penInfo.penMask & PointerConstants.PEN_MASK_PRESSURE) != 0)
                ? penInfo.pressure
                : 0.0;

        double tiltX = 0, tiltY = 0;
        if ((penInfo.penMask & PointerConstants.PEN_MASK_TILT_X) != 0) tiltX = penInfo.tiltX;
        if ((penInfo.penMask & PointerConstants.PEN_MASK_TILT_Y) != 0) tiltY = penInfo.tiltY;

        boolean barrel = (penInfo.penFlags & PointerConstants.PEN_FLAG_BARREL) != 0;
        boolean eraser = (penInfo.penFlags & PointerConstants.PEN_FLAG_ERASER) != 0;
        boolean inContact = (penInfo.pointerInfo.pointerFlags & PointerConstants.POINTER_FLAG_INCONTACT) != 0;

        PenCursorType cursorType = eraser ? PenCursorType.ERASER : PenCursorType.PEN;

        // 按钮掩码：barrel 按钮 → bit0
        int buttons = barrel ? 1 : 0;

        return PenState.builder()
                .x(x)
                .y(y)
                .pressure(pressure)
                .tiltX(tiltX)
                .tiltY(tiltY)
                .cursorType(cursorType)
                .near(true)
                .tipPressed(inContact)
                .buttons(buttons)
                .eraserPressed(eraser)
                .build();
    }
}