# Metrics update — tomorrow's short test

Use the new isolated server at 127.0.0.1:25582. Start E:/NikosNook/SeasonTwo/staging/economy-metrics-20260930/start.ps1. It is a fresh test world with copied local whitelist/ops; external services are absent. Existing Travel and quest servers are untouched. This server contains synthetic probe counters under UUID 00000000-0000-0000-0000-000000000082; exclude those from real pricing. Do not upload its database or config.

M01 — Admin: run /nooks report 1, /nooks metrics 1 and /nooks metrics export 1. Confirm readable replies and a CSV under plugins/NookCore/reports. The report uses UTC daily buckets, not the Wednesday quest period.

M02 — Survival in Overworld: note counts, walk and mine three diamond ore blocks plus four oak logs. Run /nooks metrics 1 again; the totals should increase. Check the export's material rows and produced drops (Fortune/Silk Touch as desired). Compare /nooks before/after: metrics never charges or rewards you, though normal quests/advancements still may.

M03 — As a non-op account without nookcore.admin, both report and metrics commands must deny access and offer no admin completion. Creative harvesting must not add gathering counters. Stand still without interacting for over 60 seconds: active time should stop while sampled Survival time continues.

M04 — Stop normally, start again, and repeat the report. Counters remain. Check that ordinary shops/quests/travel are unaffected on a later current-world rehearsal; the fresh world does not prove live rental behaviour.

M05 — Invalid inputs (0, 91, decimals, extra arguments) should return guidance. Repeated export requests should not freeze play; each accepted export gets its own file.

Only after acceptance, install this maintenance JAR using a matched backup and controlled restart. It includes tonight's release code; do not put it into tonight's already staged update folder. No new gameplay or Nook charges are enabled by this build. Existing release configs remain valid. Collection defaults on; economy-metrics.enabled: false disables it at startup. /nooks report remains read-only and available independently.

Before deploying, check tonight's actual release succeeded. Keep its exact final config and quests. No production files or scheduled tasks have been changed by the assistant.
