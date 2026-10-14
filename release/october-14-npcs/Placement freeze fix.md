# NPC placement freeze fix — 14 October

The local Paper watchdog caught the server thread blocked in Windows CreateFile while SpawnNpcs.save opened spawn-npcs.yml.tmp during Pip placement. The OS-level reason for the stalled file operation is unknown.

NPC position saves now run on a dedicated daemon worker. An immutable YAML snapshot is written to a unique temporary file and atomically replaces the configuration. The server checks completion without waiting; only then does it apply the position and report success. A pending save prevents overlapping placements. After ten seconds it reports the delay. A failed save retains the previous position. Shutdown does not wait on stalled storage. Initial local test-start saves use the same worker.

Validation: 489 unit tests; 51 isolated Paper NPC checks. The Paper probe deliberately blocks Pip's disk writer, verifies server ticks continue and the position remains unpublished, then releases the writer and checks placement, purchases and recreation from saved positions.

Owner retest: start local-playtest, place Pip, wait for the placement confirmation, interact in Survival outside staff mode, buy one head, and restart to verify both NPC positions. No configuration replacement is needed. This remains a local NPC test build, with trial prices and stock.
