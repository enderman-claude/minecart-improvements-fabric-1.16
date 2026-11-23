package dev.endermanclaude.minecartimprovements;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.render.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;

/** Client-only setup: the rail texture has transparent pixels, so draw it with cutout. */
public class MinecartImprovementsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlock(MinecartImprovements.SUPER_POWERED_RAIL, RenderLayer.getCutout());
    }
}
