# Daily word game — development acceptance

The feature is off by default. Enable only in an isolated local server using wordle.yml enabled:true. Trial reward is 100cents (1 Nook), not an approved live price. Keep tonight's live release and every live data file intact.

1. Run /wordle: six rows, five cells each, private readable text, next 02:00 UK reset and actual configured reward. Letters have both colour and =/~/-markers. Help/command completion must not reveal an answer or dictionary list.
2. Click try a word: the suggested command includes the displayed day. Bare /wordle <word> also works after opening. Use /wordle guess share to submit the word SHARE, because /wordle share copies a result.
3. Invalid five-letter dictionary words and repeated guesses consume no attempt. Malformed/non-letter inputs consume none. Check repeated-letter feedback with the supplied deterministic local test answer rather than a production solution.
4. Six valid attempts maximum. Winning on the sixth works; six wrong guesses end the round and reveal the word privately. A completed puzzle cannot be played again for money. Reopening and reconnect/restart preserve attempts and the once-only reward.
5. Two accounts see the same puzzle but have independent guesses and payouts. Sharing prints only day/count/feedback symbols, with no guessed letters or solution, and stays private until the player chooses to paste it.
6. A stale date-tagged command after 02:00 must be refused without using a new attempt. A bare guess from a previously displayed old board must show the fresh puzzle first, consuming none. Do not change the production or PC clock for this test; use the automated schedule/probe or a disposable time-injected fixture.
7. A full balance preserves a solved round with its reward pending. After making room, open /wordle; it should credit exactly once, including when the previous day's reward is collected after reset.
8. Hidden staff, non-Survival players, console and disabled/paused game states cannot play or earn through the command. An invalid wordle.yml should leave the economy available and the game unavailable with a useful log.

Daily answers are selected using SecureRandom inside the same transaction that persists the day's puzzle and reward. Existing days keep their saved definition after config/dictionary changes. Normal-sized pools exclude the previous 30 published answers; small custom test pools fall back without immediate repetition when possible. Daily boundaries follow Europe/London 02:00 with DST, not fixed 24 hours. Attempts and Nooks issuance share the main NookStore transaction.
