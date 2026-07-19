package top.kzre.pen4j.windows.wintab;

import top.kzre.pen4j.api.*;
import top.kzre.pen4j.windows.common.WinConstants;

final class WintabPacketParser {

    private final long pktDataMask;

    public WintabPacketParser(long pktDataMask) {
        this.pktDataMask = pktDataMask;
    }

    /**
     * 将原始数据包解析为 PenState。
     *
     * 标准 WinTab 协议中：
     * - pkButtons 的位含义：
     *   bit 0 (0x01): 笔尖接触
     *   bit 1 (0x02): 笔杆第一按钮（barrel 1）
     *   bit 2 (0x04): 笔杆第二按钮（barrel 2）
     * - pkStatus 的常用位含义（可能因驱动而异，此处采用常见约定）：
     *   bit 0 (0x0001): 笔在感应范围内（near/proximity）
     *   bit 1 (0x0002): 橡皮擦/笔倒置（invert/eraser）
     */
    public PenState parse(PACKET pkt, WintabPenDevice dev) {
        long status = pkt.getPkStatus();
        long cursor = pkt.getPkCursor();
        int buttons = (int) pkt.getPkButtons();

        // 从 buttons 提取笔尖和侧键
        boolean tipPressed      = (buttons & 0x01) != 0;  // 笔尖接触
        boolean button1Pressed  = (buttons & 0x02) != 0;  // 笔杆按钮1
        boolean button2Pressed  = (buttons & 0x04) != 0;  // 笔杆按钮2

        // 从 status 提取 near 和 eraser（约定位）
        boolean near            = (status & 0x0001) != 0; // TPS_NEAR / SYS_NEAR
        boolean eraserPressed   = (status & 0x0002) != 0; // TPS_INVERT / SYS_ERASER

        // 压力及扩展数据（按掩码解析）
        double normalPressure = (double) pkt.getPkNormalPressure();
        double tangentPressure = (pktDataMask & WintabConst.PK_TANGENTPRESSURE) != 0 ?
                (double) pkt.getPkTangentPressure() : 0.0;
        double z = (pktDataMask & WintabConst.PK_Z) != 0 ? (double) pkt.getPkZ() : 0.0;

        // 倾角/方向（仅当请求了 PK_ORIENTATION 且设备支持）
        double tiltX = 0, tiltY = 0;
        double azimuth = Double.NaN, altitude = Double.NaN, twist = Double.NaN;
        if ((pktDataMask & WintabConst.PK_ORIENTATION) != 0 && dev.supports(PenCapability.TILT)) {
            long az = pkt.getOrientationAzimuth();
            long al = pkt.getOrientationAltitude();
            long tw = pkt.getOrientationTwist();
            if (az >= 0 && al >= 0) {
                double azRad = Math.toRadians(az / 10.0);
                double alRad = Math.toRadians(al / 10.0);
                tiltX = Math.sin(azRad) * Math.sin(alRad);
                tiltY = Math.cos(azRad) * Math.sin(alRad);
                azimuth = az / 10.0;
                altitude = al / 10.0;
                twist = tw / 10.0;
            }
        }

        double roll = Double.NaN, pitch = Double.NaN, yaw = Double.NaN;
        if ((pktDataMask & WintabConst.PK_ROTATION) != 0) {
            long r = pkt.getRotationRoll();
            long p = pkt.getRotationPitch();
            long y = pkt.getRotationYaw();
            // 约定：-1 表示未提供（由 PACKET 填充），则保留 NaN
            if (r >= 0) roll  = r;
            if (p >= 0) pitch = p;
            if (y >= 0) yaw   = y;
        }

        PenCursorType cursorType = mapCursorType((int) cursor);

        return PenState.builder()
                .x((double) pkt.getPkX())
                .y((double) pkt.getPkY())
                .z(z)
                .pressure(normalPressure)
                .tangentialPressure(tangentPressure)
                .tiltX(tiltX)
                .tiltY(tiltY)
                .azimuth(azimuth)
                .altitude(altitude)
                .twist(twist)
                .roll(roll)
                .pitch(pitch)
                .yaw(yaw)
                .cursorType(cursorType)
                .serialNumber(pkt.getPkSerialNumber())
                .buttons(buttons)          // 保留原始 buttons 值，便于调试
                .near(near)
                .tipPressed(tipPressed)
                .buttons(buttons)
                .eraserPressed(eraserPressed)
                .build();
    }

    public int calcPacketSize() {
        return calcPacketSize(pktDataMask);
    }

    public static int calcPacketSize(long mask) {
        int size = 0;
        if ((mask & WintabConst.PK_CONTEXT) != 0) size += 8;
        if ((mask & WintabConst.PK_STATUS) != 0) size += 4;
        if ((mask & WintabConst.PK_TIME) != 0) size += 4;
        if ((mask & WintabConst.PK_CHANGED) != 0) size += 4;
        if ((mask & WintabConst.PK_SERIALNUMBER) != 0) size += 4;
        if ((mask & WintabConst.PK_CURSOR) != 0) size += 4;
        if ((mask & WintabConst.PK_BUTTONS) != 0) size += 4;
        if ((mask & WintabConst.PK_X) != 0) size += 4;
        if ((mask & WintabConst.PK_Y) != 0) size += 4;
        if ((mask & WintabConst.PK_Z) != 0) size += 4;
        if ((mask & WintabConst.PK_NORMALPRESSURE) != 0) size += 4;
        if ((mask & WintabConst.PK_TANGENTPRESSURE) != 0) size += 4;
        if ((mask & WintabConst.PK_ORIENTATION) != 0) size += 12;
        if ((mask & WintabConst.PK_ROTATION) != 0) size += 12;
        return size;
    }

    private PenCursorType mapCursorType(int csrType) {
        if ((csrType & WintabConst.CSR_TYPE_ERASER) != 0) return PenCursorType.ERASER;
        if ((csrType & WintabConst.CSR_TYPE_AIRBRUSH) != 0) return PenCursorType.AIRBRUSH;
        if ((csrType & WintabConst.CSR_TYPE_LENS) != 0) return PenCursorType.LENS_CURSOR;
        if ((csrType & WintabConst.CSR_TYPE_CURSOR) != 0) return PenCursorType.CURSOR;
        return PenCursorType.PEN;
    }
}