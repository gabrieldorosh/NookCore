# Pip banner identity — isolated development checkpoint

Pip banners now use the existing TileState saved-item path used by collectible heads. Placement saves exactly one original item, including its name, lore and custom data. Breaking replaces at most one already-produced, matching one-item banner drop. Standing/wall direction does not change identity. Base colour and ordered pattern layers must match both the saved block and vanilla drop; stale/corrupt data falls back to vanilla. Ordinary banners are unaffected.

The existing cancelled-event behavior remains: no extra drop entities, no synthetic creative drops, and no bypass of cancelled protection/drop events. Saved banners receive the same explosion and piston protection as saved heads. State is on the banner block entity; there is no cache.

These are copyable design tags, **not authenticated original or limited-edition ownership**. Local inspection of Paper 26.3 BannerDuplicateRecipe confirms vanilla duplication can preserve original item components, including Pip tags. This branch deliberately leaves crafting unchanged. Pattern editing while the banner is an item is preserved as that placed item variant; external changes to an already placed banner prevent restoration of stale metadata.

Offline compilation and all 17 focused checks passed: four banner policy tests and 13 existing PipProducts tests. The pure checks cover all sixteen base colours, standing/wall forms, exact ordered layers, wrong types and missing identity. Actual Paper placement/drop events, chunk unload/reload and full restart remain required before merging or deploying; no server was launched for this checkpoint.

## Paper verification â€” 4 November 2026

The later isolated proof passed: 17 focused Maven tests and 107 Paper 26.3 build 237 banner/event/lifecycle checks (82 before shutdown, 25 after a clean restart). The existing 39 product factory checks passed on each boot. No production fix was needed.

Standing/wall exact metadata, one existing drop only, cancellation, WorldGuard denial, Creative empty drops, ordinary/wrong/stale/corrupt variants, explosion/piston rules and the existing head path passed. The probe observed an actual chunk unload, checked exact bytes after reload, and checked standing/wall/head bytes and drops again in a second process. Real server inventory-menu normal and shift-click duplication retained Pip tags, as expected for copyable designs.

Evidence is under `D:/Documents/NikosNook/output/evening-20261104/banner`, especially `Verification.md` and `banner-paper237-20261104-115633-895300/RESULT.json`. The frozen clean `88a8dd4` jar SHA-256 is `a3ec501f852be96eada780b30ffc8559f6b757eaeef367b8062696ef84abee11`; the installed fixture jar matched. Both processes exited cleanly; loopback port 25621 was free afterward. Existing fixtures and the accepted game candidate were untouched.

Events were explicitly dispatched against real server blocks and a genuine CraftPlayer; crafting used server menu operations. No connected client proof was attempted. Combined regression, integration and deployment remain separate work.
