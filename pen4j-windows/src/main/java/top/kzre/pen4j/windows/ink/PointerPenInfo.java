package top.kzre.pen4j.windows.ink;

import com.sun.jna.Structure; /**
 * 对应 POINTER_PEN_INFO
 */
@Structure.FieldOrder({"pointerInfo", "penFlags", "penMask", "pressure",
        "rotation", "tiltX", "tiltY"})
public class PointerPenInfo extends Structure {
    public static class ByReference extends PointerPenInfo implements Structure.ByReference {}

    public PointerInfo pointerInfo;
    public int penFlags;
    public int penMask;
    public int pressure;   // 0-1024
    public int rotation;   // 0-359
    public int tiltX;      // -90 到 90
    public int tiltY;      // -90 到 90

    public PointerPenInfo() {
        pointerInfo = new PointerInfo(); // 必须初始化内嵌结构体
    }
}
