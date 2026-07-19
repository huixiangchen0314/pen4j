package top.kzre.pen4j.windows.wintab;

import lombok.Getter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Wintab 数据包解析器，将原生 {@code WT_PACKET} 消息附带的原始字节数组
 * 解析为结构化、类型安全的 Java 对象。
 *
 * <h3>结构说明</h3>
 * 数据包内存布局与 C/C++ 定义（{@code #pragma pack(1)} 紧凑对齐）严格一致，
 * 固定总长度为 68 字节（包含 8 字节的 {@code ULONGLONG} 对齐,暂不支持 PK_EXTENSIONS 厂商拓展数据）。
 * 布局顺序如下：
 * <pre>
 * ULONGLONG  pkContext;
 * UINT       pkStatus;
 * UINT       pkTime;
 * UINT       pkChanged;
 * UINT       pkSerialNumber;
 * UINT       pkCursor;
 * UINT       pkButtons;
 * LONG       pkX;
 * LONG       pkY;
 * LONG       pkZ;
 * LONG       pkNormalPressure;
 * LONG       pkTangentPressure;
 * struct {
 *     UINT azimuth;
 *     UINT altitude;
 *     UINT twist;
 * } pkOrientation;
 * struct {
 *     UINT roll;
 *     UINT pitch;
 *     UINT yaw;
 * } pkRotation;
 * </pre>
 *
 * <h3>掩码驱动解析</h3>
 * 并不是每个字段都会实际出现在原始数据中。Wintab 使用数据包掩码
 * （{@code lcPktData} 或每个包的 {@code pkChanged}）来指示哪些字段存在。
 * 构造器依据掩码按顺序有条件地读取字段，未包含的字段会自动填充为
 * 安全的默认值（0 或 -1），确保所有 getter 方法绝对不会抛出异常。
 *
 * <h3>使用场景</h3>
 * 通常在处理 {@code WT_PACKET} 窗口消息时，先从 {@code lParam} 提取出
 * {@code WTPACKET} 结构体（包含上下文句柄和序列号），再调用 {@code WTPacket}
 * 函数获取原始字节数组，最后用本类进行解析。
 *
 * <h3>示例代码</h3>
 * <pre>{@code
 * byte[] rawData = ...; // 从 WTPacket 获取
 * long mask = 0x...;    // 数据包掩码（来自 LOGCONTEXTW.lcPktData）
 * PACKET packet = new PACKET(rawData, mask);
 * long x = packet.getPkX();
 * long pressure = packet.getPkNormalPressure();
 * }</pre>
 *
 * @see LOGCONTEXTW
 * @see WintabConst
 */
public class PACKET {

    // ────────────── 字段定义 ──────────────

    /**
     * 上下文句柄（8 字节，对应 {@code pkContext}）。
     * 仅在掩码包含 {@link WintabConst#PK_CONTEXT} 时存在。
     * 与 {@code WTOpen} 返回的 {@code HCTX} 一致，用于区分不同设备或上下文。
     */
    private final long context;

    /**
     * 数据包状态标志（4 字节，对应 {@code pkStatus}）。
     * 仅在掩码包含 {@link WintabConst#PK_STATUS} 时存在。
     * 包含各种硬件状态，如笔是否悬浮、按钮是否按下、笔是否在设备范围内等。
     */
    private final long status;

    /**
     * 时间戳（4 字节，对应 {@code pkTime}）。
     * 仅在掩码包含 {@link WintabConst#PK_TIME} 时存在。
     * 系统时间（通常为毫秒），表示数据包产生的时间点。
     */
    private final long time;

    /**
     * 变化掩码（4 字节，对应 {@code pkChanged}）。
     * 仅在掩码包含 {@link WintabConst#PK_CHANGED} 时存在。
     * 指示此数据包中哪些字段相对于上一个包发生了变化。
     * 可用于过滤重复数据或触发特定更新。
     */
    private final long changed;

    /**
     * 序列号（4 字节，对应 {@code pkSerialNumber}）。
     * 仅在掩码包含 {@link WintabConst#PK_SERIALNUMBER} 时存在。
     * 单调递增的包序号，用于检测数据包丢失。
     */
    private final long serialNumber;

    /**
     * 光标类型（4 字节，对应 {@code pkCursor}）。
     * 仅在掩码包含 {@link WintabConst#PK_CURSOR} 时存在。
     * 标识当前使用的工具类型（如钢笔、喷枪、橡皮擦等）。
     */
    private final long cursor;

    /**
     * 按钮状态（4 字节，对应 {@code pkButtons}）。
     * 仅在掩码包含 {@link WintabConst#PK_BUTTONS} 时存在。
     * 位掩码，每一位代表一个物理按钮的按下/释放状态。
     */
    private final long buttons;

    /**
     * 坐标 X（4 字节有符号整数，对应 {@code pkX}）。
     * 仅在掩码包含 {@link WintabConst#PK_X} 时存在。
     * 已根据 {@link LOGCONTEXTW} 的输出范围映射后的绝对坐标。
     */
    private final long x;

    /**
     * 坐标 Y（4 字节有符号整数，对应 {@code pkY}）。
     * 仅在掩码包含 {@link WintabConst#PK_Y} 时存在。
     */
    private final long y;

    /**
     * 坐标 Z（4 字节有符号整数，对应 {@code pkZ}）。
     * 仅在掩码包含 {@link WintabConst#PK_Z} 时存在。
     * 通常为 0，某些设备可能用于表示滚轮或其他轴。
     */
    private final long z;

    /**
     * 标准笔尖压力（4 字节有符号整数，对应 {@code pkNormalPressure}）。
     * 仅在掩码包含 {@link WintabConst#PK_NORMALPRESSURE} 时存在。
     * 取值范围取决于设备，常见为 0 ~ 1024 或 0 ~ 2048。
     */
    private final long normalPressure;

    /**
     * 切线压力（4 字节有符号整数，对应 {@code pkTangentPressure}）。
     * 仅在掩码包含 {@link WintabConst#PK_TANGENTPRESSURE} 时存在。
     * 部分高端笔支持，表示笔在表面切向的压力分量，通常范围为 -1024 ~ 1024。
     */
    private final long tangentPressure;

    // ────────────── 笔朝向（Orientation） ──────────────

    /**
     * 方位角（十分之一度，对应 {@code pkOrientation.azimuth}）。
     * 仅在掩码包含 {@link WintabConst#PK_ORIENTATION} 时存在。
     * 范围为 0 ~ 3600（即 0° ~ 360°），表示笔在平面上的指向方向。
     * 若不存在，默认值为 -1。
     * -- GETTER --
     *  获取方位角（十分之一度）。
     */
    @Getter
    private final long orientationAzimuth;

    /**
     * 仰角（十分之一度，对应 {@code pkOrientation.altitude}）。
     * 仅在掩码包含 {@link WintabConst#PK_ORIENTATION} 时存在。
     * 范围为 0 ~ 900（即 0° ~ 90°），90° 表示笔垂直竖立。
     * 若不存在，默认值为 -1。
     * -- GETTER --
     *  获取仰角（十分之一度）。
     */
    @Getter
    private final long orientationAltitude;

    /**
     * 扭转角（十分之一度，对应 {@code pkOrientation.twist}）。
     * 仅在掩码包含 {@link WintabConst#PK_ORIENTATION} 时存在。
     * 范围为 0 ~ 3600，表示笔身绕自身轴的旋转角度。
     * 若不存在，默认值为 -1。
     * -- GETTER --
     *  获取扭转角（十分之一度）。
     */
    @Getter
    private final long orientationTwist;

    // ────────────── 笔旋转（Rotation，绕空间坐标轴） ──────────────

    /**
     * 绕 X 轴的旋转角（十分之一度，对应 {@code pkRotation.roll}）。
     * 仅在掩码包含 {@link WintabConst#PK_ROTATION} 时存在。
     * 若不存在，默认值为 -1。
     * -- GETTER --
     *  获取绕 X 轴的旋转角（十分之一度）。
     */
    @Getter
    private final long rotationRoll;

    /**
     * 绕 Y 轴的旋转角（十分之一度，对应 {@code pkRotation.pitch}）。
     * 仅在掩码包含 {@link WintabConst#PK_ROTATION} 时存在。
     * 若不存在，默认值为 -1。
     * -- GETTER --
     *  获取绕 Y 轴的旋转角（十分之一度）。
     */
    @Getter
    private final long rotationPitch;

    /**
     * 绕 Z 轴的旋转角（十分之一度，对应 {@code pkRotation.yaw}）。
     * 仅在掩码包含 {@link WintabConst#PK_ROTATION} 时存在。
     * 若不存在，默认值为 -1。
     * -- GETTER --
     *  获取绕 Z 轴的旋转角（十分之一度）。
     */
    @Getter
    private final long rotationYaw;

    // ────────────── 构造器 ──────────────

    /**
     * 从原始字节数组和掩码构建一个数据包对象。
     *
     * <p>解析过程严格按照上述 C 结构体顺序进行，每读取一个字段都检查掩码对应位。
     * 若该位为 1，则从 {@link ByteBuffer} 中读取相应数据类型（long 或 int）；
     * 否则跳过该字段，并赋默认值（0 或 -1）。这样既保证了内存偏移的正确性，
     * 也使得无论掩码如何设置，所有 getter 都是安全的。</p>
     *
     * <p><b>注意：</b> 原始字节数组的长度必须至少等于掩码所要求的总字节数，
     * 否则 {@link ByteBuffer} 可能会抛出 {@link java.nio.BufferUnderflowException}。
     * 调用者应确保通过 Wintab API 获取的数据包是完整的。</p>
     *
     * @param raw  从 Wintab 驱动获得的原始数据包字节数组（小端序）
     * @param mask 数据包掩码，指示哪些字段实际存在于 {@code raw} 中
     *             通常来自 {@link LOGCONTEXTW#lcPktData}
     */
    public PACKET(byte[] raw, long mask) {
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);

        // 按顺序解析：每个字段检查 mask 对应位
        context        = (mask & WintabConst.PK_CONTEXT)        != 0 ? buf.getLong()  : 0;
        status         = (mask & WintabConst.PK_STATUS)         != 0 ? buf.getInt()   : 0;
        time           = (mask & WintabConst.PK_TIME)           != 0 ? buf.getInt()   : 0;
        changed        = (mask & WintabConst.PK_CHANGED)        != 0 ? buf.getInt()   : 0;
        serialNumber   = (mask & WintabConst.PK_SERIALNUMBER)   != 0 ? buf.getInt()   : 0;
        cursor         = (mask & WintabConst.PK_CURSOR)         != 0 ? buf.getInt()   : 0;
        buttons        = (mask & WintabConst.PK_BUTTONS)        != 0 ? buf.getInt()   : 0;
        x              = (mask & WintabConst.PK_X)              != 0 ? buf.getInt()   : 0;
        y              = (mask & WintabConst.PK_Y)              != 0 ? buf.getInt()   : 0;
        z              = (mask & WintabConst.PK_Z)              != 0 ? buf.getInt()   : 0;
        // 无符号解析，确保高位不会被当作符号扩展
        normalPressure = (mask & WintabConst.PK_NORMALPRESSURE) != 0
                ? (buf.getInt() & 0xFFFFFFFFL)
                : 0;

        tangentPressure = (mask & WintabConst.PK_TANGENTPRESSURE) != 0
                ? (buf.getInt() & 0xFFFFFFFFL)
                : 0;

        // 朝向和旋转字段不存在时填充 -1，以便调用方检测无效值
        orientationAzimuth  = (mask & WintabConst.PK_ORIENTATION) != 0 ? buf.getInt() : -1;
        orientationAltitude = (mask & WintabConst.PK_ORIENTATION) != 0 ? buf.getInt() : -1;
        orientationTwist    = (mask & WintabConst.PK_ORIENTATION) != 0 ? buf.getInt() : -1;

        rotationRoll   = (mask & WintabConst.PK_ROTATION) != 0 ? buf.getInt() : -1;
        rotationPitch  = (mask & WintabConst.PK_ROTATION) != 0 ? buf.getInt() : -1;
        rotationYaw    = (mask & WintabConst.PK_ROTATION) != 0 ? buf.getInt() : -1;
    }

    // ────────────── 安全 Getter ──────────────

    /**
     * 获取上下文句柄。
     * @return 上下文句柄，若掩码未包含该字段则为 0
     */
    public long getPkContext()            { return context; }

    /**
     * 获取数据包状态标志。
     * @return 状态值（组合的 {@code S_*} 标志），若掩码未包含该字段则为 0
     */
    public long getPkStatus()            { return status; }

    /**
     * 获取时间戳（毫秒）。
     * @return 系统时间，若掩码未包含该字段则为 0
     */
    public long getPkTime()              { return time; }

    /**
     * 获取变化掩码。
     * @return 指示哪些字段发生变化的位掩码，若掩码未包含该字段则为 0
     */
    public long getPkChanged()           { return changed; }

    /**
     * 获取数据包序列号。
     * @return 序列号，若掩码未包含该字段则为 0
     */
    public long getPkSerialNumber()      { return serialNumber; }

    /**
     * 获取光标类型编号。
     * @return 光标类型（如笔、橡皮擦等），若掩码未包含该字段则为 0
     */
    public long getPkCursor()            { return cursor; }

    /**
     * 获取按钮状态位掩码。
     * @return 按钮状态，每一位代表一个按钮，若掩码未包含该字段则为 0
     */
    public long getPkButtons()           { return buttons; }

    /**
     * 获取映射后的 X 坐标。
     * @return X 坐标值，若掩码未包含该字段则为 0
     */
    public long getPkX()                 { return x; }

    /**
     * 获取映射后的 Y 坐标。
     * @return Y 坐标值，若掩码未包含该字段则为 0
     */
    public long getPkY()                 { return y; }

    /**
     * 获取映射后的 Z 坐标。
     * @return Z 坐标值，若掩码未包含该字段则为 0
     */
    public long getPkZ()                 { return z; }

    /**
     * 获取笔尖压力值。
     * @return 标准压力值，若掩码未包含该字段则为 0
     */
    public long getPkNormalPressure()    { return normalPressure; }

    /**
     * 获取切线压力值。
     * @return 切线压力值（带符号），若掩码未包含该字段则为 0
     */
    public long getPkTangentPressure()   { return tangentPressure; }

}