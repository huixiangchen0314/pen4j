package top.kzre.pen4j.windows.ink;

import com.sun.jna.Native;
import com.sun.jna.win32.W32APIOptions;
import com.sun.jna.Library;
import com.sun.jna.ptr.IntByReference;

/**
 * 扩展的 User32 函数，支持 Pointer 输入
 */
public interface User32Ex extends Library {
    User32Ex INSTANCE = Native.load("user32", User32Ex.class, W32APIOptions.DEFAULT_OPTIONS);

    boolean GetPointerInfo(int pointerId, PointerInfo.ByReference pointerInfo);
    boolean GetPointerPenInfo(int pointerId, PointerPenInfo.ByReference penInfo);
    boolean GetPointerType(int pointerId, IntByReference pointerType);
}