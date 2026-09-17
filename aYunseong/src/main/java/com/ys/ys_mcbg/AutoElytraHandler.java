package com.ys.ys_mcbg;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ysMcbgMod.MODID, value = Dist.CLIENT)
public class AutoElytraHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.connection == null) return;

        // 1. 겉날개 착용 여부 및 내구도 확인
        ItemStack chestItem = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chestItem.getItem() != Items.ELYTRA || !ElytraItem.isFlyEnabled(chestItem)) return;

        // 2. 활공 조건 검사
        if (player.isFallFlying() || player.onGround() || player.isInWater() || player.isPassenger()) return;

        // 3. 낙하 속도 감지 (살짝이라도 떨어지고 있을 때)
        if (player.getDeltaMovement().y < -0.1) {
            // 서버에 "활공 시작" 패킷 전송
            player.connection.send(new ServerboundPlayerCommandPacket(
                    player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
            ));
        }
    }
}