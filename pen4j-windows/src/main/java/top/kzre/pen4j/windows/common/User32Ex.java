package top.kzre.pen4j.windows.common;

import com.sun.jna.*;
import com.sun.jna.win32.W32APIOptions;

import java.util.Arrays;
import java.util.List;

/**
 * User32 扩展接口，提供 WNDPROC 回调支持。
 * JNA 自带的 User32 没有直接暴露 WNDPROC，这里补一个最小版本。
 */
public interface User32Ex extends com.sun.jna.platform.win32.User32 {

    User32Ex INSTANCE = Native.load("user32", User32Ex.class, W32APIOptions.DEFAULT_OPTIONS);

    // ---------- 回调类型 ----------
    interface WNDPROC extends Callback {
        LRESULT callback(HWND hwnd, int uMsg, WPARAM wParam, LPARAM lParam);
    }

    // ---------- 窗口类结构体 ----------
    class WNDCLASSEX extends Structure {
        public int cbSize;
        public int style;
        public WNDPROC lpfnWndProc;
        public int cbClsExtra;
        public int cbWndExtra;
        public HMODULE hInstance;
        public HICON hIcon;
        public HCURSOR hCursor;
        public HBRUSH hbrBackground;
        public String lpszMenuName;
        public String lpszClassName;
        public HICON hIconSm;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(
                    "cbSize", "style", "lpfnWndProc", "cbClsExtra", "cbWndExtra",
                    "hInstance", "hIcon", "hCursor", "hbrBackground",
                    "lpszMenuName", "lpszClassName", "hIconSm"
            );
        }
    }

    // ---------- 函数 ----------
    UINT RegisterClassEx(WNDCLASSEX lpWndClass);
    LRESULT DefWindowProc(HWND hWnd, int uMsg, WPARAM wParam, LPARAM lParam);
}