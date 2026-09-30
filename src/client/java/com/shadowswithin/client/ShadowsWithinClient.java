package com.shadowswithin.client;

import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public final class ShadowsWithinClient implements ClientModInitializer {
    private final AmbientDirector director = new AmbientDirector();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (client.player == null || client.level == null || client.isPaused()) {
            director.pause();
            return;
        }
        director.tick(client);
    }

    static final class AmbientDirector {
        private int quietTicks = 20 * 45;
        private int tension;

        void pause() { }

        void tick(Minecraft client) {
            if (quietTicks-- > 0) return;
            tension = Math.min(100, tension + ThreadLocalRandom.current().nextInt(8, 19));
            // Event selection hooks: footsteps, mining, watcher, chase.
            // v0.1 intentionally establishes pacing before individual scares are enabled.
            quietTicks = ThreadLocalRandom.current().nextInt(20 * 35, 20 * 95);
        }
    }
}
