package dev.endermanclaude.minecartimprovements;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.block.Block;
import net.minecraft.client.render.RenderLayer;

/**
 * Client-only setup.
 * 1. Cutout render layer for the super rail (its texture has transparent pixels).
 * 2. Cyan tint on the rail texture, via Fabric's ColorProviderRegistry.BLOCK. The registry's
 *    package is looked up by name at runtime, so a wrong name only logs a message.
 */
public class MinecartImprovementsClient implements ClientModInitializer {
    private static final int SUPER_RAIL_TINT = 0x00CDE1;

    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlock(MinecartImprovements.SUPER_POWERED_RAIL, RenderLayer.getCutout());
        registerTint();
    }

    private static void registerTint() {
        String[] registries = {
                "net.fabricmc.fabric.api.client.render.ColorProviderRegistry",
                "net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry"
        };
        for (String name : registries) {
            try {
                Class<?> registryClass = Class.forName(name);
                Object block = registryClass.getField("BLOCK").get(null);
                Class<?> providerInterface = Class.forName("net.minecraft.client.color.block.BlockColorProvider");
                Object provider = Proxy.newProxyInstance(
                        providerInterface.getClassLoader(),
                        new Class<?>[] {providerInterface},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "getColor":
                                    return SUPER_RAIL_TINT;
                                case "hashCode":
                                    return System.identityHashCode(proxy);
                                case "equals":
                                    return proxy == args[0];
                                case "toString":
                                    return "SuperRailTint";
                                default:
                                    return null;
                            }
                        });
                for (Method m : registryClass.getMethods()) {
                    if (m.getName().equals("register") && m.getParameterCount() == 2) {
                        m.invoke(block, provider, new Block[] {MinecartImprovements.SUPER_POWERED_RAIL});
                        System.out.println("[minecart-improvements] tint registered via " + name);
                        return;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                // try the next name
            }
        }
        System.err.println("[minecart-improvements] could not register super rail tint; rail will use the untinted texture");
    }
}
