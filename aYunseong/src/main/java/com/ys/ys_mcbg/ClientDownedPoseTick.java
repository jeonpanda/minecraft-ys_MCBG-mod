package com.ys.ys_mcbg;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ysMcbgMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientDownedPoseTick {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        for (Player p : level.players()) {
            boolean downed = ClientDownedCache.isDowned(p.getUUID());
            if (downed) {
                boolean changed = false;
                if (p.getPose() != Pose.SWIMMING) {
                    p.setPose(Pose.SWIMMING);
                    changed = true;
                }
                if (p.getForcedPose() != Pose.SWIMMING) {
                    p.setForcedPose(Pose.SWIMMING);
                    changed = true;
                }
                if (!p.isSwimming()) {
                    p.setSwimming(true);
                    changed = true;
                }
                if (changed) p.refreshDimensions();
            } else {
                // 일반 수영/포즈를 깨지 않도록 "강제 포즈"만 정리
                if (p.getForcedPose() == Pose.SWIMMING) {
                    p.setForcedPose(null);
                    p.setSwimming(false);
                    p.refreshDimensions();
                }
            }
        }
    }
}
