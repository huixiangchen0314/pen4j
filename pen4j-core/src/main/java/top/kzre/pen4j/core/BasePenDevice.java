package top.kzre.pen4j.core;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import top.kzre.pen4j.api.PenCapability;
import top.kzre.pen4j.api.PenCursorType;
import top.kzre.pen4j.api.PenDevice;
import java.util.*;

@Data
public class BasePenDevice implements PenDevice {
    private String name = "Unknown";
    private String vendor = "Unknown";
    private String uid = "unknown-0";
    private int vid = 0;
    private int pid = 0;
    private int maxX = 65535, maxY = 65535;
    private int maxPressure = 1023;
    private int maxProximity = 0;
    private int sideButtonCount = 2;
    private Set<PenCapability> caps = EnumSet.of(PenCapability.PRESSURE, PenCapability.ABSOLUTE_MODE);
    private Set<PenCursorType> supportedCursorTypes = EnumSet.of(PenCursorType.PEN);

    @Override public boolean supports(PenCapability cap) { return caps.contains(cap); }

}