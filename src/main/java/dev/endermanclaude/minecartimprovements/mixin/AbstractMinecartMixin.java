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
 * Cart changes.
 * HEAD of tick: while airborne, this mod moves the cart itself (gravity, then landing),
 * so vanilla rail snapping cannot pull it back down into the block below.
 * TAIL of tick, when on rail: slope, speed boosts, jump launch, then smoothing.
 * Speeds are in blocks per tick (20 ticks per second). Tunable constants. UNTESTED in game.
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
    // jumps and flight
    private static final double GRAVITY = 0.04D;      // vertical acceleration per tick
    private static final double JUMP_MIN_SPEED = 0.3D;
    private static final double JUMP_MIN_VY = 0.15D;
    private static final double JUMP_MAX_VY = 0.7D;
    private static final int MAX_GAP = 4;
    private static final int MAX_AIR_TICKS = 200;

    private double minecartImprovements$lastHorizontal = 0.0D;
    private boolean minecartImprovements$airborne = false;
    private int minecartImprovements$airTicks = 0;
    private BlockPos minecartImprovements$launchPos = null;

    /** While airborne, move the cart ourselves and skip vanilla rail logic for this tick. */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void minecartImprovements$flight(CallbackInfo ci) {
        if (!minecartImprovements$airborne) {
            return;
        }
        AbstractMinecartEntity self = (AbstractMinecartEntity) (Object) this;
        World world = self.getEntityWorld();

        Vec3d v = self.getVelocity();
        Vec3d nv = new Vec3d(v.x, v.y - GRAVITY, v.z);
        self.setPos(self.getX() + nv.x, self.getY() + nv.y, self.getZ() + nv.z);
        self.setVelocity(nv);
        minecartImprovements$airTicks++;

        BlockPos p = self.getBlockPos();
        boolean landed = nv.y < 0.0D
                && !p.equals(minecartImprovements$launchPos)
                && AbstractRailBlock.isRail(world.getBlockState(p))
                && self.getY() <= p.getY() + 0.5D;
        if (landed) {
            self.setPos(self.getX(), p.getY() + 0.0625D, self.getZ());
            self.setVelocity(new Vec3d(nv.x, 0.0D, nv.z));
            minecartImprovements$airborne = false;
        } else if (minecartImprovements$airTicks > MAX_AIR_TICKS) {
            minecartImprovements$airborne = false;
        }
        ci.cancel();
    }

    /** Rail logic runs after vanilla moveOnRail(BlockPos, BlockState) (Yarn 1.16.5 name and descriptor). */
    @Inject(method = "moveOnRail(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V", at = @At("TAIL"))
    private void minecartImprovements$onRail(BlockPos pos, BlockState under, CallbackInfo ci) {
        if (minecartImprovements$airborne) {
            return;
        }
        AbstractMinecartEntity self = (AbstractMinecartEntity) (Object) this;
        World world = self.getEntityWorld();
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

        // 3. jump: fast on rail, with no rail ahead on the same level. Start flight and size
        //    the launch so the cart reaches the next rail at its current horizontal speed.
        h = horizontal(v);
        if (onRail && h >= JUMP_MIN_SPEED && Math.abs(v.y) < 0.05D) {
            int dx = (int) Math.round(v.x / h);
            int dz = (int) Math.round(v.z / h);
            if (!isRailAt(world, pos.add(dx, 0, dz))) {
                int gap = -1;
                for (int k = 1; k <= MAX_GAP; k++) {
                    if (isRailAt(world, pos.add(dx * k, 0, dz * k))) {
                        gap = k;
                        break;
                    }
                }
                if (gap > 0) {
                    double airTicks = gap / h;
                    double vy = GRAVITY * airTicks / 2.0D;
                    vy = Math.max(JUMP_MIN_VY, Math.min(JUMP_MAX_VY, vy));
                    v = new Vec3d(v.x, vy, v.z);
                    minecartImprovements$airborne = true;
                    minecartImprovements$airTicks = 0;
                    minecartImprovements$launchPos = pos;
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

    /** A rail on this block or one level above or below, so ramps count as rail. */
    private static boolean isRailAt(World world, BlockPos p) {
        return AbstractRailBlock.isRail(world.getBlockState(p))
                || AbstractRailBlock.isRail(world.getBlockState(p.up()))
                || AbstractRailBlock.isRail(world.getBlockState(p.down()));
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
