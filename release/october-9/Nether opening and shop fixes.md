# October 9 — Nether opening and shop fixes

Owner confirmed October 7 is installed and approved an 80% reduction to all rents. This package builds on that release, preserving its crop/trade quests and chat fixes. Keep ChestShop installed: the rental bridge still needs it.

## Nether opening at 19:00 UK today (18:00 UTC)

In the EXISTING travel section of plugins/NookCore/config.yml, change lock-nether to false. Keep lock-end true and all other settings, including unrestricted rockets. The running plugin caches config; editing the file alone does not unlock entry.

Take a fresh matched backup. If installing the shop patch at the same time, upload this NookCore.jar to plugins/update/NookCore.jar, matching the current live filename, before the stop/start. Keep the current quests.yml, including any owner stone/tuff substitutions, and TAB files. No quest reset or activation command is needed.

Use the host panel's normal graceful STOP shortly before 19:00 (e.g. 18:58), wait until fully stopped, then START at 19:00 UK. Players can enter once startup completes; this is not an instant 19:00 portal toggle. Make sure automatic restart behaviour does not start it early. Do not use the existing one-hour warning command at 18:15 or later expecting a 19:00 restart. Do not use /reload. Stop/Start are power actions, not Minecraft console commands.

The Nether config change works with the installed October 7 JAR too. If you do not want the shop patch in this restart, open the Nether using only the config change and normal stop/start. Keep server Nether generation enabled. After startup, have an ordinary player test a Nether portal; admins can bypass the lock, so an admin-only test does not prove public access. The End should remain locked. No live schedule, config upload or power action has been performed by the assistant.

## Apply the 80% rent cut after the new JAR starts

Run separately in console (no leading slash):

    plots admin rent-cut

This previews EVERY existing plot's old/new weekly rent. The original 30/50/90 Nook plots become 6/10/18; custom prices also retain 20%, rounded to the nearest cent with a one-cent minimum. Within 60 seconds, from the same console:

    plots admin rent-cut confirm

The command saves a database backup before atomically updating prices and recording its audit. It refuses stale previews and cannot apply this reduction twice, including after restart. New automatic plot-setup quotes use the lower rate after the cut. Existing setup previews are rejected if the rent policy changed. Explicit prices supplied by an admin are still explicit prices.

Existing ownership, members, plot status, balances and paid-through dates remain unchanged. Future rent/prepayments use the new rate. Already paid rent is not automatically refunded. No plot deletion/recreation, eviction or clearance is required. Installing the JAR alone does NOT cut existing rents: run the preview/confirm commands above.

## Chest labels

Sneak while placing an ordinary sign on a NookShop chest. Ordinary nearby labels no longer prevent creating/using the shop. Avoid the four-line ChestShop trading format: an actual ChestShop trading sign still cannot share the container. ChestShop creation and transaction guards reject marked NookShop stock containers, including both halves of double chests. Existing build/protection cancellations are not overridden.

## Short checks

- In Survival as the renter, sneak-place a plain label, edit its text, then open the NookShop and perform a small purchase. Confirm stock/payment change once and the label remains.
- An unrelated player must not gain permission to place signs in the plot. Existing protection cancellations were tested, but a real WorldGuard/client check remains.
- Preview and confirm rent from console; check a rented and an empty plot. The renter's paid-through date must not move. A second command should report already applied.
- Test the Nether as a normal player after startup; End stays closed.

403 automated tests and 21 isolated Paper/ChestShop checks passed. Paper sign and pricing adapters, real sign recognition, console preview/confirm, backup and repeat prevention were exercised; full client trading/protection smoke remains. No data from production was used or altered.

## Recovery

Retain the October 7 JAR and current config. A JAR rollback while stopped does not undo the database rent cut; existing rents remain lower, but older automatic setup quotes use old rates. Avoid creating new plots with an older JAR until reviewed. Never restore an older economy database over newer player inventories merely to revert a price. No automatic rollback or refund has been implemented.
