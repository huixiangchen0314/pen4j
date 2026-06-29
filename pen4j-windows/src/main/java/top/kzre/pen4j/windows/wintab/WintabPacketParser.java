package top.kzre.pen4j.windows.wintab;

import top.kzre.pen4j.api.*;
import top.kzre.pen4j.windows.common.WinConstants;

final class WintabPacketParser {

    private final long pktDataMask;

    public WintabPacketParser(long pktDataMask) {
        this.pktDataMask = pktDataMask;
    }

    public PenState parse(PACKET pkt, WintabPenDevice dev) {
        long status = pkt.getPkStatus();
        long cursor = pkt.getPkCursor();
        int buttons = (int) pkt.getPkButtons();

        double normalPressure = (double) pkt.getPkNormalPressure();
        double tangentPressure = (pktDataMask & WintabConst.PK_TANGENTPRESSURE) != 0 ?
                (double) pkt.getPkTangentPressure() : 0.0;
        double z = (pktDataMask & WintabConst.PK_Z) != 0 ? (double) pkt.getPkZ() : 0.0;

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
        // 旋转预留

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
                .buttons(buttons)
                .near((status & WintabConst.PK_PENNEAR) != 0)
                .tipPressed((status & WintabConst.PK_PENCONTACT) != 0)
                .button1Pressed((status & WintabConst.PK_PENBARREL0) != 0)
                .button2Pressed((status & WintabConst.PK_PENBARREL1) != 0)
                .eraserPressed((status & WintabConst.PK_PENERASER) != 0)
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