# Niko and Pip — follow-up acceptance

The first owner checks T01–T09 passed on 3 November; T10 was skipped. This revision addresses that feedback. Use the fresh local follow-up fixture listed in the candidate package; keep existing data intact.

1. A new unsaved board has nine regular offers in a full row and two challenges. Select six plus one. Regular books become enchanted books when selected. A seventh regular is refused; switching challenges replaces the previous choice.
2. Previously frozen eight-plus-two boards and seven-task shared weeks retain their definitions, choices, progress and rewards. Deploying this JAR does not expand or reset those weeks.
3. Confirming choices awards a little choice once: chat, toast, quest-style celebration and 0.75 Nooks. Reopening does not repeat it.
4. Pip's first ordinary click greets the player privately. Reopening/quantity returns respect the five-minute per-player cooldown. Admin previews do not greet or award just browsing. The ordinary first visit pays 0.75 Nooks once, with chat and effects; no purchase required.
5. A successful selected contribution awards a helping hand once, with 0.75 Nooks alongside its existing quest reward. Failed/unselected/repeated hand-ins take nothing and grant no extra milestone. Review whether overlapping quest/milestone effects are too busy.
6. Completing all seven personal tasks awards all in a week's work once, with 3 Nooks and a stronger celebration. Niko recognises the completed week and invites the player back next week. Unselected tasks do not block completion.
7. The four milestones total 5.25 Nooks once per player. Weekly quests remain six × 15 plus 30, maximum 120 per week. Reconnect/restart/menu reopening cannot pay or celebrate again. Existing completed advancements are not automatically backfilled.
8. /quests shows the refresh date in its heading and a concise contribution hint. Check Java client readability, skin interactions and the custom advancement tab.
9. Optional Pip sitting: pip-idle.sitting-enabled defaults to false in spawn-npcs.yml. When enabled, she sits after 10 seconds without a visible player within 8 blocks, stands within about half a second on approach, and stands on click. Check carpet/cushion height, skin/seat cleanup, repeated approach and restart. Hidden staff/spectators must not wake her. Animation failures must not affect shopping.
10. nookadmin quests next previews nine regular offers and two challenges without current/within-board target repeats. Validate/reload checks remain admin-only, preserve current snapshots and keep the loaded bank after a bad edit. The previous automatic schedule and 02:00 UK reset remain.

Niko roaming remains planned: a bounded owner-approved route, pauses while interacting, obstruction recovery and actual-position interaction checks. No roaming is enabled here.

Deploy only NookCore.jar after acceptance and a fresh backup. Preserve the entire existing NookCore data/configuration directory. Missing custom reward entries use approved defaults automatically; explicit config values override them. No config folder replacement is required.
