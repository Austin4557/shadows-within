package com.shadowswithin.client;

import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

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
        private int quietTicks = 20 * 35;
        private int tension;
        private MinorEvent activeEvent;

        void pause() { }

        void tick(Minecraft client) {
            if (activeEvent != null) {
                if (activeEvent.tick(client)) activeEvent = null;
                return;
            }

            if (quietTicks-- > 0) return;
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            tension = Math.min(100, tension + rng.nextInt(7, 18));

            // Minor events are common enough to create doubt, while major event hooks
            // remain reserved for the Watcher and chase encounters.
            activeEvent = rng.nextInt(100) < 62
                    ? new FootstepEvent(client, rng)
                    : new MiningEvent(client, rng);

            quietTicks = rng.nextInt(20 * 40, 20 * 105);
        }
    }

    interface MinorEvent {
        boolean tick(Minecraft client);
    }

    static final class FootstepEvent implements MinorEvent {
        private int ticks;
        private int beats;
        private final int interval;
        private final double angle;
        private double distance;

        FootstepEvent(Minecraft client, ThreadLocalRandom rng) {
            this.interval = rng.nextInt(7, 13);
            this.beats = rng.nextInt(3, 8);
            this.distance = rng.nextDouble(4.5, 10.5);
            this.angle = Math.toRadians(client.player.getYRot() + 180.0 + rng.nextDouble(-55.0, 55.0));
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
            this.interval = rng.nextInt(18, 34);
            this.beats = rng.nextInt(2, 6);
            this.distance = rng.nextDouble(6.0, 14.0);
            this.angle = Math.toRadians(rng.nextDouble(0.0, 360.0));
            this.verticalOffset = rng.nextDouble(-4.0, 2.0);
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
