package com.jinsim.touch.core;

/** 三种工作模式 */
public enum TouchMode {
    /** 自动：根据桥接输入源 + 防抖时间自动切换 */
    AUTO,
    /** 强制显示：虚拟按键始终可见 */
    FORCE_TOUCH,
    /** 强制隐藏：虚拟按键始终隐藏（纯键鼠） */
    FORCE_KBM;

    /** 容错解析：配置文件里写成小写/带空格也能认 */
    public static TouchMode parse(String raw, TouchMode fallback) {
        if (raw == null) return fallback;
        switch (raw.trim().toUpperCase().replace('-', '_')) {
            case "AUTO": return AUTO;
            case "FORCE_TOUCH":
            case "TOUCH": return FORCE_TOUCH;
            case "FORCE_KBM":
            case "KBM": return FORCE_KBM;
            default: return fallback;
        }
    }
}
