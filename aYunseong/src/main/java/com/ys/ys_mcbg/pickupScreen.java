package com.ys.ys_mcbg;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = ysMcbgMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class pickupScreen {
    private static int scrollOffset = 0;
    private static boolean isDraggingScrollBar = false;
    private static final int ITEM_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 7;

    private static String pickupMessage = "";
    private static int messageTimer = 0;
    private static ItemEntity pointedItem = null;

    public static void showPickupMessage(String itemName) {
        pickupMessage = itemName + " 획득";
        messageTimer = 60;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (mc.screen == null) {
            pointedItem = getTargetItem(mc, 5.0);

            if (ClientModEvents.pickupKey != null) {
                while (ClientModEvents.pickupKey.consumeClick()) {
                    if (pointedItem != null) {
                        showPickupMessage(pointedItem.getItem().getHoverName().getString());
                        ysMcbgMod.CHANNEL.sendToServer(new PickupPacket(pointedItem.getId(), false));
                    }
                }
            }
        } else {
            pointedItem = null;
        }
    }

    private static ItemEntity getTargetItem(Minecraft mc, double distance) {
        Entity camera = mc.getCameraEntity();
        if (camera == null) return null;

        Vec3 eyePos = camera.getEyePosition();
        Vec3 viewVec = camera.getViewVector(1.0F);
        Vec3 reachVec = eyePos.add(viewVec.x * distance, viewVec.y * distance, viewVec.z * distance);

        ItemEntity closestItem = null;
        double minDistanceSqr = Double.MAX_VALUE;

        List<Entity> entities = mc.level.getEntities(camera, camera.getBoundingBox().expandTowards(viewVec.scale(distance)).inflate(1.0D));

        for (Entity entity : entities) {
            if (entity instanceof ItemEntity item) {
                // 조준 판정 범위를 0.05로 더 좁혀서 아주 정확히 조준해야 선택되도록 함
                AABB box = entity.getBoundingBox().inflate(0.05D);
                if (box.clip(eyePos, reachVec).isPresent()) {
                    double distToPlayer = eyePos.distanceToSqr(entity.position());
                    if (distToPlayer < minDistanceSqr) {
                        minDistanceSqr = distToPlayer;
                        closestItem = item;
                    }
                }
            }
        }
        return closestItem;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (pointedItem == null || !pointedItem.isAlive()) return;

        Minecraft mc = Minecraft.getInstance();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();

        double x = Mth.lerp(event.getPartialTick(), pointedItem.xo, pointedItem.getX()) - cameraPos.x;
        double y = Mth.lerp(event.getPartialTick(), pointedItem.yo, pointedItem.getY()) - cameraPos.y;
        double z = Mth.lerp(event.getPartialTick(), pointedItem.zo, pointedItem.getZ()) - cameraPos.z;

        poseStack.pushPose();
        poseStack.translate(x, y, z);

        VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        float width, height, yOffset;
        ItemStack stack = pointedItem.getItem();

        // --- 테두리 크기 대폭 축소 ---
        if (stack.getItem() instanceof BlockItem) {
            // 블록 아이템: 실제 모델 크기(약 0.25)에 맞춰 축소
            width = 0.2f;
            height = 0.4f;
            yOffset = 0.0f;
        } else {
            // 도구 및 tacz 아이템: 바닥에 붙도록 더 낮게 설정
            width = 0.3f;
            height = 0.1f;
            yOffset = 0.02f;
        }

        // 색상: 순수 하얀색 (1.0, 1.0, 1.0)
        LevelRenderer.renderLineBox(poseStack, consumer,
                -width, yOffset, -width,
                width, yOffset + height, width,
                1.0F, 1.0F, 1.0F, 1.0F);

        poseStack.popPose();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator()) return;
        GuiGraphics graphics = event.getGuiGraphics();
        int centerX = mc.getWindow().getGuiScaledWidth() / 2;
        int centerY = mc.getWindow().getGuiScaledHeight() / 2;

        if (mc.screen == null && pointedItem != null && pointedItem.isAlive()) {
            String name = pointedItem.getItem().getHoverName().getString();
            int count = pointedItem.getItem().getCount();
            String display = name + (count > 1 ? " x" + count : "") + " [G]";
            graphics.drawString(mc.font, display, centerX + 10, centerY - 4, 0xFFFFFF, true);
        }

        if (messageTimer > 0) {
            int alpha = Math.min(255, messageTimer * 10);
            graphics.drawCenteredString(mc.font, pickupMessage, centerX, centerY + 50, (alpha << 24) | 0xFFAA00);
            messageTimer--;
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof InventoryScreen) {
            scrollOffset = 0;
            isDraggingScrollBar = false;
        }
    }

    // --- 나머지 인벤토리 및 마우스 로직 (기존 유지) ---
    private static List<ItemEntity> getFilteredItems(Minecraft mc) {
        if (mc.player == null) return new ArrayList<>();
        return mc.player.level().getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(3.5))
                .stream()
                .filter(item -> {
                    double yDiff = Math.abs(item.getY() - mc.player.getY());
                    if (yDiff >= 1.5) return false;
                    BlockHitResult raytrace = mc.player.level().clip(new ClipContext(
                            mc.player.getEyePosition(),
                            item.position().add(0, 0.1, 0),
                            ClipContext.Block.VISUAL,
                            ClipContext.Fluid.NONE,
                            mc.player
                    ));
                    return raytrace.getType() == HitResult.Type.MISS;
                })
                .collect(Collectors.toList());
    }

    @SubscribeEvent
    public static void onRenderInventory(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int left = (screen.width - 176) / 2;
        int top = (screen.height - 166) / 2;
        GuiGraphics graphics = event.getGuiGraphics();
        List<ItemEntity> items = getFilteredItems(mc);
        int totalItems = items.size();
        int boxLeft = left - 130;
        int boxRight = left - 10;
        int boxTop = top;
        int boxBottom = top + 175;
        graphics.fill(boxLeft, boxTop, boxRight, boxBottom, 0xAA000000);
        graphics.drawString(mc.font, "주변 아이템 (" + totalItems + ")", boxLeft + 5, boxTop + 5, 0xFFAA00);
        graphics.drawString(mc.font, "YS_MCBG_Mod(2.1.0)", boxLeft, boxBottom + 3, 0x808080);
        int yPos = boxTop + 20;
        for (int i = scrollOffset; i < items.size() && i < scrollOffset + VISIBLE_ROWS; i++) {
            ItemStack stack = items.get(i).getItem();
            int itemX = boxLeft + 5;
            graphics.renderFakeItem(stack, itemX, yPos);
            graphics.renderItemDecorations(mc.font, stack, itemX, yPos);
            String name = stack.getHoverName().getString();
            if (name.length() > 10) name = name.substring(0, 9) + "..";
            graphics.drawString(mc.font, name, itemX + 22, yPos + 4, 0xFFFFFF);
            if (event.getMouseX() >= boxLeft && event.getMouseX() <= boxRight - 10 && event.getMouseY() >= yPos && event.getMouseY() <= yPos + 18) {
                graphics.fill(boxLeft, yPos, boxRight - 10, yPos + 18, 0x33FFFFFF);
                List<Component> tooltip = new ArrayList<>();
                tooltip.addAll(stack.getTooltipLines(mc.player, TooltipFlag.Default.NORMAL));
                tooltip.add(Component.literal(""));
                tooltip.add(Component.literal("§b습득 : [우클릭]"));
                tooltip.add(Component.literal("§b장착 : [우클릭]"));
                graphics.renderComponentTooltip(mc.font, tooltip, (int)event.getMouseX(), (int)event.getMouseY());
            }
            yPos += ITEM_HEIGHT;
        }
        if (totalItems > VISIBLE_ROWS) {
            int scrollBarX = boxRight - 8;
            int scrollBarTop = boxTop + 20;
            int scrollBarBottom = boxBottom - 10;
            int scrollBarHeight = scrollBarBottom - scrollBarTop;
            int maxScroll = totalItems - VISIBLE_ROWS;
            float thumbHeight = Math.max(20, (float)VISIBLE_ROWS / totalItems * scrollBarHeight);
            float thumbY = scrollBarTop + (scrollOffset / (float)maxScroll) * (scrollBarHeight - thumbHeight);
            graphics.fill(scrollBarX, scrollBarTop, scrollBarX + 6, scrollBarBottom, 0xFF333333);
            graphics.fill(scrollBarX, (int)thumbY, scrollBarX + 6, (int)(thumbY + thumbHeight), 0xFFAAAAAA);
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (ClientModEvents.pickupKey != null && event.getKeyCode() == ClientModEvents.pickupKey.getKey().getValue()) {
            double mouseX = mc.mouseHandler.xpos() * (double)mc.getWindow().getGuiScaledWidth() / (double)mc.getWindow().getWidth();
            double mouseY = mc.mouseHandler.ypos() * (double)mc.getWindow().getGuiScaledHeight() / (double)mc.getWindow().getHeight();
            int left = (screen.width - 176) / 2;
            int top = (screen.height - 166) / 2;
            int boxLeft = left - 130;
            int boxRight = left - 10;
            int boxTop = top;
            if (mouseX >= boxLeft && mouseX <= boxRight - 10) {
                List<ItemEntity> items = getFilteredItems(mc);
                int clickedIdx = (int)((mouseY - (boxTop + 20)) / ITEM_HEIGHT) + scrollOffset;
                if (clickedIdx >= 0 && clickedIdx < items.size() && clickedIdx < scrollOffset + VISIBLE_ROWS) {
                    ItemEntity target = items.get(clickedIdx);
                    showPickupMessage(target.getItem().getHoverName().getString());
                    ysMcbgMod.CHANNEL.sendToServer(new PickupPacket(target.getId(), false));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int left = (screen.width - 176) / 2;
        int top = (screen.height - 166) / 2;
        int boxLeft = left - 130;
        int boxRight = left - 10;
        int boxTop = top;
        if (event.getMouseX() >= boxRight - 10 && event.getMouseX() <= boxRight && event.getMouseY() >= boxTop + 20 && event.getMouseY() <= top + 175 - 10) {
            isDraggingScrollBar = true;
            return;
        }
        if (event.getMouseX() < boxLeft || event.getMouseX() > boxRight - 10) return;
        List<ItemEntity> items = getFilteredItems(mc);
        int clickedIdx = (int)((event.getMouseY() - (boxTop + 20)) / ITEM_HEIGHT) + scrollOffset;
        if (clickedIdx >= 0 && clickedIdx < items.size() && clickedIdx < scrollOffset + VISIBLE_ROWS) {
            ItemEntity target = items.get(clickedIdx);
            showPickupMessage(target.getItem().getHoverName().getString());
            ysMcbgMod.CHANNEL.sendToServer(new PickupPacket(target.getId(), event.getButton() == 1));
        }
    }

    @SubscribeEvent
    public static void onMouseDrag(ScreenEvent.MouseDragged.Pre event) {
        if (isDraggingScrollBar) {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.screen instanceof InventoryScreen screen)) return;
            int top = (screen.height - 166) / 2;
            int scrollBarTop = top + 20;
            int scrollBarBottom = top + 175 - 10;
            int scrollBarHeight = scrollBarBottom - scrollBarTop;
            List<ItemEntity> items = getFilteredItems(mc);
            int maxScroll = Math.max(0, items.size() - VISIBLE_ROWS);
            double normalizedY = (event.getMouseY() - scrollBarTop) / (double)scrollBarHeight;
            scrollOffset = Mth.clamp((int) Math.round(normalizedY * maxScroll), 0, maxScroll);
        }
    }

    @SubscribeEvent
    public static void onMouseRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        isDraggingScrollBar = false;
    }

    @SubscribeEvent
    public static void onMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (Minecraft.getInstance().screen instanceof InventoryScreen) {
            List<ItemEntity> items = getFilteredItems(Minecraft.getInstance());
            int maxScroll = Math.max(0, items.size() - VISIBLE_ROWS);
            scrollOffset = Mth.clamp(scrollOffset + (event.getScrollDelta() > 0 ? -1 : 1), 0, maxScroll);
        }
    }
}