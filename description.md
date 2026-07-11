# ✈️ Vapor Trails for Create Aeronautics

**Your airships deserve an atmosphere.** Vapor Trails adds the missing air to Create
Aeronautics — spinning propellers wear flickering vapor halos, engines cough smoke on startup,
fast hulls rip condensation streaks off their wingtips, and low passes blast dust off the
ground.

**🖥️ 100% client-side — works on any server.** No server install, no packets, no config on the
other end. Join any world running Create Aeronautics and only *you* need the mod.

## 🌀 Propeller effects (the good stuff)

- **Blade-tip vapor halos** — past ~96 RPM, condensation puffs fire at the blade-tip radius,
  timed to the blade pass, so every disc wears a flickering ring. Denser in rain and near the
  cloud layer.
- **Prop wash** — a cone of faint air swirls streams out behind every running disc, stretching
  with RPM and airspeed.
- **Takeoff dust & spray** — a running prop within 5 blocks of the surface kicks up a dust
  ring on land or a spray ring on water. Low hovers finally *look* like low hovers.
- **Startup/shutdown bursts** — engines catch with a punchy puff of smoke and vapor, and
  wind down the same way.

Works on small propellers *and* big bearing-mounted sail props — anything Sable recognises as
a propeller.

## ☁️ And the rest of the sky

- **Wingtip vapor trails** — white streaks off the ship's outer edges past 20 m/s, fading over
  3–6 seconds
- **Engine smoke** — light grey puffs while engines run, briefly dark and dense on ignition
- **Hover dust** — block-appropriate debris under low ships: sand clouds, grass flecks, water
  spray
- **Water wakes** — V-shaped foam arms, churned stern foam and bow splash, scaled by speed and
  hull width
- **Cloud punch** — burst through the cloud deck at speed and leave a ring of mist

## ⚙️ Your sky, your rules

Everything is configurable — each effect has its own toggle, a 0–2× intensity slider, and its
speed/RPM/altitude thresholds (client TOML + in-game config screen, no extra dependency).
Particle output is budget-capped globally, per ship, and per propeller group, respects the
vanilla Decreased/Minimal particle settings, and skips all work when no ships are loaded.

**Requires:** NeoForge 1.21.1 · Create Aeronautics (Sable). Without Sable installed the mod
simply sleeps — safe to keep in any pack.

*MIT licensed. Not affiliated with the Create Aeronautics team.*
