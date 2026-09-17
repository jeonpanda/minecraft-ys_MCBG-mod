package com.ys.ys_mcbg;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.KeyMapping;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;


@Mod.EventBusSubscriber(modid = ysMcbgMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    public static KeyMapping MAP_KEY;
    public static KeyMapping MINIMAP_TOGGLE_KEY;

    public static KeyMapping pickupKey;

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new HudOverlayHandler());
        MinecraftForge.EVENT_BUS.register(new HotbarHandler());
        MinecraftForge.EVENT_BUS.register(pickupScreen.class);
        MinecraftForge.EVENT_BUS.register(new ClientReviveHoldHandler());
        MinecraftForge.EVENT_BUS.register(new ClientDownedPoseTick());
        MinecraftForge.EVENT_BUS.addListener(ClientModEvents::onRegisterCommands);

    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityType.ITEM, FlatItemEntityRenderer::new);
    }

    @SubscribeEvent
    public static void onKeyRegister(RegisterKeyMappingsEvent event) {
        pickupKey = new KeyMapping(
                "key.yspickup.get",
                InputConstants.KEY_G,
                "key.categories.yspickup"
        );
        MAP_KEY = new KeyMapping("key.ys_mcbg.open_map", GLFW.GLFW_KEY_M, "key.categories.ys_mcbg");
        event.register(MAP_KEY);

        MINIMAP_TOGGLE_KEY = new KeyMapping("key.ys_mcbg.toggle_minimap", GLFW.GLFW_KEY_N, "key.categories.ys_mcbg");
        event.register(MINIMAP_TOGGLE_KEY);

        event.register(pickupKey);
    }

    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        // targets 인자 제거 -> 누구나 자신의 화면만 조절 가능
        event.getDispatcher().register(Commands.literal("hotbar")
                .then(Commands.literal("x")
                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                .executes(context -> {
                                    int val = IntegerArgumentType.getInteger(context, "value");
                                    // 즉시 static 변수 수정
                                    HotbarHandler.offsetX = val;
                                    context.getSource().sendSuccess(() -> Component.literal("핫바 X 위치 설정됨: " + val), false);
                                    return 1;
                                })))
                .then(Commands.literal("y")
                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                .executes(context -> {
                                    int val = IntegerArgumentType.getInteger(context, "value");
                                    HotbarHandler.offsetY = val;
                                    context.getSource().sendSuccess(() -> Component.literal("핫바 Y 위치 설정됨: " + val), false);
                                    return 1;
                                })))
        );
    }
}