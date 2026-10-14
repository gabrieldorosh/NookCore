# Temporary recovery mode

Install individual files with the server stopped:

- NookCore.jar goes to plugins/NookCore.jar, replacing only that file.
- recovery-mode.flag goes to plugins/NookCore/recovery-mode.flag. Add this one file; do not replace the NookCore folder.

Keep the current plugin data and downloaded evidence. If you moved NookCore.jar aside, restore it using this build. Keep WorldGuard, WorldEdit, LuckPerms, TAB, DiscordSRV and InvSee++ enabled. ChestShop can remain installed: recovery mode disables it automatically. FancyNpcs is not needed during recovery; Niko/Pip do not initialise.

On startup, nookadmin status must say PAUSED and temporarily disabled for data recovery. Test /staffmode, /sc and /invsee. /nooks, /baltop, /plots, /quests, /nookshops and /bunny return a temporary-disabled message. Vault credits and debits fail. The database is never opened; no joining bonuses, payouts, rent ticks, quest rotation or shop settlement runs. Status is read-only. Normal configured chat, staff tools, travel and selective mob protection remain available. Balance_whole displays Paused; numeric balance stays zero solely for sorting, not a recovered balance.

Existing marked native shop containers and their labels cannot be opened or broken. Hopper transfers, explosions and piston moves affecting marked shops are blocked. WorldGuard regions are left untouched: no reconciliation against the empty database. This cannot reconstruct lost plot configuration or missing shop markers. Keep the shopping district closed until recovery is reviewed; do not claim all old rental permissions have been restored.

Keep recovery-mode.flag present when restoring NookCore files from the backup. Do not overwrite newly saved staff return positions or chat preferences with older versions without review. Players should leave staff mode before restoration. Remove the marker and restart only after the restored database, settings, shops and quest plan are approved. Do not /reload.

Shop reconciliation: the backup restores receipts and virtual stock only up to its timestamp. Subsequent item purchases, deposits, withdrawals and barter payments require separate evidence. Console command logs alone do not prove success. Request ChestShop transaction logs if applicable, CoreProtect container evidence where available, and player confirmations. Unresolved shops must stay unavailable; replaying older stock can duplicate already sold/withdrawn items. Advancement completion files help compare missing rewards but do not by themselves establish that every completion was eligible for payment.
