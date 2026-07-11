package dev.levi.vaportrails.particle;

import dev.levi.vaportrails.fx.Budget;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Faint fast-moving air swirl used for prop wash and surface disturbance.
 * Spins around its view axis so streams read as turbulent air.
 */
public class WashParticle extends TextureSheetParticle {

    private float baseAlpha = 0.28f;

    protected WashParticle(ClientLevel level, double x, double y, double z,
                           double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.friction = 0.97f;
        this.gravity = 0.0f;
        this.hasPhysics = false;
        this.lifetime = 20 + this.random.nextInt(15);
        this.quadSize = 0.4f;
        this.rCol = this.gCol = this.bCol = 1.0f;
        this.alpha = 0.0f;
        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.pickSprite(sprites);
        Budget.onSpawn();
    }

    public WashParticle setup(float scale, int lifetime, float alpha) {
        this.quadSize = scale;
        this.lifetime = Math.max(1, lifetime);
        this.baseAlpha = alpha;
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        this.oRoll = this.roll;
        this.roll += 0.25f;
        float t = (float) this.age / (float) this.lifetime;
        this.alpha = this.baseAlpha * Math.min(1.0f, t * 6.0f) * (1.0f - t);
        this.quadSize *= 1.03f;
    }

    @Override
    public float getQuadSize(float partialTicks) {
        return this.quadSize;
    }

    @Override
    public void remove() {
        if (!this.removed) {
            Budget.onRemove();
        }
        super.remove();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new WashParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
