# Read-only admin status

`/nookadmin status` is console/admin-only (nookcore.admin). It remains available when the global economy health flag is paused or startup is blocked. It does not repair anything, rotate quests, resolve receipts, grant money or reload configuration. Extra arguments are rejected.

It shows embedded source commit/dirty state, UTC build time and a deterministic SHA256 of runtime source/build inputs; this fingerprint is NOT the JAR file checksum. It also shows economy health, actual chat/AFK/travel/bunny settings from loaded modules, protection-bridge initialisation, whether a staff-mode command is present, active quest week/next reset/config mismatch, metrics flush/error state and up to50 pending shop reviews. A full queue is labelled at least50. Error details and secrets stay in the server log. The review query can fail without changing plugin health. A matching quest status refers to configuration loaded at startup, not unsaved or subsequently edited disk files.

Build with build.ps1 -Offline to embed identity. Direct Maven builds use explicit unrecorded defaults unless supplied the nook.commit/state/inputs/built properties. The input fingerprint covers src/main, pom.xml and build.ps1; it does not fingerprint dependency JAR contents or tests. A dirty build names its base commit honestly and includes the actual input fingerprint.

This branch is based on stable main, NOT the staff-mode candidate. Do not replace tonight's staff-mode JAR with this development build: it does not include staff mode. Merge/integrate and retest deliberately after the staff candidate is accepted. Tonight's bundle remains unchanged.

Validation: BuildIdentity unit cases cover missing/unfiltered metadata and recorded properties. AdminStatusProbe on disposable Paper checks permission denial, identity, harmless extra arguments, paused health, no quest rotation, no SQL changes, blocked startup and absent-store handling. The probe deliberately triggers a synthetic startup-blocked log near shutdown; it is not a live server test. Run src/test/probes/run_admin_status.py only on the isolated local harness.
