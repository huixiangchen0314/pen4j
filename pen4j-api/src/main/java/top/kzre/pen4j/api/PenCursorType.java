package top.kzre.pen4j.api;

/**
 * 数位板支持的笔光标类型（工具类型）。
 * 一支物理笔可能支持多种光标，例如正端的钢笔、后端的橡皮擦。
 */
public enum PenCursorType {
    /** 标准笔尖（钢笔、画笔等） */
    PEN,
    /** 喷枪（带指轮或特殊压力映射） */
    AIRBRUSH,
    /** 橡皮擦端 */
    ERASER,
    /** 十字光标（定位 / 鼠标模式） */
    LENS_CURSOR,
    /** 标准鼠标光标 */
    CURSOR
}