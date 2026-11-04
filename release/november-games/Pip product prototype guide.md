# Pip stock beyond heads — development prototype

This adds a typed item factory for heads, vanilla banners and written travel journals. It does not add products to the live catalogue or change an already saved seven-item visit. All 48 existing head IDs, textures, prices, stock allocations and purchase caps remain unchanged.

The JAR contains `pip-products-examples.yml` as a reference. Its six entries are disabled: four banner designs and two short journals. After reviewing them locally, copy only chosen entries into the existing `plugins/NookCore/pip-catalogue.yml`, give each a permanent unique ID, and enable the chosen entries. Keep all existing entries and data. A new definition becomes eligible for a future unsaved visit; do not reset a current visit to force new stock. The inherited six-to-seven-slot migration can still add its seventh legacy head, but its pool excludes banners and books. Already saved seven-item visits retain their stored item bytes, prices and stock.

Supported `type` values:

- `HEAD` is the default for existing entries. It still requires the existing texture hash and uses the original head factory and placement/break handling.
- `BANNER` uses `banner.base` and up to six `banner.patterns` layers, each with a vanilla `pattern` key and `colour` dye. The sample is a white banner with a warm yellow border and orange flower.
- `WRITTEN_BOOK` uses `book.title`, `author`, `pages` and `generation`. Titles/authors are limited to 32 characters, books to 20 pages and each page to 512 characters. Use short pages for comfortable client rendering. Pages are literal text; markup does not create click actions or commands.

The journal sample has three short cozy pages by Pip and `COPY_OF_COPY`, which follows vanilla's automatic-copy restrictions. Its story can still be read and manually rewritten. Banner patterns can be copied in vanilla. These are decorative designs and conveniences; no exclusive visual appearance is promised.

While items, banners/books carry the design, rarity and product type metadata and use the existing exact-item purchase/resale path. Matching standing and wall banners retain their saved unit metadata after placement/breaking, chunk reload and a clean restart. Invalid or changed variants fall back to vanilla drops. Vanilla duplication can copy design tags too: these are copyable designs, not authenticated limited editions. Existing collectible head identity preservation remains intact.

Sample prices of 4 Nooks for the banner and 8 for the journal, and their quantities, are trial proposals only. The examples remain disabled until the owner chooses them. Seven display slots, seasonal diversity and no personal purchase caps are retained.

## Client checks

1. Preview a catalogue containing the two enabled test entries. Confirm the actual banner patterns, name, rarity and journal icon; read each page and check line wrapping and `<3`.
2. Buy one and multiple units. Verify the displayed total, actual items, remaining stock and delivery with nearly full inventories. Purchases must not charge for undeliverable items.
3. Restart and check the already published visit's saved products, stock and exact item variants remain unchanged. Changing definitions should not replace current persisted products.
4. Offer each product through NookShops, buy it from another account and compare patterns, title, author, generation, pages and lore.
5. Place/break standing and wall banners, including after restart: check the exact item details are preserved. Copies can inherit design tags; do not describe them as authenticated originals.
6. Recheck a pre-existing Pip head: place/break it and confirm its original design metadata and resale behavior still work.

`PipProductsChecks.run(core)` in the Paper probe verifies the actual factory, literal pages, vanilla metadata, serialization, native stack limits, complete-bundle space rejection and existing inventory codec. Compile the adjacent `PipProductsChecks.java` with the combined Paper probe and call its public static `int run(org.bukkit.plugin.java.JavaPlugin core)` on the server thread after NookCore enables; it returns its passed-check count or throws. It uses the NookCore plugin classloader and does not need to be packaged inside NookCore. It does not alter worlds or balances and does not replace client or two-account purchase tests.

The admin command `/nookadmin vendor catalogue` now browses all enabled designs in pages of45, with a separate navigation row. It never sells or gives preview items. New examples remain absent from the live catalogue until explicitly chosen and merged as definitions.
