# Editing Niko's quests and custom advancements

## Quest bank

Edit the existing live plugins/NookCore/quest-rotation.yml. Keep its root sections enabled, starts, regular and challenges. Each entry has a stable lowercase ID. Copy an existing entry in the same section and change its title, kind, target and amount. Example under regular:

```yaml
  contribute-honey-bottle:
    enabled: true
    title: Contribute 16 honey bottles
    kind: DEPOSIT
    target: HONEY_BOTTLE
    amount: 16
```

Use enabled:false to leave a draft out of the pool. Existing supported kinds are MINE, KILL, FISH, BIOME, DEPOSIT, HARVEST and TRADE. Use an existing entry as the template for its target syntax; a new kind needs a tracker in the plugin. Rewards are fixed by section: regular15Nooks, challenge30Nooks.

After saving:

```text
nookadmin quests validate
nookadmin quests reload
nookadmin quests next
```

Validate checks entries and the next eight weeks without publishing them. Reload accepts only a valid candidate and changes future unsaved weeks. The current saved board, choices, progress and payouts remain intact. A failed reload keeps the loaded configuration; fix the identified entries and retry. Use validation before restarting with edited files. These commands are admin-only.

The scheduler treats the same kind+target as one family. Different amounts/titles do not make a second family; regular/challenge variants cannot occur together or on consecutive boards. Keep IDs stable when editing the same task; use a new ID for genuinely new meanings. Do not use activate-config for normal editing: that is the older explicit current-week replacement tool, not the future-bank reload workflow.

## Advancements

plugins/NookCore/advancements.yml is installed automatically if missing. New definitions use a stable nikosnook path, title, description, vanilla item icon, event and enabled/show-toast flags. Events currently supported: QUESTS_CHOSEN, PIP_VISITED, COMMUNITY_CONTRIBUTION and WEEK_COMPLETE. These are cosmetic: no money or command reward fields are supported in this catalogue.

Adding a new ID for an existing event needs no JAR rebuild. New action counters/events still need tracking code. Restart to load changes; there is no advancement reload command in this iteration. Disabled definitions stop granting and retain existing completion history. Existing installed title/icon/description changes are warned and retained for a reviewed migration; the loader does not delete/recreate earned advancement trees. Keep IDs and criteria stable.

Owner checks: confirm each toast/entry once, reopen menus/restart without duplicate rewards, confirm a Pip admin preview does not count as a visit, confirm a failed contribution does not grant the contribution milestone, and complete a selected week. Existing choice progress must survive the upgrade. Check actual Java client display; the automated server probe is not a client visual check.
