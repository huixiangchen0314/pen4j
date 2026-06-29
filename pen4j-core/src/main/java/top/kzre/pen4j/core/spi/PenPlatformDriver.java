package top.kzre.pen4j.core.spi;

import top.kzre.pen4j.api.PenDevice;
import top.kzre.pen4j.api.PenListener;

import java.util.List;

/**
 * 平台笔设备驱动 SPI。
 * 每个平台（Windows / macOS / Linux）提供一个实现类，
 * 并在 META-INF/services 中注册。
 */
public interface PenPlatformDriver {

    /** 当前平台是否可用（比如 dll/dylib/so 存在、权限足够） */
    boolean isAvailable();

    /** 获取当前平台所有已连接的笔设备 */
    List<PenDevice> getDevices();

    /** 启动事件采集，开始向 listener 推送事件 */
    void start(PenListener listener);

    /** 停止事件采集 */
    void stop();
}