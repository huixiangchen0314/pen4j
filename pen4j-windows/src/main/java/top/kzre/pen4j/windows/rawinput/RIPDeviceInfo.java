package top.kzre.pen4j.windows.rawinput;

import com.sun.jna.Structure;
import java.util.Arrays;
import java.util.List;

@Structure.FieldOrder({"deviceName", "uid", "vid", "pid", "maxPressure", "maxLogicalX", "maxLogicalY", "buttonCount", "reserved"})
public class RIPDeviceInfo extends Structure {
    public byte[] deviceName = new byte[128];   // UTF-8
    public byte[] uid = new byte[256];
    public short vid;
    public short pid;
    public int maxPressure;
    public int maxLogicalX;
    public int maxLogicalY;
    public int buttonCount;
    public int reserved;

    public static class ByReference extends RIPDeviceInfo implements Structure.ByReference {}

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("deviceName", "uid", "vid", "pid", "maxPressure", "maxLogicalX", "maxLogicalY", "buttonCount", "reserved");
    }

    public String getDeviceName() {
        return new String(deviceName, 0, indexOfNull(deviceName), java.nio.charset.StandardCharsets.UTF_8);
    }

    public String getUid() {
        return new String(uid, 0, indexOfNull(uid), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static int indexOfNull(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == 0) return i;
        }
        return bytes.length;
    }
}