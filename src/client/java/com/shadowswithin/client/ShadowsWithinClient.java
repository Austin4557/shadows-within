package com.shadowswithin.client;

import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class ShadowsWithinClient implements ClientModInitializer {
    private static final Identifier WATCHER_HUD = Identifier.fromNamespaceAndPath("shadows_within", "watcher");
    private static final Identifier WATCHER_TEXTURE = Identifier.fromNamespaceAndPath("shadows_within", "textures/gui/watcher.png");
    private static final Identifier CHASE_HUD = Identifier.fromNamespaceAndPath("shadows_within", "chase");
    private static final Identifier AUSTIN_WHISPER_ID = Identifier.fromNamespaceAndPath("shadows_within", "austin_whisper");
    private static final SoundEvent AUSTIN_WHISPER = SoundEvent.createVariableRangeEvent(AUSTIN_WHISPER_ID);
    private final AmbientDirector director = new AmbientDirector();

    static {
        Registry.register(BuiltInRegistries.SOUND_EVENT, AUSTIN_WHISPER_ID, AUSTIN_WHISPER);
    }

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudElementRegistry.addLast(WATCHER_HUD, (graphics, deltaTracker) -> {
            if (!director.shouldRenderWatcher()) return;
            Minecraft client = Minecraft.getInstance();
            int width = client.getWindow().getGuiScaledWidth();
            int height = client.getWindow().getGuiScaledHeight();
            int figureHeight = Math.max(96, height * 2 / 3);
            int figureWidth = Math.max(85, figureHeight * 680 / 768);
            int centerX = width / 2 + director.watcherScreenOffset(width);
            int feetY = height * 19 / 20;
            int left = centerX - figureWidth / 2;
            int top = feetY - figureHeight;
            graphics.blit(RenderPipelines.GUI_TEXTURED, WATCHER_TEXTURE,
                    left, top, 0.0F, 0.0F, figureWidth, figureHeight, figureWidth, figureHeight);
        });
        HudElementRegistry.addLast(CHASE_HUD, (graphics, deltaTracker) -> {
            if (!director.shouldRenderChaseCatch()) return;
            Minecraft client = Minecraft.getInstance();
            int width = client.getWindow().getGuiScaledWidth();
            int height = client.getWindow().getGuiScaledHeight();
            graphics.fill(0, 0, width, height, 0xF20A0A0A);
            int eyeY = height * 2 / 5;
            int eyeW = Math.max(14, width / 18);
            int eyeH = Math.max(5, height / 35);
            int gap = Math.max(18, width / 12);
            graphics.fill(width / 2 - gap - eyeW, eyeY, width / 2 - gap, eyeY + eyeH, 0xFFE8E8DF);
            graphics.fill(width / 2 + gap, eyeY, width / 2 + gap + eyeW, eyeY + eyeH, 0xFFE8E8DF);
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
        private int quietTicks = 20 * 12;
        private int tension;
        private MinorEvent activeEvent;
        private WatcherEvent watcher;
        private int watcherCooldown = 20 * 45;
        private ChaseEvent chase;
        private int chaseCooldown = 20 * 90;
        private int silenceCooldown = 20 * 75;

        void pause() { }

        void tick(Minecraft client) {
            if (chase != null) {
                if (chase.tick(client)) {
                    chase = null;
                    chaseCooldown = ThreadLocalRandom.current().nextInt(20 * 150, 20 * 240);
                    quietTicks = Math.max(quietTicks, 20 * 25);
                    tension = Math.max(5, tension - 45);
                }
                return;
            }
            if (watcher != null) {
                if (watcher.tick(client)) {
                    watcher = null;
                    watcherCooldown = ThreadLocalRandom.current().nextInt(20 * 90, 20 * 150);
                    quietTicks = Math.max(quietTicks, 20 * 20);
                }
                return;
            }
            if (activeEvent != null) {
                if (activeEvent.tick(client)) activeEvent = null;
                return;
            }

            if (watcherCooldown > 0) watcherCooldown--;
            if (chaseCooldown > 0) chaseCooldown--;
            if (silenceCooldown > 0) silenceCooldown--;
            if (quietTicks-- > 0) return;

            ThreadLocalRandom rng = ThreadLocalRandom.current();
            tension = Math.min(100, tension + rng.nextInt(7, 18));

            // Major events are mutually exclusive. The chase is rarer than the Watcher.
            if (chaseCooldown <= 0 && tension >= 70 && rng.nextInt(100) < 14) {
                chase = new ChaseEvent(client, rng);
                return;
            }

            // The Watcher is deliberately uncommon and cannot overlap a minor event.
            if (watcherCooldown <= 0 && tension >= 45 && rng.nextInt(100) < 24) {
                watcher = new WatcherEvent(client, rng);
                tension = Math.max(10, tension - 35);
                return;
            }

            // Silence is intentionally mostly a fake-out: a short stretch with no event at all.
            if (silenceCooldown <= 0 && rng.nextInt(100) < 12) {
                silenceCooldown = rng.nextInt(20 * 150, 20 * 300);
                quietTicks = rng.nextInt(20 * 5, 20 * 11);
                return;
            }

            int minorRoll = rng.nextInt(100);
            activeEvent = minorRoll < 55
                    ? new FootstepEvent(client, rng)
                    : minorRoll < 90
                    ? new MiningEvent(client, rng)
                    : new WhisperEvent(client, rng);
            quietTicks = rng.nextInt(20 * 12, 20 * 28);
        }

        boolean shouldRenderWatcher() {
            return watcher != null && watcher.visible;
        }

        int watcherScreenOffset(int width) {
            return watcher == null ? 0 : watcher.screenOffset(width);
        }

        boolean shouldRenderChaseCatch() {
            return chase != null && chase.catchTicks > 0;
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
        private final int behavior;
        private int seenCount;

        WatcherEvent(Minecraft client, ThreadLocalRandom rng) {
            targetYaw = Mth.wrapDegrees(client.player.getYRot() + 180.0F + rng.nextFloat(-28.0F, 28.0F));
            behavior = rng.nextInt(100);
        }

        boolean tick(Minecraft client) {
            lastYawDifference = Mth.wrapDegrees(targetYaw - client.player.getYRot());
            if (!visible && Math.abs(lastYawDifference) < 38.0F) {
                visible = true;
                seenCount++;
                // Most sightings are fleeting. Some Watchers hold their ground much longer.
                visibleTicks = behavior < 68
                        ? ThreadLocalRandom.current().nextInt(9, 22)
                        : ThreadLocalRandom.current().nextInt(38, 82);
            }
            if (visible && --visibleTicks <= 0) {
                // Rarely it disappears, waits unseen, then allows one second sighting.
                if (behavior >= 92 && seenCount == 1) {
                    visible = false;
                    lifetime = Math.max(lifetime, ThreadLocalRandom.current().nextInt(20 * 3, 20 * 7));
                    return false;
                }
                return true;
            }
            return --lifetime <= 0;
        }

        int screenOffset(int screenWidth) {
            float normalized = Mth.clamp(lastYawDifference / 38.0F, -1.0F, 1.0F);
            return (int)(normalized * screenWidth * 0.32F);
        }
    }

    static final class ChaseEvent {
        private double distance;
        private Vec3 lastPosition;
        private int ticks;
        private int catchTicks;
        private int lifetime = 20 * 60;

        ChaseEvent(Minecraft client, ThreadLocalRandom rng) {
            distance = rng.nextDouble(20.0, 28.0);
            lastPosition = client.player.position();
        }

        boolean tick(Minecraft client) {
            if (catchTicks > 0) return --catchTicks <= 0;

            Vec3 now = client.player.position();
            double moved = now.distanceTo(lastPosition);
            lastPosition = now;

            // The final 15 seconds are a pressure phase: it gains faster, but escape remains possible.
            boolean finalPressure = lifetime <= 20 * 15;
            if (moved < 0.045) distance -= finalPressure ? 0.135 : 0.085;
            else if (moved > 0.19) distance += finalPressure ? 0.025 : 0.045;
            else distance -= finalPressure ? 0.050 : 0.025;

            if (++ticks % 16 == 0) {
                float volume = (float)Mth.clamp(1.35 - distance / 28.0, 0.28, 1.05);
                double yaw = Math.toRadians(client.player.getYRot() + 180.0);
                Vec3 p = client.player.position();
                double soundDistance = Math.max(2.0, Math.min(distance, 12.0));
                client.level.playLocalSound(
                        p.x + Math.sin(yaw) * soundDistance, p.y,
                        p.z - Math.cos(yaw) * soundDistance,
                        SoundEvents.STONE_STEP, SoundSource.AMBIENT,
                        volume, 0.62F + ThreadLocalRandom.current().nextFloat() * 0.10F, false);
            }

            if (distance >= 38.0) return true;
            if (distance <= 1.8) {
                catchTicks = 12;
                // Layer a sharp, high scare sting over the low Enderman hit for a stronger catch surprise.
                client.player.playSound(SoundEvents.ENDERMAN_STARE, 0.78F, 0.62F);
                client.player.playSound(SoundEvents.GHAST_SCREAM, 0.82F, 1.65F);
                return false;
            }
            return --lifetime <= 0;
        }
    }

    static final class WhisperEvent implements MinorEvent {
        private int delayTicks;
        private final double angle;
        private final double distance;
        private final float pitch;

        WhisperEvent(Minecraft client, ThreadLocalRandom rng) {
            delayTicks = rng.nextInt(8, 28);
            angle = Math.toRadians(client.player.getYRot() + 180.0 + rng.nextDouble(-70.0, 70.0));
            distance = rng.nextDouble(3.5, 7.5);
            pitch = 0.96F + rng.nextFloat() * 0.08F;
        }

        @Override
        public boolean tick(Minecraft client) {
            if (--delayTicks > 0) return false;
            Vec3 p = client.player.position();
            client.level.playLocalSound(
                    p.x + Math.sin(angle) * distance,
                    p.y + ThreadLocalRandom.current().nextDouble(-0.3, 0.8),
                    p.z - Math.cos(angle) * distance,
                    AUSTIN_WHISPER, SoundSource.AMBIENT,
                    0.72F, pitch, false);
            return true;
        }
    }

    static final class FootstepEvent implements MinorEvent {
        private int ticks;
        private int beats;
        private final int interval;
        private final double angle;
        private double distance;
        private final boolean mirrorsPlayer;
        private Vec3 lastPlayerPosition;

        FootstepEvent(Minecraft client, ThreadLocalRandom rng) {
            interval = rng.nextInt(7, 13);
            beats = rng.nextInt(3, 8);
            distance = rng.nextDouble(4.5, 10.5);
            angle = Math.toRadians(client.player.getYRot() + 180.0 + rng.nextDouble(-55.0, 55.0));
            mirrorsPlayer = rng.nextInt(100) < 32;
            lastPlayerPosition = client.player.position();
        }

        @Override
        public boolean tick(Minecraft client) {
            Vec3 p = client.player.position();
            double playerMoved = p.distanceTo(lastPlayerPosition);
            lastPlayerPosition = p;
            if (mirrorsPlayer && playerMoved < 0.025) return false;
            if (++ticks % interval != 0) return false;
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
