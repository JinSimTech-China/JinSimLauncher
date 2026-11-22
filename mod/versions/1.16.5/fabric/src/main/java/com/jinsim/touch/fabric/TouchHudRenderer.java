package com.jinsim.touch.fabric;

import com.jinsim.touch.core.TouchConfig;
import com.jinsim.touch.core.TouchLayout;
import com.jinsim.touch.core.TouchRuntime;
import com.jinsim.touch.core.TouchStateMachine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Fabric 渲染模块（1.16.5，MatrixStack 版本）。
 * 和 1.20.1 的区别只在绘制 API：DrawContext.fill → DrawableHelper.fill(MatrixStack, ...)。
 */
public final class TouchHudRenderer {

    private static final int KEY_FILL = 0x66080A12;
    private static final int KEY_BORDER = 0x55FFFFFF;
    private static final int KEY_ICON = 0xFFFFFFFF;
    private static final int EXIT_FILL = 0x99C0392B;

    private TouchHudRenderer() { }

    public static void render(MatrixStack matrices, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TouchRuntime runtime = TouchRuntime.get();
        if (runtime == null || mc.player == null || mc.options.hudHidden) return;

        TouchConfig cfg = runtime.config();
        TouchLayout layout = runtime.layout();
        layout.update(mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), cfg.keyScale);

        if (runtime.machine().isVisible()) drawKeys(matrices, layout, mc, cfg);
        if (cfg.showDebug) drawDebug(matrices, mc, runtime.machine());
    }

    private static void drawKeys(MatrixStack ms, TouchLayout l, MinecraftClient mc, TouchConfig cfg) {
        key(ms, l.up); key(ms, l.down); key(ms, l.left); key(ms, l.right);
        arrow(ms, l.up, 0); arrow(ms, l.down, 2); arrow(ms, l.left, 3); arrow(ms, l.right, 1);

        fill(ms, l.exit.left, l.exit.top, l.exit.right, l.exit.bottom, EXIT_FILL);
        String label = "EXIT";
        int tw = mc.textRenderer.getWidth(label);
        mc.textRenderer.draw(ms, label, l.exit.cx() - tw / 2f, l.exit.cy() - 4f, KEY_ICON);

        if (cfg.showActionKeys) {
            actionKey(ms, l.jump, "JUMP", mc);
            actionKey(ms, l.sneak, "SNEAK", mc);
            actionKey(ms, l.inventory, "INV", mc);
        }
    }

    private static void fill(MatrixStack ms, int x1, int y1, int x2, int y2, int color) {
        DrawableHelper.fill(ms, x1, y1, x2, y2, color);
    }

    private static void key(MatrixStack ms, TouchLayout.Rect r) {
        fill(ms, r.left, r.top, r.right, r.bottom, KEY_FILL);
        fill(ms, r.left, r.top, r.right, r.top + 1, KEY_BORDER);
        fill(ms, r.left, r.bottom - 1, r.right, r.bottom, KEY_BORDER);
        fill(ms, r.left, r.top, r.left + 1, r.bottom, KEY_BORDER);
        fill(ms, r.right - 1, r.top, r.right, r.bottom, KEY_BORDER);
    }

    private static void actionKey(MatrixStack ms, TouchLayout.Rect r, String label, MinecraftClient mc) {
        key(ms, r);
        int tw = mc.textRenderer.getWidth(label);
        mc.textRenderer.draw(ms, label, r.cx() - tw / 2f, r.cy() - 4f, KEY_ICON);
    }

    private static void arrow(MatrixStack ms, TouchLayout.Rect r, int dir) {
        int cx = r.cx(), cy = r.cy();
        int half = Math.max(4, r.width() / 2 - 4);
        int rows = Math.max(4, r.height() / 2 - 4);
        for (int i = 0; i < rows; i++) {
            int w = (i + 1) * half / rows;
            switch (dir) {
                case 0: fill(ms, cx - w, cy - rows / 2 + i, cx + w, cy - rows / 2 + i + 1, KEY_ICON); break;
                case 1: fill(ms, cx + rows / 2 - i, cy - w, cx + rows / 2 - i + 1, cy + w, KEY_ICON); break;
                case 2: fill(ms, cx - w, cy + rows / 2 - i, cx + w, cy + rows / 2 - i + 1, KEY_ICON); break;
                default: fill(ms, cx - rows / 2 + i, cy - w, cx - rows / 2 + i + 1, cy + w, KEY_ICON); break;
            }
        }
    }

    private static void drawDebug(MatrixStack ms, MinecraftClient mc, TouchStateMachine sm) {
        String l1 = "JinSimTouch 1.16.5 fabric";
        String l2 = "mode " + sm.getConfiguredMode() + "  src " + sm.getLastSource() + "  age " + sm.inputAgeMs() + "ms";
        String l3 = "keys " + (sm.isVisible() ? "SHOW" : "HIDE") + "  guiLock " + sm.isGuiLocked() + "  bridge " + (sm.isBridgeReady() ? "OK" : "MISSING");
        fill(ms, 2, 2, 6 + mc.textRenderer.getWidth(l1) + 4, 2 + 30, 0x66000000);
        mc.textRenderer.draw(ms, l1, 6, 5, 0xFF9BE7FF);
        mc.textRenderer.draw(ms, l2, 6, 15, 0xFFFFE58A);
        mc.textRenderer.draw(ms, l3, 6, 25, 0xFFB8FFB8);
    }
}
