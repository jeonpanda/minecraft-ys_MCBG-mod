package com.ys.ys_mcbg;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;

@Mod.EventBusSubscriber(
        modid = "ys_mcbg", // ← 너 mods.toml modId
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public class AutoCloseRecipeBook {

    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre e) {
        Screen screen = e.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?> s)) return;

        // 1) 레시피북 열려있으면 닫기
        RecipeBookComponent comp = findRecipeBookComponent(screen);
        if (comp != null && comp.isVisible()) {
            comp.toggleVisibility();
        }

        // 2) GUI 위치를 항상 "가운데"로 강제 (레시피북 때문에 밀리는 현상 제거)
        forceCenter(s);
    }

    private static void forceCenter(AbstractContainerScreen<?> s) {
        try {
            // AbstractContainerScreen: protected int leftPos, topPos, imageWidth, imageHeight
            Field leftPos = AbstractContainerScreen.class.getDeclaredField("leftPos");
            Field topPos = AbstractContainerScreen.class.getDeclaredField("topPos");
            Field imageWidth = AbstractContainerScreen.class.getDeclaredField("imageWidth");
            Field imageHeight = AbstractContainerScreen.class.getDeclaredField("imageHeight");

            leftPos.setAccessible(true);
            topPos.setAccessible(true);
            imageWidth.setAccessible(true);
            imageHeight.setAccessible(true);

            int iw = (int) imageWidth.get(s);
            int ih = (int) imageHeight.get(s);

            int w = s.width;
            int h = s.height;

            int newLeft = (w - iw) / 2;
            int newTop = (h - ih) / 2;

            leftPos.setInt(s, newLeft);
            topPos.setInt(s, newTop);
        } catch (Throwable ignored) {
            // 맵핑/필드명 차이로 실패할 수 있음 (아래 대안 코드로 해결 가능)
        }
    }

    private static RecipeBookComponent findRecipeBookComponent(Screen screen) {
        Class<?> c = screen.getClass();
        while (c != null) {
            for (Field f : c.getDeclaredFields()) {
                if (RecipeBookComponent.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        return (RecipeBookComponent) f.get(screen);
                    } catch (Throwable ignored) {}
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }
}
