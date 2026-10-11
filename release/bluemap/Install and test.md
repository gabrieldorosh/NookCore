# BlueMap polish — 11 October 2026

Ready to upload; not deployed. Web-only changes, independent of the staff-mode JAR.

Includes the existing server icon/favicon, cream and green light/dark themes, a collapsible community card, the verified Discord invite and joining information. BlueMap attribution and high-contrast mode remain. The card starts collapsed and hides during native-menu and free-flight use. No map data, player lists, permissions, render settings or server admission rules are changed.

## Install

1. Back up plugins/BlueMap/webapp.conf and any existing bluemap/web/nook-theme folder.
2. Upload the contents of UPLOAD to the server root, preserving folders. This adds only three files in bluemap/web/nook-theme. If your webroot differs, place nook-theme inside that configured webroot instead.
3. In plugins/BlueMap/webapp.conf, add the entries below to the EXISTING scripts/styles lists. Retain any other entries; do not create duplicate keys or overwrite the whole configuration. CONFIG-EDITS.conf is a snippet, not a replacement file.

```hocon
scripts: [
  "nook-theme/nook-map.js?v=20261011"
]
styles: [
  "nook-theme/nook-map.css?v=20261011"
]
```

4. Run this in the server console:

```text
bluemap reload light
```

5. Hard-refresh the map in your browser (Ctrl+F5). Confirm the icon, title and collapsible card appear. If a proxy caches settings.json, purge that specific cache and reload. No full Minecraft restart, plugin update or map re-render is needed for these web customisations.

Do not upload the preview images, audit JSON, CONFIG-EDITS.conf or this guide into plugins. Do not replace index.html, hashed BlueMap assets or settings.json. New files alone do not activate the theme; the two webapp.conf entries do.

## Staff visibility

The September 27 server snapshot already has these settings in plugins/BlueMap/plugin.conf:

```hocon
hidden-game-modes: [ "spectator" ]
hide-vanished: true
hide-invisible: true
```

Check the current hosted file still includes spectator and both true values. Keep any additional hidden modes. No change is needed if it matches. Inspection of the archived BlueMap 5.27 BukkitPlayer class confirms it reads the `vanished` metadata flag used by NookCore staff mode. In this version, hide-vanished concerns plugin vanish; hide-invisible is the separate potion-invisibility setting. Earlier generic documentation suggesting hide-vanished only means potion invisibility does not describe this installed version.

The theme itself does not hide staff. Test the backend: from a separate normal browser, enable staff mode and check both map markers AND BlueMap's player list, across maps, reconnect and the next normal restart. Confirm hidden names/positions are absent from the returned live player data as well, not merely hidden by CSS. A browser preview cannot establish live server-side staff invisibility.

## Verified and remaining checks

- Browser preview loaded the public map with local-only asset overrides; no remote files were written.
- Tested the current public 5.13 web interface and bundled 5.27 interface against public map data, each without JavaScript page errors.
- Verified title/favicon, card toggle, Discord URL, keyboard Escape, native menu access, dark/light/high-contrast styling, 390px and 320px widths, script idempotence and unobstructed free-flight controls. Screenshots inspected.
- Discord invite 9AxdaMKuyX resolved to niko's nook on October 11. No Discord messages or membership changes made.
- Current public page advertises web version 5.13; archived plugin/settings are 5.27. This package supports both and does not attempt an unrelated webapp upgrade. An outdated index.html may explain that difference, but current hosted files must be checked before changing them.
- After upload: pan/zoom, switch Overworld/Nether, open Maps/Players/Settings/Info, follow Discord, test a real phone and check staff visibility. Keep existing BlueMap attribution.

Page metadata/favicon are updated by JavaScript after BlueMap loads. Static social-preview crawlers and previously installed PWA icons may retain BlueMap's original branding; modifying those generated assets is outside this small package.

## Rollback

Remove only the two nook-theme entries from scripts/styles, run bluemap reload light, then hard-refresh. The inactive three files can remain until convenient cleanup. Restore the backed-up configuration if needed; preserve other custom scripts/styles. No world or plugin database rollback is involved.

BlueMap's supported extension/reload instructions: https://bluemap.bluecolored.de/community/Customisation.html
Webapp settings: https://bluemap.bluecolored.de/wiki/configs/Webapp.html
