package com.ys.ys_mcbg;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class HotbarHandler {
    public static int offsetX = -10;
    public static int offsetY = -58;

    // 큰 슬롯(1~4) 직사각형 유지
    private static final int BIG_SLOT_W = 40;
    private static final int BIG_SLOT_H = 22;
    private static final int BIG_GAP_Y   = 26;

    // 작은 슬롯(5~9)
    private static final int SMALL_SLOT  = 20;
    private static final int SMALL_GAP_X = 22;

    // 큰 슬롯 아이템: 슬롯 높이에 거의 꽉 차게
    private static final float BIG_ICON_SCALE = 1.25f;

    // 배경(조금 더 투명)
    private static final int BG_NORMAL   = 0x60000000;
    private static final int BG_SELECTED = 0x70444444;

    @SubscribeEvent
    public void onRenderPre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderGuiPost(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) return;
        if (player.isSpectator()) return;

        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();

        int rightEdgeLimit = width - 10 + offsetX;
        int baseY = (height - 35) + offsetY;

        // 오른쪽 끝 정렬 기준(9번 오른쪽 끝 = 1~4 오른쪽 끝)
        int uiRight = rightEdgeLimit - 2;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);

        // =========================
        // 큰 슬롯 1~4 (세로)
        // =========================
        int yOfSlot4 = baseY - 80 + (3 * BIG_GAP_Y);

        for (int invIndex = 0; invIndex <= 3; invIndex++) {
            int x = uiRight - BIG_SLOT_W;
            int y = baseY - 80 + (invIndex * BIG_GAP_Y);

            boolean isSelected = (player.getInventory().selected == invIndex);
            int bgColor = isSelected ? BG_SELECTED : BG_NORMAL;

            graphics.fill(x, y, x + BIG_SLOT_W, y + BIG_SLOT_H, bgColor);

            if (isSelected) {
                graphics.renderOutline(x - 1, y - 1, BIG_SLOT_W + 2, BIG_SLOT_H + 2, 0xFFFFFFFF);
            }

            ItemStack stack = player.getInventory().items.get(invIndex);
            if (!stack.isEmpty()) {
                float s = BIG_ICON_SCALE;
                float iconSize = 16f * s; // 20px

                int itemX = Math.round(x + (BIG_SLOT_W - iconSize) / 2f);
                int itemY = Math.round(y + (BIG_SLOT_H - iconSize) / 2f);

                graphics.pose().pushPose();
                graphics.pose().translate(itemX, itemY, 0);
                graphics.pose().scale(s, s, 1f);
                graphics.renderItem(stack, 0, 0);
                graphics.pose().popPose();

                // ✅ 큰 슬롯(1~4) 개수/내구도 위치: "슬롯 안쪽 우하단"으로 고정
                // (이게 가장 자연스럽고, 지금 배치에선 아래 슬롯과도 안 겹침)
                int decoX = x + BIG_SLOT_W - 18;
                int decoY = y + BIG_SLOT_H - 16;
                graphics.renderItemDecorations(mc.font, stack, decoX, decoY);
            }

            // ✅ 1,2,3,4 숫자 라벨: 왼쪽 위
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);

            String label = String.valueOf(invIndex + 1);
            int labelColor = isSelected ? 0xFFFFFF : 0xAAAAAA;

            graphics.drawString(mc.font, label, x + 2, y + 2, labelColor, true);

            graphics.pose().popPose();
        }

        // =========================
        // 작은 슬롯 5~9 (가로)
        // 4번 슬롯 아래를 BIG_GAP_Y 리듬으로 맞춰 배치
        // =========================
        int desiredNextRowTop = yOfSlot4 + BIG_GAP_Y;
        int smallRowY = desiredNextRowTop + ((BIG_SLOT_H - SMALL_SLOT) / 2);

        for (int invIndex = 4; invIndex <= 8; invIndex++) {
            int p = invIndex - 4;     // 0..4
            int fromRight = 4 - p;    // 4..0

            int x = uiRight - SMALL_SLOT - (fromRight * SMALL_GAP_X);
            int y = smallRowY;

            boolean isSelected = (player.getInventory().selected == invIndex);
            int bgColor = isSelected ? BG_SELECTED : BG_NORMAL;

            graphics.fill(x, y, x + SMALL_SLOT, y + SMALL_SLOT, bgColor);

            if (isSelected) {
                graphics.renderOutline(x - 1, y - 1, SMALL_SLOT + 2, SMALL_SLOT + 2, 0xFFFFFFFF);
            }

            ItemStack stack = player.getInventory().items.get(invIndex);
            if (!stack.isEmpty()) {
                int itemX = x + 2;
                int itemY = y + (SMALL_SLOT - 16) / 2;
                graphics.renderItem(stack, itemX, itemY);
                graphics.renderItemDecorations(mc.font, stack, itemX, itemY);
            }

            // 숫자 라벨(작은 슬롯 좌상단)
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);

            String label = String.valueOf(invIndex + 1);
            int labelColor = isSelected ? 0xFFFFFF : 0xAAAAAA;

            graphics.drawString(mc.font, label, x + 2, y + 2, labelColor, true);

            graphics.pose().popPose();
        }

        graphics.pose().popPose();
    }
}
