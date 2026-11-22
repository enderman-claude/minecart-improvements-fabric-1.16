package dev.endermanclaude.minecartimprovements.mixin;

import net.minecraft.block.AbstractRailBlock;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Jumps: a fast cart on rail that sees a missing rail one block ahead launches upward.
 * Landing is left to vanilla rail behavior, so cart may overshoot on long gaps.
 * Tunable constants.
 */
@Mixin(AbstractMinecartEntity.class)
public abstract class AbstractMinecartJumpMixin {
    private static final double JUMP_MIN_SPEED = 0.45D; // horizontal speed needed to jump, blocks per tick
    private static final double JUMP_VELOCITY = 0.2D;   // upward launch speed, blocks per tick

    @Inject(method = "tick", at = @At("TAIL"))
    private void minecartImprovements$jump(CallbackInfo ci) {
        AbstractMinecartEntity self = (AbstractMinecartEntity) (Object) this;
        BlockPos pos = self.getBlockPos();
        if (!AbstractRailBlock.isRail(self.getWorld().getBlockState(pos))) {
            return;
        }
        Vec3d v = self.getVelocity();
        double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
        if (horizontal < JUMP_MIN_SPEED || Math.abs(v.y) > 0.05D) {
            return; // too slow, or already airborne
        }
        int dx = (int) Math.round(v.x / horizontal);
        int dz = (int) Math.round(v.z / horizontal);
        BlockPos ahead = pos.add(dx, 0, dz);
        if (!AbstractRailBlock.isRail(self.getWorld().getBlockState(ahead))) {
            self.setVelocity(v.x, JUMP_VELOCITY, v.z);
        }
    }
}
