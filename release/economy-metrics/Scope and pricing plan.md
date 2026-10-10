# Tools of the Trade — target 7 October 2026

First deliver a silent maintenance update with staff-only economy/activity measurements. Then prototype enchanted vein-mining tools, tree-felling tools and bunny boots, with Nook payments required in addition to the enchantment. Prices are deliberately undecided. The owner's proposed acquisition route is rare books in existing JustLootIt loot tables; validate item identity and anvil transfer before committing to drops. No command-shop purchase route is assumed.

Accepted activation for gathering: sneak while breaking, bounded work per use, normal durability and full protection checks. Ordinary mining remains available without spending. Initial scope excludes tiers, weapons, mass excavation and automatic replanting. Alternative activation designs considered: always-on enchanted tools (accidental spending), right-click activation (competes with normal interactions), item fuel (more bookkeeping). Sneaking is the chosen first prototype.

## Price from observations

Collect at least three complete UTC days after installation, preferably through the weekend. Use /nooks report for rewards, sinks and delivered shop prices, and /nooks metrics export for daily activity/material counts. Joining bonuses, staff grants, transfers and existing savings are not sustainable earned income. Shop sale value is turnover, not profit; do not count both sides as newly earned currency.

Compare typical players as well as high earners. Avoid choosing prices from the richest accounts or population totals alone. Start with hypothetical costs for representative sessions, compare them with quest/advancement earnings and useful shop purchases, then playtest whether the time saved feels worthwhile. Do not infer causal time savings from pre-feature mining rates: measure paired ordinary/perk sessions during testing.

Ore fees may need categories because coal and diamond have different value. Fortune affects yield; Silk Touch can permit repeated harvesting. Log fees must not price ordinary building out of reach. Bunny use needs an explicit definition of active charged time; currently the existing boots consume time while worn in Survival, including standing still. Decide that behaviour before assigning a public rate.

No final or trial price has been accepted. The earlier suggestions of 0.10/ore, 0.02/log and 1/minute are not release decisions.

## Measurement limits

Activity is sampled every five server seconds in Overworld Survival. A player counts as active if they moved or interacted in the last 60 seconds. This is an activity proxy, not perfect AFK detection; vehicles, automation and lag affect it. Harvests count ore/log blocks that produce a non-cancelled block-drop event, plus item quantities produced. Placed blocks can count, drops are not necessarily picked up, and downstream plugin changes may affect observed yields.

Historical rewards exist before activity collection, so never divide old rewards by a partial collection interval. UTC daily buckets include a partial current day. No chat, IP addresses, coordinates or movement history are stored. UUIDs link per-player daily counters to economy data. Counters stay locally in plugins/NookCore/activity.db for 90 UTC dates. Admin-created CSV exports persist until staff remove them. Up to roughly a minute of buffered counters can be lost on a crash; collection failures are logged and do not stop the economy. No external analytics service is used.
