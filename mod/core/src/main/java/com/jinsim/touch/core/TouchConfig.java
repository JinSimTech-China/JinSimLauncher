package com.jinsim.touch.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置文件：config/jinsimtouch.json
 * 只做客户端读写，不参与游戏逻辑。文件被外部修改后 2 秒内自动热重载。
 */
public final class TouchConfig {

    /** 工作模式 */
    public String mode = TouchMode.AUTO.name();
    /** 防抖时间（毫秒）：距上次输入小于该值才切换显示状态，默认 800 */
    public long debounceMs = 800L;
    /** 是否渲染左上角调试指示器 */
    public boolean showDebug = true;
    /** 是否同时绘制右下角动作键（跳跃/潜行/背包） */
    public boolean showActionKeys = true;
    /** 虚拟按键整体缩放（0.6 ~ 1.6），手机小屏可调小 */
    public float keyScale = 1.0f;

    private transient Path path;
    private transient long lastModified = -1L;
    private transient long lastReloadNs = 0L;

    public static final String FILE_NAME = "jinsimtouch.json";

    public static TouchConfig load(Path path) {
        TouchConfig cfg = new TouchConfig();
        cfg.path = path;
        if (path != null && Files.isRegularFile(path)) {
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                TouchConfig parsed = new Gson().fromJson(r, TouchConfig.class);
                if (parsed != null) cfg = parsed;
            } catch (Exception ignored) {
                // 坏文件不影响启动，用默认值继续
            }
            cfg.path = path;
            try {
                cfg.lastModified = Files.getLastModifiedTime(path).toMillis();
            } catch (IOException ignored) { }
        }
        cfg.path = path;
        cfg.normalize();
        cfg.saveIfMissing();
        return cfg;
    }

    /** 把非法值收敛到安全范围 */
    private void normalize() {
        mode = TouchMode.parse(mode, TouchMode.AUTO).name();
        if (debounceMs < 0L) debounceMs = 0L;
        if (debounceMs > 10_000L) debounceMs = 10_000L;
        if (keyScale < 0.6f) keyScale = 0.6f;
        if (keyScale > 1.6f) keyScale = 1.6f;
    }

    private void saveIfMissing() {
        if (path == null || Files.isRegularFile(path)) return;
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(this, w);
            }
        } catch (IOException ignored) { }
    }

    /** 文件修改后自动重载（每 2 秒检查一次 mtime，不做轮询读内容） */
    public void reloadIfChanged(long nowNs) {
        if (path == null) return;
        if (nowNs - lastReloadNs < 2_000_000_000L) return;
        lastReloadNs = nowNs;
        try {
            long m = Files.getLastModifiedTime(path).toMillis();
            if (m == lastModified) return;
            lastModified = m;
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                TouchConfig parsed = new Gson().fromJson(r, TouchConfig.class);
                if (parsed == null) return;
                mode = TouchMode.parse(parsed.mode, TouchMode.AUTO).name();
                debounceMs = parsed.debounceMs;
                showDebug = parsed.showDebug;
                showActionKeys = parsed.showActionKeys;
                keyScale = parsed.keyScale;
                normalize();
            }
        } catch (Exception ignored) { }
    }

    public TouchMode modeOrDefault() {
        return TouchMode.parse(mode, TouchMode.AUTO);
    }

    public long debounceNs() {
        return debounceMs * 1_000_000L;
    }
}
