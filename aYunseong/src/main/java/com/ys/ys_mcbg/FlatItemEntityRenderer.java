package com.ys.ys_mcbg;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class FlatItemEntityRenderer extends EntityRenderer<ItemEntity> {

    // TacZ 모드의 부착물인지 확인하는 태그 (없어도 오류는 안 납니다)
    private static final TagKey<Item> TACZ_ATTACHMENT_TAG =
            TagKey.create(Registries.ITEM, new ResourceLocation("tacz", "attachment"));

    private final ItemRenderer itemRenderer;

    public FlatItemEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
        this.shadowRadius = 0.0F; // 그림자 제거
    }

    @Override
    public void render(ItemEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) return;

        poseStack.pushPose();

        // 1. 위치 조정: 카펫 높이(0.0625) + 0.1 = 0.1625D 만큼 띄움
        // 이렇게 하면 카펫 위에 떨어져도 파묻히지 않고 공중에 살짝 뜬 느낌이 납니다.
        poseStack.translate(0.0D, 0.1625D, 0.0D);

        // 2. 크기 조정 로직
        boolean isAttachment = stack.getItem().builtInRegistryHolder().is(TACZ_ATTACHMENT_TAG)
                || stack.getItem().toString().toLowerCase().contains("attachment");

        float scale = isAttachment ? 1.6F : 0.7F;
        poseStack.scale(scale, scale, scale);

        // 3. 랜덤 회전 (엔티티 ID 기반 고정 회전)
        float randomRotation = (float)(entity.getId() * 31) % 360.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(randomRotation));

        // 4. 평평하게 눕히기 (X축 90도 회전)
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));

        // 5. 렌더링 실행
        int brightLight = 15728880; // 풀 브라이트 유지

        this.itemRenderer.renderStatic(
                stack,
                ItemDisplayContext.FIXED,
                brightLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                entity.getId()
        );

        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(ItemEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}