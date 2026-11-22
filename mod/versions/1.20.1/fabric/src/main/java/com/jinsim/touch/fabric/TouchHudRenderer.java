package com.jinsim.touch.fabric;

import com.jinsim.touch.core.TouchConfig;
import com.jinsim.touch.core.TouchLayout;
import com.jinsim.touch.core.TouchRuntime;
import com.jinsim.touch.core.TouchStateMachine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Fabric 渲染模块（1.20.x，DrawContext 版本）。
 * 只读 TouchRuntime 的结果来画，不参与状态判断。
 * 点击不在这里处理：按键命中区由启动器触摸层接管（见 README 坐标约定）。
 */
public final class TouchHudRenderer {

    private static final int KEY_FILL = 0x66080A12;
    private static final int KEY_BORDER = 0x55FFFFFF;
    private static final int KEY_ICON = 0xFFFFFFFF;
    private static final int EXIT_FILL = 0x99C0392B;

    private TouchHudRenderer() { }

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TouchRuntime runtime = TouchRuntime.get();
        if (runtime == null || mc.player == null || mc.options.hudHidden) return;

        TouchConfig cfg = runtime.config();
        TouchLayout layout = runtime.layout();
        layout.update(mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), cfg.keyScale);

        if (runtime.machine().isVisible()) drawKeys(ctx, layout, mc, cfg);
        if (cfg.showDebug) drawDebug(ctx, mc, runtime.machine(), runtime);
    }

    private static void drawKeys(DrawContext ctx, TouchLayout l, MinecraftClient mc, TouchConfig cfg) {
        key(ctx, l.up);
        key(ctx, l.down);
        key(ctx, l.left);
        key(ctx, l.right);
        arrow(ctx, l.up, 0);
        arrow(ctx, l.down, 2);
        arrow(ctx, l.left, 3);
        arrow(ctx, l.right, 1);

        // EXIT：右上角，点击由启动器映射到暂停菜单
        ctx.fill(l.exit.left, l.exit.top, l.exit.right, l.exit.bottom, EXIT_FILL);
        ctx.fill(l.exit.left, l.exit.top, l.exit.right, l.exit.top + 1, KEY_BORDER);
        ctx.fill(l.exit.left, l.exit.bottom - 1, l.exit.right, l.exit.bottom, KEY_BORDER);
        String label = "EXIT";
        int tw = mc.textRenderer.getWidth(label);
        ctx.drawText(mc.textRenderer, label, l.exit.cx() - tw / 2, l.exit.cy() - 4, KEY_ICON, false);

        if (cfg.showActionKeys) {
            actionKey(ctx, l.jump, "JUMP", mc);
            actionKey(ctx, l.sneak, "SNEAK", mc);
            actionKey(ctx, l.inventory, "INV", mc);
        }
    }

    private static void key(DrawContext ctx, TouchLayout.Rect r) {
        ctx.fill(r.left, r.top, r.right, r.bottom, KEY_FILL);
        ctx.fill(r.left, r.top, r.right, r.top + 1, KEY_BORDER);
        ctx.fill(r.left, r.bottom - 1, r.right, r.bottom, KEY_BORDER);
        ctx.fill(r.left, r.top, r.left + 1, r.bottom, KEY_BORDER);
        ctx.fill(r.right - 1, r.top, r.right, r.bottom, KEY_BORDER);
    }

    private static void actionKey(DrawContext ctx, TouchLayout.Rect r, String label, MinecraftClient mc) {
        key(ctx, r);
        int tw = mc.textRenderer.getWidth(label);
        ctx.drawText(mc.textRenderer, label, r.cx() - tw / 2, r.cy() - 4, KEY_ICON, false);
    }

    /** 用矩形逐行画出三角箭头，避免依赖字体里的 unicode 箭头 */
    private static void arrow(DrawContext ctx, TouchLayout.Rect r, int dir) {
        int cx = r.cx(), cy = r.cy();
        int half = Math.max(4, r.width() / 2 - 4);
        int rows = Math.max(4, r.height() / 2 - 4);
        for (int i = 0; i < rows; i++) {
            int w = (i + 1) * half / rows;
            switch (dir) {
                case 0: // 上
                    ctx.fill(cx - w, cy - rows / 2 + i, cx + w, cy - rows / 2 + i + 1, KEY_ICON);
                    break;
                case 1: // 右
                    ctx.fill(cx + rows / 2 - i, cy - w, cx + rows / 2 - i + 1, cy + w, KEY_ICON);
                    break;
                case 2: // 下
                    ctx.fill(cx - w, cy + rows / 2 - i, cx + w, cy + rows / 2 - i + 1, KEY_ICON);
                    break;
                default: // 左
                    ctx.fill(cx - rows / 2 + i, cy - w, cx - rows / 2 + i + 1, cy + w, KEY_ICON);
                    break;
            }
        }
    }

    private static void drawDebug(DrawContext ctx, MinecraftClient mc, TouchStateMachine sm, TouchRuntime rt) {
        String l1 = "JinSimTouch 1.20.1 fabric";
        String l2 = "mode " + sm.getConfiguredMode() + "  src " + sm.getLastSource() + "  age " + sm.inputAgeMs() + "ms";
        String l3 = "keys " + (sm.isVisible() ? "SHOW" : "HIDE") + "  guiLock " + sm.isGuiLocked() + "  bridge " + (sm.isBridgeReady() ? "OK" : "MISSING");
        ctx.fill(2, 2, 2 + 4 + mc.textRenderer.getWidth(l1) + 4, 2 + 3 * 10 + 6, 0x66000000);
        ctx.drawText(mc.textRenderer, l1, 6, 5, 0xFF9BE7FF, false);
        ctx.drawText(mc.textRenderer, l2, 6, 15, 0xFFFFE58A, false);
        ctx.drawText(mc.textRenderer, l3, 6, 25, 0xFFB8FFB8, false);
    }
}
