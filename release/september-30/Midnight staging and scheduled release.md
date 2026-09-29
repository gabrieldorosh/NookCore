# Midnight staging and 02:00 release — 30 September

Current candidate: local-update-20260929-september-release-rc3. Same tested NookCore JAR as rc2; the release config now disables all new rocket restrictions. Road/rail buffs, titles, TAB and the confirmed quests remain included. Q1–Q4 passed according to the owner. Do not use rc2's rocket-enabled config.

## Before midnight

Finish TAB's visual/numeric sorting and title checks on 127.0.0.1:25578, plus a short road/cart/shop smoke test in Survival. This local integration copy is stopped with rockets disabled. The separate active quest server on 25581 has not been interrupted or reconfigured; its older rocket setting is not the release setting.

In PebbleHost, prepare one-off scheduled tasks for 30 September, verify the panel timezone and the displayed next-run dates. The account's exact action choices/timezone have not been inspected. Use host-panel power actions for Stop and Start, not a Minecraft console `start` command. Ensure Start is allowed to execute while the server is offline. Check that watchdog/auto-restart settings will not immediately undo a deliberate scheduled stop. Test a harmless scheduled console message while present to establish the scheduler clock; do not test by unexpectedly stopping production.

Recommended schedule, if separate Stop and Start actions exist:

| UK time (BST) | UTC on 30 September | Action |
|---|---|---|
| 01:50 | 00:50 | Optional warning: update shutdown in five minutes |
| 01:54 | 00:54 | Optional one-minute warning |
| 01:55 | 00:55 | Graceful panel Stop; allow time to save |
| 02:00 | 01:00 | Panel Start, provided the process has fully stopped |

Use Europe/London if supported. These UTC conversions are for this date; they change after British Summer Time ends. Make these one-off tasks, or disable any temporary recurring tasks after the release. The start should happen at 02:00; players will be able to join after startup completes. A restart first triggered at 02:00 is too late to guarantee that the old plugin has not frozen the old quest set for the new week.

If the panel only offers Restart, use a restart before the boundary (for example 01:55) with the candidate queued. Quests still refresh at 02:00, but road/rail/TAB/title changes become active a few minutes early. If exact 02:00 feature activation matters, use the separate Stop/Start route or arrange a staff member to perform the start. Do not invent an unsupported panel action.

## Before bed: backup and staging

1. Stop normally and wait for the process to finish. Take a full matched backup of worlds/player data, plugin data/databases, JARs and configuration. Verify it completed, preserve the old NookCore JAR and the individual config files for a code/config rollback.
2. Start again with the ORIGINAL JAR and ORIGINAL configs. Confirm NookCore, TAB, shops and current quests are healthy before staging anything. The old JAR cannot start with the selected diamond/multiple-contribution quest config.
3. Upload only the prepared NookCore JAR into **plugins/update/NookCore.jar**. Do not overwrite the JAR in plugins while it is running. Use the existing live plugin filename; the verified snapshot uses NookCore.jar. The snapshot's bukkit.yml has settings.update-folder: update; verify production still matches. Upload fully before considering staging complete.
4. Merge nookcore-additions.yml into **plugins/NookCore/config.yml**, preserving unrelated live settings and avoiding duplicate keys. In particular travel.grounded.enabled is true, roads/minecarts are enabled, and travel.grounded.elytra.enabled is false. The additions file is a fragment, not a complete replacement config. Never copy the isolated test server's complete configuration to production.
5. Upload the selected **quests.yml** to **plugins/NookCore/quests.yml**. Compare/apply the supplied TAB changes to the live **plugins/TAB/config.yml** and **groups.yml**. These are configuration files, not new TAB JARs. Preserve any production edits made since the snapshot.
6. Recheck uploaded sizes/hashes and the scheduler's next-run times. Do not use /reload, /tab reload, activate-config, or config-changing administration between staging and the restart: the new configs are intended for the next start and some commands can overwrite them from memory.

Staging in plugins/update means the next start installs the JAR, including an unexpected restart before 02:00. This plan does not have an activation-time gate. The running plugin retains the old quest definitions in memory until shutdown; the new JAR/config will open the selected rotation on the first start at/after 02:00.

The midnight backup is a valid fallback, but a full restore from it would lose activity since midnight. Do not restore only an older economy database against newer player inventories. If the host can create a fresh stopped-server backup between Stop and Start, use it only when startup can wait for confirmed completion; do not assume a five-minute backup deadline. A JAR/config rollback normally preserves current data.

## At 02:00

The owner is available overnight but would prefer to sleep. Ideally have one senior admin watch startup. They should check NookCore and TAB enabled without errors, /quests shows the seven selected goals, /nooks and one ordinary shop purchase work, and TAB/Travel look right in Survival. No messages to admins or live scheduler changes have been made by the assistant.

The quest refresh needs no scheduled quest command. It follows Wednesday 02:00 Europe/London automatically. If the server is stopped across the boundary, the correct week is opened on startup. Never run activate-config as a routine weekly reset.

## Evidence and limits

The exact old live-version JAR (73338ca) was started locally. The candidate was queued in plugins/update while that old JAR remained unchanged; configs were staged; graceful shutdown preserved them; the next start promoted the candidate and consumed the queued JAR. Both runs enabled all local gameplay plugins and stopped cleanly. The final local config had rockets disabled. This proves the Paper/config sequence, not the unseen live host scheduler, watchdog or timezone. SCHEDULED-REHEARSAL-RESULT.json records the evidence.

Paper update-folder documentation: https://docs.papermc.io/paper/updating/
PebbleHost scheduled-task guide: https://help.pebblehost.com/en/minecraft/how-to-set-up-scheduled-tasks

## Launch copy and player guides

See Discord launch scheduling.md for the prepared nikos news command mentioning @nookling, plus the private-channel formatting check. No message is posted or scheduled. Road guide.md and Weekly quest guide.md are ready as companion posts.
