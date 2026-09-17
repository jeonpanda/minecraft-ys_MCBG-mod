package com.ys.ys_mcbg;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "ys_mcbg", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BlockTopInventoryRows {

    /*
     * 인벤 인덱스:
     * 0~8   : 핫바
     * 9~35  : 메인(3줄)
     *  9~17  : 위 1줄
     * 18~26  : 위 2줄(가운데 줄)
     * 27~35  : 아래 1줄
     *
     * 규칙:
     * - 가방 없음 : 핫바만 허용 => 9~35 봉인
     * - Lv1 : 핫바 + 27~35 허용 => 9~26 봉인
     * - Lv2 : 핫바 + 18~35 허용 => 9~17 봉인
     * - Lv3 : 핫바 + 9~35 허용 => 봉인 없음
     */

    private enum BagLevel { NONE, LV1, LV2, LV3 }

    private static BagLevel getBagLevel(Player player) {
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (boots.isEmpty()) return BagLevel.NONE;

        if (boots.is(ModItems.BAG_LV3.get())) return BagLevel.LV3;
        if (boots.is(ModItems.BAG_LV2.get())) return BagLevel.LV2;
        if (boots.is(ModItems.BAG_LV1.get())) return BagLevel.LV1;
        return BagLevel.NONE;
    }

    private static boolean isBlockedIndex(int idx, BagLevel level) {
        // 핫바는 항상 허용
        if (idx >= 0 && idx <= 8) return false;

        // 메인 인벤만 처리
        if (idx < 9 || idx > 35) return false;

        return switch (level) {
            case NONE -> true;                    // 9~35 전부 봉인
            case LV1  -> (idx >= 9 && idx <= 26); // 윗2줄 봉인
            case LV2  -> (idx >= 9 && idx <= 17); // 윗1줄 봉인
            case LV3  -> false;                   // 전부 허용
        };
    }


    /** 현재 프로젝트의 실제 규칙 기준 사용 가능 슬롯 수. */
    public static int getUsableSlotCount(Player player) {
        return switch (getBagLevel(player)) {
            case NONE -> 9;
            case LV1 -> 18;
            case LV2 -> 27;
            case LV3 -> 36;
        };
    }

    /** 현재 프로젝트에서 실제로 열려 있는 메인 인벤토리의 첫 인덱스. */
    public static int getAllowedMainStart(Player player) {
        return switch (getBagLevel(player)) {
            case NONE -> 36;
            case LV1 -> 27;
            case LV2 -> 18;
            case LV3 -> 9;
        };
    }

    private static void moveIntoAllowedOrDrop(Player player, Inventory inv, ItemStack moving, BagLevel level) {
        if (moving.isEmpty()) return;

        // 허용 범위 우선순위: (메인 허용 줄) -> 핫바
        switch (level) {
            case LV3 -> {
                // 전부 허용이면 사실 봉인 슬롯이 없어야 함. 안전용.
                moveIntoRange(inv, moving, 9, 35);
                if (!moving.isEmpty()) moveIntoRange(inv, moving, 0, 8);
            }
            case LV2 -> {
                moveIntoRange(inv, moving, 18, 35);
                if (!moving.isEmpty()) moveIntoRange(inv, moving, 0, 8);
            }
            case LV1 -> {
                moveIntoRange(inv, moving, 27, 35);
                if (!moving.isEmpty()) moveIntoRange(inv, moving, 0, 8);
            }
            case NONE -> {
                moveIntoRange(inv, moving, 0, 8);
            }
        }

        // 허용 슬롯 부족하면 드랍
        if (!moving.isEmpty()) {
            player.drop(moving, false);
        }
    }

    private static void moveIntoRange(Inventory inv, ItemStack moving, int start, int end) {
        if (moving.isEmpty()) return;

        // (A) 같은 아이템/태그에 먼저 합치기
        for (int i = start; i <= end; i++) {
            ItemStack target = inv.items.get(i);
            if (target.isEmpty()) continue;

            if (ItemStack.isSameItemSameTags(target, moving)) {
                int max = Math.min(target.getMaxStackSize(), inv.getMaxStackSize());
                int canMove = Math.min(moving.getCount(), max - target.getCount());
                if (canMove > 0) {
                    target.grow(canMove);
                    moving.shrink(canMove);
                    if (moving.isEmpty()) return;
                }
            }
        }

        // (B) 빈칸에 넣기
        for (int i = start; i <= end; i++) {
            ItemStack target = inv.items.get(i);
            if (target.isEmpty()) {
                inv.items.set(i, moving.copy());
                moving.setCount(0);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        Player player = e.player;
        if (player.level().isClientSide) return; // 서버에서만 강제

        Inventory inv = player.getInventory();
        BagLevel level = getBagLevel(player);

        // 봉인 슬롯에 들어간 아이템은 "허용 슬롯으로 이동", 부족하면 드랍
        for (int idx = 9; idx <= 35; idx++) {
            if (!isBlockedIndex(idx, level)) continue;

            ItemStack stack = inv.items.get(idx);
            if (stack.isEmpty()) continue;

            ItemStack moving = stack.copy();
            inv.items.set(idx, ItemStack.EMPTY);

            moveIntoAllowedOrDrop(player, inv, moving, level);
        }
    }
}
