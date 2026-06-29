package top.kzre.pen4j.windows.wintab;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.WinDef.UINT;
import lombok.extern.slf4j.Slf4j;
import top.kzre.pen4j.api.PenCapability;
import top.kzre.pen4j.api.PenCursorType;

import java.util.*;

/**
 * WinTab 笔设备探测器。
 * 通过 WTInfoW 动态查询所有可用的设备 / 光标，并构建 WintabPenDevice 列表。
 */
@Slf4j
final class WintabPenProbe {

    private WintabPenProbe() {}

    /**
     * 探测当前系统所有可用的 WinTab 笔设备（每个光标作为一个逻辑设备）。
     * 典型情况：设备 0 的光标 0 为钢笔，光标 1 为橡皮擦。
     */
    public static List<WintabPenDevice> probeAll() {
        List<WintabPenDevice> list = new ArrayList<>();
        try {
            // 获取设备数量（这里假设一台物理设备，光标可能有多个）
            int deviceIdx = 0;
            // 尝试探测光标 0（通常为钢笔）
            WintabPenDevice pen = probeCursor(deviceIdx, 0);
            if (pen != null) list.add(pen);
            // 尝试探测光标 1（通常为橡皮擦）
            WintabPenDevice eraser = probeCursor(deviceIdx, 1);
            if (eraser != null) list.add(eraser);
        } catch (Exception e) {
            log.error("Failed to probe WinTab devices", e);
        }
        // 若全部失败，回退硬编码（防止驱动空指针）
        if (list.isEmpty()) {
            log.warn("No WinTab devices found, using fallback defaults");
            list.add(createFallbackPen());
            list.add(createFallbackEraser());
        }
        return list;
    }

    private static WintabPenDevice probeCursor(int deviceIdx, int cursorIdx) {
        try {
            // 1. 检查光标是否存在（CSR_NAME）
            Memory tmp = new Memory(4);
            UINT ret = WintabLibrary.INSTANCE.WTInfoW(
                    new UINT(WintabConst.WTI_CURSORS + cursorIdx),
                    new UINT(WintabConst.CSR_NAME), tmp);
            if (ret.intValue() == 0) {
                log.debug("Cursor {}/{} not available", deviceIdx, cursorIdx);
                return null;
            }

            // 2. 设备名
            Memory devNameBuf = new Memory(256);
            WintabLibrary.INSTANCE.WTInfoW(
                    new UINT(WintabConst.WTI_DEVICES + deviceIdx),
                    new UINT(WintabConst.DVC_NAME), devNameBuf);
            String devName = devNameBuf.getWideString(0);

            // 3. 光标类型
            Memory csrTypeBuf = new Memory(4);
            WintabLibrary.INSTANCE.WTInfoW(
                    new UINT(WintabConst.WTI_CURSORS + cursorIdx),
                    new UINT(WintabConst.CSR_TYPE), csrTypeBuf);
            int csrType = csrTypeBuf.getInt(0);

            // 4. 压力范围 (DVC_NPRESSURE)
            Memory prBuf = new Memory(8);
            UINT prRet = WintabLibrary.INSTANCE.WTInfoW(
                    new UINT(WintabConst.WTI_DEVICES + deviceIdx),
                    new UINT(WintabConst.DVC_NPRESSURE), prBuf);
            int maxPressure = 8192; // 默认
            if (prRet.intValue() >= 8) {
                int minPr = prBuf.getInt(0);
                int maxPr = prBuf.getInt(4);
                maxPressure = Math.max(maxPr, 1);
            }

            // 5. 从默认上下文获取坐标范围
            LOGCONTEXTW defCtx = new LOGCONTEXTW();
            UINT defRet = WintabLibrary.INSTANCE.WTInfoW(
                    new UINT(WintabConst.WTI_DEFCONTEXT),
                    new UINT(0), defCtx.getPointer());
            defCtx.read();
            int inOrgX = (int) defCtx.lcInOrgX.longValue();
            int inOrgY = (int) defCtx.lcInOrgY.longValue();
            int inExtX = (int) defCtx.lcInExtX.longValue();
            int inExtY = (int) defCtx.lcInExtY.longValue();
            if (inExtX <= 0) inExtX = 20000;
            if (inExtY <= 0) inExtY = 15000;
            int maxX = inExtX - 1;
            int maxY = inExtY - 1;

            // 6. 能力判断
            Set<PenCapability> caps = EnumSet.of(
                    PenCapability.PRESSURE, PenCapability.PROXIMITY,
                    PenCapability.SIDE_BUTTON, PenCapability.ABSOLUTE_MODE);

            // 倾斜 (DVC_ORIENTATION)
            Memory orBuf = new Memory(12);
            UINT orRet = WintabLibrary.INSTANCE.WTInfoW(
                    new UINT(WintabConst.WTI_DEVICES + deviceIdx),
                    new UINT(WintabConst.DVC_ORIENTATION), orBuf);
            if (orRet.intValue() > 0) {
                caps.add(PenCapability.TILT);
            }

            // 切向压力 (DVC_TPRESSURE)
            Memory tpBuf = new Memory(8);
            UINT tpRet = WintabLibrary.INSTANCE.WTInfoW(
                    new UINT(WintabConst.WTI_DEVICES + deviceIdx),
                    new UINT(WintabConst.DVC_TPRESSURE), tpBuf);
            if (tpRet.intValue() > 0) {
                caps.add(PenCapability.TANGENTIAL_PRESSURE);
            }

            // 橡皮擦
            if ((csrType & WintabConst.CSR_TYPE_ERASER) != 0) {
                caps.add(PenCapability.ERASER);
            }

            // 多笔区分（若驱动支持）
            caps.add(PenCapability.MULTIPEN);

            // 旋转 (PK_ROTATION) 通过在打开上下文后检测，这里先不加

            // 7. 光标类型映射
            Set<PenCursorType> cursors = EnumSet.noneOf(PenCursorType.class);
            if ((csrType & WintabConst.CSR_TYPE_PEN) != 0) cursors.add(PenCursorType.PEN);
            if ((csrType & WintabConst.CSR_TYPE_AIRBRUSH) != 0) cursors.add(PenCursorType.AIRBRUSH);
            if ((csrType & WintabConst.CSR_TYPE_ERASER) != 0) cursors.add(PenCursorType.ERASER);
            if ((csrType & WintabConst.CSR_TYPE_LENS) != 0) cursors.add(PenCursorType.LENS_CURSOR);
            if ((csrType & WintabConst.CSR_TYPE_CURSOR) != 0) cursors.add(PenCursorType.CURSOR);
            if (cursors.isEmpty()) cursors.add(PenCursorType.PEN);

            return new WintabPenDevice(
                    deviceIdx, cursorIdx, devName,
                    maxX, maxY, maxPressure,
                    inOrgX, inOrgY, inExtX, inExtY,
                    caps, cursors);
        } catch (Exception e) {
            log.warn("Failed to probe cursor {}/{}: {}", deviceIdx, cursorIdx, e.getMessage());
            return null;
        }
    }

    private static WintabPenDevice createFallbackPen() {
        return new WintabPenDevice(
                0, 0, "Pen Tablet",
                19999, 14999, 8192,
                0, 0, 20000, 15000,
                EnumSet.of(PenCapability.PRESSURE, PenCapability.TILT, PenCapability.PROXIMITY),
                EnumSet.of(PenCursorType.PEN));
    }

    private static WintabPenDevice createFallbackEraser() {
        return new WintabPenDevice(
                0, 1, "Pen Tablet Eraser",
                19999, 14999, 8192,
                0, 0, 20000, 15000,
                EnumSet.of(PenCapability.PRESSURE, PenCapability.ERASER),
                EnumSet.of(PenCursorType.ERASER));
    }
}