package top.kzre.pen4j.windows.rawinput;

import com.sun.jna.Structure;
import java.util.Arrays;
import java.util.List;

@Structure.FieldOrder({"deviceUid", "event"})
public class RIPExtendedEvent extends Structure {
    public byte[] deviceUid = new byte[256];
    public RIPEvent event;

    public static class ByReference extends RIPExtendedEvent implements Structure.ByReference {}

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("deviceUid", "event");
    }

    public String getDeviceUid() {
        int len = 0;
        for (byte b : deviceUid) {
            if (b == 0) break;
            len++;
        }
        return new String(deviceUid, 0, len, java.nio.charset.StandardCharsets.UTF_8);
    }
}