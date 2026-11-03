# Coin flips and shared game money — development handoff

Coin flips are disabled by default. Proposed test stakes are 1–100 Nooks each, a 90-second invitation and a five-second invitation cooldown. These are trial settings for owner review, not approved live prices. The coin is 50/50; the winner takes the complete two-player pot and no house fee is charged.

Commands use player names:

- `/coinflip invite <player> <amount>` — canonical invitation; every valid player name works, including Help/History/Invite.
- `/coinflip <player> <amount>` — shorthand for names that are not subcommands.
- `/coinflip accept <player>` / `decline <player>` — respond to their waiting invitation.
- `/coinflip cancel` — cancel the player's own waiting invitation.
- `/coinflip collect` — collect safe pending game credits when the balance has room.
- `/coinflip history` — the last five matches, read-only; `/coinflip help` is read-only too.

The creator's stake is reserved immediately. The recipient's equal stake is reserved only on acceptance. Both players must be online, in Survival, outside staff mode and authorised by `nookcore.coinflip`; hidden/unavailable players are not offered as targets or completions. Outcomes and payout entitlements commit before messages or sound. Double acceptance, reconnects, expiry and server restarts cannot pay/reroll a committed outcome. A capped account retains a safe payable credit, never a deleted pot/refund.

Recovery is always constructed, even when new flips are disabled. Existing invitations can still be declined/cancelled and expire normally. Private result notices survive a restart until acknowledged. No public chat or Discord message is sent by the adapter.

The shared GameLedger is the common reservation/payment boundary for later trusted minigame modules. This WagerService implements the coin-flip lifecycle; player-built templates, honour-result agreement and duels are not yet exposed. A builder has no payout command or unilateral result authority.

Integration:

```java
var game = new CoinFlipGame(plugin, store, healthy, failure);
game.onPlayed(player -> gameMilestones.trigger("COINFLIP_PLAYED", player)); // receiver chooses its event name
game.enabled();
game.status();
game.close();
```

The callback also recovers on join and the ordinary summary for a saved completed game. It must be idempotent. Cancelled/expired invitations do not count as playing. Cosmetic callback failures do not affect results or Nooks. History intentionally does not grant the milestone because it is read-only.

For Paper probes only, a package-private seven-argument constructor accepts UUID/player-name resolvers and an online-player supplier after the four normal arguments. Production uses Bukkit resolution. This supports two synthetic players without a production bot command or relaxing the both-online policy.

Root integration must register `coinflip` and the default-true `nookcore.coinflip` node in plugin.yml, instantiate/close the adapter, add config keys and expose status. Adapter guards its permission directly as well as relying on metadata.

```yaml
games:
  coinflip:
    enabled: false
    minimum-cents: 100
    maximum-cents: 10000
    invitation-seconds: 90
    cooldown-seconds: 5
    incoming-limit: 5
```

Report changes include outstanding holds and payable credits in circulating supply, show cash/held totals separately, omit game-pot transfers from issuance/removal/net, and count `game-wordle` as real issuance.

Validation: the first focused run passed 53 tests (19 wager, 3 settings, 13 foundation, 18 report). After adding the milestone recovery query, the 20 wager and 3 settings checks passed again. Final adapter routing/permissions/player resolution compile successfully; root Paper UI checks and owner two-client acceptance remain.

Suggested owner checks: invite/accept a two-Nook stake and check the four-Nook pot, decline/cancel, a 90-second expiry, reconnect/result notices, private copy, hidden-staff/permission rejection, and `/nookadmin economy report` held totals. Automated tests already cover duplicate concurrent accepts, restart, insufficient funds, balance-cap pending credits, rollback and once-only settlement. Do not mint a huge live balance to repeat cap tests manually.
