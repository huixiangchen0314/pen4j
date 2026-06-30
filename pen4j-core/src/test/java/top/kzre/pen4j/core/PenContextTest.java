package top.kzre.pen4j.core;

import org.junit.jupiter.api.*;
import org.mockito.*;
import top.kzre.pen4j.api.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PenContextTest {

    @Mock PenPlatformDriver mockDriver;
    @Mock PenListener mockListener;
    @Mock PenDevice mockDevice;

    PenContext context;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockDriver.isAvailable()).thenReturn(true);
        when(mockDriver.getDevices()).thenReturn(Collections.singletonList(mockDevice));
        when(mockDevice.getUid()).thenReturn("mock-device");

        context = PenContext.create(mockDriver, PenContext.DispatchMode.DIRECT);
        context.addListener(mockListener);
    }

    @Test
    void shouldDeliverEventsToListener() throws InterruptedException {
        // 用真实 PenState，不用 mock
        PenState state = PenState.builder()
                .x(0.5).y(0.3)
                .pressure(0.8)
                .near(true)
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
            PenListener listener = inv.getArgument(0);
            PenEvent event = new DefaultPenEvent(mockDevice, 123L, state);
            listener.onPenData(event);
            latch.countDown();
            return null;
        }).when(mockDriver).start(any(PenListener.class));

        context.start();
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        verify(mockListener).onPenData(any());
    }

    @Test
    void pollShouldReturnLastEvent() {
        PenState state = PenState.builder()
                .x(0.1).y(0.2)
                .pressure(0.5)
                .build();
        PenEvent ev = new DefaultPenEvent(mockDevice, 456L, state);

        doAnswer(inv -> {
            PenListener l = inv.getArgument(0);
            l.onPenData(ev);
            return null;
        }).when(mockDriver).start(any());

        context.start();
        assertEquals(ev, context.poll(mockDevice));
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }
}