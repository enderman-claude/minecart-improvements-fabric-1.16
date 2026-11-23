package dev.endermanclaude.minecartimprovements;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.RailBlock;

/**
 * Super powered rail: a plain rail with no redstone or braking.
 * The cart mixin gives carts on this block a strong speed boost.
 */
public class SuperPoweredRailBlock extends RailBlock {
    public SuperPoweredRailBlock(AbstractBlock.Settings settings) {
        super(settings);
    }
}
