package dev.hyperionfps.mixin.modern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.blaze3d.platform.SDLEventHandler;
import net.minecraft.client.Minecraft;

/**
 * Reads the SDL event handler of the client (26.3 only).
 *
 * <p>26.3 moved the windowing layer from GLFW to SDL3, and the game's event drain
 * became {@code RenderSystem.pollEvents(SDLEventHandler)} - it needs the handler
 * that dispatches the SDL events into the game's callbacks. The handler lives in a
 * private field of {@code Minecraft}, so this accessor is the sanctioned way to reach
 * it (AGENTS.md §4).</p>
 *
 * <p>The accessor exists only on the 26.3 node: the 26.1 and 26.2 nodes still use
 * GLFW and never touch SDL.</p>
 */
@Mixin(Minecraft.class)
public interface MinecraftSdlEventHandlerAccessor {

    /** The handler that turns SDL events into the game's input callbacks. */
    @Accessor("sdlEventHandler")
    SDLEventHandler thorium$sdlEventHandler();
}
