package dev.endermanclaude.minecartimprovements.mixin;

import net.minecraft.block.AbstractRailBlock;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Faster carts: while on rail, a cart speeds up each tick until it reaches FAST_CAP.
 * Blocks per tick: 0.8 = 16 blocks per second (vanilla is about 0.4).
 * Tunable constants.
 */
@Mixin(AbstractMinecartEntity.class)
public abstract class AbstractMinecartSpeedMixin {
    private static final double ACCEL = 0.03D;     // fractional speed gain per tick
    private static final double FAST_CAP = 0.8D;   // max horizontal speed, blocks per tick

    @Inject(method = "tick", at = @At("TAIL"))
    private void minecartImprovements$speedUp(CallbackInfo ci) {
        AbstractMinecartEntity self = (AbstractMinecartEntity) (Object) this;
        if (!AbstractRailBlock.isRail(self.getWorld().getBlockState(self.getBlockPos()))) {
            return;
        }
        Vec3d v = self.getVelocity();
        double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
        if (horizontal < 1.0E-4D || horizontal >= FAST_CAP) {
            return;
        }
        double factor = Math.min(1.0D + ACCEL, FAST_CAP / horizontal);
        self.setVelocity(v.x * factor, v.y, v.z * factor);
    }
}
