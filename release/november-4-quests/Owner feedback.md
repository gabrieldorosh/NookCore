# Owner feedback — 3 November

T01–T09 passed. T10 was skipped. The first fixture and its completed player progress are retained.

Implemented for follow-up testing: nine regular offers plus two challenges, still choose six plus one; enchanted selected books; concise contribution hint and refresh heading; completed-week recognition; Pip's private rotating greeting with a five-minute cooldown; advancement chat announcements, celebrations and approved one-time rewards.

The approved one-time rates are 0.75 Nooks for choosing quests, visiting Pip and contributing, and 3 Nooks for completing a week. Missing config keys use these defaults through the existing durable award ledger; explicit config values win. Existing completions are not backfilled. Weekly quest payouts remain 15 Nooks per regular quest and 30 per challenge.

Reward tiers are a discussion item, not a configured change. Recommendation: keep fixed payouts for the first selection week, then compare offer pick rates and completion effort. Players are likely to optimise Nooks per minute; a high payout alone does not establish a fair difficulty band. An easier quest may be preferred when time is short, even at a lower reward.

Optional Pip sitting uses the supported FancyNpcs pose at her saved anchor. It defaults off for existing live stalls. The follow-up fixture enables it: stand within eight blocks of visible players, sit after ten quiet seconds, avoid hidden-staff/spectator reactions. Client checks must verify cushion height, clipping and clean reappearance. It does not add a physical cushion build.

Niko roaming is queued separately. FancyNpcs has no built-in pathfinding/waypoint system. Use a small owner-approved public path route with pauses, no pathfinding through arbitrary player plots, a stop while someone uses his menu, current-position interaction checks and safe home recovery on obstruction/unloaded chunks. Do not enable movement around a fixed interaction anchor.

Future custom advancements should include server life and infrastructure: first player-shop purchase, contributing to a community project, a meaningful road journey and a railway journey. Travel counters need persistence and teleport/lag filtering; avoid adding several titles for the same immediate event. Coordinate later themes with each Wednesday drop.

All follow-up F01–F06 passed on 3 November. The final requested style change keeps only the NookQuests label bold/gold; the refresh suffix is grey and explicitly non-bold. The owner is preparing the live JAR-only deployment. Optional Pip sitting still requires its existing-file opt-in.
