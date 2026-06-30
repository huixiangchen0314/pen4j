package top.kzre.pen4j.windows.ink;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.BaseTSD.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.win32.W32APIOptions;
import com.sun.jna.Library;
import com.sun.jna.Callback;

public interface ComCtl32 extends Library {
    ComCtl32 INSTANCE = Native.load("comctl32", ComCtl32.class, W32APIOptions.DEFAULT_OPTIONS);

    boolean SetWindowSubclass(HWND hWnd, SUBCLASSPROC pfnSubclass, UINT_PTR uIdSubclass,
                              ULONG_PTR dwRefData);
    boolean RemoveWindowSubclass(HWND hWnd, SUBCLASSPROC pfnSubclass, UINT_PTR uIdSubclass);
    LRESULT DefSubclassProc(HWND hWnd, int uMsg, WPARAM wParam, LPARAM lParam);

    // 自定义回调接口，替代未提供的 WinDef.SUBCLASSPROC
    interface SUBCLASSPROC extends Callback {
        LRESULT callback(HWND hWnd, int uMsg, WPARAM wParam, LPARAM lParam,
                         UINT_PTR uIdSubclass, ULONG_PTR dwRefData);
    }
}