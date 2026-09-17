package com.ys.ys_mcbg;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ClientReviveHoldHandler {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return; // GUI 열려있으면 무시

        // 우클릭(Use) 홀드 감지
        if (!mc.options.keyUse.isDown()) return;

        HitResult hr = mc.hitResult;
        if (!(hr instanceof EntityHitResult ehr)) return;
        if (!(ehr.getEntity() instanceof Player target)) return;

        // 서버에 "살리기 틱" 전송(서버가 다운/팀/거리 체크)
        ysMcbgMod.CHANNEL.sendToServer(new ReviveTickPacket(target.getUUID()));
    }
}
