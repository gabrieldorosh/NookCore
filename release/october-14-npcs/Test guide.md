# Niko and Pip — local tests

This is a local test build, not tonight's live update. It includes the preceding maintenance and staff-presence fixes. Do not copy the local test configuration to the hosted server.

## Start and place

Start the existing `SeasonTwo/staging/local-playtest/start.ps1`, then join `localhost:25576`. Keep the current main config and quests. The new settings are in `plugins/NookCore/spawn-npcs.yml`; FancyNpcs and both supplied PNG skins are included.

Stand where each NPC should be, facing the direction you want them to face:

```
/nookadmin npc place niko
/nookadmin npc place pip
/nookadmin npc status
```

Allow a few seconds after startup for the NPC provider. Skin generation may take longer on the first run; status reports how many of the two skins are ready. Helpers and ordinary players cannot place NPCs. Play in Survival and leave staff mode to interact.

The local configuration opens a fixed seven-day test visit immediately, with a second allocation after one hour. Neither stock nor that clock resets on restart. Quests use their normal Wednesday reset. Normal live scheduling remains Wednesday 02:00 arrival, Friday 19:00 restock, Monday 02:00 departure, UK time including DST.

## Tests

| Test | Action and expected result |
|---|---|
| N01 — Acceptance | Before speaking to Niko, `/quests` gives his coordinates. Opening then closing his menu does not accept. Accept all six quests and the challenge once; `/quests` then shows progress anywhere. Existing progress is retained. |
| N02 — Progress | Complete part of a quest after acceptance. Check that rewards and completion notices work as before. Restart: acceptance and progress remain. At the next Wednesday reset, a new visit is required. |
| N03 — NPC appearance | Inspect both supplied skins, arm models, nameplates and facing. Right-click each from nearby. Neither should be pushable, damageable, lootable or a permanent entry in TAB. Check a second account if available. |
| P01 — Browse | Pip shows exactly six designs, at least two seasonal, and CCTV in her first collection. Products show the exact price and stock. `/nookadmin vendor catalogue` previews all 24 test designs without giving them out. Report any wrong texture or rarity. |
| P02 — Buy | Buy one item, then several. Check the precise Nooks debit, item count and remaining global stock. Double-click, shift-click, number keys, off-hand swap and dragging must not extract display items or charge unexpectedly. |
| P03 — Failed checkout | Try buying with a full inventory or insufficient Nooks. No money or stock should be lost. Open confirmation, walk away, then confirm: no purchase. Two players trying for the last item must not oversell it. |
| P04 — Place and recover | Place a purchased head on the ground and wall. Break and pick it up. Name, texture, rarity and lore should remain, and it should stack with an unplaced copy of the same design. Rename one at an anvil and repeat. |
| P05 — Player resale | Use a purchased head to create a NookShop. Stock it with both an unplaced copy and a placed/recovered copy. Buy through a second account, collect leftovers, and check exact items and payments. Renamed copies are intentionally separate exact-item variants. |
| P06 — Protection | Try breaking a placed collectible where you lack permission: it stays in place with no extra drop. Collectible heads are deliberately left intact by explosions and blocked from piston movement in this first build. Creative breaking should not create a free extra drop. |
| P07 — Restart and stock | Note stock and balances, restart, and check both remain unchanged. After one hour from the first test startup, the second allocation adds to unsold stock once. Restart again: it must not replenish twice. |
| S01 — Staff and compatibility | Hidden staff cannot accept or buy. Retest `/sc`, public chat suppression and whispers from the prior maintenance build. If available, check Bedrock NPC interaction, menus and skins. |

If you need local spending money, use `/nookadmin economy give <your-name> 100 NPC testing`. This only changes whichever server you run it on.

## Review decisions before a live release

- Trial prices are ₦4 / ₦8 / ₦15 for Common / Uncommon / Rare. Each allocation supplies 10 / 5 / 4 per design. There are no purchase caps in the local test; this is not a final launch decision.
- Niko currently offers the existing six quests plus challenge together. Choosing six of eight is not implemented or assumed approved.
- Pip currently uses an inventory menu. A native villager-style virtual-currency interface remains a separate prototype, not a verified feature.
- Black-cat sourcing and additional medieval/seasonal textures remain catalogue work. The present pool has 24 owner-requested/suggested designs; the catalogue screen is for visual approval.
- Only the Halloween seasonal dates/pool are configured. Outside that season, an insufficient seasonal pool blocks publication instead of silently dropping the two-seasonal minimum.
- Walking, sleeping, ambient dialogue, announcement automation and custom advancements are not in this build. The stall is yours to build. WorldEdit/CoreProtect restoration of collectible metadata still needs explicit compatibility testing.

## Recovery and rollback

Use `/nookadmin vendor review` for an uncertain purchase. Check whether the item was delivered before choosing `/nookadmin vendor recover <receipt> delivered <reason>` or `refund <reason>`, then the displayed confirmation command. A refund restores money and stock but does not remove a delivered item. Never refund blindly or give a replacement automatically. Niko has a logged admin fallback: `/nookadmin npc accept <player>`.

The local installation backs up the previous JAR and NookCore folder before replacement. For a live rollout, back up the matching world/player data and plugin database together. Do not downgrade while collectible heads are placed without a plan to preserve their metadata; older builds do not restore the custom item on breaking. Keep NPC features disabled in production until these tests and the launch choices are accepted. Do not `/reload`.
