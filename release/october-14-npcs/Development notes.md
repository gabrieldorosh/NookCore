# Niko and Pip test build

Branch feature/spawn-npcs, based on staff-presence maintenance. Local acceptance candidate only; no Wednesday deployment promise.

Niko accepts the existing shared six quests plus challenge. The first production enablement starts the requirement at the next Wednesday boundary; prior progress and payouts remain. Local test mode starts the requirement immediately without clearing progress. Acceptance is stored by player/week. Contributions and progress are guarded before acceptance. Logged admin fallback is available.

Pip uses FancyNpcs 2.12.1, pinned from the publisher's Modrinth release LigxTtVw. Official JAR SHA512: a370600889f7c08058fc0a7ead3549382efa6e17d3f95f7224822921e3899260d51ad6fe0fb3fe36fa4f61903698de27d1e4ce9804e9ee716733b55f5251ea07. It is a provided build dependency and separate runtime plugin, never bundled into NookCore. Paper's SnakeYAML 2.2 is explicit on the build classpath to avoid DiscordSRV's older embedded copy during tests.

Catalogue/price/allowance/item snapshots persist per visit. The selector enforces six unique designs, 3/2/1 rarity counts, at least two seasonal and two year-round designs, at most one modern design and two per category. Seeded selection prefers less recent designs. It fails closed if constraints are impossible. First-visit CCTV is pinned. Rarity assignments and economic settings are provisional.

The debit, stock reduction and REVIEW receipt commit in one SQLite transaction. Full-inventory planning precedes charging. Inventory application/save acknowledgement precedes DELIVERED. An ambiguous crash never automatically replays an item or refunds; pending receipts block the buyer until audited recovery. Optional caps include pending/delivered receipts and exclude verified refunds. NPC lifecycle never owns the authoritative financial state.

Collectible PDC and the exact canonical item are explicitly copied into skull tile data and restored into the single vanilla break drop. Explosions preserve these blocks; pistons cannot move them. Same-design copies have no per-copy serial metadata. Creative and restoration-tool behavior still require client acceptance.

Default feature configuration is disabled. The package's local configuration enables a fixed seven-day test visit and immediate Niko acceptance requirement. Do not deploy it to production. Build with build.ps1 -Offline using the verified dependency in staging/candidates; existing Java/Maven dependencies are under SeasonTwo/tools. run_spawn_npcs.py creates only a localhost isolated probe server and archives its previous test database. No Discord bot or hosted server is used.

See Test guide.md for owner acceptance and remaining scope. The physical stall, native villager interface, walking/sleeping/dialogue, custom advancements, additional seasonal pools and final economic approval are not claimed complete.
