package com.jinsim.touch.fabric;

import com.jinsim.touch.core.TouchConfig;
import com.jinsim.touch.core.TouchRuntime;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

/**
 * Fabric 客户端入口（1.20.x）。
 * 只做两件事：每个客户端 tick 推进状态机；把 HUD 渲染器挂到 HudRenderCallback。
 * 不含任何服务端逻辑。
 */
public class JinSimTouchClient implements ClientModInitializer {

    public static final String MOD_ID = "jinsimtouch";

    @Override
    public void onInitializeClient() {
        TouchRuntime.init(FabricLoader.getInstance().getConfigDir().resolve(TouchConfig.FILE_NAME));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TouchRuntime runtime = TouchRuntime.get();
            if (runtime == null) return;
            runtime.tick(client.currentScreen != null);
        });

        HudRenderCallback.EVENT.register(TouchHudRenderer::render);
    }
}
