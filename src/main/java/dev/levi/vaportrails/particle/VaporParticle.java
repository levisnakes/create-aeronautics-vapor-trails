package dev.levi.vaportrails.particle;

import dev.levi.vaportrails.fx.Budget;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Soft white condensation puff. Used for wingtip trails, blade-tip vortex
 * rings, startup puffs and cloud-punch bursts; emitters configure size,
 * lifetime and opacity after spawning via {@link #setup}.
 */
public class VaporParticle extends TextureSheetParticle {

    private float baseAlpha = 0.55f;
    private float baseSize = 0.5f;
    private float growth = 0.6f;

    protected VaporParticle(ClientLevel level, double x, double y, double z,
                            double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.friction = 0.94f;
        this.gravity = 0.0f;
        this.hasPhysics = false;
        this.lifetime = 60 + this.random.nextInt(40);
        this.quadSize = 0.5f;
        this.rCol = this.gCol = this.bCol = 1.0f;
        this.alpha = 0.0f;
        this.pickSprite(sprites);
        Budget.onSpawn();
    }

    /**
     * Configure after {@code ParticleEngine.createParticle}. {@code growth} is
     * the fraction of extra size gained over the whole lifetime (2 = triples).
     */
    public VaporParticle setup(float scale, int lifetime, float alpha, float growth) {
        this.baseSize = scale;
        this.quadSize = scale;
        this.lifetime = Math.max(1, lifetime);
        this.baseAlpha = alpha;
        this.growth = growth;
        return this;
    }

    public VaporParticle setGray(float shade) {
        this.rCol = this.gCol = this.bCol = shade;
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        float t = (float) this.age / (float) this.lifetime;
        // Quick fade-in, slow fade-out; grows as it disperses.
        float in = Math.min(1.0f, t * 8.0f);
        float out = 1.0f - t;
        this.alpha = this.baseAlpha * in * out * out;
    }

    @Override
    public float getQuadSize(float partialTicks) {
        float t = (this.age + partialTicks) / this.lifetime;
        return this.baseSize * (1.0f + this.growth * Math.min(1.0f, t));
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
            return new VaporParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
