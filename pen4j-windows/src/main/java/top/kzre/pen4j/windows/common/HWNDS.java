// ── HWNDS.java ──────────────────────────
package top.kzre.pen4j.windows.common;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HWND;

public final class HWNDS {
    private HWNDS() {}
    public static final HWND NULL = new HWND(Pointer.NULL);
}