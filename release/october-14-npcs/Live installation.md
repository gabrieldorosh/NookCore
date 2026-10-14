# Niko and Pip — live installation

## Included settings

NPCs enabled; test mode off; no local NPC coordinates or test database. Seven products per visit: four common, two uncommon, one rare, at least two seasonal, at most one modern. CCTV is pinned for the first visit. Current launch settings carried forward from testing: common ₦4, uncommon ₦8, rare ₦15; 10/5/4 per design per allocation; no per-player purchase caps. Across the opening and restock allocations that is 108 heads and a maximum ₦600 spent per visit. These are configurable in spawn-npcs.yml before the first startup; published visit prices and stock are retained across restarts.

Pip arrives Wednesdays at 02:00, restocks Fridays at 19:00, leaves Mondays at 02:00, Europe/London time with daylight-saving handled automatically. Installing during a visit opens that visit; installing after Friday's restock includes both allocations. She appears once her position is set. Additional seasonal stock must be configured before the 4 November visit; the supplied Halloween pool covers October arrivals only.

During the transition week Niko says exactly: "Hi! Come and speak to me next week for new quests!" His progress menu stays available. Once acceptance becomes required, he uses the normal quest greeting and labels accepted and unaccepted quests. Current weekly quest progress remains active when first installed in production. Visiting Niko becomes required from the next Wednesday 02:00 reset (21 October if first enabled on 14 October). This package does not replace or select weekly quests. Quests already active through this transition are displayed as accepted.

## Upload and restart

1. Stop the hosted server and take a matching backup of the worlds/player data and plugins/NookCore folder, including its database. Keep the current NookCore.jar as a rollback copy.
2. Extract the package locally. Upload the contents of its `plugins` folder into the server's `plugins` folder. This replaces NookCore.jar and adds FancyNpcs-2.12.1.jar, NookCore/spawn-npcs.yml, the two skin PNGs and their signed skin cache. If FancyNpcs is already installed, keep only one FancyNpcs JAR. Do not replace any existing NookCore config.yml, quests.yml or database; none are included here.
3. Start the server normally. Do not /reload. Wait for startup to finish.
4. In game, stand at Niko's intended location and facing, then run `/nookadmin npc place niko`. Wait for the placement confirmation. Repeat at Pip's stall with `/nookadmin npc place pip`.
5. Run `/nookadmin npc status`: both skins should be ready; Pip is visiting only within her schedule. Use `/nookadmin vendor preview` during a visit to review her current collection, and `/nookadmin vendor catalogue` for all designs.
6. Exit staff mode and use Survival. Click Niko to check the acceptance status; check Pip's seven products, quantity stack and stock labels. After any later restart, positions and remaining stock must remain.

The upload uses a stopped server so the new dependency, settings and NookCore build activate together. If uploading in advance, keep the package outside the active plugins folder until the planned stop. The package is ready for manual installation; it does not schedule the restart or publish an announcement.

## Recovery

For an uncertain purchase use `/nookadmin vendor review` and investigate its receipt before delivered/refund recovery. Never automatically refund or replace an item. If NPC setup is delayed, `/nookadmin npc accept <player>` is the logged quest fallback.

If disabling NPCs after launch, retain this NookCore build so placed collectible heads keep their metadata when broken. Do not roll back only the database after players have traded; any full rollback needs the matching world/player backup. Keep staff out of staff mode before reverting to an older build.

## Validation and remaining check

Owner reported N01–N03, P01–P07 and S01 PASS before the latest presentation refinements. Automated checks cover quantity stacks, seven slots, acceptance status, purchases, stock persistence and stalled placement saves. Final owner check: Niko's accepted/not-accepted presentation and Pip's polished quantity/stock display. This is an inventory menu; a native villager trade screen, roaming, dialogue schedules and custom advancements are not included.
