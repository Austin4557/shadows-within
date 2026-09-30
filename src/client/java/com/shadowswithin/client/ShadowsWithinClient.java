package com.shadowswithin.client;

import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class ShadowsWithinClient implements ClientModInitializer {
    private static final Identifier WATCHER_HUD = Identifier.fromNamespaceAndPath("shadows_within", "watcher");
    private final AmbientDirector director = new AmbientDirector();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudElementRegistry.addLast(WATCHER_HUD, (graphics, deltaTracker) -> {
            if (!director.shouldRenderWatcher()) return;
            Minecraft client = Minecraft.getInstance();
            int width = client.getWindow().getGuiScaledWidth();
            int height = client.getWindow().getGuiScaledHeight();
            int figureHeight = Math.max(72, height * 3 / 5);
            int figureWidth = Math.max(18, figureHeight / 5);
            int centerX = width / 2 + director.watcherScreenOffset(width);
            int feetY = height * 9 / 10;
            int topY = feetY - figureHeight;
            int left = centerX - figureWidth / 2;
            int right = centerX + figureWidth / 2;
            int head = Math.max(8, figureWidth * 2 / 3);
            graphics.fill(left, topY + head, right, feetY, 0xEE050505);
            graphics.fill(centerX - head / 2, topY, centerX + head / 2, topY + head, 0xF20A0A0A);
            graphics.fill(centerX - head / 4, topY + head / 3, centerX - 1, topY + head / 2, 0xFFB7B7B7);
            graphics.fill(centerX + 1, topY + head / 3, centerX + head / 4, topY + head / 2, 0xFFB7B7B7);
        });
    }

    private void tick(Minecraft client) {
        if (client.player == null || client.level == null || client.isPaused()) {
            director.pause();
            return;
        }
        director.tick(client);
    }

    static final class AmbientDirector {
        private int quietTicks = 20 * 35;
        private int tension;
        private MinorEvent activeEvent;
        private WatcherEvent watcher;
        private int watcherCooldown = 20 * 90;

        void pause() { }

        void tick(Minecraft client) {
            if (watcher != null) {
                if (watcher.tick(client)) {
                    watcher = null;
                    watcherCooldown = ThreadLocalRandom.current().nextInt(20 * 300, 20 * 720);
                    quietTicks = Math.max(quietTicks, 20 * 70);
                }
                return;
            }
            if (activeEvent != null) {
                if (activeEvent.tick(client)) activeEvent = null;
                return;
            }

            if (watcherCooldown > 0) watcherCooldown--;
            if (quietTicks-- > 0) return;

            ThreadLocalRandom rng = ThreadLocalRandom.current();
            tension = Math.min(100, tension + rng.nextInt(7, 18));

            // The Watcher is deliberately uncommon and cannot overlap a minor event.
            if (watcherCooldown <= 0 && tension >= 45 && rng.nextInt(100) < 24) {
                watcher = new WatcherEvent(client, rng);
                tension = Math.max(10, tension - 35);
                return;
            }

            activeEvent = rng.nextInt(100) < 62
                    ? new FootstepEvent(client, rng)
                    : new MiningEvent(client, rng);
            quietTicks = rng.nextInt(20 * 40, 20 * 105);
        }

        boolean shouldRenderWatcher() {
            return watcher != null && watcher.visible;
        }

        int watcherScreenOffset(int width) {
            return watcher == null ? 0 : watcher.screenOffset(width);
        }

