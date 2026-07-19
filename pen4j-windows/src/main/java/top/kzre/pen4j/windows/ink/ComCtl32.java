package top.kzre.pen4j.windows.ink;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.BaseTSD.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

public interface ComCtl32 extends StdCallLibrary {
    ComCtl32 INSTANCE = Native.load("comctl32", ComCtl32.class, W32APIOptions.DEFAULT_OPTIONS);

    interface SUBCLASSPROC extends StdCallLibrary.StdCallCallback {
        LRESULT callback(HWND hWnd, int uMsg, WPARAM wParam, LPARAM lParam,
                         UINT_PTR uIdSubclass, ULONG_PTR dwRefData);
    }

    boolean SetWindowSubclass(HWND hWnd, SUBCLASSPROC pfnSubclass, UINT_PTR uIdSubclass, ULONG_PTR dwRefData);
    boolean RemoveWindowSubclass(HWND hWnd, SUBCLASSPROC pfnSubclass, UINT_PTR uIdSubclass);
    LRESULT DefSubclassProc(HWND hWnd, int uMsg, WPARAM wParam, LPARAM lParam);


}