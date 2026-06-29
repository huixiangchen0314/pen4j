package top.kzre.pen4j.windows.wintab;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinUser.MSG;
import lombok.extern.slf4j.Slf4j;
import top.kzre.pen4j.api.*;
import top.kzre.pen4j.core.DefaultPenEvent;
import top.kzre.pen4j.windows.common.WinConstants;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class WintabPacketPoller {

    private final HWND hwnd;
    private final AtomicReference<PenListener> listenerRef;
    private final List<WintabPenDevice> devices;
    private final WintabContext context;
    private final WintabPacketParser parser;

    private volatile boolean running = false;
    private Thread pollThread;

    public WintabPacketPoller(HWND hwnd,
                              AtomicReference<PenListener> listenerRef,
                              List<WintabPenDevice> devices,
                              WintabContext context) {
        this.hwnd = hwnd;
        this.listenerRef = listenerRef;
        this.devices = devices;
        this.context = context;
        this.parser = new WintabPacketParser(context.getPktDataMask());
    }

    public void start() {
        if (running) return;
        running = true;
        CountDownLatch pumpStarted = new CountDownLatch(1);
        pollThread = new Thread(() -> {
            MSG msg = new MSG();
            pumpStarted.countDown();
            while (running) {
                while (User32.INSTANCE.PeekMessage(msg, hwnd, 0, 0, WinConstants.PM_REMOVE)) {
                    User32.INSTANCE.TranslateMessage(msg);
                    User32.INSTANCE.DispatchMessage(msg);
                }
                pollOnce();
                try {
                    Thread.sleep(8);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "pen4j-wintab-poll");
        pollThread.setDaemon(true);
        pollThread.start();
        try {
            pumpStarted.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    public void stop() {
        running = false;
        if (pollThread != null) {
            pollThread.interrupt();
            try {
                pollThread.join(2000);
            } catch (InterruptedException ignored) {}
        }
    }

    private void pollOnce() {
        WinNT.HANDLE hCtx = context.getContextHandle();
        if (hCtx == null || Pointer.nativeValue(hCtx.getPointer()) == 0) return;
        PenListener listener = listenerRef.get();
        if (listener == null) return;

        int packetSize = parser.calcPacketSize();
        Memory buf = new Memory((long) 32 * packetSize); // MAX_PACKETS
        int count = WintabLibrary.INSTANCE.WTPacketsGet(hCtx, 32, buf);
        if (count <= 0) return;

        for (int i = 0; i < count; i++) {
            byte[] raw = new byte[packetSize];
            buf.read((long) i * packetSize, raw, 0, packetSize);
            PACKET pkt = new PACKET(raw, context.getPktDataMask());
            WintabPenDevice dev = devices.isEmpty() ? null : devices.get(0);
            if (dev == null) continue;

            PenState state = parser.parse(pkt, dev);
            int pkTime = (int) pkt.getPkTime();
            PenEvent event = new DefaultPenEvent(dev, pkTime * 1000L, state);
            listener.onPenData(event);
        }
    }
}