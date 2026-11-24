package dev.endermanclaude.minecartimprovements;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Load protection for the minecart mixin. Kept out of the mixin class on purpose: mixins should not
 * carry static initialisers or shared mutable state. All counters here are touched only from the
 * server thread (the mixin returns early on the client).
 */
public final class MinecartSafety {
    /** Server-wide cap on new launches per tick. */
    public static final int MAX_LAUNCHES_PER_TICK = 32;
    private static final int DEBUG_LINES_PER_SECOND = 20;

    /** Debug output is off unless the JVM is started with -Dminecartimprovements.debug=true. */
    public static final boolean DEBUG = Boolean.getBoolean("minecartimprovements.debug");

    private static final Logger LOGGER = LogManager.getLogger("minecart-improvements");

    private static int budgetTick = -1;
    private static int launchesThisTick = 0;
    private static int debugWindow = -1;
    private static int debugLines = 0;

    private MinecartSafety() {
    }

    /** Block state at p, or air if its chunk is not loaded. Never forces a chunk to load or generate. */
    public static BlockState stateAt(World world, BlockPos p) {
        if (!world.isChunkLoaded(p)) {
            return Blocks.AIR.getDefaultState();
        }
        return world.getBlockState(p);
    }

    public static boolean finite(double... values) {
        for (double d : values) {
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return false;
            }
        }
        return true;
    }

    /** True if this cart may start a launch this tick. Carts refused a slot stay on vanilla rail logic. */
    public static boolean takeLaunchSlot(Entity cart) {
        MinecraftServer server = cart.getServer();
        if (server == null) {
            return false;
        }
        int now = server.getTicks();
        if (now != budgetTick) { // != (not >) so a new world with a reset tick counter works too
            budgetTick = now;
            launchesThisTick = 0;
        }
        if (launchesThisTick >= MAX_LAUNCHES_PER_TICK) {
            if (DEBUG) {
                debug(cart, "launch skipped: per-tick launch cap reached");
            }
            return false;
        }
        launchesThisTick++;
        return true;
    }

    /** Debug line, capped server-wide. Call sites check DEBUG first so nothing is formatted or boxed when off. */
    public static void debug(Entity cart, String format, Object... args) {
        MinecraftServer server = cart.getServer();
        if (server == null) {
            return;
        }
        int window = server.getTicks() / 20;
        if (window != debugWindow) {
            debugWindow = window;
            debugLines = 0;
        }
        if (debugLines++ < DEBUG_LINES_PER_SECOND) {
            LOGGER.info(String.format(format, args));
        }
    }
}
