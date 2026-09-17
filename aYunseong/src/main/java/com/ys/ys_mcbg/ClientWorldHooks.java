package com.ys.ys_mcbg;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ysMcbgMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientWorldHooks {

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load e) {
        if (!e.getLevel().isClientSide()) return;
        // 새 월드 들어갈 때 이전 맵 캐시 제거
        ClientMapCache.clearAll();
        ClientTeamCache.clear();
        ClientTeamMarkerCache.clear();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload e) {
        if (!e.getLevel().isClientSide()) return;
        // 월드 나갈 때도 제거
        ClientMapCache.clearAll();
        ClientTeamCache.clear();
        ClientTeamMarkerCache.clear();
    }
}
