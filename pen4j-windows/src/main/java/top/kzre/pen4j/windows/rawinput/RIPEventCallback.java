package top.kzre.pen4j.windows.rawinput;

import com.sun.jna.Callback;

public interface RIPEventCallback extends Callback {
    void invoke(RIPEvent event);
}