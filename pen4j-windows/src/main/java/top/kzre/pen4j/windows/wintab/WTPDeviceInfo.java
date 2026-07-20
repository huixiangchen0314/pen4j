package top.kzre.pen4j.windows.wintab;

import com.sun.jna.Structure;
import java.util.Arrays;
import java.util.List;

@Structure.FieldOrder({"deviceName", "uid", "vid", "pid", "maxPressure", "maxLogicalX", "maxLogicalY", "buttonCount", "reserved"})
public class WTPDeviceInfo extends Structure {
    public byte[] deviceName = new byte[128];
    public byte[] uid = new byte[256];
    public short vid;
    public short pid;
    public int maxPressure;
    public int maxLogicalX;
    public int maxLogicalY;
    public int buttonCount;
    public int reserved;

    public static class ByReference extends WTPDeviceInfo implements Structure.ByReference {}

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("deviceName", "uid", "vid", "pid", "maxPressure", "maxLogicalX", "maxLogicalY", "buttonCount", "reserved");
    }

    public String getDeviceName() {
        int len = 0;
        for (byte b : deviceName) { if (b == 0) break; len++; }
        return new String(deviceName, 0, len, java.nio.charset.StandardCharsets.UTF_8);
    }

    public String getUid() {
        int len = 0;
        for (byte b : uid) { if (b == 0) break; len++; }
        return new String(uid, 0, len, java.nio.charset.StandardCharsets.UTF_8);
    }
}