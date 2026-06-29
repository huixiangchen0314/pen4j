package top.kzre.pen4j.windows.common;

/**
 * 补充 JNA 平台库中缺失的常用 Windows 常量。
 * 所有值均来自 MSDN / Windows SDK，确保与系统定义一致。
 */
public final class WinConstants {
    private WinConstants() {}

    // ── 窗口样式 (Window Styles) ─────────────────────────
    public static final int WS_OVERLAPPED       = 0x00000000;
    public static final int WS_POPUP            = 0x80000000;
    public static final int WS_CHILD            = 0x40000000;
    public static final int WS_MINIMIZE         = 0x20000000;
    public static final int WS_VISIBLE          = 0x10000000;
    public static final int WS_DISABLED         = 0x08000000;
    public static final int WS_CLIPSIBLINGS     = 0x04000000;
    public static final int WS_CLIPCHILDREN     = 0x02000000;
    public static final int WS_MAXIMIZE         = 0x01000000;
    public static final int WS_CAPTION          = 0x00C00000;
    public static final int WS_BORDER           = 0x00800000;
    public static final int WS_DLGFRAME         = 0x00400000;
    public static final int WS_VSCROLL          = 0x00200000;
    public static final int WS_HSCROLL          = 0x00100000;
    public static final int WS_SYSMENU          = 0x00080000;
    public static final int WS_THICKFRAME       = 0x00040000;
    public static final int WS_GROUP            = 0x00020000;
    public static final int WS_TABSTOP          = 0x00010000;
    public static final int WS_MINIMIZEBOX      = 0x00020000;
    public static final int WS_MAXIMIZEBOX      = 0x00010000;

    // 常用复合样式
    public static final int WS_OVERLAPPEDWINDOW =
            WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_THICKFRAME |
                    WS_MINIMIZEBOX | WS_MAXIMIZEBOX;
    public static final int WS_POPUPWINDOW      =
            WS_POPUP | WS_BORDER | WS_SYSMENU;
    public static final int WS_CHILDWINDOW      = WS_CHILD;

    // ── 扩展窗口样式 (Extended Window Styles) ──────────
    public static final int WS_EX_DLGMODALFRAME   = 0x00000001;
    public static final int WS_EX_NOPARENTNOTIFY  = 0x00000004;
    public static final int WS_EX_TOPMOST         = 0x00000008;
    public static final int WS_EX_ACCEPTFILES     = 0x00000010;
    public static final int WS_EX_TRANSPARENT     = 0x00000020;
    public static final int WS_EX_MDICHILD        = 0x00000040;
    public static final int WS_EX_TOOLWINDOW      = 0x00000080;
    public static final int WS_EX_WINDOWEDGE      = 0x00000100;
    public static final int WS_EX_CLIENTEDGE      = 0x00000200;
    public static final int WS_EX_CONTEXTHELP     = 0x00000400;
    public static final int WS_EX_LAYERED         = 0x00080000;
    public static final int WS_EX_APPWINDOW       = 0x00040000;

    // ── ShowWindow 命令 ─────────────────────────────────
    public static final int SW_HIDE            = 0;
    public static final int SW_SHOWNORMAL      = 1;
    public static final int SW_SHOWMINIMIZED   = 2;
    public static final int SW_SHOWMAXIMIZED   = 3;
    public static final int SW_SHOWNOACTIVATE  = 4;
    public static final int SW_SHOW            = 5;
    public static final int SW_MINIMIZE        = 6;
    public static final int SW_SHOWMINNOACTIVE = 7;
    public static final int SW_SHOWNA          = 8;
    public static final int SW_RESTORE         = 9;

    // ── PeekMessage / GetMessage 标志 ────────────────────
    public static final int PM_NOREMOVE = 0x0000;
    public static final int PM_REMOVE   = 0x0001;
    public static final int PM_NOYIELD  = 0x0002;

    // ── 虚拟键码 ─────────────────────────────────────────
    public static final int VK_ESCAPE = 0x1B;
    public static final int VK_RETURN = 0x0D;
    public static final int VK_SPACE  = 0x20;
    public static final int VK_TAB    = 0x09;
    public static final int VK_SHIFT  = 0x10;
    public static final int VK_CONTROL= 0x11;
    public static final int VK_MENU   = 0x12;

    // ── 系统命令 ─────────────────────────────────────────
    public static final int SC_CLOSE    = 0xF060;
    public static final int SC_MINIMIZE = 0xF020;
    public static final int SC_MAXIMIZE = 0xF030;

    // ── 特殊坐标 ─────────────────────────────────────────
    /** 用于 CreateWindow 的默认位置/尺寸 */
    public static final int CW_USEDEFAULT = 0x80000000;

    // ── 特殊 HWND 值（可用 WinDef.HWND 构造时使用 Pointer.createConstant） ──
    public static final int HWND_BOTTOM    = 1;
    public static final int HWND_NOTOPMOST = -2;
    public static final int HWND_TOP       = 0;
    public static final int HWND_TOPMOST   = -1;

    // ── 颜色常量 ─────────────────────────────────────────
    public static final int COLOR_WINDOW  = 5;
    public static final int COLOR_BTNFACE = 15;
}