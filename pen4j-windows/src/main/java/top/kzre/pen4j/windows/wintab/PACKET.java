package top.kzre.pen4j.windows.wintab;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * PACKET 按 C++ 结构体顺序解析，固定 68 字节（含 ULONGLONG 对齐）。
 * 对应 C++ 定义（#pragma pack(1)）：
 *   ULONGLONG pkContext; UINT pkStatus; UINT pkTime; UINT pkChanged;
 *   UINT pkSerialNumber; UINT pkCursor; UINT pkButtons;
 *   LONG pkX; LONG pkY; LONG pkZ;
 *   LONG pkNormalPressure; LONG pkTangentPressure;
 *   struct { UINT azimuth, altitude, twist; } pkOrientation;
 */
public class PACKET {
    private final long context;
    private final long status;
    private final long time;
    private final long changed;
    private final long serialNumber;
    private final long cursor;
    private final long buttons;
    private final long x, y, z;
    private final long normalPressure, tangentPressure;
    private final long orientationAzimuth, orientationAltitude, orientationTwist;
    private final long rotationRoll, rotationPitch, rotationYaw;

    public PACKET(byte[] raw, long mask) {
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        context        = (mask & WintabConst.PK_CONTEXT)        != 0 ? buf.getLong()  : 0;
        status         = (mask & WintabConst.PK_STATUS)         != 0 ? buf.getInt()   : 0;
        time           = (mask & WintabConst.PK_TIME)           != 0 ? buf.getInt()   : 0;
        changed        = (mask & WintabConst.PK_CHANGED)        != 0 ? buf.getInt()   : 0;
        serialNumber   = (mask & WintabConst.PK_SERIALNUMBER)   != 0 ? buf.getInt()   : 0;
        cursor         = (mask & WintabConst.PK_CURSOR)         != 0 ? buf.getInt()   : 0;
        buttons        = (mask & WintabConst.PK_BUTTONS)        != 0 ? buf.getInt()   : 0;
        x              = (mask & WintabConst.PK_X)              != 0 ? buf.getInt()   : 0;
        y              = (mask & WintabConst.PK_Y)              != 0 ? buf.getInt()   : 0;
        z              = (mask & WintabConst.PK_Z)              != 0 ? buf.getInt()   : 0;
        normalPressure = (mask & WintabConst.PK_NORMALPRESSURE) != 0 ? buf.getInt()   : 0;
        tangentPressure= (mask & WintabConst.PK_TANGENTPRESSURE)!= 0 ? buf.getInt()   : 0;
        orientationAzimuth  = (mask & WintabConst.PK_ORIENTATION) != 0 ? buf.getInt() : -1;
        orientationAltitude = (mask & WintabConst.PK_ORIENTATION) != 0 ? buf.getInt() : -1;
        orientationTwist    = (mask & WintabConst.PK_ORIENTATION) != 0 ? buf.getInt() : -1;
        rotationRoll   = (mask & WintabConst.PK_ROTATION) != 0 ? buf.getInt() : -1;
        rotationPitch  = (mask & WintabConst.PK_ROTATION) != 0 ? buf.getInt() : -1;
        rotationYaw    = (mask & WintabConst.PK_ROTATION) != 0 ? buf.getInt() : -1;
    }

    // 安全 getter，永不会抛异常
    public long getPkContext()            { return context; }
    public long getPkStatus()            { return status; }
    public long getPkTime()              { return time; }
    public long getPkChanged()           { return changed; }
    public long getPkSerialNumber()      { return serialNumber; }
    public long getPkCursor()            { return cursor; }
    public long getPkButtons()           { return buttons; }
    public long getPkX()                 { return x; }
    public long getPkY()                 { return y; }
    public long getPkZ()                 { return z; }
    public long getPkNormalPressure()    { return normalPressure; }
    public long getPkTangentPressure()   { return tangentPressure; }
    public long getOrientationAzimuth()  { return orientationAzimuth; }
    public long getOrientationAltitude() { return orientationAltitude; }
    public long getOrientationTwist()    { return orientationTwist; }
    public long getRotationRoll()  { return rotationRoll; }
    public long getRotationPitch() { return rotationPitch; }
    public long getRotationYaw()   { return rotationYaw; }
}