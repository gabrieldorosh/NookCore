# October 7 update — install and announce

Use THIS package's new NookCore.jar AND quests.yml together. The October 2 maintenance JAR and September 30 live JAR cannot read the new HARVEST/TRADE quest types. This build includes the title correction and private keyword tips as well as the two new quest trackers. No NPC, paid enchantments, AFK, vanish or metrics update is included.

398 automated tests and 16 isolated Paper checks passed. Paper checks use real block age data and simulated player events; actual inventory trading/shift-click and client/Discord smoke remain manual. No production upload, restart or announcement has been performed.

## Install

1. Take a matched server backup and retain the current JAR/config/quests files.
2. Stop normally. Replace plugins/NookCore.jar with this package's NookCore.jar and replace plugins/NookCore/quests.yml with the complete quests.yml from THIS package. Start normally. Alternatively queue the same-name JAR in plugins/update before a normal restart. The host restart command has a ONE-HOUR warning countdown; it does not restart immediately. Do not use /reload.
3. Keep existing config.yml, TAB files, worlds and player/economy data. Existing mrs-bee mapping now displays miss bee automatically. Keyword defaults work without config edits. To customise merge chat-settings-fragment.yml under the EXISTING chat key and restart after editing; never duplicate the chat key.
4. Check startup and /quests. If restarted with the new file BEFORE Wednesday 7 October 02:00 UK (01:00 UTC), last week's frozen quests remain until that boundary, then the new set activates automatically. No activation command is needed just because old goals still show before reset.
5. If the NEW week already froze the old definitions before this restart, run /quests activate-config. Check that its preview matches Selected quests and checks.md, then run /quests activate-config confirm from the SAME actor within 60 seconds. This starts fresh current-week progress for everyone, keeps old rewards/balances/community stock, and allows the new goals to pay again. It saves a database backup and is limited to once per week. Do not run it repeatedly or automatically. It uses definitions loaded at startup; editing the file alone does not reload them.

## Check, then announce

Run the focused checks in Selected quests and checks.md, particularly a mature crop and a completed villager trade. Check Moon's title, private keyword tips and Minecraft/Discord chat. Verify the new set is active before sending a message saying it is live.

Inspect Announcement.md. Paste the ONE-LINE contents of Discord private test command.txt into the server console to preview in the established private channel. After the update is active, paste Discord launch command.txt once into console. No leading slash is required there. The role mention is at the END. No schedule or message has been sent by the assistant.

## Recovery

Before the new quests have activated, restore the previous JAR AND previous quests.yml while stopped if needed. Remove any queued new JAR from plugins/update before starting.

AFTER activation, do not blindly downgrade: old JARs cannot read stored HARVEST/TRADE goals. Prefer a forward fix, preserving the economy database and earned progress. Any full-backup restoration needs a deliberate decision about player activity since that backup. Restoring quests.yml alone does not reverse a frozen weekly set.
