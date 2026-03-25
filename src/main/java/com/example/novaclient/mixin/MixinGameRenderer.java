package com.example.novaclient.mixin;

import com.example.novaclient.NovaClient;
import com.example.novaclient.event.events.RenderWorldEvent;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    
    @Inject(method = "renderWorld", at = @At("TAIL"))
    private void onRenderWorld(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {
        RenderWorldEvent event = new RenderWorldEvent(matrices, tickDelta);
        NovaClient.getInstance().getEventBus().post(event);
    }
}