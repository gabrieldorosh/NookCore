# Staff mode — test candidate, 10 October

This build adds hidden spectator tools to the consolidated October 10 maintenance build. It includes the shop fixes, activity metrics and AFK display changes. It is not installed on production. Keep feature/staff-mode separate from main until client acceptance.

## Install

Back up the current JAR and NookCore data. Queue this NookCore.jar as plugins/update/NookCore.jar for a normal restart. Preserve config.yml, quests.yml and TAB files. Do not activate or replace quests. Do not use /reload.

Grant the parent permission only to intended staff accounts/groups. From console, for example:

```text
lp user YOUR_MINECRAFT_NAME permission set nookcore.staff true
```

Replace the name; repeat for approved helpers/admins, or apply to an existing staff group after checking its members. This grants staff mode, seeing hidden colleagues, teleport/spectate, read-only inspection and TAB's see-vanished permission. It does not grant Nooks administration or the dimension-lock bypass. Cosmetic titles alone do not grant staff tools. Operators already have access.

## Use

Start from a safe, grounded position in your normal game mode:

```text
/staffmode on
/staffmode tp PlayerName
/staffmode spectate PlayerName
/staffmode inspect PlayerName
/staffmode list
/staffmode off
```

Entry saves your original position/game mode/flight settings before hiding you and entering spectator. Other authorised staff can see you and an [on duty] TAB marker (allow a few seconds for refresh). You can see regular players; they cannot see you. Public chat and /me or /say are blocked while hidden. Inventory inspection is a read-only snapshot; reopen to refresh it.

Off returns you to the saved position and original game mode before revealing you. If the landing is unsafe, the world unavailable or teleport blocked, you stay hidden with the return point retained. Clear the landing and retry. Existing dimension locks still apply to staff teleports; only the exact saved return bypasses them. Staff permission removal still permits /staffmode off.

Logging out while on duty retains staff mode for the next login, including across a restart. A normal login before first enabling staff mode is still visible. Staff mode cannot erase notices already sent. Avoid other vanish plugins on the same account.

## Map, voice and Discord

Before relying on this for moderation, check the production settings:

- BlueMap: ensure `spectator` is in `hidden-game-modes` in plugins/BlueMap/plugin.conf, retaining other entries. For example `hidden-game-modes: [ "spectator" ]`. BlueMap's hide-vanished setting alone refers to potion invisibility, not this staff mode. Reload BlueMap using its normal configuration procedure or include the edit in the restart. https://bluemap.bluecolored.de/wiki/configs/Plugin.html
- Simple Voice Chat: in plugins/voicechat/voicechat-server.properties retain `spectator_interaction=false` and `spectator_player_possession=false`. The archived server settings have both false; current production settings still need checking. Mute your microphone and leave public voice groups during the first test; group membership and other client integrations are not covered by Bukkit hiding. https://modrepo.de/minecraft/voicechat/wiki/server_config
- DiscordSRV 1.30.5: this build registers a vanish hook for its player lists and join/quit handling. The optional API bridge was checked against the archived installed JAR without connecting a bot. Confirm the startup log says `Staff mode connected to DiscordSRV vanish filtering.` If integration cannot initialise, new staff-mode entry is blocked. Live Discord delivery/presence is still a manual test.

## Client checks

Use a normal non-op, non-staff observer account and a second authorised staff account if available. The observer must not inherit wildcard or vanish-view permissions.

1. Enable from Survival. The regular account must lose your TAB row and not see your body, nametag or spectator head; check while that account is in spectator too. Staff must see you and [on duty].
2. Teleport to and spectate the regular player. They must see no teleport/spectate notice, particles or visible staff head. Check /list, player-name completion, server-list hover/count, Discord's player list and the map.
3. Inspect their inventory; try click, shift-click, number keys and drag. No items should move or duplicate. Confirm their actual inventory is unchanged.
4. Leave staff mode after moving elsewhere. Confirm original position, game mode, flight and visibility return. No staff entry/exit messages should reach regular players.
5. Log out while hidden, log back in, then repeat across a normal restart. Check no join/quit notices appear in Minecraft or Discord. Confirm /staffmode off still returns to the original point. This client/restart check is not replaced by the automated persistence tests.
6. Ordinary players must be denied /staffmode. A helper with only nookcore.staff must be able to use tools but not economy admin or closed-dimension bypass. Recheck ordinary AFK greying/chat and a normal shop purchase/shift-stock deposit.

Do not treat this as undetectable against every plugin or modified client. Custom player lists, Discord status templates and voice groups need checking on the actual stack. Avoid public staff commands/messages while observing.

## Evidence and rollback

443 unit tests, 30 focused staff Paper checks and 15 AFK/PAPI regression checks pass. Paper checks use simulated player adapters with real Paper events/inventories and the DiscordSRV API; they are not network-client or full connected Discord tests. TAB loads in the staff harness, but client packet/rendering acceptance remains manual. Host logs contain Windows performance-counter/native-library cleanup warnings, not failed NookCore event checks. Test servers are stopped.

Before rolling back to the maintenance JAR, every on-duty account must run /staffmode off and log out normally. Back up plugins/NookCore/staff-mode.properties with player data; never delete it to clear staff mode. Older JARs do not restore these return points or hide saved spectators. If an account cannot exit, keep this JAR and repair the landing/world/permission issue before rollback.
