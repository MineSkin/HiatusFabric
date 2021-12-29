package org.mineskin.hiatus.fabric.mixin;

import net.minecraft.client.gui.screen.TitleScreen;
import org.mineskin.hiatus.fabric.HiatusFabric;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(at = @At("HEAD"), method = "init()V")
    private void init(CallbackInfo info) {
        if (HiatusFabric.launchTracked) return;
        HiatusFabric.getHiatus().onGameLaunching();
        HiatusFabric.launchTracked = true;
    }

}
