# Changelog

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
