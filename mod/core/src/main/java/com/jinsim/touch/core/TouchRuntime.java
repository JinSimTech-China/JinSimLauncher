package com.jinsim.touch.core;

import java.nio.file.Path;

/**
 * 运行时聚合：配置 + 状态机 + 布局，两个加载器共用一份，避免各写一套。
 */
public final class TouchRuntime {

    private static volatile TouchRuntime instance;

    private final TouchConfig config;
    private final TouchStateMachine machine = new TouchStateMachine();
    private final TouchLayout layout = new TouchLayout();

    private TouchRuntime(TouchConfig config) {
        this.config = config;
    }

    public static synchronized TouchRuntime init(Path configFile) {
        if (instance == null) instance = new TouchRuntime(TouchConfig.load(configFile));
        return instance;
    }

    public static TouchRuntime get() {
        return instance;
    }

    public void tick(boolean guiOpen) {
        long now = System.nanoTime();
        config.reloadIfChanged(now);
        machine.tick(now, guiOpen, config);
    }

    public TouchConfig config() { return config; }
    public TouchStateMachine machine() { return machine; }
    public TouchLayout layout() { return layout; }
}
