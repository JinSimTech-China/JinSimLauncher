package com.jinsim.touch.forge;

import com.jinsim.touch.core.TouchConfig;
import com.jinsim.touch.core.TouchRuntime;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Forge 客户端入口（1.16.5）。
 * 1.16.5 没有 GuiLayer / IGuiOverlay，HUD 走 RenderGameOverlayEvent.Post（见 TouchOverlay）。
 */
@Mod(JinSimTouchForge.MOD_ID)
public class JinSimTouchForge {

    public static final String MOD_ID = "jinsimtouch";

    public JinSimTouchForge() {
        TouchRuntime.init(FMLPaths.CONFIGDIR.get().resolve(TouchConfig.FILE_NAME));
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ClientGameEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            TouchRuntime runtime = TouchRuntime.get();
            if (runtime == null) return;
            Minecraft mc = Minecraft.getInstance();
            runtime.tick(mc.currentScreen != null);
        }
    }
}
