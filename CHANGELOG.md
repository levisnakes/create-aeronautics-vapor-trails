# Changelog

## 2.4.0 — 2026-08-22

### Assembly and disassembly

Every other effect only exists once a ship is already flying. This one covers
the moment every airship player is guaranteed to experience, standing right
next to it: the instant a structure tears loose of the ground and becomes a
ship.

Assembly reads as a release of pressure - the ground under the hull blows
outward in a ring of block-appropriate dust, debris shakes loose from the
underside as it separates, and displaced air rolls off it. Disassembly is the
opposite shape: heavier and slower, settling rather than blasting.

Both are one-shot set pieces sized to the ship. Nothing here repeats during
normal flight.

Telling a real assembly from a ship that merely streamed into view takes two
signals, because Sable's finalize packet fires for both: a reloaded ship keeps
its UUID while a freshly assembled one gets a new one, and an assembly happens
near a player because somebody had to trigger it. Disassembly needs no
guesswork - Sable reports `REMOVED` for a ship that was taken apart and
`UNLOADED` for one that just left range, so flying away from a ship never fires
a plume.

Observer callbacks run inside Sable's own code, so they are fully guarded: a
stray exception from a cosmetic mod must never take the game down.

New config section `assembly`: `enabled`, `intensity` and `triggerRange` (64).

Verified with `tools/AssemblyCheck.java`: 18 checks over assembly detection,
reload suppression, unload-vs-removal, and the bounded id memory.

## 2.3.1 — 2026-08-22

### Runs on any NeoForge 21.1.x

The mod declared `[21.1.233,)`, so it refused to load on anything older even
though every API it uses has existed since the start of the 1.21.1 line. Packs
and servers pin all sorts of 21.1.x builds, and this locked out most of them
for no reason.

The floor is now `[21.1.1,)`, and it is enforced rather than assumed: the mod
compiles against 21.1.1 itself, so the compiler rejects any API a later build
introduced. The compile target and the declared range are separate properties
now, so bumping the dev target can no longer silently raise the requirement for
users.

Verify with `./gradlew compileJava -Pneo_version=21.1.1`.

Other Minecraft versions remain out of scope: Sable and Create Aeronautics are
both 1.21.1 only.

## 2.3.0 — 2026-08-22

### Wake turbulence

The sky now remembers where ships have flown.

A fast wing sheds a pair of counter-rotating vortices from its tips. Those
tubes outlive the visible condensation by a long way: they linger for about
twenty seconds, sink slowly the way real wake vortices do, and stay strong
enough to throw anything that flies through them around.

Cross another ship's flight path and your own vapor is torn apart as you hit
their wake. Turn tightly enough to cross your own and you fly through your own
turbulence. Because a ship is always sitting in the wake it just shed, cores
ignore their own shedder for the first two seconds, so nothing shakes itself
apart on takeoff.

Flying into disturbed air draws it back out as ragged vapor, which is what
makes the effect discoverable rather than a subtle nudge you would never
notice. That reveal can be turned off on its own, leaving wakes invisible but
still physically active.

Cores use a Rankine vortex profile (solid-body rotation inside the core, 1/r
falloff outside) and live in a uniform spatial hash, so sampling stays cheap no
matter how much traffic has passed through. Shedding is driven independently of
the visible wingtip trails, so turning the trails off does not silently disable
the physics.

New config section `wakeTurbulence`: `enabled`, `lifetimeSeconds` (20),
`strength` (1.0) and `reveal`.

Verified with `tools/WakeCheck.java`: 18 checks covering the vortex profile,
the counter-rotating pair signature, lifetime decay, own-wake suppression, and
exact agreement between the spatially hashed sampling and a brute-force scan
over 5000 randomised queries.

## 2.2.0 — 2026-08-21

### Effects follow the ship you can actually see

Every spawn position was derived from the ship's logical pose, which advances
once per tick. Sable draws ships at an interpolated render pose, so at speed
particles were emitted slightly off the hull. Spawn positions now use the
render pose; velocity is still differenced from the logical pose, which is the
only one that ticks cleanly.

### Rotation is now part of the physics

The mod previously treated every hull as if it only translated. A rotating ship
moves its extremities much faster than its centre of mass, so a banking
aircraft's wingtips were being rated at hull speed. Angular velocity is now
recovered from the orientation each tick, and effects anchored away from the
centre use `v + omega x r`.

Each wingtip is now gated on its own airspeed rather than the hull's, so a hard
roll streams vapor from the rising tip while the other fades. For a 20-block
wing rolling at 5 degrees/tick the tip moves 263% faster than the hull, which
previously meant the difference between no vapor and heavy vapor.

Blade-tip vapor, prop wash and the slipstream jets are all carried by their own
local hull velocity too.

### Blade rings track the real blades

Blade phase was integrated from Create's raw kinetic speed. Aeronautics
advances its own angle through a smoothed rotation speed, so our phase ran
ahead of the visible blades during spin-up and drifted over time. The mod now
reads the propeller's actual rendered angle (`getAngle`/`getInterpolatedAngle`),
falling back to integration only when it is unavailable.

Blade *count* is still a config value: Aeronautics does not expose it, and its
sail list is not public, so reading it would mean reflecting into internals.

Verified with `tools/SpinCheck.java` (13 checks over quaternion recovery,
shortest-arc handling, teleport rejection and point velocity).

## 2.1.0 — 2026-08-21

### Real hull shapes

Hull-based effects used to work off the world-axis-aligned bounding box, which
is a poor stand-in for an airframe: the corner of the box on a cross-shaped
plane is empty air, and the box inflates the moment a ship rotates. The mod now
scans each ship's blocks and keeps the real silhouette.

- Wingtip trails come off the actual wingtips. The span axis is resolved in
  SHIP space, so a banking aircraft still trails from its real tips instead of
  from wherever the world-aligned box happens to bulge.
- Hover dust samples columns that genuinely contain blocks, and measures
  clearance from that column's own underside rather than the lowest point of
  the entire ship.
- Wake width and shock-cone girth use real hull extents, so a hull sitting at
  an angle to the world axes no longer gets a wake as wide as its diagonal.

The scan is incremental (a few chunk sections per tick) so large ships never
cause a hitch, and results are cached and refreshed every ~10 seconds. Ships
wider than 192 blocks, or ships whose scan has not finished yet, fall back to
the old bounding-box behaviour. Turn the whole thing off with
`hullShapeScan = false`.

Verified with `tools/ShapeCheck.java`: on a cross-shaped airframe the old box
corner sat 18.5 blocks away from the true wingtip.

## 2.0.0 — 2026-08-21

### Airflow simulation

Propellers no longer just emit particles and forget about them. Each running
prop now publishes its slipstream into a shared airflow field, and every Vapor
Trails particle drifts through that field each tick. Prop wash actually blows
nearby vapor, dust and foam around; a moving hull drags its own wingtip trails
along behind it; and there's a slow ambient breeze (stronger in rain and
storms) so long contrails disperse instead of hanging in straight lines.
Wash cones carry a tangential swirl, so they curl rather than blow straight.

Turn it off with `airflowSimulation = false` for the old static behaviour.

### Shock cone

New effect: past 40 m/s a condensation disc forms around the hull and gets
dragged along with it, tightening and sliding aft as speed climbs.

### Adaptive quality

The mod now watches your frame rate and quietly thins effects out when you
drop below `targetFps` (default 45), restoring them once there's headroom.
Recovery is slower than cutback so density doesn't visibly pulse.

### Bug fixes

- **Prop wash blew the wrong way on every propeller.** Sable applies thrust as
  `facing * getScaledThrust()`, and `getScaledThrust()` negates `getThrust()`
  before scaling. That minus sign was missed, so the wash was drawn pointing
  the way the ship is pushed rather than the way the air goes. Verified against
  the decompiled formulas across all six facings, both rotation directions and
  the reverse flag.
- **Reversing a propeller didn't reverse its effects.** 1.0.2 replaced the
  direction guess with a constant when thrust read zero, which pinned the wash
  in place. Direction is now derived from signed thrust, with a rotation-based
  fallback only while the prop is idle.
- Propellers kicked up ground dust regardless of which way they pointed. A
  horizontal cruise prop no longer sandblasts the ground beneath it, and the
  dust ring is now centred where the slipstream actually lands, weakening and
  spreading at glancing angles.
- The live particle counter leaked on dimension changes, because the particle
  engine is wiped without running removal hooks. Given enough portal trips the
  counter would sit at the cap and silently refuse every effect.
- Toggling cloud punch on mid-flight could fire one burst from a stale altitude.
- Removed a per-tick stream allocation in the hover dust check.

## 1.0.2 — 2026-07-18

- Propeller bearing (contraption) props now work properly: rotation speed is
  read from the spinning contraption instead of the bearing's kinetic speed,
  which reads 0 on the client
- Prop wash direction for bearing props comes straight from the bearing's own
  thrust vector instead of being guessed
- Block props no longer guess wash direction from RPM sign (that flips with
  gearing); new `invertPropWash` config option if your setup still blows the
  wrong way
- Condensation puffs (wingtip trails, blade-tip rings) are ~40% smaller

## 1.0.1 — 2026-07-11

Effects were too subtle to see in normal play. Big visibility pass:

- Roughly doubled particle sizes and opacity across all effects
- 2-3x spawn rates (wingtips, tip rings, wash, dust, wake, engine smoke, bursts)
- Vapor fade-out is now linear instead of quadratic, so particles stay visible
  for most of their life
- Default budgets raised: maxParticles 800 -> 2000, perShipSpawnsPerTick 40 -> 90
- Blade-tip ring default threshold lowered to 64 RPM
- Delete `config/vaportrails-client.toml` to pick up the new defaults

## 1.0.0 — 2026-07-11

Initial release.

- Wingtip vapor trails (speed-gated, 3–6 s fade, denser in rain/near clouds)
- Propeller effects: blade-tip vapor halos phased to the blade pass, prop-wash cone,
  ground dust / water spray under low discs, startup & shutdown bursts
- Engine smoke with darker ignition puffs
- Hover dust with block-appropriate debris (and water spray), propeller zones excluded
- Water wake: V foam arms, stern churn, bow splash
- Cloud punch mist ring at the cloud layer
- Full client TOML config + in-game config screen; per-effect toggles, intensities and
  thresholds; global/per-ship/propeller particle budgets; degrades with vanilla particle
  settings
- 100% client-side; safe without Sable installed
