package dev.endermanclaude.minecartimprovements.mixin;

import dev.endermanclaude.minecartimprovements.SuperPoweredRailBlock;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * All cart changes run at the end of each tick, in order:
 * slope, speed boosts, jump launch, then a smoothing limit on acceleration.
 * Speeds are in blocks per tick (20 ticks per second).
 * Tunable constants. UNTESTED in game.
 */
@Mixin(AbstractMinecartEntity.class)
public abstract class AbstractMinecartMixin {
    // slope
    private static final double SLOPE_GAIN = 2.0D;
    private static final double SLOPE_MAX_DELTA = 0.25D;
    // speed
    private static final double FAST_CAP = 0.8D;      // normal rail top speed (16 b/s)
    private static final double FAST_BOOST = 0.03D;   // fractional gain per tick on normal rail
    private static final double SUPER_CAP = 2.0D;     // super powered rail top speed (40 b/s)
    private static final double SUPER_BOOST = 0.1D;   // blocks per tick added per tick on super rail
    // smoothing: largest horizontal speed increase allowed in one tick, on any rail
    private static final double MAX_ACCEL_STEP = 0.05D;
    // jumps
    private static final double GRAVITY = 0.04D;      // vertical acceleration per tick
    private static final double JUMP_MIN_SPEED = 0.3D;
    private static final double JUMP_MIN_VY = 0.15D;
    private static final double JUMP_MAX_VY = 0.7D;
    private static final int MAX_GAP = 4;

    private double minecartImprovements$lastHorizontal = 0.0D;

    @Inject(method = "tick", at = @At("TAIL"))
    private void minecartImprovements$tick(CallbackInfo ci) {
        AbstractMinecartEntity self = (AbstractMinecartEntity) (Object) this;
        World world = self.getEntityWorld();
        BlockPos pos = self.getBlockPos();
        BlockState under = world.getBlockState(pos);
        boolean onRail = AbstractRailBlock.isRail(under);
        boolean onSuper = under.getBlock() instanceof SuperPoweredRailBlock;

        Vec3d v = self.getVelocity();

        // 1. slope: downhill gains horizontal speed, uphill loses it
        double h = horizontal(v);
        if (h >= 1.0E-4D && v.y != 0.0D) {
            double delta = Math.max(-SLOPE_MAX_DELTA, Math.min(SLOPE_MAX_DELTA, -v.y * SLOPE_GAIN));
            v = withHorizontal(v, h * (1.0D + delta));
        }

        // 2. speed boosts while on rail
        h = horizontal(v);
        if (onRail && h >= 1.0E-4D) {
            if (onSuper && h < SUPER_CAP) {
                v = withHorizontal(v, Math.min(SUPER_CAP, h + SUPER_BOOST));
            } else if (!onSuper && h < FAST_CAP) {
                v = withHorizontal(v, Math.min(FAST_CAP, h * (1.0D + FAST_BOOST)));
            }
        }

        // 3. jump: on rail, fast, with a missing rail ahead. Launch height is set so the
        //    cart lands on the next rail at the current horizontal speed.
        h = horizontal(v);
        if (onRail && h >= JUMP_MIN_SPEED && Math.abs(v.y) < 0.05D) {
            int dx = (int) Math.round(v.x / h);
            int dz = (int) Math.round(v.z / h);
            if (!AbstractRailBlock.isRail(world.getBlockState(pos.add(dx, 0, dz)))) {
                int gap = -1;
                for (int k = 1; k <= MAX_GAP; k++) {
                    if (AbstractRailBlock.isRail(world.getBlockState(pos.add(dx * k, 0, dz * k)))) {
                        gap = k;
                        break;
                    }
                }
                if (gap > 0) {
                    double airTicks = gap / h;
                    double vy = GRAVITY * airTicks / 2.0D;
                    vy = Math.max(JUMP_MIN_VY, Math.min(JUMP_MAX_VY, vy));
                    v = new Vec3d(v.x, vy, v.z);
                }
            }
        }

        // 4. smoothing: cap how fast horizontal speed can rise in a single tick
        h = horizontal(v);
        double limit = minecartImprovements$lastHorizontal + MAX_ACCEL_STEP;
        if (h > limit) {
            v = withHorizontal(v, limit);
            h = limit;
        }

        self.setVelocity(v);
        minecartImprovements$lastHorizontal = h;
    }

    private static double horizontal(Vec3d v) {
        return Math.sqrt(v.x * v.x + v.z * v.z);
    }

    private static Vec3d withHorizontal(Vec3d v, double newH) {
        double h = horizontal(v);
        if (h < 1.0E-6D) {
            return v;
        }
        double f = newH / h;
        return new Vec3d(v.x * f, v.y, v.z * f);
    }
}
