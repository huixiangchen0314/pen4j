package top.kzre.pen4j.windows.wintab;

/** Wintab 常量，从 wintab.h / wintab32.h 摘的。 */
final class WintabConst {
    private WintabConst() {}

    // WTInfo categories
    public static final int WTI_INTERFACE  = 1;
    public static final int WTI_STATUS     = 2;
    public static final int WTI_DEFCONTEXT = 3;
    public static final int WTI_DEVICES    = 100;
    public static final int WTI_CURSORS    = 200;

    // WTInfo indexes (DEFCONTEXT)
    public static final int IFC_WINTABID   = 1;
    public static final int IFC_SPECVERSION = 2;

    // Device / Cursor capability tags
    public static final int DVC_NAME       = 1;
    public static final int DVC_XFACTOR    = 2;
    public static final int DVC_YFACTOR    = 3;
    public static final int DVC_NPRESSURE  = 4;   // 压力轴范围
    public static final int DVC_TPRESSURE  = 5;   // 切向压力轴范围
    public static final int DVC_ORIENTATION = 6;  // 倾斜支持标记

    public static final int CSR_NAME       = 1;
    public static final int CSR_PHYSID     = 2;
    public static final int CSR_TYPE       = 3;   // 光标类型

    // LOGCONTEXT lcOptions
    public static final int CXO_SYSTEM     = 0x0001;
    public static final int CXO_PEN        = 0x0002;
    public static final int CXO_MESSAGES   = 0x0004;  // 我们不用
    public static final int CXO_CSRMESSAGES = 0x0008;

    // PACKET lcPktData 掩码
    public static final int PK_CONTEXT     = 0x0001;  // pkContext
    public static final int PK_STATUS      = 0x0002;  // pkStatus
    public static final int PK_TIME        = 0x0004;  // pkTime
    public static final int PK_CHANGED     = 0x0008;  // pkChanged
    public static final int PK_SERIALNUMBER= 0x0010;  // pkSerialNumber
    public static final int PK_CURSOR      = 0x0020;  // pkCursor
    public static final int PK_BUTTONS     = 0x0040;  // pkButtons
    public static final int PK_X           = 0x0080;  // pkX
    public static final int PK_Y           = 0x0100;  // pkY
    public static final int PK_Z           = 0x0200;  // pkZ
    public static final int PK_NORMALPRESSURE = 0x0400; // pkNormalPressure
    public static final int PK_TANGENTPRESSURE = 0x0800;
    public static final int PK_ORIENTATION = 0x1000;
    public static final int PK_ROTATION    = 0x2000;

    // pkStatus 按钮位（来自 wintab.h PK_Button* 和 PK_In*)
    public static final int PK_PENCONTACT  = 0x0001;  // tip down
    public static final int PK_PENNEAR     = 0x0002;  // in proximity
    public static final int PK_PENERASER   = 0x0004;  // 橡皮擦端 inverted
    public static final int PK_PENBARREL0  = 0x0008;  // 侧键1
    public static final int PK_PENBARREL1  = 0x0010;  // 侧键2

    // Cursor type (CSR_TYPE)
    public static final int CSR_TYPE_PEN       = 0x0001;
    public static final int CSR_TYPE_AIRBRUSH  = 0x0002;
    public static final int CSR_TYPE_ERASER    = 0x0004;
    public static final int CSR_TYPE_LENS      = 0x0008;
    public static final int CSR_TYPE_CURSOR    = 0x0010;
}