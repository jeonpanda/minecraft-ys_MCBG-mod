package com.ys.ys_mcbg;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = ysMcbgMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ysMcbgMod.MODID);

    // ===== 가방 =====
    public static final RegistryObject<Item> BAG_LV1 =
            ITEMS.register("bag_lv1", () -> new BagItem(BagItem.Level.LV1, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BAG_LV2 =
            ITEMS.register("bag_lv2", () -> new BagItem(BagItem.Level.LV2, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BAG_LV3 =
            ITEMS.register("bag_lv3", () -> new BagItem(BagItem.Level.LV3, new Item.Properties().stacksTo(1)));

    // ===== 힐/부스트 아이템 =====
    // 붕대: 4초(80틱), 사용 후 2초(40틱)동안 총 10 회복, 스택 5
    public static final RegistryObject<Item> BANDAGE = ITEMS.register("bandage",
            () -> new BgConsumableItem(
                    BgConsumableItem.Kind.BANDAGE,
                    new Item.Properties().stacksTo(5),
                    80, 0f, 40
            ));

    // 구급상자: 6초(120틱), 사용 후 2초(40틱)동안 75%까지 회복, 스택 2
    public static final RegistryObject<Item> FIRST_AID = ITEMS.register("first_aid",
            () -> new BgConsumableItem(
                    BgConsumableItem.Kind.FIRST_AID,
                    new Item.Properties().stacksTo(2),
                    120, 0f, 40
            ));

    // 의료용 키트: 8초(160틱), 즉시 풀피, 스택 1
    public static final RegistryObject<Item> MED_KIT = ITEMS.register("med_kit",
            () -> new BgConsumableItem(
                    BgConsumableItem.Kind.MED_KIT,
                    new Item.Properties().stacksTo(1),
                    160, 0f, 0
            ));

    // 에너지드링크: 4초(80틱), +40%, 스택 3
    public static final RegistryObject<Item> ENERGY_DRINK = ITEMS.register("energy_drink",
            () -> new BgConsumableItem(
                    BgConsumableItem.Kind.ENERGY_DRINK,
                    new Item.Properties().stacksTo(3),
                    80, 0.40f, 0
            ));

    // 진통제: 6초(120틱), +60%, 스택 2
    public static final RegistryObject<Item> PAINKILLER = ITEMS.register("painkiller",
            () -> new BgConsumableItem(
                    BgConsumableItem.Kind.PAINKILLER,
                    new Item.Properties().stacksTo(2),
                    120, 0.60f, 0
            ));

    // 아드레날린: 6초(120틱), +100%, 스택 1
    public static final RegistryObject<Item> ADRENALINE = ITEMS.register("adrenaline",
            () -> new BgConsumableItem(
                    BgConsumableItem.Kind.ADRENALINE,
                    new Item.Properties().stacksTo(1),
                    120, 1.00f, 0
            ));

    // 크리에이티브 탭 표시(원하는 탭으로 바꿔도 됨)
    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            e.accept(BAG_LV1.get());
            e.accept(BAG_LV2.get());
            e.accept(BAG_LV3.get());

            e.accept(BANDAGE.get());
            e.accept(FIRST_AID.get());
            e.accept(MED_KIT.get());
            e.accept(ENERGY_DRINK.get());
            e.accept(PAINKILLER.get());
            e.accept(ADRENALINE.get());
        }
    }
}
