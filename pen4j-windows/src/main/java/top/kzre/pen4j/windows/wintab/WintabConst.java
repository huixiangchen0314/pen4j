package top.kzre.pen4j.windows.wintab;

/** Wintab 常量，从 wintab.h / wintab32.h 摘的。 */
final class WintabConst {
    private WintabConst() {}

    // ────────── WTInfo 分类 ──────────
    /** 接口 / 设备数量 */
    public static final int WTI_INTERFACES = 1;   // 获取接口数量（同 WTI_INTERFACE）
    public static final int WTI_INTERFACE  = 1;   // 接口信息
    public static final int WTI_STATUS     = 2;   // 状态
    public static final int WTI_DEFCONTEXT = 3;   // 默认上下文
    public static final int WTI_DEVICES    = 100; // 设备
    public static final int WTI_CURSORS    = 200; // 光标
    // WTInfo indexes (INTERFACE)
    public static final int IFC_WINTABID   = 1;   // 新增：WinTab ID
    public static final int IFC_SPECVERSION = 2;  // 协议版本
    // ────────── 光标 / 设备属性标签 ──────────
    /** 设备名称 */
    public static final int DVC_NAME       = 1;
    /** X 缩放因子（已废弃） */
    public static final int DVC_XFACTOR    = 2;
    /** Y 缩放因子（已废弃） */
    public static final int DVC_YFACTOR    = 3;
    /** 压力轴范围 (min, max) */
    public static final int DVC_NPRESSURE  = 4;
    /** 切向压力轴范围 (min, max) */
    public static final int DVC_TPRESSURE  = 5;
    /** 倾斜支持标记 */
    public static final int DVC_ORIENTATION = 6;
    /** 即插即用 ID 字符串（如 HID\VID_056A&PID_0087\...） */
    public static final int DVC_PNPID      = 8;   // 新增

    /** 光标名称 */
    public static final int CSR_NAME       = 1;
    /** 光标物理 ID（可能与硬件序列号相关） */
    public static final int CSR_PHYSID     = 2;
    /** 光标类型（位掩码，如 CSR_TYPE_PEN 等） */
    public static final int CSR_TYPE       = 3;
    /** 光标按钮数量（通常包括笔尖，但实际笔杆按钮数需减 1） */
    public static final int CSR_NBUTTONS   = 4;   // 新增

    // ────────── LOGCONTEXT lcOptions 标志 ──────────
    public static final int CXO_SYSTEM      = 0x0001; // 系统光标
    public static final int CXO_PEN         = 0x0002; // 笔设备
    public static final int CXO_MESSAGES    = 0x0004; // 接收消息（一般不用于本驱动）
    public static final int CXO_CSRMESSAGES = 0x0008; // 光标改变消息

    // ────────── PACKET 数据掩码 ──────────
    public static final int PK_CONTEXT      = 0x0001; // 上下文句柄
    public static final int PK_STATUS       = 0x0002; // 状态
    public static final int PK_TIME         = 0x0004; // 时间
    public static final int PK_CHANGED      = 0x0008; // 变化掩码
    public static final int PK_SERIALNUMBER = 0x0010; // 序列号
    public static final int PK_CURSOR       = 0x0020; // 光标类型
    public static final int PK_BUTTONS      = 0x0040; // 按钮
    public static final int PK_X            = 0x0080; // X 坐标
    public static final int PK_Y            = 0x0100; // Y 坐标
    public static final int PK_Z            = 0x0200; // Z 坐标（压力、指轮等）
    public static final int PK_NORMALPRESSURE = 0x0400; // 笔尖压力
    public static final int PK_TANGENTPRESSURE = 0x0800; // 切向压力
    public static final int PK_ORIENTATION  = 0x1000; // 朝向（方位角、仰角、扭转）
    public static final int PK_ROTATION     = 0x2000; // 旋转（roll, pitch, yaw）

    // ────────── 按钮状态位（pkStatus） ──────────
    public static final int PK_PENCONTACT   = 0x0001; // 笔尖接触
    public static final int PK_PENNEAR      = 0x0002; // 笔在附近（悬停）
    public static final int PK_PENERASER    = 0x0004; // 橡皮擦端（笔倒置）
    public static final int PK_PENBARREL0   = 0x0008; // 笔杆按钮 1
    public static final int PK_PENBARREL1   = 0x0010; // 笔杆按钮 2

    // ────────── 光标类型（CSR_TYPE） ──────────
    public static final int CSR_TYPE_PEN      = 0x0001; // 钢笔
    public static final int CSR_TYPE_AIRBRUSH = 0x0002; // 喷枪
    public static final int CSR_TYPE_ERASER   = 0x0004; // 橡皮擦
    public static final int CSR_TYPE_LENS     = 0x0008; // 透镜光标
    public static final int CSR_TYPE_CURSOR   = 0x0010; // 普通光标（鼠标）
}