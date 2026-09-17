package com.ys.ys_mcbg;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TACZ와 같은 총기 모드의 발사를 막기 위해
 * 클라이언트 단에서 마우스/키보드 입력을 직접 차단합니다.
 */
@Mod.EventBusSubscriber(modid = ysMcbgMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class DownBlockInteractions {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (isLocalPlayerDowned()) {
            // 이벤트를 취소하면 TACZ가 클릭을 인식하지 못합니다.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyInput(InputEvent.Key event) {
        if (isLocalPlayerDowned()) {
            // 장전(R)이나 발사 모드 변경 등 키보드 입력도 차단합니다.
            event.setCanceled(true);
        }
    }

    private static boolean isLocalPlayerDowned() {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;

        // 기존에 사용하시던 BgPlayerState.isDowned를 그대로 활용합니다.
        return BgPlayerState.isDowned(player);
    }
}