# Selected quests — week of 30 September

Owner-confirmed: 8 witches, 128 rails, 256 oak logs, 8 biomes, 32 salmon, 8 natural diamond ore, plus 200 hostile mobs. Six regular goals pay 15 Nooks each; challenge pays 30, totalling 120 Nooks per player.

| Goal | Quantity | Planning estimate without an established farm or stored supplies |
|---|---:|---|
| Witch hunt | 8 | 1–3+ hours of active searching/combat; potentially several sessions. Most variable target. |
| Railway contribution | 128 rails | Around 20–45 minutes acquiring/crafting; a few minutes with materials already stored or rails looted. |
| Timber contribution | 256 oak logs | Around 15–30 minutes chopping and collecting with an ordinary axe. |
| Exploration | 8 distinct biomes | Around 20–60 minutes; highly dependent on known routes and terrain. |
| Fishing | 32 salmon | Around 5–20 minutes hunting river fish; roughly 20–60 minutes rod fishing depending on gear/luck. |
| Diamond mining | 8 ore blocks | Around 20–60+ minutes including searching; existing exposed veins can be much quicker. |
| Hostile challenge | 200 kills | Around 1–2 hours of focused combat; much faster with a productive player-kill farm. |

These are judgement-based planning ranges, not measured server completion times. Supplies, tools, travel and spawn availability can move them substantially. Eight witches may take longer than the challenge without a known source; the owner explicitly chose to retain eight. Witch kills also progress the hostile challenge, so those times overlap. Buying/donating stored materials is valid; contribution goals are not gather-it-yourself requirements.

Rails: six iron ingots plus one stick make sixteen rails, so 128 rails require **48 iron ingots and 8 sticks**, or looted rails. Source: https://www.minecraft.net/en-us/article/taking-inventory--rails . Diamond progress is per observed ore block rather than item drops; Fortune does not multiply quest progress. Minecraft's drop behaviour is described at https://www.minecraft.net/en-us/article/block-week-diamond-ore .

## Player flow

`/quests contribute` offers separate clickable rail and oak-log choices. `/quests contribute rail` and `/quests contribute oak_log` submit only that material, up to its remaining target. Partial deliveries work. Completed goals pay once. Named/modified items are excluded and unneeded items remain in inventory. Donations become community stock, not a refundable weekly deposit. Staff collect each material separately with the existing `/quests collect` command (maximum one stack per collection).

Both normal and deepslate diamond ore count. Ore positions count once per player per quest; newly placed or piston-moved ore is excluded by the existing changed-block tracking. The plugin cannot retrospectively identify diamond ore placed before diamond tracking was introduced. It does not alter Fortune drops, tool durability or ore generation.

The hostile challenge uses the existing tracked list: zombie, skeleton, spider, cave spider, creeper, husk, stray, drowned, witch, phantom and pillager. Player kills in Overworld Survival count, including player-kill farms. Passive fish do not count towards that challenge.

## Quiet progress

Mining, mob kills and fishing each show the first progress update, then at most one progress notice per quest per ten seconds; suppressed actions still count. Completion and reward messages bypass the cooldown. Overlapping witch/challenge progress may produce two messages at the first kill or when both complete, but not a message for every kill. `/quests` always shows current progress. Explicit contribution receipts and infrequent biome updates remain immediate.

## Final checks

The snapshot-derived world on 25578 preserves the current launch rotation until 02:00 UK. Its config contains the next set, which will freeze at the reset. Do not activate-config to preview it there.

For immediate local checks of the selected set, use the disposable fresh quest course on 25581. Launch `& 'E:\NikosNook\SeasonTwo\staging\september-quests-check\start.ps1'`. It deliberately has the selected set active already and does not represent live progression.

- Q1: Carry rails and oak logs. Plain `/quests contribute` takes nothing and offers both choices. Submit rails, verify logs untouched; submit logs, verify rails untouched. Test a partial delivery, completion, surplus items and a repeated completed submission. Each regular goal pays exactly 15 Nooks once. Restart and verify both totals/rewards persist.
- Q2: Break normal/deepslate diamond ore in Survival; both progress the same goal. A newly placed ore does not count. Fortune does not add extra progress. Completing eight pays 15 Nooks once.
- Q3: Kill a rapid group of qualifying hostiles: progress increments on every kill without chat spam. Killing witches progresses both goals. First updates and completion stay visible; /quests reports the full count. The challenge pays 30 Nooks once.
- Q4: Check 32 salmon and 8 distinct biomes appear correctly. Normal/deepslate diamonds are the only new mining targets; coal/iron behaviour remains supported.

Production schedule: stop before Wednesday 02:00 UK, install the final JAR and selected quests.yml with the release configuration, then start at/after the boundary. On 30 September, 02:00 BST equals 01:00 UTC. The new file must be in place before the new week is first opened by the plugin; existing frozen rotations are retained, not silently replaced.
