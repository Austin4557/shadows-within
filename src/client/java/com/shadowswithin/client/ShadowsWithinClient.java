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
        HudElementRegistry.addLast(WATCHER_HUD, (graphics, deltaTracker) -> director.renderWatcher(graphics));
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

        void renderWatcher(net.minecraft.client.gui.GuiGraphics graphics) {
            if (watcher == null || !watcher.visible) return;
            Minecraft client = Minecraft.getInstance();
            int width = client.getWindow().getGuiScaledWidth();
            int height = client.getWindow().getGuiScaledHeight();

            // Temporary in-engine silhouette. The final realistic art will replace this
            // without changing the event's spawn/detection lifecycle.
            int figureHeight = Math.max(72, height * 3 / 5);
            int figureWidth = Math.max(18, figureHeight / 5);
            int centerX = width / 2 + watcher.screenOffset(width);
            int feetY = height * 9 / 10;
            int topY = feetY - figureHeight;
            int left = centerX - figureWidth / 2;
            int right = centerX + figureWidth / 2;
            int head = Math.max(8, figureWidth * 2 / 3);

            graphics.fill(left, topY + head, right, feetY, 0xEE050505);
            graphics.fill(centerX - head / 2, topY, centerX + head / 2, topY + head, 0xF20A0A0A);
            graphics.fill(centerX - head / 4, topY + head / 3, centerX - 1, topY + head / 2, 0xFFB7B7B7);
            graphics.fill(centerX + 1, topY + head / 3, centerX + head / 4, topY + head / 2, 0xFFB7B7B7);
        }
    }

    interface MinorEvent {
        boolean tick(Minecraft client);
    }

    static final class WatcherEvent {
        private final float targetYaw;
        private int lifetime = 20 * 18;
        private int visibleTicks;
        private boolean visible;
        private float lastYawDifference;

        WatcherEvent(Minecraft client, ThreadLocalRandom rng) {
            targetYaw = Mth.wrapDegrees(client.player.getYRot() + 180.0F + rng.nextFloat(-28.0F, 28.0F));
        }

        boolean tick(Minecraft client) {
            lastYawDifference = Mth.wrapDegrees(targetYaw - client.player.getYRot());

            if (!visible && Math.abs(lastYawDifference) < 38.0F) {
                visible = true;
                visibleTicks = ThreadLocalRandom.current().nextInt(9, 22);
            }

            if (visible && --visibleTicks <= 0) return true;
            return --lifetime <= 0;
        }

        int screenOffset(int screenWidth) {
            float normalized = Mth.clamp(lastYawDifference / 38.0F, -1.0F, 1.0F);
            return (int)(normalized * screenWidth * 0.32F);
        }
    }

    static final class FootstepEvent implements MinorEvent {
        private int ticks;
        private int beats;
        private final int interval;
        private final double angle;
        private double distance;

        FootstepEvent(Minecraft client, ThreadLocalRandom rng) {
            interval = rng.nextInt(7, 13);
            beats = rng.nextInt(3, 8);
            distance = rng.nextDouble(4.5, 10.5);
            angle = Math.toRadians(client.player.getYRot() + 180.0 + rng.nextDouble(-55.0, 55.0));
        }

        @Override
        public boolean tick(Minecraft client) {
            if (++ticks % interval != 0) return false;
            Vec3 p = client.player.position();
            double x = p.x + Math.sin(angle) * distance;
            double z = p.z - Math.cos(angle) * distance;
            client.level.playLocalSound(x, p.y, z, SoundEvents.STONE_STEP, SoundSource.AMBIENT,
                    0.72F, 0.82F + ThreadLocalRandom.current().nextFloat() * 0.22F, false);
            distance = Math.max(2.8, distance - 0.45);
            return --beats <= 0;
        }
    }

    static final class MiningEvent implements MinorEvent {
        private int ticks;
        private int beats;
        private final int interval;
        private final double angle;
        private double distance;
        private final double verticalOffset;

        MiningEvent(Minecraft client, ThreadLocalRandom rng) {
            interval = rng.nextInt(18, 34);
            beats = rng.nextInt(2, 6);
            distance = rng.nextDouble(6.0, 14.0);
            angle = Math.toRadians(rng.nextDouble(0.0, 360.0));
            verticalOffset = rng.nextDouble(-4.0, 2.0);
        }

        @Override
        public boolean tick(Minecraft client) {
            if (++ticks % interval != 0) return false;
            Vec3 p = client.player.position();
            double x = p.x + Math.sin(angle) * distance;
            double z = p.z - Math.cos(angle) * distance;
            float pitch = Mth.clamp(0.72F + ThreadLocalRandom.current().nextFloat() * 0.22F, 0.5F, 1.2F);
            client.level.playLocalSound(x, p.y + verticalOffset, z, SoundEvents.STONE_HIT,
                    SoundSource.AMBIENT, 0.9F, pitch, false);
            distance = Math.max(3.5, distance - 0.8);
            return --beats <= 0;
        }
    }
}
