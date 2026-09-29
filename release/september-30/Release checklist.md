# September 30 release candidate

Local branch: release/september-30. Production untouched; no push, merge, tag or timed deployment performed. This package is a candidate until the final owner checks are complete; quest selection is confirmed.

## Included

- Accepted road/minecart changes, retaining camel dash and current road grace/decay.
- Rocket changes are deferred. travel.grounded.elytra.enabled is false; rockets retain their existing behaviour tonight. Revisit flight policy and cooldown tuning when planning the End opening, which is over three weeks away. The tested implementation remains available but disabled.
- Cosmetic account titles: qahar0 = mr. duck (#E8A0A0), MoonCheese213 = mrs. bee (#F1DEA0), paigebun = mrs. niko (#EAB8D2). Applies to chat, preview and TAB through account UUIDs. No permission or parent-group changes.
- TAB replaces numeric ping with Nooks, highest balance first and alphabetical ties; footer shows native server TPS. Balance snapshots refresh about every five seconds, with TAB polling every second. Java displays cents; legacy integer objective uses whole Nooks. Bedrock presentation needs a client check.
- Quest reset stays Wednesday 02:00 Europe/London, including daylight-saving changes. On 30 September this is 01:00 UTC.

## Still needed from owner

1. The owner selected the seven goals in quests.yml and reports Q1–Q4 PASS. Quest gameplay acceptance is complete for those checks; no repeat is requested. See Selected quests and estimates.md for confirmed amounts and expected effort.
2. Start the updated Season Two copy using `& 'E:\NikosNook\SeasonTwo\staging\travel-live-matched-20260928\start.ps1'`, join 127.0.0.1:25578 and switch to Survival.
3. Rocket restriction acceptance is deferred with the feature. Confirm the final release config explicitly has travel.grounded.elytra.enabled: false.
4. TAB: with two accounts at different balances, confirm descending order, correct cents versus /nooks, and updated order after a transfer (allow six seconds). Equal balances sort by name. Footer displays TPS without a raw placeholder. Confirm the three title labels/colours when those accounts join and that existing names/pronouns remain intact.
5. One quick road ride, cart braking check and ordinary shop purchase on the final JAR. Earlier baseline, course and real-route tests passed on 936da08; this is the final combined-build smoke check. Bedrock final presentation is untested.

Optional economy reporting is separate and is not included. Existing disposable crash-recovery rehearsals remain outstanding; they are not claimed complete by this release.

## Deployment at 02:00 UK

The owner controls deployment. They will be driving at 02:00, so use Midnight staging and scheduled release.md for the rehearsed staging sequence and proposed one-off host schedule. Host action choices/timezone still require verification; no live tasks are configured. The final quest selection is confirmed and Q1–Q4 passed; complete the remaining TAB/final smoke checks first. Stop the production server before 02:00, take a matched stopped-server backup, and install the selected candidate while stopped. If the old server runs beyond the boundary it can freeze the old quests for the new week; do not silently overwrite that saved rotation or use activate-config as a routine reset.

Replace plugins/NookCore.jar. Merge nookcore-additions.yml into existing plugins/NookCore/config.yml without duplicating YAML keys, preserving all operational settings. Compare TAB-config.yml against current production TAB settings before copying; it was based on the September 27 snapshot. TAB-groups.yml retains the existing rank/name/pronoun placeholders. Install the selected quests.yml into plugins/NookCore/quests.yml. No database, world or isolated server configuration belongs in an upload.

Start at/after 02:00 with someone observing. Check NookCore and TAB enabled, /quests lists the selected seven goals and the next reset, /nooks matches balances, shops work, and Travel/TAB work in Survival. For a pre-reset configuration restart, the stored current rotation should remain intact; the new file becomes the next rotation. Never use /reload for NookCore or replace a database to reset quests.

Rollback: stop cleanly and restore the previous JAR and changed config files. Retain current world/player/economy data; do not restore only an old economy database against newer inventories. If a matched full restore becomes necessary, assess transactions since the backup first. Keep maintenance supervised; if acceptance misses the window, use the next agreed content slot rather than an unobserved deployment.

## Verification

The final selected-quest candidate passed 384 automated tests, 37 Travel + 22 presentation + 7 quest Paper checks, and enabled/disabled copied-world restarts. Existing quest tables, progress and receipts were unchanged by the upgrade. They exercise rocket re-arming, title identity/colour, read-only balance output including maximum supported balance, refresh after debit, numeric fallback and TAB configuration startup. They do not prove client rendering or observed multiplayer order. See bundled Paper and integration results for build identity and restart evidence.

TAB references: https://github.com/NEZNAMY/TAB/wiki/Feature-guide:-Sorting-players-in-tablist ; https://github.com/NEZNAMY/TAB/wiki/Feature-guide:-Playerlist-Objective ; https://github.com/NEZNAMY/TAB/wiki/Placeholders
