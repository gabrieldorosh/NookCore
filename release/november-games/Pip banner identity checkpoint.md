# Pip banner identity — isolated development checkpoint

Pip banners now use the existing TileState saved-item path used by collectible heads. Placement saves exactly one original item, including its name, lore and custom data. Breaking replaces at most one already-produced, matching one-item banner drop. Standing/wall direction does not change identity. Base colour and ordered pattern layers must match both the saved block and vanilla drop; stale/corrupt data falls back to vanilla. Ordinary banners are unaffected.

The existing cancelled-event behavior remains: no extra drop entities, no synthetic creative drops, and no bypass of cancelled protection/drop events. Saved banners receive the same explosion and piston protection as saved heads. State is on the banner block entity; there is no cache.

These are copyable design tags, **not authenticated original or limited-edition ownership**. Local inspection of Paper 26.3 BannerDuplicateRecipe confirms vanilla duplication can preserve original item components, including Pip tags. This branch deliberately leaves crafting unchanged. Pattern editing while the banner is an item is preserved as that placed item variant; external changes to an already placed banner prevent restoration of stale metadata.

Offline compilation and all 17 focused checks passed: four banner policy tests and 13 existing PipProducts tests. The pure checks cover all sixteen base colours, standing/wall forms, exact ordered layers, wrong types and missing identity. Actual Paper placement/drop events, chunk unload/reload and full restart remain required before merging or deploying; no server was launched for this checkpoint.
