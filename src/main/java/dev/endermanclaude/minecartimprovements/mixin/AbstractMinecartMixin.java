package dev.endermanclaude.minecartimprovements.mixin;

import dev.endermanclaude.minecartimprovements.MinecartSafety;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
    // load protection (see MinecartSafety)
    // Vanilla moves a cart at most getMaxOffRailSpeed() (0.4) blocks per tick no matter its velocity, so the
    // caps above do nothing unless that limit is raised. Kept at or below 1 block per tick so vanilla's
    // one-block-at-a-time rail following stays valid.
    private static final double MAX_STEP = 1.0D;
    private static final double WORLD_BOTTOM_Y = 0.0D;     // 1.16.5 worlds are 256 tall: flight ends below this
    private static final double WORLD_TOP_Y = 512.0D;      // ...and far above the build limit

    private double minecartImprovements$railCap = 0.0D; // movement limit while inside vanilla moveOnRail, else 0
    private double minecartImprovements$lastHorizontal = 0.0D;
    private boolean minecartImprovements$airborne = false;
    private int minecartImprovements$airTicks = 0;
    private BlockPos minecartImprovements$launchPos = null;
    private int minecartImprovements$debugTicks = 0;

    /** While airborne, move the cart ourselves and skip vanilla rail logic for this tick. */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void minecartImprovements$flight(CallbackInfo ci) {
        minecartImprovements$railCap = 0.0D; // never carry a raised limit over from an interrupted tick
        if (!minecartImprovements$airborne) {
            return;
        }
        AbstractMinecartEntity self = (AbstractMinecartEntity) (Object) this;
        World world = self.getEntityWorld();

        // Flight is server-only, and a removed cart must not keep flying: hand it back to vanilla.
        if (world.isClient || !self.isAlive()) {
            minecartImprovements$endFlight(self, false);
            return;
        }

        Vec3d v = self.getVelocity();
        Vec3d nv = new Vec3d(v.x, v.y - GRAVITY, v.z);
        double nx = self.getX() + nv.x;
        double ny = self.getY() + nv.y;
        double nz = self.getZ() + nv.z;

        // Bad numbers or out of the world: stop the cart and let vanilla take over.
        if (!MinecartSafety.finite(nx, ny, nz, nv.x, nv.y, nv.z)
                || ny < WORLD_BOTTOM_Y || ny > WORLD_TOP_Y) {
            minecartImprovements$endFlight(self, true);
            return;
        }

        // Never fly into a chunk that is not loaded: reading blocks there would force the server to
        // load or generate it on the tick thread. Stop at the edge of the loaded area instead.
        BlockPos feet = new BlockPos(MathHelper.floor(nx), MathHelper.floor(ny), MathHelper.floor(nz));
        if (!world.isChunkLoaded(feet)) {
            if (MinecartSafety.DEBUG) {
                MinecartSafety.debug(self, "flight stopped at the edge of loaded chunks");
            }
            minecartImprovements$endFlight(self, true);
            return;
        }

        // Ground collision: if the block the cart would be inside is solid, stop on top of it.
        if (!MinecartSafety.stateAt(world, feet).getCollisionShape(world, feet).isEmpty()) {
            self.setPos(nx, feet.getY() + 1.0D, nz);
            self.setVelocity(new Vec3d(nv.x, 0.0D, nv.z));
            minecartImprovements$endFlight(self, false);
            if (MinecartSafety.DEBUG) {
                MinecartSafety.debug(self, "flight ended on solid ground");
            }
            ci.cancel();
            return;
        }

        self.setPos(nx, ny, nz);
        self.setVelocity(nv);
        minecartImprovements$airTicks++;

        BlockPos p = self.getBlockPos();
        boolean landed = nv.y < 0.0D
                && !p.equals(minecartImprovements$launchPos)
                && AbstractRailBlock.isRail(MinecartSafety.stateAt(world, p))
                && self.getY() <= p.getY() + 0.5D;
        if (landed) {
            self.setPos(self.getX(), p.getY() + 0.0625D, self.getZ());
            self.setVelocity(new Vec3d(nv.x, 0.0D, nv.z));
            minecartImprovements$endFlight(self, false);
        } else if (minecartImprovements$airTicks > MAX_AIR_TICKS) {
            minecartImprovements$endFlight(self, false);
        }
        ci.cancel();
    }

    /** Clear flight state. With stop=true the cart also loses its horizontal speed (and any bad values). */
    private void minecartImprovements$endFlight(AbstractMinecartEntity self, boolean stop) {
        minecartImprovements$airborne = false;
        minecartImprovements$airTicks = 0;
        minecartImprovements$launchPos = null;
        if (stop) {
            Vec3d v = self.getVelocity();
            double vy = Double.isNaN(v.y) || Double.isInfinite(v.y) ? 0.0D : v.y;
            self.setVelocity(new Vec3d(0.0D, vy, 0.0D));
            minecartImprovements$lastHorizontal = 0.0D;
        }
    }

    /** Before vanilla rail movement: remember how fast this rail lets the cart move this tick. */
    @Inject(method = "moveOnRail(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V", at = @At("HEAD"))
    private void minecartImprovements$beginRail(BlockPos pos, BlockState under, CallbackInfo ci) {
        minecartImprovements$railCap = 0.0D;
        if (AbstractRailBlock.isRail(under)) {
            double cap = under.getBlock() instanceof SuperPoweredRailBlock ? SUPER_CAP : FAST_CAP;
            minecartImprovements$railCap = Math.min(MAX_STEP, cap);
        }
    }

    /** Raise vanilla's per-tick movement limit (0.4) to the rail's cap, only while moving along a rail. */
    @Inject(method = "getMaxOffRailSpeed", at = @At("RETURN"), cancellable = true)
    private void minecartImprovements$maxStep(CallbackInfoReturnable<Double> cir) {
        if (minecartImprovements$railCap > cir.getReturnValueD()) {
            cir.setReturnValue(minecartImprovements$railCap);
        }
    }

    /** Rail logic runs after vanilla moveOnRail(BlockPos, BlockState) (Yarn 1.16.5 name and descriptor). */
    @Inject(method = "moveOnRail(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V", at = @At("TAIL"))
    private void minecartImprovements$onRail(BlockPos pos, BlockState under, CallbackInfo ci) {
        minecartImprovements$railCap = 0.0D; // vanilla rail movement is over
        if (minecartImprovements$airborne) {
            return;
        }
        AbstractMinecartEntity self = (AbstractMinecartEntity) (Object) this;
        World world = self.getEntityWorld();
        if (world.isClient) {
            return;
        }
        boolean onRail = AbstractRailBlock.isRail(under);
        boolean onSuper = under.getBlock() instanceof SuperPoweredRailBlock;

        Vec3d v = self.getVelocity();

        // 1. slope: left to vanilla. Our own slope term double-counted vanilla's slope physics
        //    and read vertical velocity, which is about zero on rails.
        double h = horizontal(v);

        // 2. speed boosts while on rail, capped. The cap bounds total speed, so loops cannot build speed.
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
            if (isUpwardSingleRail(world, pos, v) && MinecartSafety.takeLaunchSlot(self)) {
                v = new Vec3d(v.x, FORCE_LAUNCH_VY, v.z);
                minecartImprovements$airborne = true;
                minecartImprovements$airTicks = 0;
                minecartImprovements$launchPos = pos;
                if (MinecartSafety.DEBUG) {
                    MinecartSafety.debug(self, "FORCE LAUNCH upward single rail, speed=%.3f", h);
                }
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
                if (gap > 0 && MinecartSafety.takeLaunchSlot(self)) {
                    double airTicks = gap / h;
                    double vy = GRAVITY * airTicks / 2.0D;
                    vy = Math.max(JUMP_MIN_VY, Math.min(JUMP_MAX_VY, vy));
                    v = new Vec3d(v.x, vy, v.z);
                    minecartImprovements$airborne = true;
                    minecartImprovements$airTicks = 0;
                    minecartImprovements$launchPos = pos;
                    if (MinecartSafety.DEBUG) {
                        MinecartSafety.debug(self, "JUMP gap=%d speed=%.3f vy=%.3f", gap, h, vy);
                    }
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

        // Debug (off by default): once per second per cart, and capped server-wide.
        if (MinecartSafety.DEBUG && ++minecartImprovements$debugTicks % 20 == 0) {
            MinecartSafety.debug(self,
                    "onRail=%s super=%s speed=%.3f blocks/tick (%.1f b/s) vy=%.3f airborne=%s",
                    onRail, onSuper, h, h * 20.0D, v.y, minecartImprovements$airborne);
        }
    }

    /** A rail on this block or one level above or below, so ramps count as rail. */
    private static boolean isRailAt(World world, BlockPos p) {
        if (!world.isChunkLoaded(p)) {
            return false;
        }
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
        BlockState state = MinecartSafety.stateAt(world, p);
        if (!AbstractRailBlock.isRail(state)) {
            return null;
        }
        return state.get(((AbstractRailBlock) state.getBlock()).getShapeProperty());
    }

    /** A rail at this position, one block up, or one block down. Slopes connect across levels. */
    private static BlockPos findRailNear(World world, BlockPos p) {
        if (!world.isChunkLoaded(p)) {
            return null;
        }
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
