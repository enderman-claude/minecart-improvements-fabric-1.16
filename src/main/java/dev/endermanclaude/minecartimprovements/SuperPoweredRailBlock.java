package dev.endermanclaude.minecartimprovements;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.RailBlock;

/**
 * Super powered rail: a plain rail with no redstone braking, so it never stops carts.
 * The mixin boosts carts on this block.
 */
public class SuperPoweredRailBlock extends RailBlock {
    public SuperPoweredRailBlock(AbstractBlock.Settings settings) {
        super(settings);
    }
}
