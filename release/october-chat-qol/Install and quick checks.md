# Quick maintenance update — 2 October

Includes the miss bee title correction and configurable private keyword tips for lag and staff help. Based on the September 30 release. No ChatControl installation is needed. No AFK, vanish, NPC quest selection, economy metrics or unfinished paid perks are included.

## Install

1. Keep a backup of the current NookCore.jar and config.yml.
2. Upload this NookCore.jar to plugins/update/NookCore.jar and perform one normal graceful restart. Alternatively replace plugins/NookCore.jar while the server is stopped. The host's restart command has a one-hour warning period: allow for that if scheduling it. Do not use /reload.
3. Keep the existing config.yml, quests.yml, TAB files, worlds and databases. No quest activation/reset command is required.

The existing mrs-bee account mapping now displays exactly "miss bee" in pastel yellow. No config edit or permission change is needed. A new miss-bee alias is also accepted, but keeping the old key makes rolling back easier.

Keyword help is enabled through bundled defaults even when the existing config has no help-replies section. It replies only to the sender, leaves public chat intact, and does not ping staff or execute commands. It matches whole words, case-insensitively: lag/laggy/lagging and admin/admins/staff/helper/helpers. One reply per message, five minutes per player/topic, and a minimum ten seconds between topics. Session cooldowns clear on disconnect/restart. Ordinary mentions also trigger tips; this is literal matching, not intent detection.

To customise, merge the contents of chat-settings-fragment.yml into the EXISTING chat section. Do not add a second chat key or replace the entire config. Set help-replies.enabled to false to disable tips; restart after edits. Reply strings are plain text. If a help setting is invalid, the helper disables itself and logs a warning; normal chat still works. Installing ChatControl disables the existing NookCore chat formatter, so do not add it alongside this update.

## Two-minute live checks

- Moon's title is [miss bee] in chat and TAB, yellow as before. Her permissions, name colour and pronouns remain correct; duck/niko titles remain correct too.
- With a second player watching, type "LAG!". The original chat message remains visible, but only you receive the NookHelp tip. Repeat "laggy": no second tip. "flag" should not trigger it.
- After ten seconds, type "admin". Receive the private help message without a staff ping. Check normal Minecraft/Discord chat still flows once. After five minutes the original topic should work again.

Local automated checks do not replace a real Java/Bedrock client and Discord bridge check. No production change has been performed by the assistant.

## Rollback

Stop the server normally and restore the previous NookCore.jar; remove any still-queued maintenance JAR from plugins/update before starting. Keep current data. If you changed the optional title alias to miss-bee, restore it to mrs-bee before using the older JAR. No database schema changes are introduced.
