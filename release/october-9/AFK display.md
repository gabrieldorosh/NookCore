# Optional AFK display — October 9

Includes the October 9 shop fixes, the rent-cut commands and mr. duck's stronger pastel red. The earlier local-update-20261009-shops JAR remains available unchanged.

Upload this NookCore.jar as plugins/update/NookCore.jar instead of the earlier JAR, then restart normally. Only one NookCore JAR should be queued. Existing TAB files already use nookcore_rank and nookcore_name; no TAB/config replacement is required. Keep live quests, TAB currency symbol and unrelated config.

After five minutes on Paper's native inactivity timer, the title and name placeholders turn soft grey (#AAAAAA). The existing five-second snapshot refresh and TAB's own refresh control the visible delay. Player activity restores their saved colours. Chat remains normally coloured, Nooks sorting/balances stay unchanged, and this feature sends no AFK messages or kicks. Paper's existing idle-kick settings are not changed. Other displays using these same placeholders will also receive their grey values.

Optional settings under the EXISTING chat section of plugins/NookCore/config.yml:

```yaml
  afk:
    enabled: true
    after-seconds: 300
```

Missing settings use these defaults. Values below 30 seconds clamp to 30. Restart after changing them. Set enabled to false to disable the display effect.

## Client check

1. Confirm the usual title/name colours in TAB after joining.
2. Leave all controls idle for five minutes; allow roughly another 5–10 seconds for display refresh. Title and name should become grey.
3. Move or interact, then check that the original colours return within roughly 5–10 seconds. Repeat with chat activity.
4. Confirm balances, richest-first sorting and the currency symbol remain as before. Check a mounted player on Java and a Bedrock player when available; activity detection follows Paper's native timer, not a custom anti-AFK detector.

405 automated tests passed. The isolated Paper/PlaceholderAPI probe checks controlled idle durations, title/name greying, restoration, normal chat colours, balance/pronoun preservation and quit cleanup. This does not replace a real-client input/rendering check. Earlier shop checks belong to fe9e759; shop logic is unchanged.

If the visual check fails, use the preceding local-update-20261009-shops/NookCore.jar at the next normal restart. No database migration is added by the AFK feature, and reverting it does not reverse any rent cut already applied.
