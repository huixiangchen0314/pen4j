package top.kzre.pen4j.windows.wintab;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import top.kzre.pen4j.api.PenCapability;
import top.kzre.pen4j.api.PenCursorType;
import top.kzre.pen4j.api.PenDevice;
import com.sun.jna.platform.win32.WinDef.*;

import java.util.Set;

@Slf4j
@Getter
@ToString
public class WintabPenDevice implements PenDevice {
    private final String name;
    private final String vendor;
    private final int maxX, maxY;
    private final int maxPressure;
    private final int maxProximity;
    private final int sideButtonCount;
    private final String uid;
    private final Set<PenCapability> caps;
    private final Set<PenCursorType> cursorTypes;

    final int inOrgX, inOrgY, inExtX, inExtY;

    WintabPenDevice(int deviceIdx, int cursorIdx,
                    String name, int maxX, int maxY, int maxPressure,
                    int inOrgX, int inOrgY, int inExtX, int inExtY,
                    Set<PenCapability> caps, Set<PenCursorType> cursorTypes) {
        this.name = name;
        this.vendor = guessVendor(name);
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxPressure = maxPressure;
        this.maxProximity = 0;
        this.sideButtonCount = 2;
        this.uid = "wintab-" + deviceIdx + "-" + cursorIdx;
        this.caps = caps;
        this.cursorTypes = cursorTypes;
        this.inOrgX = inOrgX; this.inOrgY = inOrgY;
        this.inExtX = inExtX; this.inExtY = inExtY;
    }

    @Override
    public int getVid() {
        return 0;
    }

    @Override
    public boolean supports(PenCapability cap) {
        return caps.contains(cap);
    }

    @Override
    public Set<PenCursorType> getSupportedCursorTypes() {
        return cursorTypes;  // 修复：返回构造函数传入的集合
    }


    private static String guessVendor(String name) {
        if (name == null) return "Unknown";
        String n = name.toLowerCase();
        if (n.contains("wacom")) return "Wacom";
        if (n.contains("huion") || n.contains("绘王")) return "Huion";
        if (n.contains("xp-pen") || n.contains("xppen")) return "XP-Pen";
        if (n.contains("gaomon") || n.contains("高漫")) return "Gaomon";
        if (n.contains("ugee") || n.contains("友基")) return "Ugee";
        return "Unknown";
    }
}