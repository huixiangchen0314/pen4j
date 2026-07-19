package top.kzre.pen4j.windows.wintab;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import lombok.extern.slf4j.Slf4j;
import top.kzre.pen4j.windows.common.BOOLS;
import top.kzre.pen4j.windows.common.LONGS;
import top.kzre.pen4j.windows.common.UINTS;

@Slf4j
public class WintabContext implements AutoCloseable {

    private static final int REQUESTED_MSG_BASE = 0x8000;   // 请求的消息基址

    private final HWND hwnd;
    private HANDLE hCtx;
    private final long pktDataMask;
    private int actualMsgBase;            // 驱动实际分配的消息基址

    public WintabContext(HWND hwnd, long pktDataMask) {
        this.hwnd = hwnd;
        this.pktDataMask = pktDataMask;
    }

    public void open() {
        if (hCtx != null) throw new IllegalStateException("Context already opened");

        LOGCONTEXTW ctx = buildContext();
        hCtx = WintabLibrary.INSTANCE.WTOpenW(hwnd, ctx, BOOLS.TRUE);
        if (hCtx == null || Pointer.nativeValue(hCtx.getPointer()) == 0) {
            throw new RuntimeException("WTOpenW failed");
        }

        // 重新读取结构体，获取驱动实际写入的 lcMsgBase
        ctx.read();
        actualMsgBase = ctx.lcMsgBase.intValue();
        log.info("Wintab context opened, HCTX={}, actual msgBase=0x{}",
                hCtx, Integer.toHexString(actualMsgBase));

        WintabLibrary.INSTANCE.WTEnable(hCtx, BOOLS.TRUE);
    }

    public HANDLE getHandle() { return hCtx; }
    public long getPktDataMask() { return pktDataMask; }
    public int getActualMsgBase() { return actualMsgBase; }

    @Override
    public synchronized void close() {
        if (hCtx != null && Pointer.nativeValue(hCtx.getPointer()) != 0) {
            try {
                WintabLibrary.INSTANCE.WTEnable(hCtx, BOOLS.FALSE);
                WintabLibrary.INSTANCE.WTClose(hCtx);
            } catch (Exception e) {
                log.error("Error closing context", e);
            } finally {
                hCtx = null;
            }
        }
    }

    private LOGCONTEXTW buildContext() {
        LOGCONTEXTW ctx = new LOGCONTEXTW();
        UINT ret = WintabLibrary.INSTANCE.WTInfoW(
                new UINT(WintabConst.WTI_DEFCONTEXT), new UINT(0), ctx.getPointer());
        if (ret.intValue() == 0) throw new RuntimeException("WTInfoW failed");
        ctx.read();

        // 使用消息模式
        long options = ctx.lcOptions.longValue();
        options |= WintabConst.CXO_MESSAGES | WintabConst.CXO_PEN;
        ctx.lcOptions = new UINT(options);

        ctx.lcMsgBase = new UINT(REQUESTED_MSG_BASE);
        ctx.lcPktData = new UINT(pktDataMask);
        ctx.lcPktMode = UINTS.ZERO;
        ctx.lcMoveMask = UINTS.ZERO;
        ctx.lcBtnDnMask = UINTS.ZERO;
        ctx.lcBtnUpMask = UINTS.ZERO;

        int sw = User32.INSTANCE.GetSystemMetrics(WinUser.SM_CXSCREEN);
        int sh = User32.INSTANCE.GetSystemMetrics(WinUser.SM_CYSCREEN);
        if (ctx.lcInExtX == null || ctx.lcInExtX.longValue() == 0) ctx.lcInExtX = new LONG(sw);
        if (ctx.lcInExtY == null || ctx.lcInExtY.longValue() == 0) ctx.lcInExtY = new LONG(sh);
        if (ctx.lcInExtZ == null || ctx.lcInExtZ.longValue() == 0) ctx.lcInExtZ = new LONG(1);

        ctx.lcOutOrgX = LONGS.ZERO; ctx.lcOutOrgY = LONGS.ZERO; ctx.lcOutOrgZ = LONGS.ZERO;
        ctx.lcOutExtX = new LONG(sw); ctx.lcOutExtY = new LONG(sh);
        if (ctx.lcOutExtZ == null || ctx.lcOutExtZ.longValue() == 0) ctx.lcOutExtZ = LONGS.ONE;

        ctx.lcSensX = LONGS.FIX32_ONE; ctx.lcSensY = LONGS.FIX32_ONE; ctx.lcSensZ = LONGS.FIX32_ONE;
        ctx.write();
        return ctx;
    }
}