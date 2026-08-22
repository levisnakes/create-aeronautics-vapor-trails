# Changelog

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
