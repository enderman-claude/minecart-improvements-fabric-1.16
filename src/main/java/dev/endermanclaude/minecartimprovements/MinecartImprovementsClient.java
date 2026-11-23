package dev.endermanclaude.minecartimprovements;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.render.RenderLayer;

/**
 * Client-only setup: the rail texture has transparent pixels, so draw the super rail with cutout.
 * Fabric's render layer API has moved packages between versions, so it is looked up by name at
 * runtime. If neither name exists, the rail still works but may draw transparent edges as solid.
 */
public class MinecartImprovementsClient implements ClientModInitializer {
    private static final String[] CANDIDATES = {
            "net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap",
            "net.fabricmc.fabric.api.client.render.BlockRenderLayerMap"
    };

    @Override
    public void onInitializeClient() {
        for (String name : CANDIDATES) {
            try {
                Class<?> map = Class.forName(name);
                Object instance = map.getField("INSTANCE").get(null);
                map.getMethod("putBlock", net.minecraft.block.Block.class, RenderLayer.class)
                        .invoke(instance, MinecartImprovements.SUPER_POWERED_RAIL, RenderLayer.getCutout());
                System.out.println("[minecart-improvements] cutout render layer set via " + name);
                return;
            } catch (ReflectiveOperationException e) {
                // try the next name
            }
        }
        System.err.println("[minecart-improvements] could not find Fabric BlockRenderLayerMap; super rail may draw transparency wrong");
    }
}
