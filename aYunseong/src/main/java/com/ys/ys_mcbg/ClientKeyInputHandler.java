package com.ys.ys_mcbg;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles client keybindings on the FORGE bus (runtime).
 * - N: toggles minimap zoom (changes visible radius, not just UI size)
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = ysMcbgMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientKeyInputHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getInstance();
        // F(기본값) / 사용자가 변경한 "왼손 아이템 바꾸기" 키를 정리 트리거로 사용한다.
        // consumeClick()을 이 시점에 소비해서 바닐라 오프핸드 스왑이 실행되지 않게 한다.
        if (mc.options != null && mc.player != null && mc.options.keySwapOffhand.consumeClick()) {
            ysMcbgMod.CHANNEL.sendToServer(new AutoInventorySortPacket());
        }

        // Key mappings are registered on MOD bus, but clicks must be consumed on FORGE bus.
        if (ClientModEvents.MINIMAP_TOGGLE_KEY != null && ClientModEvents.MINIMAP_TOGGLE_KEY.consumeClick()) {
            MinimapRenderer.toggleZoom();
        }
    }
}
