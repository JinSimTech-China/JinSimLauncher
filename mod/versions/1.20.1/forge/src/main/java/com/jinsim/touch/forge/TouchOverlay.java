package com.jinsim.touch.forge;

import com.jinsim.touch.core.TouchConfig;
import com.jinsim.touch.core.TouchLayout;
import com.jinsim.touch.core.TouchRuntime;
import com.jinsim.touch.core.TouchStateMachine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Forge 渲染模块（1.20.x，GuiGraphics 版本）。
 * 只画，不判断；点击由启动器触摸层接管。
 */
public final class TouchOverlay implements IGuiOverlay {

    private static final int KEY_FILL = 0x66080A12;
    private static final int KEY_BORDER = 0x55FFFFFF;
    private static final int KEY_ICON = 0xFFFFFFFF;
    private static final int EXIT_FILL = 0x99C0392B;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        TouchRuntime runtime = TouchRuntime.get();
        if (runtime == null || mc.player == null || mc.options.hideGui) return;

        TouchConfig cfg = runtime.config();
        TouchLayout layout = runtime.layout();
        layout.update(screenWidth, screenHeight, cfg.keyScale);

        if (runtime.machine().isVisible()) drawKeys(g, layout, mc, cfg);
        if (cfg.showDebug) drawDebug(g, mc, runtime.machine());
    }

    private static void drawKeys(GuiGraphics g, TouchLayout l, Minecraft mc, TouchConfig cfg) {
        key(g, l.up); key(g, l.down); key(g, l.left); key(g, l.right);
        arrow(g, l.up, 0); arrow(g, l.down, 2); arrow(g, l.left, 3); arrow(g, l.right, 1);

        g.fill(l.exit.left, l.exit.top, l.exit.right, l.exit.bottom, EXIT_FILL);
        String label = "EXIT";
        int tw = mc.font.width(label);
        g.drawString(mc.font, label, l.exit.cx() - tw / 2, l.exit.cy() - 4, KEY_ICON, false);

        if (cfg.showActionKeys) {
            actionKey(g, l.jump, "JUMP", mc);
            actionKey(g, l.sneak, "SNEAK", mc);
            actionKey(g, l.inventory, "INV", mc);
        }
    }

    private static void key(GuiGraphics g, TouchLayout.Rect r) {
        g.fill(r.left, r.top, r.right, r.bottom, KEY_FILL);
        g.fill(r.left, r.top, r.right, r.top + 1, KEY_BORDER);
        g.fill(r.left, r.bottom - 1, r.right, r.bottom, KEY_BORDER);
        g.fill(r.left, r.top, r.left + 1, r.bottom, KEY_BORDER);
        g.fill(r.right - 1, r.top, r.right, r.bottom, KEY_BORDER);
    }

    private static void actionKey(GuiGraphics g, TouchLayout.Rect r, String label, Minecraft mc) {
        key(g, r);
        int tw = mc.font.width(label);
        g.drawString(mc.font, label, r.cx() - tw / 2, r.cy() - 4, KEY_ICON, false);
    }

    private static void arrow(GuiGraphics g, TouchLayout.Rect r, int dir) {
        int cx = r.cx(), cy = r.cy();
        int half = Math.max(4, r.width() / 2 - 4);
        int rows = Math.max(4, r.height() / 2 - 4);
        for (int i = 0; i < rows; i++) {
            int w = (i + 1) * half / rows;
            switch (dir) {
                case 0: g.fill(cx - w, cy - rows / 2 + i, cx + w, cy - rows / 2 + i + 1, KEY_ICON); break;
                case 1: g.fill(cx + rows / 2 - i, cy - w, cx + rows / 2 - i + 1, cy + w, KEY_ICON); break;
                case 2: g.fill(cx - w, cy + rows / 2 - i, cx + w, cy + rows / 2 - i + 1, KEY_ICON); break;
                default: g.fill(cx - rows / 2 + i, cy - w, cx - rows / 2 + i + 1, cy + w, KEY_ICON); break;
            }
        }
    }

    private static void drawDebug(GuiGraphics g, Minecraft mc, TouchStateMachine sm) {
        String l1 = "JinSimTouch 1.20.1 forge";
        String l2 = "mode " + sm.getConfiguredMode() + "  src " + sm.getLastSource() + "  age " + sm.inputAgeMs() + "ms";
        String l3 = "keys " + (sm.isVisible() ? "SHOW" : "HIDE") + "  guiLock " + sm.isGuiLocked() + "  bridge " + (sm.isBridgeReady() ? "OK" : "MISSING");
        g.fill(2, 2, 6 + mc.font.width(l1) + 4, 2 + 3 * 10 + 6, 0x66000000);
        g.drawString(mc.font, l1, 6, 5, 0xFF9BE7FF, false);
        g.drawString(mc.font, l2, 6, 15, 0xFFFFE58A, false);
        g.drawString(mc.font, l3, 6, 25, 0xFFB8FFB8, false);
    }
}
