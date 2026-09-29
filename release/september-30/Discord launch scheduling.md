# Discord launch post — prepared, not scheduled

Destination: nikos news, channel 1444144089682546809.
Mention: @nookling, role 1444142089272299580. The announcement contains one role mention and no @everyone or @here.

Launch announcement.md is the public copy. Discord launch command.txt is the same text encoded for a single console command: literal \n becomes a line break in DiscordSRV. No leading slash is needed. Road guide.md and Weekly quest guide.md are separate companion posts; they are not included in the scheduled command and have no role ping.

## Before scheduling

1. Review Launch announcement.md: it follows the owner's revised copy with light wording corrections and makes the rocket deferral explicit. The original is retained in Launch announcement - owner draft.md. The final line says the update is live: use it after startup and quest checks, or change that line before unconditional timed posting.
2. Use Discord test command.txt for the owner's private channel 1552852930422640681. The @nookling role mention is retained at the owner's request. Ensure the DiscordSRV bot can view and send in that private channel. Confirm the installed DiscordSRV accepts the command, all paragraphs/formatting survive, and no text is cut off. Check the host scheduler's command-length limit using its saved value.
3. Verify the bot can view/send in nikos news. Check that the role is mentionable or that the bot has the appropriate mention permission. A visible mention is not proof everyone received a notification; personal notification settings still apply.
4. Verify the host scheduler timezone and next-run date with a harmless task while present. Use a one-off console task for 30 September 2026, 02:05 Europe/London (01:05 UTC). Paste Discord launch command.txt exactly. Do not use a repeating daily announcement.
5. A scheduled message is unconditional and does not verify NookCore, TAB or the quest refresh. Prefer an admin checking startup before sending the prepared command. If choosing unattended timed posting without a startup check, replace the final live-status claim with: “These are the notes for tonight's scheduled update. Build a road, lay down some tracks, and get travelling!” Arrange a way to report a failed launch. Do not enable both manual and timed delivery accidentally.

The server's proposed Stop 01:55 / Start 02:00 UK schedule remains dependent on verified panel actions. DiscordSRV must be connected when the command runs. No retry or success-gated delivery has been implemented. Do not assume a missed console task will be delivered later.

## Small remaining checks

- Private-channel formatting and saved command length; role mention permissions.
- TAB balance values, ordering after transfer (allow six seconds), TPS and three title colours.
- Brief Survival road ride, cart braking and ordinary shop purchase on the final candidate.
- Q1–Q4 already passed; no repeat requested for these documentation-only changes.

References: https://docs.discordsrv.com/commands/ and https://github.com/DiscordSRV/DiscordSRV/blob/master/src/main/java/github/scarsz/discordsrv/commands/CommandBroadcast.java

The owner will post guides manually; no guide channel needs creating tonight. Suggested location: follow-up posts in nikos news, with the road guide pinned. No guide publication is scheduled. Neither test nor public announcement has been sent by the assistant.
