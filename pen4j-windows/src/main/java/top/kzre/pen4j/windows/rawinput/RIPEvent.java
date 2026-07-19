package top.kzre.pen4j.windows.rawinput;

import com.sun.jna.Structure;
import java.util.Arrays;
import java.util.List;

@Structure.FieldOrder({"timestamp", "x", "y", "pressure", "tiltX", "tiltY", "tip", "reserved", "buttons"})
public class RIPEvent extends Structure {
    public int timestamp;        // uint32_t
    public float x;
    public float y;
    public float pressure;
    public float tiltX;
    public float tiltY;
    public byte tip;            // 0/1
    public byte reserved;       // 对齐保留
    public short buttons;       // uint16_t 位掩码，bit0=button1

    public static class ByReference extends RIPEvent implements Structure.ByReference {}

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("timestamp", "x", "y", "pressure", "tiltX", "tiltY", "tip", "reserved", "buttons");
    }
}