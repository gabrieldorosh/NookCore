# Automatic End opening — Friday 23 October, 19:00 UK

Install this NookCore.jar and edit two EXISTING configuration files. Do not replace any plugin directory, database or complete config with an old copy.

1. Back up the current files. If the server is running, upload the new JAR as plugins/update/NookCore.jar (matching its existing filename). Otherwise replace plugins/NookCore.jar while fully stopped.
2. In the EXISTING travel section of plugins/NookCore/config.yml, keep lock-end: true and add this sibling line with the same indentation as lock-end:

       end-opens-at: "2026-10-23T19:00:00+01:00"

   Keep lock-nether: false. Set travel.grounded.elytra.enabled: true if enabling the tested Overworld rocket restriction; preserve its takeoff/rearm values and the other travel settings.
3. In the SERVER ROOT bukkit.yml, set settings.allow-end: true now. This permits the End world to load; NookCore still prevents regular-player entry until the scheduled time.
4. Restart normally NOW and run nookadmin status. It must show this release's source commit, healthy economy, and End access: LOCKED until 23 Oct 2026, 19:00 BST. If enabling the nerf, Travel must show rocket restriction=true. Do not leave until the scheduled End-access line is present and correct.
5. Have a regular Survival player without travel bypass try an End portal before 19:00: entry must be refused. OP/staff travel can bypass the lock. Verify End world startup succeeded in the console. An admin-only access check does not prove public gating.

At 19:00 UK (18:00 UTC), regular players can enter automatically. No second restart, console command, file write or database change is needed. A restart before opening keeps the lock; a restart after opening keeps access open. Other plugins may still block access, so an ordinary-player portal check is required.

At/after 19:00, nookadmin status shows End access: OPEN since 23 Oct 2026, 19:00 BST. The first post-opening portal attempt observes the clock directly; server lag may delay processing, but no scheduler action can be missed.

To cancel or re-lock later: remove/blank end-opens-at, keep lock-end true, and restart. lock-end:false is an immediate manual unlock and ignores the schedule. A malformed timestamp blocks NookCore startup; use the exact quoted value above. Keep the server clock correct.

No quests.yml, database, spawn-npcs.yml or other plugin files are included in this update. Pip's 19:00 restock remains independent and automatic. The 8-shulker and 16-end-rod quests are already active. The existing Overworld rocket limiter allows a three-second takeoff window after three seconds continuously grounded; Nether and End flight stay unrestricted.
