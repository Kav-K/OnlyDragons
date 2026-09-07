# Checkpoint 2 Windows operator validation

The accepted gameplay remains [PR86's original combined cohort](checkpoint2-held-books.md),
tested at `79f64bdf2b3472dd083c1d62e6aead05d34aa85c` and merged at
`1a63ce5749e4debe4369cd154953ffea3e07e09a`. This record covers the separate
Windows lab repair and operator checks on 7 September 2026. It does not relabel
the original complete Paper receipt as a run of the later Windows tooling revision.

## Log encoding repair

The first Windows smoke run started Paper and enabled the plugin, but timed out
on the unchanged `Dragon . Idle` assertion. Java's redirected output used CP1252,
Windows PowerShell's process readers used CP437, and the BOM-less UTF-8 log was
then read as ANSI. A same-JDK subprocess probe reproduced the corruption in both
stdout and stderr; setting only one side's encoding did not repair the pipeline.

Lab revision `6571c3dd6cce36845965f7ed436319cf78c3ed69` explicitly sets Java's
stdout/stderr and both process readers to UTF-8, then reads the saved log as UTF-8.
The console/log views use the same decoding. Command expectations, gameplay,
Paper fixtures, protocol actors, dependency pins and acceptance requirements are unchanged.

The existing lifecycle fixture now responds to a `unicode` command with the exact
middle dot, arrow and checkmark on both output streams. Its seven checks passed
in `server-lifecycle-dcdc3e056c404bb6a68b1af354e1efab`: Unicode preservation,
unrelated-process exclusion, stale PID protection, graceful/idempotent shutdown,
deliberately hung process shutdown, orphan cleanup and unrelated-process survival.
Forced shutdown belongs only to the deliberately unresponsive small Java controls.
Windows CI runs this same regression script; it creates no Minecraft world.

## Build and actual Paper smoke

- Normal Windows Build at accepted merge `1a63ce5`: 321 production tests passed,
  zero failures/errors/skips. The subsequent smoke build at clean `6571c3d` passed.
- Pinned Paper 26.2 build 121, protocol 776, isolated `26.2-smoke` profile on
  loopback port 25566; run `b6b82ebd43d34b81bd060c0a300807b4`.
- All 17 unchanged command checks and both plugin-enable checks passed, including
  status after rejected dragon spawn, shortbow help/list and player-only books.
  Protocol status passed and the server stopped cleanly without forcing.
- Original result SHA-256: `2923a08cbe5435ed354181883dc471a9d834f51e6a7dc6c7e11862aef81d9779`;
  saved server log: `f93b722197dbb5caae400d130c97b1f6f54f0661e16f4a99ce1daf8784a76c9d`.
  The original failed run is retained separately rather than overwritten as a pass.

## Artifact comparison and remaining human checks

The Windows plugin SHA-256 is
`19017d84e4248c62795ec5149a9210cfd04509d2d3f0cbe74626d336f14df3ed`;
the accepted Linux plugin remains
`d4734a1fb07599f0d2e32e14adb4e10439d8d630b2652abf913478ca6db6fc5a`.
All 244 entry names/order and all 201 compiled classes match. Three properties
resources contain 64 additional CR bytes from Windows line endings; normalizing
only CRLF to LF makes those resources identical. Six other files and 34 directory
entries are identical. The resulting three compressed payload/CRC/size changes
and downstream ZIP offsets fully explain the 17-byte JAR size difference.
These remain distinct actual artifact hashes.

The lead must still verify the final built/deployed hash, normal Play startup,
saved arena and safe standing pad before publishing the operator guide.
Authenticated-client appearance and input feel require human observation.
This validation enables no real rewards, armor effects or later milestone.
