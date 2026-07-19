package top.kzre.pen4j.windows.wintab;

import com.sun.jna.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

public interface WintabLibrary extends StdCallLibrary {
    WintabLibrary INSTANCE = Native.load("wintab32", WintabLibrary.class,
            W32APIOptions.UNICODE_OPTIONS);

    // WTInfoW: 返回 sizeof 或 0=fail
    UINT WTInfoW(UINT wCategory, UINT nIndex, Pointer lpOutput);

    // WTOpenW: HCTX 实际是 VOID*，用 HANDLE 承
    HANDLE WTOpenW(HWND hWnd, LOGCONTEXTW pLogCtx, BOOL fEnable);

    // WTPacketsGet: 返回实际取的包数
    int WTPacketsGet(HANDLE hCtx, int cMaxPkts, Pointer lpPkts);

    // WTPacket: 单包取（响应 WM_TABLET* 时用，轮询模式不用）
    BOOL WTPacket(HANDLE hCtx, UINT wSerial, PACKET lpPkt);

    BOOL WTEnable(HANDLE hCtx, BOOL fEnable);
    BOOL WTOverlap(HANDLE hCtx, BOOL fOverlap);
    BOOL WTClose(HANDLE hCtx);
}