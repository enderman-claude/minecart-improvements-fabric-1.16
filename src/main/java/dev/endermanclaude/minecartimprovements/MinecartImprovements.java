package dev.endermanclaude.minecartimprovements;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class MinecartImprovements implements ModInitializer {
    public static final String MOD_ID = "minecart-improvements";

    public static final SuperPoweredRailBlock SUPER_POWERED_RAIL = new SuperPoweredRailBlock(
            FabricBlockSettings.copyOf(Blocks.POWERED_RAIL));

    public static final Item SUPER_POWERED_RAIL_ITEM = new BlockItem(
            SUPER_POWERED_RAIL, new Item.Settings().group(ItemGroup.TRANSPORTATION));

    @Override
    public void onInitialize() {
        Registry.register(Registry.BLOCK, new Identifier(MOD_ID, "super_powered_rail"), SUPER_POWERED_RAIL);
        Registry.register(Registry.ITEM, new Identifier(MOD_ID, "super_powered_rail"), SUPER_POWERED_RAIL_ITEM);
    }
}
