# Niko quest selection — client checks

Development candidate, not a confirmed live release. Choose six of eight regular quests and one of two challenges; rewards remain six ×15 plus30 Nooks. The current accepted shared week is preserved. Automatic new offer boards begin at the next eligible unsaved week from4November02:00UK.

Use the separate generated quest-selection playtest, not the old travel server. Its start script and address are recorded in the staging package. Keep it local. Its bank start date is deliberately earlier for testing today's board; never upload its configuration, worlds or databases to the live server.

1. Open Niko: eight regular tasks, two challenges, zero selected. Closing without confirmation leaves quests unaccepted and /quests directs you to Niko.
2. Choose six regular quests. A seventh is refused. Tap a choice to deselect it; select another. Switch between the two challenges and confirm only one remains selected. Selected items have a tick/glint as well as colour.
3. Confirm an incomplete selection: no acceptance or progress. Confirm six-plus-one, including repeated clicks: /quests shows only those seven tasks. Acceptance itself pays nothing. The 'a little choice' advancement should appear once; its extra Nooks reward is zero by default.
4. Reopen Niko: saved choices and current progress are visible, with View my progress. Choices cannot be changed after confirmation. Reconnect and restart; both choices and progress remain.
5. A second account chooses a different six-plus-one set. Progress on an unselected task pays nothing. Check a selected combat/mining/crop/trade task and the contribution flow if offered. Contribution completion and its reward happen once; an unselected contribution should not take items or appear in your contribution choices.
6. Leave the menu open and move away from Niko. Selection/confirmation is refused. Shift-click, number keys, dragging and double clicks must not move menu items into your inventory.
7. On a disposable database, test the02:00 roll and leave an old menu open. It must say quests refreshed and accept neither the old nor new week. The saved prior week remains available to staff inspection without resetting rewards. Do not change the production clock for this test.
8. Check Java and Bedrock menu interaction and advancement presentation. FancyNpcs skins and actual NPC clicks are owner/client checks beyond the synthetic inventory-event probe.

Read-only live checks: `nookadmin status` and `nookadmin quests next`. Once this candidate is installed, next-week preview lists ten offers, not ten payable quests per player. No manual activate-config command is required for normal rollover.

Admin fallback: `nookadmin npc accept <player>` lists the offers and their IDs for a choice week. After asking the player, use `nookadmin npc accept <player> <seven,comma,separated,IDs>` with six regular IDs and one challenge ID. This is logged and cannot replace an accepted selection. Legacy shared weeks retain the existing fallback.

Deployment is JAR-only after acceptance and a fresh backup. Preserve every live NookCore file/data directory. New quest_choices schema and the cosmetic advancement definitions are installed automatically. Deploying after a shared week has already been frozen will preserve that set; it will not silently convert everyone's current quests into a new board.
