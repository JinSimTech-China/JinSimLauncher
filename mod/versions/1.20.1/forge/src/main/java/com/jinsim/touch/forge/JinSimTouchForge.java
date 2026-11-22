package com.jinsim.touch.forge;

import com.jinsim.touch.core.TouchConfig;
import com.jinsim.touch.core.TouchRuntime;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Forge 客户端入口（1.20.x）。
 * 1.20.1 的 Forge 还没有 ClientTickEvent.Post，用 TickEvent.ClientTickEvent 的 END 阶段等价替代。
 * GUI 层用 IGuiOverlay + RegisterGuiOverlaysEvent（1.20.5+ 才改叫 GuiLayer）。
 */
@Mod(JinSimTouchForge.MOD_ID)
public class JinSimTouchForge {

    public static final String MOD_ID = "jinsimtouch";

    public JinSimTouchForge() {
        // 尽早初始化运行时，保证第一帧 HUD 就有配置
        TouchRuntime.init(FMLPaths.CONFIGDIR.get().resolve(TouchConfig.FILE_NAME));
    }

    /** 游戏总线：客户端 tick */
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ClientGameEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            TouchRuntime runtime = TouchRuntime.get();
            if (runtime == null) return;
            Minecraft mc = Minecraft.getInstance();
            runtime.tick(mc.screen != null);
        }
    }

    /** 模组总线：注册 HUD 覆盖层 */
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ClientModEvents {
        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("jinsim_keys", new TouchOverlay());
        }
    }
}
