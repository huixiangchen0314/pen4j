package top.kzre.pen4j.windows.ink;

import com.sun.jna.Structure;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindowsInkJNAStructureTest {

    @Test
    void pointerInfoSizeShouldBeNonZero() {
        WindowsInkJNA.POINTER_INFO pi = new WindowsInkJNA.POINTER_INFO();
        assertTrue(pi.size() > 0);
    }

    @Test
    void penInfoFieldsAreAccessible() {
        WindowsInkJNA.POINTER_PEN_INFO pen = new WindowsInkJNA.POINTER_PEN_INFO();
        pen.pressure = 512;
        pen.tiltX = 30;
        assertEquals(512, pen.pressure);
        assertEquals(30, pen.tiltX);
    }
}