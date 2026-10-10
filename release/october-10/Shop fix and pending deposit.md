# Shop inventory verification fix — October 10

This builds on the October 9 shop release with mr. duck's stronger pastel red (1891875). It includes the earlier sign/rent fixes and October 7 quest/chat changes. AFK greying and unfinished paid perks are not included.

## What failed

At 13:11:08, MoonCheese213's stock deposit stopped with `Inventory changed before save`. Receipt: `4fb02739-b8d2-4e60-a021-0199259bfece`; player: `983f1c6c-7f17-4729-ab16-8c0d3407592a`. The owner confirmed the earlier October 9 shop JAR is installed, and Moon was stocking enchanted books.

The deposit wrote a REVIEW receipt and attempted the inventory removal, but failed before the explicit save and before crediting the shop. Later autosaves or logging out may have saved the removed items. The log alone does not establish which items Moon currently has. Her receipt must not be replayed or deleted.

On the same Paper 26.2 build 126, the old JAR reproduced false mismatches: serialization can reorder NBT component entries without changing the item. An unrelated item in the player's storage inventory can trigger the check. The reproduction found 54 byte differences and no semantic differences across 5,484 checks. This demonstrates the failure mechanism; Moon's exact inventory has not been inspected.

The fix compares each storage slot's presence, item components, amount and stack limit before and after saving. It retains the existing receipt hashes, player save marker, disk acknowledgement and fail-closed handling for real changes. No inventory replay, refund or database migration is performed.

## Install

1. Take a current matched backup of the server/player data and NookCore database.
2. Queue this `NookCore.jar` as `plugins/update/NookCore.jar`, replacing any earlier queued NookCore JAR. Keep only one queued version.
3. Restart normally. Keep the live config, quests, TAB, Nether settings and rent data. Do not reset quests, rerun the rent cut or use `/reload`.

A successful clean startup clears the in-memory global pause. Moon's stored REVIEW receipt remains and prevents another inventory operation for her until recovery is reviewed. Installing this fix does not resolve that existing deposit.

## Inspect Moon's deposit

Run in console without a leading slash:

```text
nookshops review 4fb02739-b8d2-4e60-a021-0199259bfece
```

This new read-only command shows the recorded quantity, shop, plot, receipt state, stock credit and original inventory hashes. Send the output back, together with whether the books disappeared and whether anybody has already replaced them. The printed item label is the material name; the receipt retains the exact encoded book metadata for later recovery.

Do not restore an old whole-server/database backup over subsequent player activity to repair this one receipt. Any later staff correction must be backed up, audited and applied at most once after the item outcome is established.

## Small live check

Use an unaffected owner/account, with a named or enchanted item also in their inventory. Deposit one ordinary test item, then withdraw it. Confirm inventory and shop stock each change by exactly one and no global pause occurs. Follow with one enchanted-book deposit/withdrawal and, if convenient, one small purchase from another player. Moon remains excluded from these tests until her pending deposit is reconciled.

406 automated regression tests passed. The isolated Paper probe passed 5,495 checks covering the reproduced encoding cases, rejection of real quantity/component/slot changes and a full enchanted-book custody/save/credit transaction. The local server was stopped after verification; see the adjacent result files. A real-client UI check remains.
