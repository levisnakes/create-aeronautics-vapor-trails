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
 * White sea foam that stays bobbing on the water surface. Horizontal motion
 * decays quickly; vertical position is a gentle sine bob around the spawn
 * waterline.
 */
public class FoamParticle extends TextureSheetParticle {

    private final double surfaceY;
    private final float bobPhase;
    private float baseAlpha = 0.75f;

    protected FoamParticle(ClientLevel level, double x, double y, double z,
                           double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, 0.0, vz);
        this.xd = vx;
        this.yd = 0.0;
        this.zd = vz;
        this.surfaceY = y;
        this.bobPhase = this.random.nextFloat() * Mth.TWO_PI;
        this.friction = 0.90f;
        this.gravity = 0.0f;
        this.hasPhysics = false;
        this.lifetime = 30 + this.random.nextInt(30);
        this.quadSize = 0.25f;
        this.rCol = this.gCol = this.bCol = 1.0f;
        this.pickSprite(sprites);
        Budget.onSpawn();
    }

    public FoamParticle setup(float scale, int lifetime, float alpha) {
        this.quadSize = scale;
        this.lifetime = Math.max(1, lifetime);
        this.baseAlpha = alpha;
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        double bob = Math.sin((this.age + this.bobPhase) * 0.25) * 0.03;
        this.setPos(this.x, this.surfaceY + bob, this.z);
        this.yd = 0.0;
        float t = (float) this.age / (float) this.lifetime;
        this.alpha = this.baseAlpha * (1.0f - t * t);
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
            return new FoamParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
