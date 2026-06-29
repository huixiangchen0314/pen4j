package top.kzre.pen4j.windows.common;

/**
 * 通用工具方法。
 */
public final class Utils {

    private Utils() {}

    /**
     * 将原始值归一化到 [0, 1] 范围。
     * @param val 原始值
     * @param org 范围起始
     * @param ext 范围长度（最大值+1）
     * @return 归一化值，若 ext<=0 返回 0
     */
    public static double norm(long val, int org, int ext) {
        if (ext <= 0) return 0;
        return (val - (double) org) / (double) ext;
    }

    /**
     * 将值限制在 [0, 1] 范围内。
     */
    public static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}