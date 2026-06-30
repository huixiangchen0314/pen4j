package top.kzre.pen4j.windows.ink;

/**
 * WM_POINTER 相关常量
 */
public final class PointerConstants {
    private PointerConstants() {}

    // WM_POINTER 消息
    public static final int WM_POINTERDOWN = 0x0246;
    public static final int WM_POINTERUP   = 0x0247;
    public static final int WM_POINTERUPDATE = 0x0245;
    public static final int WM_POINTERENTER = 0x0249;
    public static final int WM_POINTERLEAVE = 0x024A;
    public static final int WM_POINTERCAPTURECHANGED = 0x024C;

    // 指针类型
    public static final int PT_POINTER = 1;
    public static final int PT_TOUCH   = 2;
    public static final int PT_PEN     = 3;
    public static final int PT_TOUCHPAD = 4;

    // POINTER_INFO.pointerFlags 常用值（与 WinUser 中重复的可直接引用 WinUser）
    public static final int POINTER_FLAG_NONE       = 0x00000000;
    public static final int POINTER_FLAG_NEW        = 0x00000001;
    public static final int POINTER_FLAG_INRANGE    = 0x00000002;
    public static final int POINTER_FLAG_INCONTACT  = 0x00000004;
    public static final int POINTER_FLAG_FIRSTBUTTON   = 0x00000010;
    public static final int POINTER_FLAG_SECONDBUTTON  = 0x00000020;
    public static final int POINTER_FLAG_THIRDBUTTON   = 0x00000040;
    public static final int POINTER_FLAG_FOURTHBUTTON  = 0x00000080;
    public static final int POINTER_FLAG_PRIMARY       = 0x00002000;
    public static final int POINTER_FLAG_CONFIDENCE    = 0x00000400;
    public static final int POINTER_FLAG_CANCELED      = 0x00000800;

    // POINTER_PEN_INFO.penFlags
    public static final int PEN_FLAG_NONE     = 0x00000000;
    public static final int PEN_FLAG_BARREL   = 0x00000001;
    public static final int PEN_FLAG_INVERTED = 0x00000002;
    public static final int PEN_FLAG_ERASER   = 0x00000004;

    // POINTER_PEN_INFO.penMask
    public static final int PEN_MASK_NONE     = 0x00000000;
    public static final int PEN_MASK_PRESSURE = 0x00000001;
    public static final int PEN_MASK_ROTATION = 0x00000002;
    public static final int PEN_MASK_TILT_X   = 0x00000004;
    public static final int PEN_MASK_TILT_Y   = 0x00000008;
}