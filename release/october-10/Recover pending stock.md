# Resolve Moon's stock receipt when she returns

Current evidence: one enchanted book was attempted for the shop in honey-place. Receipt 4fb02739-b8d2-4e60-a021-0199259bfece is REVIEW, has no stock credit, and the shop currently had zero stock at 14:01 on October 10. Moon subsequently confirmed that she lost the book and is blocked from stocking by the pending receipt. Use the credit path below, provided nobody has separately replaced the book.

This follow-up JAR includes the inventory verification fix and adds an admin recovery command. The earlier local-update-20261010-shop-inventory JAR remains usable while waiting. There is no need to restart urgently just because Moon is offline.

## Install when convenient

Take a current backup, replace the queued plugins/update/NookCore.jar with this package's JAR, and restart normally. No config, quests or TAB replacements are required. AFK greying and unfinished paid perks are not included. Startup alone never resolves a receipt.

## Choose only after confirming the outcome

If Moon confirms the book is missing and nobody has replaced it, run this in console:

```text
nookshops recover-stock 4fb02739-b8d2-4e60-a021-0199259bfece credit Moon confirmed the book is missing and has not been replaced
```

This only previews adding the recorded one book to the original shop's virtual stock, preserving its exact enchantments and other metadata. It does not give an additional book to her inventory or remove anything from her inventory.

If she still has the book, run this instead:

```text
nookshops recover-stock 4fb02739-b8d2-4e60-a021-0199259bfece no-credit Moon confirmed she still has the book
```

If staff already replaced it, choose no-credit with that fact as the reason instead. Never choose credit as well as manually giving a replacement. If she is unsure, leave the receipt pending; neither action can infer the original inventory outcome from the saved hashes.

## Confirm the displayed choice

Check the preview's action, player, quantity, plot and reason. Within 60 seconds, from the same console/admin:

```text
nookshops recover-stock 4fb02739-b8d2-4e60-a021-0199259bfece confirm
```

The command first creates a SQLite backup under plugins/NookCore/backups, then atomically checks the preview and records the correction. A changed receipt/shop stock requires a fresh preview. Repeated confirmation cannot apply it twice. Missing/failed backup prevents recovery. Services must be available; the command does not clear a global economy failure.

Then inspect:

```text
nookshops review 4fb02739-b8d2-4e60-a021-0199259bfece
```

Expect COMPLETE with an explicit staff resolution and audit reason. Credit records one item credited; no-credit records no new stock. The pending lock for this receipt is released, allowing Moon to use the shop normally unless another receipt requires review. If the original shop is closed, credited stock remains with that original offer for its owner to collect.

417 automated tests pass, including both recovery outcomes, permission/actor/expiry checks, backup contents, restart/repeat protection, stale previews, capacity, and transaction rollback. The package also includes the isolated console-command test result. No recovery has been applied to the live receipt.
