# Vapor Trails

Particle effects for Create Aeronautics ships. Propellers get vapor rings at the blade tips, engines smoke, fast ships pull condensation streaks off their wingtips, and low flyovers kick dust off the ground.

**Client-side only. Works on any server.** Nothing to install on the server, no packets sent. If the pack has Aeronautics, you get the effects.

## Propeller effects

This is the main feature. Almost every Aeronautics ship is covered in propellers and in vanilla they just silently spin.

- Spin a prop past ~64 RPM and condensation puffs fire at the blade-tip radius, timed to the blade pass. The disc gets a flickering ring around it. More visible in rain and near the cloud layer.
- Running props blow a cone of swirling air out the back. Gets longer and stronger with RPM and airspeed, and reverses when you reverse the prop.
- A prop blowing at ground within 5 blocks kicks up a dust ring where the wash lands. Over water it's a spray ring instead. Takeoffs and low hovers actually look like something now.
- When a prop spins up from a standstill (or winds down), it coughs out a burst of smoke and vapor.

Works on the small andesite/wooden props and on the big bearing-mounted sail props.

## Airflow simulation

Propellers don't just spit particles and forget them. Every running prop feeds its slipstream into a shared airflow field, and every particle drifts through it. Prop wash shoves nearby vapor, dust and foam around. A moving hull drags its own wingtip trails along behind it. A slow ambient breeze, stronger in rain and storms, pulls long contrails apart instead of leaving them hanging in straight lines.

You can switch it off if you prefer the cheaper static behaviour.

## Everything else

- Wingtip trails: white streaks off the ship's outer edges above 20 m/s, fading over a few seconds
- Engine smoke: grey puffs while running, briefly dark and thick on ignition
- Hover dust: debris under low ships, matched to the block below (sand, grass, water spray)
- Water wake: V-shaped foam trail, churned foam behind the stern, bow splash at speed
- Cloud punch: a ring of mist when you break through the cloud layer at speed
- Shock cone: past 40 m/s a condensation disc forms around the hull and tightens as you accelerate

## Config and performance

Every effect has its own toggle, an intensity slider, and its speed/RPM/altitude thresholds. TOML plus an in-game config screen, no extra library needed.

Particle counts are capped globally, per ship, and per propeller group, so a ten-prop gunship won't starve the wingtip and wake effects. On top of that the mod watches your frame rate and thins effects out on its own when you dip below target, easing them back once there's headroom. It respects the vanilla Decreased/Minimal particle settings and does zero work when no ships are loaded.

Requires NeoForge 1.21.1 and Create Aeronautics (Sable). Without Sable installed the mod just sleeps, so it's safe to leave in any pack.

MIT licensed. Not affiliated with the Create Aeronautics team.
