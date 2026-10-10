# October maintenance — 10 October 2026

This build consolidates the stable Season Two release history, including the latest shop inventory recovery, attached-sign access and shift-click stocking fixes. It adds private economy/activity reporting and AFK greying in TAB. Paid enchantments remain deferred and are not included.

## Install at a nightly restart

Keep the normal matched server backup. Upload this package's NookCore.jar as plugins/update/NookCore.jar, replacing any earlier queued NookCore JAR, then let the existing nightly restart run. The installed plugin filename must also be NookCore.jar. Do not use /reload. The separate warning-based restart command starts a one-hour countdown; do not schedule that command at the intended restart time.

Keep the live config.yml, quests.yml and TAB files. No quest activation or reset is required. Existing configs inherit metrics enabled, AFK display enabled and a 300-second AFK threshold. If these keys already exist, their explicit values take precedence. Both features can be disabled independently at startup using economy-metrics.enabled: false or chat.afk.enabled: false. The threshold is chat.afk.after-seconds. Do not replace a full config merely to add these options.

## Short player check

1. As admin, run /nooks report 1, /nooks metrics 1 and /nooks metrics export 1. Confirm readable results and a CSV under plugins/NookCore/reports. A non-admin must be denied access.
2. In Overworld Survival, move around and mine a few ore blocks and logs. Repeat the metrics report after the next minute flush. Activity/harvest counts should increase; metrics itself never charges or rewards Nooks. Ordinary quest/advancement rewards remain possible.
3. Stay idle for five minutes: TAB name and title should turn grey within the next five-second refresh. Move or interact and check the original colours return. Balance sorting, pronouns and normal chat colours should remain unchanged. Check Java and Bedrock presentation when available.
4. As a customer, open a shop through its chest and directly attached ordinary sign; neither should produce the redundant protection warning. Buy while looking at its sign. As owner, shift-click a matching stack from the first shop screen and check exact inventory/stock quantities. Customers must still be unable to edit signs or access physical containers.
5. After the next normal restart, confirm the metrics remain and check console for NookCore errors. Existing quests and shop operation should continue normally.

Moon's October 10 pending book recovery was confirmed resolved by the owner. Do not repeat that recovery.

## What the measurements mean

/nooks report [days] summarises money flows and delivered native-shop sales over a rolling interval. Reported sale prices are historical weighted averages, not profit. Item-payment trades are counted separately.

/nooks metrics [days] and /nooks metrics export [days] use UTC calendar dates, including the partial current day. Activity collection covers Overworld Survival, with movement/interaction in the previous 60 seconds used as a rough activity indicator. This differs intentionally from the five-minute AFK display timer. Placed ore/logs can count; these are observational counters, not anti-cheat or quest qualification.

Counters are held in the separate local plugins/NookCore/activity.db database, retained for 90 UTC dates. No chat, IP addresses or movement history are collected. UUIDs identify daily player counters. Exports remain until staff remove them. A crash may lose around a minute of buffered counters; a normal stop flushes pending work. Collection failure does not pause the economy.

Collect several complete days before interpreting activity rates. Historic quest/advancement rewards can predate measurement, so do not divide them by partial measured time. No enchantment pricing is being introduced.

## Validation and fallback

439 automated tests pass. The combined isolated Paper suite covers private report access and read-only behaviour, metric persistence through restart and shutdown flushing, disabled collection, AFK placeholders and chat preservation, stock recovery, attached-sign interaction and direct shift-click deposits. BUILD.json and PAPER-RESULT.json record the exact artifact and results. Real-client checks above remain.

The previous local-update-20261010-shop-interaction/NookCore.jar is the compatible fallback for this maintenance delta. Restore that exact JAR with a normal restart if needed; keep live quests and economy data. It ignores the separate activity database. Do not roll back to pre-October quest builds or replace the live database to undo a display/metrics change.
