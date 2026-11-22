package dev.endermanclaude.minecartimprovements.mixin;

import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Slope and gravity effects: a cart moving downhill (negative vertical velocity)
 * gains horizontal speed, and a cart climbing uphill loses it.
 * Runs after vanilla tick logic. Constants are tunable.
 * UNTESTED: relies on Yarn names Entity#getVelocity, Entity#setVelocity and AbstractMinecartEntity#tick.
 */
@Mixin(AbstractMinecartEntity.class)
public abstract class AbstractMinecartSlopeMixin {
    private static final double SLOPE_GAIN = 2.0D;   // speed factor change per unit of vertical velocity
    private static final double SLOPE_MAX_DELTA = 0.25D; // cap on the per-tick speed factor change

    @Inject(method = "tick", at = @At("TAIL"))
    private void minecartImprovements$applySlope(CallbackInfo ci) {
        AbstractMinecartEntity self = (AbstractMinecartEntity) (Object) this;
        Vec3d v = self.getVelocity();
        double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
        if (horizontal < 1.0E-4D || v.y == 0.0D) {
            return;
        }
        double delta = -v.y * SLOPE_GAIN;
        delta = Math.max(-SLOPE_MAX_DELTA, Math.min(SLOPE_MAX_DELTA, delta));
        double factor = 1.0D + delta;
        self.setVelocity(v.x * factor, v.y, v.z * factor);
    }
}
