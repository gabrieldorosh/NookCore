# Staff-mode return confirmation

Upload only `NookCore.jar` to the live server's `plugins/NookCore.jar` while the server is stopped, then start it normally. Keep the existing `plugins/NookCore/` directory, especially `staff-mode.properties`, and all other data/configuration files.

After reconnecting, qahar0 should run:

```text
/staffmode off confirm
```

This returns to the position saved when staff mode was entered and restores the saved game mode, flight flags and visibility. It explicitly bypasses the landing safety check, so the saved position can cause a fall or suffocation. Normal `/staffmode off` and the no-argument toggle keep the safety check.

No new permission is needed. The command only exits the executing player's existing staff session; it does not let regular players enter staff mode or override another player. A staff member whose permissions were revoked can still exit their saved session, as before. The override attempt is recorded in the console with the player's name and destination.

If another plugin cancels teleportation or game-mode restoration, staff mode remains hidden and the return record is retained. An unavailable return world still requires administrator repair.

## Quick checks

1. qahar0 reconnects after the update, runs `/staffmode off confirm`, returns to the saved position and game mode, and becomes visible again in-world and in TAB.
2. From solid ground, enter staff mode, move elsewhere and use `/staffmode off`; ordinary return still works.
3. On a disposable test, enter staff mode and remove the block supporting the saved return. `/staffmode off` must keep the player hidden and show the confirmation command. Only use the confirmation when you accept the landing risk.
4. `/staffmode off c` completes to `confirm`. An invalid word after `off` must show `/staffmode off [confirm]` and keep the session intact.

No database, quest, NPC placement or configuration replacement is required.
