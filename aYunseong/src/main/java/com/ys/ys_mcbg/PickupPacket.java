package com.ys.ys_mcbg;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PickupPacket {
    private final int entityId;
    private final boolean isRightClick;

    public PickupPacket(int entityId, boolean isRightClick) {
        this.entityId = entityId;
        this.isRightClick = isRightClick;
    }

    public PickupPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.isRightClick = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeBoolean(isRightClick);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            Entity entity = player.level().getEntity(entityId);
            if (!(entity instanceof ItemEntity itemEntity)) return;

            ItemStack stack = itemEntity.getItem();
            if (isRightClick && stack.getItem() instanceof ArmorItem armor) {
                EquipmentSlot slot = armor.getEquipmentSlot();
                if (player.getItemBySlot(slot).isEmpty()) {
                    player.setItemSlot(slot, stack.copy());
                    itemEntity.discard();
                    ysMcbgMod.playPickupSound(player);
                    return;
                }
            }
            // 좌클릭은 기존처럼 일반 인벤토리 획득으로 처리하고,
            // 우클릭은 장착 가능한 아이템이 아니면 일반 인벤토리로 획득한다.
            // ArmorItem(BagItem 포함)은 위에서 장착을 먼저 시도하므로 기존 동작을 유지한다.
            if (!isRightClick || !(stack.getItem() instanceof ArmorItem)) {
                if (player.getInventory().add(stack)) {
                    itemEntity.discard();
                    ysMcbgMod.playPickupSound(player);
                }
            }
        });
        return true;
    }
}