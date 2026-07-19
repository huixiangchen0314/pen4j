package top.kzre.pen4j.windows.wintab;

import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinDef.*;

import java.util.Arrays;
import java.util.List;

public class LOGCONTEXTW extends Structure {
    public static final int LCNAMESIZE = 40;

    public char[] lcName = new char[LCNAMESIZE]; // WCHAR[40]

    // UINT 字段
    public UINT lcOptions;
    public UINT lcStatus;
    public UINT lcLocks;
    public UINT lcMsgBase;
    public UINT lcDevice;
    public UINT lcPktRate;

    // WTPKT 字段（均为 UINT）
    public UINT lcPktData;
    public UINT lcPktMode;
    public UINT lcMoveMask;
    public UINT lcBtnDnMask;
    public UINT lcBtnUpMask;

    // 坐标范围字段（LONG = signed 32-bit）
    public LONG lcInOrgX;
    public LONG lcInOrgY;
    public LONG lcInOrgZ;
    public LONG lcInExtX;
    public LONG lcInExtY;
    public LONG lcInExtZ;
    public LONG lcOutOrgX;
    public LONG lcOutOrgY;
    public LONG lcOutOrgZ;
    public LONG lcOutExtX;
    public LONG lcOutExtY;
    public LONG lcOutExtZ;

    // 灵敏度 FIX32（LONG）
    public LONG lcSensX;
    public LONG lcSensY;
    public LONG lcSensZ;

    public BOOL lcSysMode;         // BOOL (4 bytes)

    // 系统光标原点（有符号 int）
    public LONG lcSysOrgX;
    public LONG lcSysOrgY;

    // 系统光标范围（UINT）
    public UINT lcSysExtX;
    public UINT lcSysExtY;

    // 系统灵敏度（UINT）
    public UINT lcSysSensX;
    public UINT lcSysSensY;

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList(
                "lcName",
                "lcOptions", "lcStatus", "lcLocks", "lcMsgBase",
                "lcDevice", "lcPktRate",
                "lcPktData", "lcPktMode", "lcMoveMask", "lcBtnDnMask", "lcBtnUpMask",
                "lcInOrgX", "lcInOrgY", "lcInOrgZ",
                "lcInExtX", "lcInExtY", "lcInExtZ",
                "lcOutOrgX", "lcOutOrgY", "lcOutOrgZ",
                "lcOutExtX", "lcOutExtY", "lcOutExtZ",
                "lcSensX", "lcSensY", "lcSensZ",
                "lcSysMode",
                "lcSysOrgX", "lcSysOrgY",
                "lcSysExtX", "lcSysExtY",
                "lcSysSensX", "lcSysSensY"
        );
    }
}