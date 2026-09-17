package com.ys.ys_mcbg;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ysMcbgMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientInventoryLockOverlay {

    private enum BagLevel { NONE, LV1, LV2, LV3 }

    private static BagLevel getBagLevel(Player player) {
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (boots.isEmpty()) return BagLevel.NONE;

        if (boots.is(ModItems.BAG_LV3.get())) return BagLevel.LV3;
        if (boots.is(ModItems.BAG_LV2.get())) return BagLevel.LV2;
        if (boots.is(ModItems.BAG_LV1.get())) return BagLevel.LV1;
        return BagLevel.NONE;
    }

    // 서버 BlockTopInventoryRows와 동일 규칙
    private static boolean isBlockedIndex(int idx, BagLevel level) {
        // 핫바(0~8)는 항상 허용
        if (idx >= 0 && idx <= 8) return false;

        // 메인(9~35)만 처리
        if (idx < 9 || idx > 35) return false;

        return switch (level) {
            case NONE -> true;                    // 9~35 전부 봉인
            case LV1  -> (idx >= 9 && idx <= 26); // 윗2줄 봉인
            case LV2  -> (idx >= 9 && idx <= 17); // 윗1줄 봉인
            case LV3  -> false;                   // 전부 허용
        };
    }

    private static boolean isLockedPlayerInvSlot(Slot slot, BagLevel level) {
        if (!(slot.container instanceof Inventory)) return false; // 플레이어 인벤 슬롯만
        int invIndex = slot.getSlotIndex(); // 0~35
        return isBlockedIndex(invIndex, level);
    }

    // =========================
    // 1) 잠긴 슬롯 클릭 무시 + 띡 소리
    // =========================
    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre e) {
        if (!(e.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        BagLevel level = getBagLevel(player);

        Slot hovered = screen.getSlotUnderMouse();
        if (hovered == null) return;

        if (isLockedPlayerInvSlot(hovered, level)) {
            // 클릭 무시
            e.setCanceled(true);

            // 띡 소리 (클라 로컬)
            player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.6f, 1.2f);
        }
    }

    // =========================
    // 2) 잠긴 슬롯 표시(오버레이 + X)
    // =========================
    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        BagLevel level = getBagLevel(player);

        for (Slot slot : screen.getMenu().slots) {
            if (!isLockedPlayerInvSlot(slot, level)) continue;

            int x = screen.getGuiLeft() + slot.x;
            int y = screen.getGuiTop() + slot.y;

            // 슬롯 덮개(반투명 진회색)
            int fill = 0x90000000;     // 0xAA RR GG BB (AA=0x90 정도)
            int border = 0xB0303030;   // 테두리

            // 16x16
            e.getGuiGraphics().fill(x, y, x + 16, y + 16, fill);

            // 테두리 1px
            e.getGuiGraphics().fill(x, y, x + 16, y + 1, border);
            e.getGuiGraphics().fill(x, y + 15, x + 16, y + 16, border);
            e.getGuiGraphics().fill(x, y, x + 1, y + 16, border);
            e.getGuiGraphics().fill(x + 15, y, x + 16, y + 16, border);

            // "X" 표시 (또렷한 대각선 2줄)
            int xColor = 0xD0B0B0B0; // 붉은색 (원하면 회색으로 바꿔도 됨)

            // 대각선 2개를 1px 라인으로 찍기 (안티앨리어싱 없이 픽셀 단위)
            for (int i = 3; i <= 12; i++) {
                // \ 방향
                e.getGuiGraphics().fill(x + i, y + i, x + i + 1, y + i + 1, xColor);
                // / 방향
                e.getGuiGraphics().fill(x + i, y + (15 - i), x + i + 1, y + (15 - i) + 1, xColor);
            }

            // X를 조금 더 굵게(가독성 ↑): 한 픽셀 옆도 같이
            for (int i = 3; i <= 12; i++) {
                e.getGuiGraphics().fill(x + i + 1, y + i, x + i + 2, y + i + 1, xColor);
                e.getGuiGraphics().fill(x + i + 1, y + (15 - i), x + i + 2, y + (15 - i) + 1, xColor);
            }
        }
    }
}
