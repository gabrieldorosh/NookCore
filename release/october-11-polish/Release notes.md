# October 11 polish candidate

Approved command and copy changes, based on tested staff mode plus the tested read-only status branch. No automatic production deployment.

- Private `/staff <message>` and `/sc <message>` reach online helpers/admins, including hidden staff, without requiring staff mode. Uses nookcore.staff.chat, inherited through nookcore.staff.use and nookcore.admin. No public chat event or Discord broadcast. Console may send; normal server command logging still applies.
- Grouped admin help: /nookadmin economy, plots, quests, shops, status. Plot invitations under /plots invite and prepayment under /plots rent. Legacy plot/admin routes still resolve for compatibility, but new help/completion favours the groups.
- /nookshops create [payment price], e.g. nooks 2.50 or diamond 3. Setup GUI cancels using Cancel. Remote cancel/preview/manage/collect commands no longer operate; open the shop instead. /mine shows addresses and exact enchantments without raw shop IDs; closed empty shops are omitted.
- Shop close collects stock first, then retires its location and marker. Receipts are retained; pending payments/deliveries keep collection access and block replacement. Old closed stock remains recoverable.
- Nooks sale messages aggregate per recipient/plot until 15 seconds of no purchases. Shows actual ledger share only when the plot has multiple members. Item payments use collection reminders on join and every two hours.
- AFK names are grey, italic and tagged [AFK]. Public away/return announcements default on, can be disabled with chat.afk.announcements: false. Hidden staff do not announce AFK transitions.
- /list registered for discovery. Staff chat and /me use the same approved refusal. Default /staffmode (or on disconnect) sends regular players a Minecraft departure message, and a join message on safe exit. Real disconnect/reconnect while on duty stays hidden. Existing saved states retain their silent-return behaviour.
- Inventory inspection uses existing /invsee independently of staff mode. Non-admin holders of nookcore.staff.use receive view permission with edit explicitly denied while connected. Admin/operator editing is unchanged. /staffmode inspect directs to /invsee.
- NookCore staff teleports and intercepted /tp require active staff mode. Helpers' explicit staff-tool teleport gets a scoped permit; closed-dimension restrictions remain. Console recovery remains available.
- Bunny boots are disabled in this release even if an older config enabled them. Only NookCore's own boost modifier is removed; unrelated player attributes remain.
- Economy report headings are clearer and item variants include enchantments. Read-only /nookadmin status identifies the packaged build and module state.

Not part of this candidate: retained AFK/normal TAB presence, fake stationary bodies/map markers, full chest cursor/drag stock editing, shopkeeper NPCs, website dashboard. Existing safe shift-click stock transfers remain. Departure simulation currently targets Minecraft chat, not synthetic Discord join/quit events. Do not claim this hides all evidence of online presence.

Client acceptance: test grouped help/completion; off-duty teleport denial; one departure/return message with no spectator leaks; /invsee read-only helper clicks/drags; AFK tag/announcements; create/payment/price; stock then close/collect with full inventory; owner/co-owner sale messages after 15 seconds quiet; item-payment collection at the chest; normal restart while on duty. T08 BlueMap remains separate.

## Staff presence hotfix — 12 October

- Block hidden public chat at both Bukkit chat APIs and DiscordSRV pre/post relay events. Staff chat remains available.
- Simulated join/leave messages now use the player name colour and reach helpers too. DiscordSRV receives explicit join/leave calls using its existing configured formats/channels; no fake Bukkit lifecycle events are fired.
- /w, /msg and /tell (including namespaced forms) cannot target hidden staff from ordinary players; missing and hidden direct targets return the same response. Selector recipients are filtered. Hidden staff cannot send private messages that would reveal them; use /sc.
- Live Discord delivery still needs owner verification. No bot was connected during isolated tests. Other plugins' custom private-message/reply mechanisms are not universally covered by Bukkit command interception.
