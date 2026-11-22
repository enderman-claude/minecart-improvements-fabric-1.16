package dev.endermanclaude.minecartimprovements.mixin;

import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the minecart speed cap. Vanilla 1.16.5 uses 0.4 blocks per tick (8 b/s).
 * UNTESTED: method name and signature come from Yarn 1.16.5+build.10 and must be verified at runtime.
 */
@Mixin(AbstractMinecartEntity.class)
public abstract class AbstractMinecartEntityMixin {
    private static final double MAX_SPEED = 0.8D; // 16 blocks per second

    @Inject(method = "getMaxSpeed", at = @At("HEAD"), cancellable = true)
    private void minecartImprovements$getMaxSpeed(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(MAX_SPEED);
    }
}
