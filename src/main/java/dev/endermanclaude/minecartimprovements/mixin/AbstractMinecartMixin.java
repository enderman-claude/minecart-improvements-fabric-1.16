package dev.endermanclaude.minecartimprovements.mixin;

import dev.endermanclaude.minecartimprovements.SuperPoweredRailBlock;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.RailShape;
import net.minecraft.util.math.MathHelper;
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
    // speed
    private static final double FAST_CAP = 0.8D;      // normal rail top speed (16 b/s)
    private static final double FAST_BOOST = 0.10D;   // fractional gain per tick on normal rail (vanilla loses about 4% per tick)
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
    private static final double FORCE_LAUNCH_VY = 0.5D; // launch speed for the upward single-rail rule
    private static final int MAX_AIR_TICKS = 200;
    private static final int MAX_WALK = 6; // rails to follow along the track

    private double minecartImprovements$lastHorizontal = 0.0D;
    private boolean minecartImprovements$airborne = false;
    private int minecartImprovements$airTicks = 0;
    private BlockPos minecartImprovements$launchPos = null;
    private int minecartImprovements$debugTicks = 0;

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
        double nx = self.getX() + nv.x;
        double ny = self.getY() + nv.y;
        double nz = self.getZ() + nv.z;

        // Ground collision: if the block the cart would be inside is solid, stop on top of it.
        BlockPos feet = new BlockPos(MathHelper.floor(nx), MathHelper.floor(ny), MathHelper.floor(nz));
        if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()) {
            self.setPos(nx, feet.getY() + 1.0D, nz);
            self.setVelocity(new Vec3d(nv.x, 0.0D, nv.z));
            minecartImprovements$airborne = false;
            System.out.println("[minecart-improvements] flight ended on solid ground");
            ci.cancel();
            return;
        }

        self.setPos(nx, ny, nz);
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

        // 1. slope: energy-conserving. Going down converts height into horizontal speed,
        //    going up converts speed back into height. A loop cannot create speed this way.
        double h = horizontal(v);
        if (h >= 1.0E-4D && v.y != 0.0D) {
            double h2 = h * h;
            if (v.y < 0.0D) {
                h2 += 2.0D * GRAVITY * -v.y;
            } else {
                h2 = Math.max(0.0D, h2 - 2.0D * GRAVITY * v.y);
            }
            v = withHorizontal(v, Math.sqrt(h2));
        }

        // 2. speed boosts while on rail, capped. The cap is checked after the slope step too,
        //    so boosts never push a cart past its cap.
        h = horizontal(v);
        if (onRail && h >= 1.0E-4D) {
            if (onSuper && h < SUPER_CAP) {
                v = withHorizontal(v, Math.min(SUPER_CAP, h + SUPER_BOOST));
            } else if (!onSuper && h < FAST_CAP) {
                v = withHorizontal(v, Math.min(FAST_CAP, h * (1.0D + FAST_BOOST)));
            }
        }
        h = horizontal(v);
        if (!onSuper && h > FAST_CAP) {
            v = withHorizontal(v, FAST_CAP);
        } else if (onSuper && h > SUPER_CAP) {
            v = withHorizontal(v, SUPER_CAP);
        }

        // 3a. rule: on an upward rail, with exactly one rail ahead along the connected track
        //     and nothing after it, force launch.
        h = horizontal(v);
        if (onRail && h >= JUMP_MIN_SPEED && Math.abs(v.y) < 0.05D) {
            if (isUpwardSingleRail(world, pos, v)) {
                v = new Vec3d(v.x, FORCE_LAUNCH_VY, v.z);
                minecartImprovements$airborne = true;
                minecartImprovements$airTicks = 0;
                minecartImprovements$launchPos = pos;
                System.out.println(String.format(
                        "[minecart-improvements] FORCE LAUNCH upward single rail, speed=%.3f", h));
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
                    System.out.println(String.format(
                            "[minecart-improvements] JUMP gap=%d speed=%.3f vy=%.3f", gap, h, vy));
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

        // Debug: report what the mod sees, once per second per cart.
        if (++minecartImprovements$debugTicks % 20 == 0) {
            System.out.println(String.format(
                    "[minecart-improvements] onRail=%s super=%s speed=%.3f blocks/tick (%.1f b/s) vy=%.3f airborne=%s",
                    onRail, onSuper, h, h * 20.0D, v.y, minecartImprovements$airborne));
        }
    }

    /** A rail on this block or one level above or below, so ramps count as rail. */
    private static boolean isRailAt(World world, BlockPos p) {
        return AbstractRailBlock.isRail(world.getBlockState(p))
                || AbstractRailBlock.isRail(world.getBlockState(p.up()))
                || AbstractRailBlock.isRail(world.getBlockState(p.down()));
    }

    /** Connected-track detection. Returns true for an upward rail with exactly one connected rail ahead. */
    private static boolean isUpwardSingleRail(World world, BlockPos pos, Vec3d v) {
        RailShape here = shapeAt(world, pos);
        if (here == null) {
            return false;
        }
        int[][] dirs = directionsOf(here);
        int[] forward = null;
        double best = 0.0D;
        for (int[] d : dirs) {
            double dot = d[0] * v.x + d[1] * v.z;
            if (dot > best) {
                best = dot;
                forward = d;
            }
        }
        if (forward == null) {
            return false;
        }
        BlockPos ahead = findRailNear(world, pos.add(forward[0], 0, forward[1]));
        if (ahead == null) {
            return false;
        }
        boolean upward = ahead.getY() > pos.getY();

        int count = 1;
        BlockPos cur = ahead;
        int[] back = {-forward[0], -forward[1]};
        for (int i = 0; i < MAX_WALK; i++) {
            RailShape s = shapeAt(world, cur);
            if (s == null) {
                break;
            }
            int[] next = null;
            for (int[] d : directionsOf(s)) {
                if (d[0] != back[0] || d[1] != back[1]) {
                    next = d;
                }
            }
            if (next == null) {
                break;
            }
            BlockPos after = findRailNear(world, cur.add(next[0], 0, next[1]));
            if (after == null) {
                break;
            }
            count++;
            back = new int[] {-next[0], -next[1]};
            cur = after;
        }
        return upward && count == 1;
    }

    /** The two horizontal directions (x, z) a rail shape connects to. */
    private static int[][] directionsOf(RailShape shape) {
        final int[] n = {0, -1};
        final int[] s = {0, 1};
        final int[] e = {1, 0};
        final int[] w = {-1, 0};
        switch (shape) {
            case NORTH_SOUTH:
            case ASCENDING_NORTH:
            case ASCENDING_SOUTH:
                return new int[][] {n, s};
            case EAST_WEST:
            case ASCENDING_EAST:
            case ASCENDING_WEST:
                return new int[][] {e, w};
            case SOUTH_EAST:
                return new int[][] {s, e};
            case SOUTH_WEST:
                return new int[][] {s, w};
            case NORTH_WEST:
                return new int[][] {n, w};
            case NORTH_EAST:
                return new int[][] {n, e};
            default:
                return new int[][] {n, s};
        }
    }

    /** The rail shape at a position, or null if there is no rail. */
    private static RailShape shapeAt(World world, BlockPos p) {
        BlockState state = world.getBlockState(p);
        if (!AbstractRailBlock.isRail(state)) {
            return null;
        }
        return state.get(((AbstractRailBlock) state.getBlock()).getShapeProperty());
    }

    /** A rail at this position, one block up, or one block down. Slopes connect across levels. */
    private static BlockPos findRailNear(World world, BlockPos p) {
        if (AbstractRailBlock.isRail(world.getBlockState(p))) {
            return p;
        }
        if (AbstractRailBlock.isRail(world.getBlockState(p.up()))) {
            return p.up();
        }
        if (AbstractRailBlock.isRail(world.getBlockState(p.down()))) {
            return p.down();
        }
        return null;
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
