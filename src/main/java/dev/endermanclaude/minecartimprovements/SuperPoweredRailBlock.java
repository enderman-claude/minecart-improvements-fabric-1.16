package dev.endermanclaude.minecartimprovements;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.PoweredRailBlock;

/** Powered rail with a much stronger push. Carts on this block are boosted by the mixin. */
public class SuperPoweredRailBlock extends PoweredRailBlock {
    public SuperPoweredRailBlock(AbstractBlock.Settings settings) {
        super(settings);
    }
}
