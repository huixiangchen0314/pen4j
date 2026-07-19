package top.kzre.pen4j.windows.common;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 常用数位板厂商识别工具。
 * 支持根据 USB Vendor ID 查表，以及根据设备名称字符串推测厂商。
 */
public final class PenVendorVIDTable {

    private PenVendorVIDTable() {
        // 工具类，禁止实例化
    }

    /** VID -> 厂商名称 */
    private static final Map<Integer, String> VID_TO_VENDOR;

    static {
        Map<Integer, String> map = new HashMap<>();
        map.put(0x056A, "Wacom");
        map.put(0x256C, "Huion");
        map.put(0x5543, "XP-Pen");
        map.put(0x28BD, "XP-Pen");
        map.put(0x045E, "Microsoft");
        map.put(0x08BB, "Texas Instruments"); // 某些数位板使用
        // 可按需扩展
        VID_TO_VENDOR = Collections.unmodifiableMap(map);
    }

    /**
     * 根据 USB Vendor ID 获取厂商名称。
     *
     * @param vid Vendor ID（16 位无符号整数）
     * @return 厂商名称，若未找到则返回 {@code "Unknown"}
     */
    public static String getVendorName(int vid) {
        return VID_TO_VENDOR.getOrDefault(vid & 0xFFFF, "Unknown");
    }

    /**
     * 从设备名称字符串中猜测厂商（大小写不敏感）。
     * 作为备用方案，当无法从 VID 识别时使用。
     *
     * @param deviceName 设备名称（可能为 null）
     * @return 猜测的厂商名称，若无法识别则返回 "Unknown"
     */
    public static String guessVendor(String deviceName) {
        if (deviceName == null) return "Unknown";
        String n = deviceName.toLowerCase();
        if (n.contains("wacom")) return "Wacom";
        if (n.contains("huion") || n.contains("绘王")) return "Huion";
        if (n.contains("xp-pen") || n.contains("xppen")) return "XP-Pen";
        if (n.contains("gaomon") || n.contains("高漫")) return "Gaomon";
        if (n.contains("ugee") || n.contains("友基")) return "Ugee";
        return "Unknown";
    }
}