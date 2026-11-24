package dev.endermanclaude.minecartimprovements;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;

/**
 * Client-only setup: cutout render layer for the super rail (its texture has transparent pixels).
 * The rail now ships its own texture (derived from the vanilla rail), so no runtime tint is needed.
 */
public class MinecartImprovementsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlock(MinecartImprovements.SUPER_POWERED_RAIL, RenderLayer.getCutout());
    }
}
