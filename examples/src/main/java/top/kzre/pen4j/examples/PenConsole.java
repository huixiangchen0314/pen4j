package top.kzre.pen4j.examples;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinUser.*;
import lombok.extern.slf4j.Slf4j;
import top.kzre.pen4j.api.*;
import top.kzre.pen4j.core.PenContext;
import top.kzre.pen4j.core.spi.PenPlatformDriver;
import top.kzre.pen4j.windows.ink.WindowsInkDriver;
import top.kzre.pen4j.windows.wintab.WintabDriver;

import static com.sun.jna.platform.win32.WinUser.*;

/**
 * 控制台演示：创建一个极小的焦点窗口用于 WinTab 驱动，实时打印笔事件。
 * 按 ESC 或等待 60 秒自动退出。
 */
@Slf4j
public class PenConsole {

    // Windows 常量（避免 JNA 版本缺失）
    private static final int WS_EX_TOOLWINDOW = 0x00000080;
    private static final int WS_POPUP = 0x80000000;
    private static final int PM_REMOVE = 0x0001;
    private static final int SW_SHOWNOACTIVATE = 4;
    private static final int VK_ESCAPE = 0x1B;

    private static final String WINDOW_CLASS = "Pen4jTestWindow";

    public static void main(String[] args) throws Exception {
        log.info("Starting PenConsole demo...");

        // 1. 创建极小的可见窗口（WinTab 需要可见/焦点窗口）
        HWND hwnd = createTinyWindow();
        log.info("Created tiny window: {}", hwnd);

        // 2. 用窗口句柄构造 Wintab 驱动
        long hwndVal = Pointer.nativeValue(hwnd.getPointer());
        PenPlatformDriver driver = new WintabDriver(hwndVal);

        // 3. 创建 PenContext（传入驱动实例）
        try (PenContext ctx = PenContext.create(driver)) {

            ctx.addListener(new PenListener() {
                @Override
                public void onPenData(PenEvent event) {
                    PenState s = event.getState();
                    PenDevice dev = event.getDevice();
                    log.info("Pen state: {}, device: {}", s, dev);
                }

                @Override
                public void onDeviceAdded(PenDevice device) {
                    System.out.println("Device added: " + device.getName());
                }

                @Override
                public void onDeviceRemoved(PenDevice device) {
                    System.out.println("Device removed: " + device.getName());
                }
            });

            ctx.start();

            log.info("Listening for pen events. Press ESC to exit, or wait 60 seconds...");

            long endTime = System.currentTimeMillis() + 60_000;
            while (System.currentTimeMillis() < endTime) {
                // 必须处理窗口消息，否则 WinTab 驱动不会刷新数据包队列
                MSG msg = new MSG();
                while (User32.INSTANCE.PeekMessage(msg, hwnd, 0, 0, PM_REMOVE)) {
                    User32.INSTANCE.TranslateMessage(msg);
                    User32.INSTANCE.DispatchMessage(msg);
                }
                // 检测 ESC 键
                if (User32.INSTANCE.GetAsyncKeyState(VK_ESCAPE) != 0) {
                    log.info("ESC pressed, exiting.");
                    break;
                }
                Thread.sleep(10);
            }
        } finally {
            // 清理窗口
            User32.INSTANCE.DestroyWindow(hwnd);
            User32.INSTANCE.UnregisterClass(WINDOW_CLASS, Kernel32.INSTANCE.GetModuleHandle(null));
        }
        log.info("Demo finished.");
    }

    private static HWND createTinyWindow() {
        User32 user32 = User32.INSTANCE;
        Kernel32 kernel32 = Kernel32.INSTANCE;
        HMODULE hInst = kernel32.GetModuleHandle(null);

        WNDCLASSEX wc = new WNDCLASSEX();
        wc.cbSize = wc.size();
        wc.lpfnWndProc = new WindowProc() {
            @Override
            public LRESULT callback(HWND hwnd, int uMsg, WPARAM wParam, LPARAM lParam) {
                return user32.DefWindowProc(hwnd, uMsg, wParam, lParam);
            }
        };
        wc.hInstance = hInst;
        wc.lpszClassName = WINDOW_CLASS;
        user32.RegisterClassEx(wc);

        // 改为普通弹出窗口（有边框，可拖拽，但不是任务栏窗口）
        HWND hwnd = user32.CreateWindowEx(
                0,                          // 无扩展风格（不隐藏任务栏，但可见）
                WINDOW_CLASS, "Pen4jWin",
                WS_POPUP | WS_CAPTION | WS_SYSMENU,  // 有标题栏和关闭按钮
                100, 100, 200, 200,        // 位置与大小
                null, null, hInst, null);

        user32.ShowWindow(hwnd, SW_SHOW);
        user32.SetForegroundWindow(hwnd);
        return hwnd;
    }
}