/* Optional BlueMap webapp extension. No player data, telemetry or map API writes. */
(() => {
  'use strict';
  const script = document.currentScript;
  const assets = new URL('.', script ? script.src : new URL('nook-theme/nook-map.js', document.baseURI));
  const icon = new URL('nook-icon.png', assets).href;
  const title = "Niko's Nook · Map";
  document.title = title;
  const meta = (name, content) => {
    let tag = document.querySelector(`meta[name="${name}"]`);
    if (!tag) { tag = document.createElement('meta'); tag.name = name; document.head.append(tag); }
    tag.content = content;
  };
  meta('description', "Explore Niko's Nook. Find your next journey and join our community on Discord.");
  meta('theme-color', '#293b33');
  meta('og:site_name', "Niko's Nook"); meta('og:title', title);
  meta('og:description', "Our community Minecraft map. Explore, build and make yourself at home.");
  meta('og:image', icon);
  let favicon = document.querySelector('link[rel="icon"]');
  if (!favicon) { favicon = document.createElement('link'); favicon.rel = 'icon'; document.head.append(favicon); }
  favicon.type = 'image/png'; favicon.href = icon;

  function mount() {
    const app = document.getElementById('app');
    if (!app) return false;
    if (document.getElementById('nn-community')) return true;
    const panel = document.createElement('aside');
    panel.id = 'nn-community'; panel.setAttribute('aria-label', "Niko's Nook community");
    const details = document.createElement('details');
    const summary = document.createElement('summary');
    summary.setAttribute('aria-label', "Niko's Nook — community and joining information");
    const image = document.createElement('img'); image.src = icon; image.alt = ''; image.width = 40; image.height = 40;
    const names = document.createElement('span');
    const name = document.createElement('strong'); name.textContent = "niko's nook";
    const subtitle = document.createElement('small'); subtitle.textContent = 'a little place to call home';
    names.append(name, subtitle); summary.append(image, names);
    const body = document.createElement('div'); body.className = 'nn-body';
    const eyebrow = document.createElement('p'); eyebrow.className = 'nn-eyebrow'; eyebrow.textContent = 'Explore together';
    const description = document.createElement('p'); description.textContent = 'Follow a road, find a neighbour, or plan your next build. This is our little corner of Minecraft.';
    const join = document.createElement('p'); join.textContent = 'New here? Start in Discord for the server address, rules and account linking.';
    const discord = document.createElement('a'); discord.className = 'nn-discord'; discord.href = 'https://discord.gg/9AxdaMKuyX'; discord.target = '_blank'; discord.rel = 'noopener noreferrer'; discord.textContent = 'Join our Discord ↗';
    const credit = document.createElement('a'); credit.className = 'nn-credit'; credit.href = 'https://bluemap.bluecolored.de/'; credit.target = '_blank'; credit.rel = 'noopener noreferrer'; credit.textContent = 'Map powered by BlueMap ↗';
    body.append(eyebrow, description, join, discord, credit); details.append(summary, body); panel.append(details); app.append(panel);
    // Interacting with the card must not drag/zoom the map beneath it.
    for (const event of ['pointerdown','pointerup','click','dblclick','wheel','touchstart','touchmove','keydown','keyup']) {
      panel.addEventListener(event, e => {
        if (e.type === 'keydown' && e.key === 'Escape' && details.open) { details.open = false; summary.focus(); e.preventDefault(); }
        e.stopPropagation();
      }, {passive: event === 'wheel' || event.startsWith('touch')});
    }
    return true;
  }
  if (!mount()) {
    const observer = new MutationObserver(() => { if (mount()) observer.disconnect(); });
    observer.observe(document.documentElement, {childList:true, subtree:true});
  }
})();
