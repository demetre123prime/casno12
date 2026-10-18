package dev.fpsforge;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * A client-side performance controller. It avoids custom networking,
 * telemetry, account/session access, and native code.
 */
public final class FPSForgeClient implements ClientModInitializer {
    private static final int SAMPLE_TICKS = 100;
    private static final int INITIAL_VIEW_DISTANCE = 8;
    private static final int INITIAL_SIMULATION_DISTANCE = 5;
    private static final int MIN_VIEW_DISTANCE = 5;
    private static final int MIN_SIMULATION_DISTANCE = 3;
    private static final int LOW_FPS_THRESHOLD = 55;
    private static final int RECOVERY_FPS_THRESHOLD = 100;
    private static final int RECOVERY_SAMPLES_REQUIRED = 3;

    private KeyBinding toggleBoostKey;
    private boolean enabled;
    private int sampleTicks;
    private int highFpsSamples;

    private int savedViewDistance;
    private int savedSimulationDistance;
    private int savedBiomeBlendRadius;
    private double savedEntityDistance;
    private ParticlesMode savedParticles;
    private CloudRenderMode savedClouds;

    @Override
    public void onInitializeClient() {
        toggleBoostKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.fpsforge.toggle_boost",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleBoostKey.wasPressed()) {
                if (enabled) {
                    disableBoost(client, true);
                } else {
                    enableBoost(client);
                }
            }

            if (enabled) {
                runAdaptiveController(client);
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (enabled) {
                disableBoost(client, false);
            }
        });
    }

    private void enableBoost(MinecraftClient client) {
        GameOptions options = client.options;
        savedViewDistance = options.getViewDistance().getValue();
        savedSimulationDistance = options.getSimulationDistance().getValue();
        savedParticles = options.getParticles().getValue();
        savedClouds = options.getCloudRenderMode().getValue();
        savedEntityDistance = options.getEntityDistanceScaling().getValue();
        savedBiomeBlendRadius = options.getBiomeBlendRadius().getValue();

        enabled = true;
        sampleTicks = 0;
        highFpsSamples = 0;

        options.getViewDistance().setValue(Math.min(savedViewDistance, INITIAL_VIEW_DISTANCE));
        options.getSimulationDistance().setValue(Math.min(savedSimulationDistance, INITIAL_SIMULATION_DISTANCE));
        options.getParticles().setValue(ParticlesMode.MINIMAL);
        options.getCloudRenderMode().setValue(CloudRenderMode.OFF);
        options.getEntityDistanceScaling().setValue(Math.min(savedEntityDistance, 0.75));
        options.getBiomeBlendRadius().setValue(0);

        tellPlayer(client, "Smart Boost ON — adaptive view distance enabled (F8 to restore settings).");
    }

    private void disableBoost(MinecraftClient client, boolean announce) {
        if (!enabled) {
            return;
        }

        GameOptions options = client.options;
        options.getViewDistance().setValue(savedViewDistance);
        options.getSimulationDistance().setValue(savedSimulationDistance);
        options.getParticles().setValue(savedParticles);
        options.getCloudRenderMode().setValue(savedClouds);
        options.getEntityDistanceScaling().setValue(savedEntityDistance);
        options.getBiomeBlendRadius().setValue(savedBiomeBlendRadius);

        enabled = false;
        sampleTicks = 0;
        highFpsSamples = 0;

        if (announce) {
            tellPlayer(client, "Smart Boost OFF — your previous video settings were restored.");
        }
    }

    private void runAdaptiveController(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return;
        }

        if (++sampleTicks < SAMPLE_TICKS) {
            return;
        }
        sampleTicks = 0;

        int fps = client.getCurrentFps();
        GameOptions options = client.options;
        int currentView = options.getViewDistance().getValue();
        int currentSimulation = options.getSimulationDistance().getValue();
        int minView = Math.min(MIN_VIEW_DISTANCE, savedViewDistance);
        int minSimulation = Math.min(MIN_SIMULATION_DISTANCE, savedSimulationDistance);

        if (fps > 0 && fps < LOW_FPS_THRESHOLD) {
            boolean changed = false;
            if (currentView > minView) {
                options.getViewDistance().setValue(currentView - 1);
                changed = true;
            }
            if (currentSimulation > minSimulation) {
                options.getSimulationDistance().setValue(currentSimulation - 1);
                changed = true;
            }
            highFpsSamples = 0;
            if (changed) {
                tellPlayer(client, "FPS low (" + fps + "); reducing view/simulation distance.");
            }
            return;
        }

        if (fps >= RECOVERY_FPS_THRESHOLD) {
            highFpsSamples++;
            if (highFpsSamples >= RECOVERY_SAMPLES_REQUIRED) {
                boolean changed = false;
                if (currentView < savedViewDistance) {
                    options.getViewDistance().setValue(currentView + 1);
                    changed = true;
                }
                if (currentSimulation < savedSimulationDistance) {
                    options.getSimulationDistance().setValue(currentSimulation + 1);
                    changed = true;
                }
                highFpsSamples = 0;
                if (changed) {
                    tellPlayer(client, "FPS stable (" + fps + "); cautiously restoring distance.");
                }
            }
        } else {
            highFpsSamples = 0;
        }
    }

    private static void tellPlayer(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[FPSForge] " + message), true);
        }
    }
}
