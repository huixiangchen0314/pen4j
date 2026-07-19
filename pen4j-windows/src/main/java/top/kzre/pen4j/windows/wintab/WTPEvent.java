package top.kzre.pen4j.windows.wintab;

import com.sun.jna.Structure;
import java.util.Arrays;
import java.util.List;

@Structure.FieldOrder({"timestamp", "x", "y", "pressure", "tangentialPressure",
        "tiltX", "tiltY", "azimuth", "altitude", "twist", "roll", "pitch", "yaw",
        "tip", "proximity", "eraser", "reserved", "buttons"})
public class WTPEvent extends Structure {
    public int timestamp;
    public float x, y;
    public float pressure;
    public float tangentialPressure;
    public float tiltX, tiltY;
    public float azimuth;
    public float altitude;
    public float twist;
    public float roll, pitch, yaw;
    public byte tip;
    public byte proximity;
    public byte eraser;
    public byte reserved;
    public short buttons;   // uint16_t -> JNA short

    public static class ByReference extends WTPEvent implements Structure.ByReference {}

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("timestamp", "x", "y", "pressure", "tangentialPressure",
                "tiltX", "tiltY", "azimuth", "altitude", "twist", "roll", "pitch", "yaw",
                "tip", "proximity", "eraser", "reserved", "buttons");
    }
}