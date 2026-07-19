package top.kzre.pen4j.windows.wintab;

import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinDef.*;

import java.util.Arrays;
import java.util.List;

/**
 * Wintab 逻辑上下文配置结构体（宽字符版本）。
 * <p>
 * 该结构体用于 {@code WTOpenW} 函数，定义应用程序希望从数位板设备获取的
 * 数据种类、坐标映射方式、消息格式以及其他行为参数。
 * <p>
 * 内存布局与 C/C++ {@code LOGCONTEXTW} 严格一致，通过 JNA 直接映射到原生层。
 * <p>
 * 使用示例：
 * <pre>{@code
 * LOGCONTEXTW ctx = new LOGCONTEXTW();
 * ctx.lcOptions = new UINT(CXO_SYSTEM | CXO_PEN | CXO_MESSAGES);
 * ctx.lcPktData = new UINT(PK_X | PK_Y | PK_NORMAL_PRESSURE);
 * ctx.lcMsgBase = new UINT(0x8000);
 * // 设置输出范围为屏幕尺寸...
 * }</pre>
 *
 * @see <a href="https://developer-docs.wacom.com/intuos-creative-pen-tablet/docs/wintab-reference">Wintab Specification</a>
 */
public class LOGCONTEXTW extends Structure {

    /** 逻辑上下文名称的最大字符数（包括结尾的 null 终止符） */
    public static final int LCNAMESIZE = 40;

    /**
     * 逻辑上下文名称（宽字符数组）。
     * <p>
     * 用于标识此上下文，在管理多个上下文时便于区分。
     * 通常由应用程序设置为有意义的字符串，例如 {@code "PenContext"}。
     * 注意：数组长度固定为 {@value #LCNAMESIZE}，必须以 {@code '\0'} 结尾。
     */
    public char[] lcName = new char[LCNAMESIZE];

    // ────────────── UINT 字段 ──────────────

    /**
     * 选项标志组合（{@code CXO_*} 常量）。
     * <p>
     * 常用标志（可通过按位或组合）：
     * <ul>
     *   <li>{@code CXO_SYSTEM}   – 管理系统光标（若设置，Wintab 会控制系统光标移动）</li>
     *   <li>{@code CXO_PEN}      – 使用笔设备（而非鼠标）</li>
     *   <li>{@code CXO_MESSAGES} – 希望接收 {@code WT_PACKET} 等窗口消息</li>
     *   <li>{@code CXO_MGNINSIDE}– 笔在上下文区域内时抑制系统光标</li>
     * </ul>
     * 详细列表请参考 Wintab 规范。
     */
    public UINT lcOptions;

    /**
     * 状态标志（{@code CXS_*} 常量）。
     * <p>
     * 通常由 {@code WTOpen} 在返回时填充，表示上下文是否就绪、设备是否存在、
     * 是否处于集成模式等。应用程序一般只读取此字段，而不主动设置。
     */
    public UINT lcStatus;

    /**
     * 锁定标志（{@code CXL_*} 常量）。
     * <p>
     * 用于“锁定”某些属性，阻止用户或系统进行修改。
     * 常见值：
     * <ul>
     *   <li>{@code CXL_INSIZE}  – 锁定输入范围</li>
     *   <li>{@code CXL_INSENS}  – 锁定灵敏度</li>
     *   <li>{@code CXL_SYSORG}  – 锁定系统光标原点</li>
     *   <li>{@code CXL_SYSSENS} – 锁定系统光标灵敏度</li>
     * </ul>
     */
    public UINT lcLocks;

    /**
     * 消息基数。
     * <p>
     * 仅当 {@code lcOptions} 包含 {@code CXO_MESSAGES} 时有效。
     * Wintab 会向指定窗口发送以该基数为起点的消息序列，
     * 例如设置 {@code 0x8000} 时，{@code WT_PACKET} 的实际消息 ID 为
     * {@code 0x8000 + WintabConst.WT_PACKET_OFFSET}。
     * 必须搭配有效的窗口句柄（通过 {@code WTMessageWindow} 或队列发送）。
     */
    public UINT lcMsgBase;

    /**
     * 设备编号（从 0 开始）。
     * <p>
     * 当系统存在多个数位板设备时，用于指定要打开的目标设备。
     * 通常设为 {@code 0}，表示使用系统默认设备。
     */
    public UINT lcDevice;

    /**
     * 期望的数据包速率（单位：Hz）。
     * <p>
     * 非零值表示要求设备每秒最多发送指定数量的数据包。
     * 设为 {@code 0} 则不限制速率，由硬件和驱动决定实际频率。
     * 实际速率可能低于请求值，取决于设备能力和系统负载。
     */
    public UINT lcPktRate;

    // ────────────── WTPKT 字段（均为 UINT） ──────────────

    /**
     * 请求的数据字段掩码（{@code PK_*} 常量组合）。
     * <p>
     * 按位指定每个数据包（{@link PACKET}）中应包含哪些信息。
     * 常用标志：
     * <ul>
     *   <li>{@code PK_X}, {@code PK_Y}, {@code PK_Z} – 坐标</li>
     *   <li>{@code PK_NORMAL_PRESSURE} – 笔尖压力</li>
     *   <li>{@code PK_ORIENTATION}    – 笔的方位角/仰角/扭转</li>
     *   <li>{@code PK_ROTATION}       – 笔的旋转（绕各轴的欧拉角）</li>
     *   <li>{@code PK_BUTTONS}        – 按钮状态</li>
     *   <li>{@code PK_TIME}           – 时间戳</li>
     *   <li>{@code PK_SERIALNUMBER}   – 序列号</li>
     * </ul>
     * 只有这里指定的字段才会出现在随后的数据包中，这有助于减小数据包体积。
     */
    public UINT lcPktData;

    /**
     * 数据包模式标志。
     * <p>
     * 通常设为 {@code 0}（绝对坐标模式）。
     * 在某些特殊场景下可以包含 {@code PKBUTTONS} 等标志来改变行为，
     * 但多数应用保持默认即可。
     */
    public UINT lcPktMode;

    /**
     * 移动检测掩码。
     * <p>
     * 指定哪些数据字段的变化会触发 {@code WT_PACKET} 消息。
     * 例如，若只关心位置变化，可设为 {@code PK_X | PK_Y}，
     * 此时只有 X 或 Y 坐标改变时才会收到新数据包。
     * 设为 {@code 0} 表示任何请求字段的改变都会发送数据包（最大频率）。
     */
    public UINT lcMoveMask;

    /**
     * 按钮按下时强制报告的数据掩码。
     * <p>
     * 当笔上任何一个按钮从释放状态变为按下状态时，
     * 除了常规变化字段外，额外保证这些掩码指定的字段也会被包含在数据包中。
     * 典型设置：{@code PK_BUTTONS}，确保按钮状态总是随按下事件一起更新。
     */
    public UINT lcBtnDnMask;

    /**
     * 按钮释放时强制报告的数据掩码。
     * <p>
     * 作用与 {@link #lcBtnDnMask} 类似，但在按钮从按下变为释放时生效。
     * 通常也设为 {@code PK_BUTTONS}。
     */
    public UINT lcBtnUpMask;

    // ────────────── 输入坐标范围（Input Range） ──────────────

    /** 输入范围 X 轴原点（通常为 0） */
    public LONG lcInOrgX;
    /** 输入范围 Y 轴原点（通常为 0） */
    public LONG lcInOrgY;
    /** 输入范围 Z 轴原点（通常为 0） */
    public LONG lcInOrgZ;
    /** 输入范围 X 轴最大值（对应设备物理 X 方向的逻辑最大值） */
    public LONG lcInExtX;
    /** 输入范围 Y 轴最大值 */
    public LONG lcInExtY;
    /** 输入范围 Z 轴最大值（通常为 0，除非设备支持额外维度） */
    public LONG lcInExtZ;

    // ────────────── 输出坐标范围（Output Range） ──────────────

    /** 输出范围 X 轴原点（映射后的坐标最小值） */
    public LONG lcOutOrgX;
    /** 输出范围 Y 轴原点 */
    public LONG lcOutOrgY;
    /** 输出范围 Z 轴原点 */
    public LONG lcOutOrgZ;
    /** 输出范围 X 轴最大值（例如屏幕宽度） */
    public LONG lcOutExtX;
    /** 输出范围 Y 轴最大值（例如屏幕高度） */
    public LONG lcOutExtY;
    /** 输出范围 Z 轴最大值 */
    public LONG lcOutExtZ;

    // ────────────── 灵敏度（FIX32 格式） ──────────────

    /**
     * X 轴灵敏度（FIX32 定点数格式）。
     * <p>
     * 高 16 位为整数部分，低 16 位为小数部分。
     * 值为 {@code 0x00010000} 表示 1.0（恒等映射），
     * 大于该值表示放大，小于该值表示缩小。
     * 可用于实现非线性映射或用户自定义的笔加速曲线。
     */
    public LONG lcSensX;
    /** Y 轴灵敏度 */
    public LONG lcSensY;
    /** Z 轴灵敏度 */
    public LONG lcSensZ;

    // ────────────── 系统光标模式 ──────────────

    /**
     * 系统光标模式（{@code BOOL} 类型，实际 4 字节）。
     * <p>
     * 当 {@code lcOptions} 包含 {@code CXO_SYSTEM} 时，该字段定义光标的行为。
     * 通常设为 {@code 1}（{@code TRUE}），表示启用绝对定位且无加速度。
     * 某些 OEM 驱动可能支持更多模式，请参考厂商文档。
     */
    public BOOL lcSysMode;

    // ────────────── 系统光标坐标系统 ──────────────

    /** 系统光标原点 X 坐标（屏幕坐标系，通常为 0） */
    public LONG lcSysOrgX;
    /** 系统光标原点 Y 坐标 */
    public LONG lcSysOrgY;
    /** 系统光标范围 X（通常为屏幕宽度） */
    public UINT lcSysExtX;
    /** 系统光标范围 Y（通常为屏幕高度） */
    public UINT lcSysExtY;

    // ────────────── 系统光标灵敏度 ──────────────

    /**
     * 系统光标 X 轴灵敏度（FIX32 格式）。
     * <p>
     * 与 {@link #lcSensX} 类似，但仅作用于系统光标。
     * 通常设为 {@code 0x00010000}（1.0）。
     */
    public UINT lcSysSensX;
    /** 系统光标 Y 轴灵敏度（FIX32 格式） */
    public UINT lcSysSensY;

    /**
     * 返回结构体字段的内存排列顺序。
     * <p>
     * JNA 需要知道各字段在原生内存中的先后顺序，以便正确进行
     * {@code read()} 和 {@code write()} 操作。
     * 该顺序必须与 C/C++ 头文件中定义的顺序完全一致。
     *
     * @return 字段名称列表，按内存布局顺序排列
     */
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