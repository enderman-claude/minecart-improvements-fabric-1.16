package dev.endermanclaude.minecartimprovements;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.render.ColorProviderRegistry;
import net.minecraft.client.render.RenderLayer;

/**
 * Client-only setup.
 * 1. Cutout render layer for the super rail (its texture has transparent pixels).
 * 2. Cyan tint on the super rail texture via Fabric's ColorProviderRegistry.BLOCK.
 */
public class MinecartImprovementsClient implements ClientModInitializer {
    private static final int SUPER_RAIL_TINT = 0x00CDE1;

    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlock(MinecartImprovements.SUPER_POWERED_RAIL, RenderLayer.getCutout());
        ColorProviderRegistry.BLOCK.register(
                (state, view, pos, tintIndex) -> SUPER_RAIL_TINT,
                MinecartImprovements.SUPER_POWERED_RAIL);
    }
}
