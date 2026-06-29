// ── LONGS.java ──────────────────────────
package top.kzre.pen4j.windows.common;

import com.sun.jna.platform.win32.WinDef.LONG;

public final class LONGS {
    private LONGS() {}
    public static final LONG ZERO = new LONG(0);
    public static final LONG ONE  = new LONG(1);
    /** 16.16 定点数中的 1.0 */
    public static final LONG FIX32_ONE = new LONG(65536);
}