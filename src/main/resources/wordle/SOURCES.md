# Word catalogue sources

`answers.txt` is a hand-curated Niko's Nook pool of 377 familiar English and Minecraft-themed five-letter terms. It is not taken from a commercial Wordle answer list. The game's stored puzzle controls which answer is used; this file is not a dated schedule.

`guesses.txt` contains 14580 accepted five-letter terms. It derives from CMUdict (Carnegie Mellon University), commit `74790861f652b15e4ac49015a90074ad62a27690`, retrieved 3 November 2026:

- Dictionary: https://raw.githubusercontent.com/cmusphinx/cmudict/74790861f652b15e4ac49015a90074ad62a27690/cmudict.dict
- Licence: https://raw.githubusercontent.com/cmusphinx/cmudict/74790861f652b15e4ac49015a90074ad62a27690/LICENSE
- Upstream project: https://github.com/cmusphinx/cmudict

The complete upstream copyright notice, redistribution conditions and disclaimer are retained in `LICENSE-CMUdict.txt` and distributed inside the plugin JAR.

Derivation: read dictionary headwords, remove pronunciation-variant suffixes, lowercase, retain only five ASCII letters, deduplicate and sort. A small explicit abuse/sexual-word exclusion list is applied, then all curated answers are added. The broader pronunciation dictionary includes names and inflections; inclusion as a permitted private guess does not make a word a daily answer. This filter is not an exhaustive moderation dictionary.

Dictionary SHA256: `81917843c7f44ce2b094ac63873c2c7a4cf802040792c455ba3ca406891c3d22`.
Licence SHA256: `bd4ce8e44170a5f9f481310ca85c51de3c4f851a65e679b40e603b143bd3542a`.
