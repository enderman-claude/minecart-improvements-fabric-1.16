package dev.endermanclaude.minecartimprovements;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.PoweredRailBlock;

/**
 * Super powered rail: a powered rail (redstone powered, straight and sloped only, power passes between
 * adjacent super rails like vanilla powered rails). The cart mixin gives carts a strong boost on a powered
 * one and brakes them on an unpowered one, because vanilla's own boost and brake code only recognises
 * the vanilla powered rail block.
 */
public class SuperPoweredRailBlock extends PoweredRailBlock {
    public SuperPoweredRailBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    /** True for a powered state of this block. Safe to call with any state. */
    public static boolean isActive(BlockState state) {
        return state.getBlock() instanceof SuperPoweredRailBlock
                && state.contains(POWERED)
                && state.get(POWERED);
    }
}
