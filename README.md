# Create Aeronautics: Vapor Trails

Atmospheric particle effects for [Create Aeronautics](https://modrinth.com/mod/create-aeronautics) /
Sable physics ships — wingtip vapor, propeller blade-tip halos, prop wash, engine smoke, hover
dust, water wakes and cloud-punch bursts.

**100% client-side.** No packets, no server state. Join any server — vanilla, modded, with or
without this mod on the other end — and your ships get the effects locally.

- Minecraft **1.21.1**, **NeoForge**, Java 21
- Optional deps: Sable, Create, Create Aeronautics (without Sable the mod idles and does nothing)
- License: MIT · Source: <https://github.com/levisnakes/create-aeronautics-vapor-trails>

> Note: the closed-source jars this compiles against (Sable is PolyForm Shield, Aeronautics is
> closed) are **not** in this repo — see [Building](#building) for where each one comes from.

## Effects

| Effect | What it does | Key config |
| --- | --- | --- |
| Wingtip trails | Condensation streaks off the outer edges of fast ships, 3–6 s fade, denser in rain / near clouds | `minSpeed` (20 m/s) |
| Blade-tip vapor | Flickering vapor ring at propeller blade-tip radius, phased to the blade pass | `bladeTipMinRpm` (96) |
| Prop wash | Faint air-swirl cone streaming behind the disc, scales with RPM + ship speed | `propWashMinRpm` (32) |
| Ground/water disturbance | Dust ring or water spray directly under a running prop within 5 blocks of the surface | `groundDisturbanceRange` |
| Startup/shutdown puff | Punchy smoke/vapor burst when a prop spins up or winds down | `startupPuff` |
| Engine smoke | Grey puffs from running engine blocks, darker for ~1 s on startup | `engineSmoke` |
| Hover dust | Block-appropriate debris under low/hovering ships (sand, grass, water spray), away from props | `maxHeight` (4) |
| Water wake | V-shaped foam trail + bobbing stern foam + bow splash, scaled by speed and hull width | `minSpeed` (2 m/s) |
| Cloud punch | Mist burst ring when crossing the cloud layer at speed (cloud height read from the client dimension) | `minVerticalSpeed` (5 m/s) |

Every effect has an enable flag and a 0.0–2.0 intensity multiplier. Config is client TOML
(`config/vaportrails-client.toml`) and there's an in-game screen via Mods → Vapor Trails →
Config (NeoForge's built-in `ConfigurationScreen`, no extra dependency).

Particle budgeting: global live cap (default 800), per-ship per-tick cap, and a reserved
propeller sub-budget so a ten-prop gunship can't starve its own wingtip/wake effects. Emission
scales down automatically on the vanilla "Decreased" (×0.55) and "Minimal" (×0.2) particle
settings, and ships beyond `effectRange` (default 192 blocks) spawn nothing.

## Building

```
gradlew build
```

Output: `build/libs/vaportrails-<version>.jar`.

The closed-source dependencies are `compileOnly` local jars in `libs/` (not redistributed, not
bundled in the built jar):

- `sable-neoforge-*.jar`, `sable-companion-common-*.jar` — from the Sable release
- `create-*.jar` — Create for NeoForge 1.21.1
- `ponder-*.jar`, `flywheel-*.jar`, `Registrate-*.jar` — extracted from `create-*.jar` → `META-INF/jarjar/`
- `aeronautics-neoforge-*.jar`, `simulated-neoforge-*.jar` — extracted from
  `create-aeronautics-bundled-*.jar` → `META-INF/jarjar/`

## How it works / integration notes

All Sable/Create/Aeronautics-touching code is isolated behind
`dev.levi.vaportrails.sable.SableCompat`, the only class the rest of the mod calls. It never
references modded types itself; the typed work happens in classes that are only classloaded
when the respective mod is present (`Mods.CREATE` / `Mods.AERONAUTICS` guards). If Sable is
absent the mod logs one line and idles; if Sable internals ever throw repeatedly, effects
disable themselves for the session instead of crash-looping (5-strike breaker).

Per client tick (skipped entirely when no sub-levels are loaded):

1. `ClientSubLevelContainer.getAllSubLevels()` enumerates loaded physics objects.
2. Velocity is derived by diffing `logicalPose().position()` between ticks (Sable exposes no
   client velocity API). Jumps > 10 blocks/tick are treated as teleports and zeroed.
3. `boundingBox()` (world AABB) drives wingtip placement, hover footprint, wake width and
   cloud-punch ring size.
4. Every ~2 s each ship's plot chunks are rescanned for block entities:
   Sable's `BlockEntityPropeller` interface finds propellers (any implementor, including small
   Aeronautics props and bearing props); Create's `GeneratingKineticBlockEntity` + class name
   containing "Engine" finds engines (covers the portable engine and addon engines like diesel
   generators). Between rescans only cached block entities are read.
5. Propeller RPM comes from Create's `KineticBlockEntity.getSpeed()`; blade radius and disc
   offset from Aeronautics (`BasePropellerBlockEntity.getRadius()/getOffset()`, bearing props
   via `PropellerActorBehaviour.radius`). Plot-space positions are mapped to world space with
   the sub-level pose (`transformPosition` / `transformNormal`).
6. Effects spawn through vanilla `ParticleEngine` with three custom particle types
   (`vaportrails:vapor/wash/foam`) plus vanilla block-dust/splash/smoke particles.

## Judgement calls (made without asking, as requested)

- **Blade phase is synthesized**, advancing `RPM × 0.3°/tick` (Create's visual rotation rate),
  seeded per propeller — the ring flickers at the true blade-pass frequency but is not
  phase-locked to the rendered blades. Blade count isn't exposed by Aeronautics, so the pass
  pattern uses `assumedBladeCount` (default 3).
- **Prop wash direction** uses `-axis × sign(thrust)` (falling back to sign(RPM)). If a prop
  configuration blows the wrong way, that sign convention is the place to flip — see the
  smoke-test list.
- **Engine detection is a name heuristic** (`GeneratingKineticBlockEntity` whose class name
  contains "Engine"). Precise, API-based detection isn't possible because engines have no
  common interface across Aeronautics modules and addons.
- **Emitter culling is distance-based** (`effectRange`), not frustum-based: trails linger for
  seconds, so an off-screen ship still lays a trail you can turn around and see. Vanilla
  already frustum-culls particle *rendering*.
- **Angular velocity is ignored for wingtips** — emitters use the ship's linear velocity only.
  A ship spinning in place fast enough to condense tips is not a case worth the math.
- **Hover dust requires motion or a running prop**, so a ship parked on the ground doesn't
  dust forever.
- Registering particle types from a client-only mod is safe for server joins (same pattern as
  other client-only effect mods); vanilla IDs precede modded IDs so packet-referenced particles
  are unaffected.

## Smoke-test checklist (in a real Aeronautics instance)

Drop the jar in a Create Aeronautics 1.21.1 NeoForge instance and check, roughly in this order:

1. **Loading**: game reaches the title screen; log shows `Sable detected - vapor trail effects
   enabled.` after joining a world with the mod list including Sable.
2. **Tracking proof**: set `general.debugMarkers = true` — end-rod motes should sit on every
   ship's centre, propeller hubs and one blade tip (and flames above engines). Markers must
   follow the ship as it flies and rotate with it.
3. **Propeller detection**: spin a small andesite/wooden prop past ~32 RPM → wash cone; past
   ~96 RPM → tip ring. Check a *bearing* propeller (sail blades) too — the marker/effects
   should sit on the disc one block out from the bearing.
4. **Wash direction**: the swirl stream must blow *away* from the thrust direction (ship gets
   pushed opposite the stream). If reversed, flip `thrustSign` in `PropellerFx`.
5. **Startup burst**: toggle the prop's rotation on/off → one dense puff per transition, no
   repeats while running steadily.
6. **Ground/water disturbance**: hover with a running prop < 5 blocks over sand, then water —
   dust ring / spray ring directly under the disc.
7. **Wingtips**: dive past 20 m/s → streaks from the outer extremities; denser in rain.
8. **Wake**: drive a hull through water > 2 m/s → V foam arms + stern churn.
9. **Cloud punch**: climb through y≈192 at > 5 m/s vertical → one mist ring, no double-fire.
10. **Graceful absence**: same jar in a plain NeoForge instance (no Sable) → log line
    `Sable not installed - vapor trail effects idle.`, no crash, config screen still opens.
11. **Compat**: with Sodium/Embeddium + Iris/Oculus shaders and "Separate Sable Render
    Distance" installed, verify no crash and particles render (translucent sheet).
12. **Budget**: fly a many-prop ship and confirm wingtip trails still appear (prop sub-budget
    working), and set particles to Minimal → effects thin out but tip rings remain visible.
