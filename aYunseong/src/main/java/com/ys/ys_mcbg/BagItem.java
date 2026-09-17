package com.ys.ys_mcbg;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

public class BagItem extends ArmorItem {

    public enum Level { LV1, LV2, LV3 }

    private final Level level;

    public BagItem(Level level, Properties props) {
        super(BagArmorMaterial.BAG, Type.BOOTS, props); // BOOTS = FEET 슬롯
        this.level = level;
    }

    public Level getLevel() {
        return level;
    }

    // 착용해도 "겉에 안 보이게" (클라이언트에서 아머 모델 숨김)
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack,
                                                          EquipmentSlot slot, HumanoidModel<?> original) {
                original.head.visible = false;
                original.hat.visible = false;
                original.body.visible = false;
                original.rightArm.visible = false;
                original.leftArm.visible = false;
                original.rightLeg.visible = false;
                original.leftLeg.visible = false;
                return original;
            }
        });
    }
}
