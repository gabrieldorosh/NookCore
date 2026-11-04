# Niko's flat-path prototype

This is disabled by default. It adds no commands, automatic path search, terrain edits or chunk loading. Niko's quests and Pip's shop keep their existing behavior when it is off. Existing `spawn-npcs.yml` files are preserved; copy only the `niko-roaming` section when testing an installation.

## Build a small local loop

Use a disposable local Paper 26.3 world with FancyNpcs 2.12.1. The coordinates below are a **toy course**, not a survey of the real spawn. Keep its chunks loaded yourself while testing.

1. Build a cobblestone road with its block tops at Y=65. Follow the four sides of a square from (0.5,65,0.5) to (4.5,65,0.5), (4.5,65,4.5), and (0.5,65,4.5). Make the road at least two blocks wide, including corners. Keep two blocks above it clear.
2. Stand at exactly (0.5,65,0.5) and use the existing `/nookadmin npc place niko`. Wait for placement to finish saving. This is HOME; use the actual saved x/y/z if your position differs.
3. Add this section to the existing `spawn-npcs.yml`; leave all other settings intact. Both the file's existing top-level `enabled` and this section must be enabled for the test. Restart the local server after editing.

```yaml
niko-roaming:
  enabled: true
  waypoints:
    - {world: world, x: 0.5, y: 65.0, z: 0.5}
    - {world: world, x: 4.5, y: 65.0, z: 0.5}
    - {world: world, x: 4.5, y: 65.0, z: 4.5}
    - {world: world, x: 0.5, y: 65.0, z: 4.5}
```

The first point must exactly match HOME. Do not repeat it at the end: the loop closes automatically. Use 2–32 points, all in HOME's world at its exact foot height, within 32 blocks of HOME. The total closed loop is capped at 256 blocks. Niko moves at 1.2 blocks/second at 20 TPS, with 2, 3 and 4-second stops in a repeating sequence. Lag slows him down; he never catches up in a large jump.

Allowed surfaces are dirt paths, coarse dirt, podzol, mud, gravel, bricks, cobblestone, mossy cobblestone, stone bricks and cracked stone bricks. Slabs, stairs, steps, slopes and mixed-height surfaces are intentionally unsupported. A homogeneous dirt-path loop at block Y=64 needs foot Y=64.9375 at HOME and every point. Mud needs its actual collision-top height too. The whole footprint needs road underneath; the center alone is insufficient.

## Check behavior

- Stay more than six blocks from Niko: he should complete the loop, face each segment and pause at points. Check corners and the last-to-first segment.
- Approach in Survival, Adventure and Creative: he must stop before the next movement. Exactly six blocks counts. Spectators and hidden staff must not cause pauses.
- Open Niko's quest menu away from HOME. It must open and accept the same existing quests from his current position; keep it open while moving away and confirm he stays still. Closing it and leaving the area allows him to continue.
- Put a wall, pane or low ceiling ahead while he is moving. Remove a strip of road, or let a route chunk unload. He must stop before crossing it. Remove the obstruction promptly: he rechecks every second and can continue from that same position.
- Leave an obstruction for five active seconds: only roaming disables, with one warning. Niko stays at his last verified position, remains clickable there, and still accepts quests. There is no automatic HOME teleport. Check the route and restart to retry, or use the existing placement command to put stationary Niko somewhere suitable.
- Recreate Niko or restart: he starts from saved HOME. Movement must never change `locations.niko` on disk. A bad/missing route, wrong world, height change or unloaded starting route must leave Niko stationary and quests usable.
- Set `niko-roaming.enabled: false` and restart: verify the previous stationary behavior and Pip's existing shop/idle pose still work. Never replace a whole plugin folder to turn the prototype on or off.

`NikoRoamingChecks.run(core, home)` is an optional standalone Paper probe for an **already built**, loaded 2x2 flat road square extending east/south from `home`. It creates and removes only its own packet NPC; it does not edit blocks, plugin config, saved anchors or stores. Compile it against NookCore's normal dependencies and invoke it on the main server thread. It throws on failure and returns the number of passed checks. This source has no server-launch helper.

## Visual review remains required

FancyNpcs 2.12.1's exact 26.3 adapter uses `NpcData.setLocation` and `Npc.moveForAll(false)` to send teleport/head-rotation packets with zero velocity. It has no waypoint system or walking AI. Functional motion can therefore look like gliding; true leg animation is not promised. Review the result with a real Java client before deciding to use it at spawn. Existing server instances and production data are outside this prototype's test procedure.

API references: [FancyNpcs movement FAQ](https://fancyinnovations.com/docs/minecraft-plugins/fancynpcs/faq), [2.12.1 Npc API](https://fancyspaces.net/javadoc/fn/fi-releases/de.oliver:FancyNpcs/latest/de/oliver/fancynpcs/api/Npc.html). The installed `FancyNpcs-2.12.1.jar` was also inspected with `javap`, including its `v26_3.Npc_26_3.move` implementation.
