package top.kzre.pen4j.windows.common;

import com.sun.jna.platform.win32.WinDef;

public final class BOOLS {
    public static final WinDef.BOOL TRUE = new WinDef.BOOL(true);
    public static final WinDef.BOOL FALSE = new WinDef.BOOL(false);
    private BOOLS() {}
}
