package top.kzre.pen4j.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PenStateTest {
    @Test
    void builderShouldCreateImmutableSnapshot() {
        PenState state = PenState.builder()
                .x(0.5).y(0.3)
                .pressure(0.8)
                .tiltX(0.1).tiltY(-0.2)
                .twist(45.0)
                .near(true)
                .tipPressed(true)
                .button1Pressed(false)
                .button2Pressed(false)
                .eraserPressed(false)
                .build();

        assertEquals(0.5, state.getX());
        assertEquals(0.8, state.getPressure());
        assertTrue(state.isNear());
        assertTrue(state.isTipPressed());
    }
}