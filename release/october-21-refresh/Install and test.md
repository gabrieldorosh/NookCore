# Wednesday 21 October refresh

Upload only NookCore.jar and quests.yml. Do not replace any plugin directory. Do not upload a database, config.yml or spawn-npcs.yml from another server.

1. Back up NookCore's current data and settings, then stop the server normally. Preserve the current database and its matching journal files. Check that no older NookCore JAR is queued in plugins/update.
2. Replace plugins/NookCore.jar with this release's NookCore.jar. Replace only plugins/NookCore/quests.yml with this release's quests.yml.
3. Start before Wednesday 02:00 UK so the new quest definitions are loaded. A restart tonight is sufficient; another restart at 02:00 is not required. If using the host's restart-warning command, account for its one-hour countdown. Do not use /reload.
4. Run nookadmin status and nookadmin npc status. Verify the release commit, healthy economy, enabled NPCs, and Niko acceptance from 2026-10-21. Before 02:00, configured quests DIFFER from active is expected. At/after 02:00, active week should be 2026-10-21 and definitions should match. If acceptance date is later, report it before announcing the gate as live.
5. Do NOT run nookadmin quests activate-config confirm before rollover: that replaces this week's active progress. If installation happens after 02:00 and the old set has already rolled over, preview nookadmin quests activate-config, then decide whether to replace that week's progress before confirming.

## Owner checks

- Q01: Existing balances, inventories, name colours, shops and NPC positions remain. A regular Survival player can click Niko and Pip at their actual locations. Pip's previously reported plot-access issue is not changed by this release.
- Q02: After Wednesday 02:00, /quests directs a player to Niko. Killing/mining before acceptance earns no quest progress. Accept once, then /quests shows the chosen six regulars and diamond challenge. Interacting again clearly says accepted.
- Q03: In Survival, credited kills count in the Nether and End after acceptance. Test with a disposable local quest/config or wait for the End opening for shulkers. Confirm cancelled/unattributed kills and Creative/Spectator give no progress.
- Q04: Mine natural iron/diamond ore. Replaced ore does not count; diamond target counts blocks, not Fortune drops. The 24-block challenge pays 30 Nooks once. Quiet kill notifications remain throttled.
- Q05: Contribute carved pumpkins, honey bottles and end rods separately; partial hand-ins and completion deduct only the intended items. Each regular pays 15 Nooks once. No need to gather End materials before Friday.
- P01: Before Wednesday 09:00 Pip is away, including 02:00–08:59. At 09:00 she arrives with seven designs, finite shared stock and at least two Halloween items.
- P02: Friday 19:00 adds one allocation to the same seven products exactly once, preserving sales. Restart does not repeat it. This coincides with the End opening but does not unlock the dimension itself.
- P03: Monday 19:00 Pip leaves. Her next arrival is Wednesday 09:00. All boundaries use Europe/London and handle the clock change on Sunday 25 October.

## Announcement scheduling

Review Announcement.md. Optional test: paste Discord test command.txt as a single console command; private test channel and trailing nookling mention are the same as the previously tested setup. Then schedule the single console command in Discord launch command.txt for Wednesday 21 October 02:05 UK (01:05 UTC). Use a one-off panel console-command task, not a repeating weekly task. If the panel uses UK time, enter 02:05; if UTC, enter 01:05. No leading slash in a console task. The files contain literal backslash-n sequences for DiscordSRV's tested line-break handling. The ping remains last.

No announcement or host schedule has been created by preparing this release. The End still needs its separate Friday 19:00 opening operation. Revisit rocket policy for that opening: this build keeps the current rocket setting. Custom advancements and outstanding compensation are separate; neither is added here.
