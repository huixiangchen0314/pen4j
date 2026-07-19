package top.kzre.pen4j.windows.wintab;

import com.sun.jna.Callback;

/**
 * 对应 C 胶水层的 WTPEventCallback 函数指针。
 */
public interface WTPEventCallback extends Callback {
    void invoke(WTPEvent event);
}