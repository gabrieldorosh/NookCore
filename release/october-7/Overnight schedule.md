# Overnight schedule — Wednesday 7 October

Use the prepared October 7 package. Displayed quest titles are now simply "Harvest 64 fully grown crops" and "Complete 8 villager trades"; internal IDs remain stable. This is a titles/docs change only, with the same tested JAR.

## Before bed

1. Take/verify a fresh server backup and retain the current JAR, config.yml and quests.yml.
2. Upload the complete NEW JAR as plugins/update/NookCore.jar (matching the live filename NookCore.jar). Upload this package's full quests.yml over plugins/NookCore/quests.yml. The new definitions require the new JAR; stage both before considering the update ready. Keep current config/TAB files. Do not use /reload or activate-config while staging.
3. Confirm that the upload finished and that the host still uses Paper's update folder. At the next startup, Paper replaces the installed matching plugin JAR with the queued version. An unexpected earlier restart also installs it; there is no timed activation switch for the small title/chat changes.
4. Set the one-off schedule below using the SAME restart command already known to give one hour of warnings. Verify the panel's timezone and displayed next-run date. Do not enter an invented replacement command or confuse a host power action with that warning command.

| Task | UK time (BST) | If the panel uses UTC |
|---|---|---|
| Execute the existing one-hour restart command | Wednesday Oct 7, 00:55 | Tuesday Oct 6, 23:55 |
| Expected restart after its countdown | Wednesday Oct 7, 01:55 | Wednesday Oct 7, 00:55 |
| Automatic weekly quest refresh; NO scheduled quest command | Wednesday Oct 7, 02:00 | Wednesday Oct 7, 01:00 |
| Execute Discord launch command.txt once | Wednesday Oct 7, 02:05 | Wednesday Oct 7, 01:05 |

Starting the countdown at 01:00 UK would put the restart exactly on the quest boundary. Starting it at 02:00 UK would restart around 03:00. We intentionally finish five minutes before the boundary so the new plugin has loaded the selected definitions. Title/chat polish goes live a few minutes early; quests stay on the usual 02:00 schedule. If startup takes beyond 02:00, a stopped server cannot freeze the old goals; the new set opens on startup. A delayed/failed shutdown that leaves the old plugin running past 02:00 can still freeze the old set.

The host schedule itself has not been inspected or configured by the assistant. Use one-off tasks, or disable temporary recurring tasks after this release. The established unconditional 02:05 announcement is not a health check; if startup fails or is late, it can still say now live. Owner previously accepts this tradeoff.

## Simpler alternative while awake

If timing of the small title/chat changes does not matter, install the new JAR and quests file during a normal restart NOW, check startup, then leave only the 02:05 announcement scheduled. The current week's quest snapshot stays until 02:00; the new definitions take over then automatically. This avoids relying on an unattended plugin upgrade.

## Morning check

/quests should show drowned, iron, pillagers, glowstone, crops, villager trades and ghast tears. Do NOT schedule activate-config/confirm as a routine reset. If the old set persisted because the old plugin ran past the boundary, first verify the new JAR/file are loaded, then use the preview and same-actor confirmation procedure in Install and announce.md.

Sources: Paper update-folder behaviour https://docs.papermc.io/paper/updating/ ; countdown length is owner-reported behaviour of this server's existing restart command, not a claim about every host restart action. Actual crop/trade/client checks remain unrun; automated test evidence is in BUILD.json and PAPER-RESULT.json.
