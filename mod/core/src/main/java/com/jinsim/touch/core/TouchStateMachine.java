package com.jinsim.touch.core;

/**
 * 状态机：只决定“虚拟按键当前该不该显示”，不碰渲染、不碰输入注入。
 *
 * <p>规则：
 * <ol>
 *   <li>桥接不存在（桌面 / 其他启动器）→ 等效 FORCE_KBM，隐藏，不崩溃；</li>
 *   <li>FORCE_TOUCH 恒显示，FORCE_KBM 恒隐藏，两者不受 GUI 与防抖影响；</li>
 *   <li>AUTO：任一 GUI 打开时锁定当前状态，禁止自动切换；正常游戏时，
 *       距上次输入 &lt; 防抖阈值则按输入源切换，超过阈值则保持上一次状态（防闪烁）。</li>
 * </ol>
 */
public final class TouchStateMachine {

    /** 初始值：手机端默认显示虚拟按键（键鼠检测由启动器通过桥接覆盖） */
    private boolean visible = true;
    private boolean guiLocked = false;
    private boolean bridgeReady = false;

    private String lastSource = BridgeReader.SRC_NONE;
    private long lastInputNs = 0L;
    private long nowNs = 0L;

    private TouchMode configuredMode = TouchMode.AUTO;
    private TouchMode effectiveMode = TouchMode.FORCE_KBM;

    public void tick(long nowNs, boolean guiOpen, TouchConfig cfg) {
        this.nowNs = nowNs;
        this.bridgeReady = BridgeReader.ensure(nowNs);

        // 1. 桥接缺失：回退纯键鼠，虚拟按键隐藏
        if (!bridgeReady) {
            effectiveMode = TouchMode.FORCE_KBM;
            configuredMode = cfg.modeOrDefault();
            visible = false;
            guiLocked = guiOpen;
            lastSource = BridgeReader.SRC_NONE;
            lastInputNs = 0L;
            return;
        }

        lastSource = BridgeReader.readSourceName();
        lastInputNs = BridgeReader.readLastInputNs();

        configuredMode = cfg.modeOrDefault();
        effectiveMode = configuredMode;
        guiLocked = guiOpen;

        switch (configuredMode) {
            case FORCE_TOUCH:
                visible = true;
                return;
            case FORCE_KBM:
                visible = false;
                return;
            case AUTO:
            default:
                // 2. GUI 打开：锁定当前显示状态，不自动切换
                if (guiLocked) return;
                // 3. 没有输入记录：保持当前状态（首次进入默认显示）
                if (lastInputNs <= 0L) return;
                long age = nowNs - lastInputNs;
                if (age < cfg.debounceNs()) {
                    if (BridgeReader.SRC_TOUCH.equals(lastSource)) visible = true;
                    else if (BridgeReader.SRC_KBM.equals(lastSource)) visible = false;
                }
                // 4. 超过防抖阈值：维持上一次状态，避免长时间静止时闪烁
                return;
        }
    }

    public boolean isVisible() { return visible; }
    public boolean isGuiLocked() { return guiLocked; }
    public boolean isBridgeReady() { return bridgeReady; }
    public String getLastSource() { return lastSource; }
    public TouchMode getEffectiveMode() { return effectiveMode; }
    public TouchMode getConfiguredMode() { return configuredMode; }

    public long inputAgeMs() {
        if (lastInputNs <= 0L) return -1L;
        return Math.max(0L, (nowNs - lastInputNs) / 1_000_000L);
    }
}
