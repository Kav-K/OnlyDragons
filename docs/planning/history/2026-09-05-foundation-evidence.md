# Foundation delivery evidence archive — 5 September 2026

Archived from `docs/planning/03-agent-tasks-and-validation.md` at revision
`5e8cfbfc7c9bfc7bc395e76f1e608f5853cd78be`.
[Original source](https://github.com/Kav-K/OnlyDragons/blob/5e8cfbfc7c9bfc7bc395e76f1e608f5853cd78be/docs/planning/03-agent-tasks-and-validation.md).

This preserves the original delivery wording, results, revisions, and then-pending
gates. Only relative Markdown link paths have been rebased for this directory.
Historical statements such as “In review” or “remain pending” are not the current
status. Use the [current delivery ledger](../03-agent-tasks-and-validation.md#current-delivery-status)
and [foundation design](../02-foundation-plan.md) for current decisions and gates.
Read these detailed runs when investigating provenance or a regression; the
required living context keeps their latest evidence summaries and stable links.

### Context change record

Keep one concise row per meaningful decision or delivery update. The tables and
design sections above are the current summary; this record explains changes.

| Date | Task / reference | Change and rationale | Evidence / remaining work |
| --- | --- | --- | --- |
| 2026-09-05 | Worker skill-update sandbox maintenance, In review | Grant only the real issue-local `.agents` directory alongside existing Git/lease roots so an ordinary upstream skill merge works. Reject missing/file/symlink or changed checkout paths on launch/each transformed turn; retain trusted operator MCP configuration and the other policy/input fields. | Eleven Linux bridge tests and the explicit Codex 0.153.4 no-model sandbox smoke passed: real two-parent skill merge preserved worker content; `.codex`/sibling writes remained EROFS; owned app-server exit 0 and scratch cleanup passed. Current-head review, CI and lead merge remain pending. [Scope and runnable smoke](../../symphony.md#start-and-stop). No new Paper or gameplay acceptance is claimed; prior receipts retain their recorded input identities. |
| 2026-09-05 | T09c / #28 and T01b / #7, complete via [PR #32](https://github.com/Kav-K/OnlyDragons/pull/32) | Integrated strict shared suites/checkpoints, objective player equipment and received command text, catalog-declared actor admission, and the callback/disconnect lock-cycle fix with delayed-quit soak. | Reviewed head `78c3de4` merged at `73a8cc8`; fresh 15-case baseline (10 positive, 5 intended negative), 82 production and 7 client tests; 130 Linux Python, 124 Windows Python plus 6 Linux-only exclusions. [T09c baseline](../../../dev/game-tests/findings/t09c-baseline.md). The Windows operator Cursor Build target passed on clean main 73a8cc8 (82 tests, zero failures/errors/skips); Windows Play/smoke and authenticated/multiplayer/visual/feel checks remain unrun. P01–P14/M0–M5 remain pending. |
| 2026-09-05 | T01b / #7 / [PR #29](https://github.com/Kav-K/OnlyDragons/pull/29), In review | Connected one active main-hand projection, full validated fingerprints, session-only source replacement/cleanup and permission-gated inspection/grants. Storage-only grants preserve equipment; incompatible bonuses reset visibly rather than leaving stale stats. | Clean 4c28474 after main 9092fbe: 82 production tests, 36 runner tests, 23 equipment/35 item Paper assertions, 25 unchanged protocol calibration assertions and 2 client tests; clean owned shutdowns. Retained all 11 scenario registrations through Map.ofEntries; runtime-head Windows/Linux CI passed. [Evidence and unrun human gates](#t01b-equipment-validation-gh-7). No shot-time integration or milestone acceptance. |
| 2026-09-05 | T03 / #6 | Adopted immutable versioned combat policy and a single-thread encounter authority while preserving T00 DTOs. Physical/proc identity and inherited mitigated basis prevent duplicate or recursive credit. | Clean 633d81c after actor main 5b0e030: 74 production tests, 36 runner tests and 31 production-service Paper assertions passed with clean shutdown. See T03 evidence below. Native suppression and human gates remain unaccepted. |
| 2026-09-05 | Shared-context setup; user request | Made the three planning documents required project context and added an agent maintenance/handoff protocol. | Starter-only source inventory reconciled; all gameplay tasks remain planned. Shared reading routes are in AGENTS.md, Cursor rules, and WORKFLOW.md. |
| 2026-09-05 | Shared agent tooling; user request | Bundled three Minecraft skills with references/provenance; configured Context7 and project-scoped Serena for Cursor, Codex, and isolated Symphony workers. | All three skill validators passed; Windows/Linux MCP initialize/tool-list and Java-symbol checks passed. A fresh Linux issue clone discovered all three skills, connected Context7 (2 tools) and Serena (8), queried Paper docs and production lifecycle symbols. Direct Codex from a nested directory also connected. Five bridge tests and scaffold skill-preservation checks passed. No gameplay milestone advanced; see dev/agent-tools.md. |
| 2026-09-05 | User-confirmed execution policy | Authorized feature agents to test against isolated real Minecraft, parallel coding, and lead merges of reviewed/tested PRs into main. Reuse existing local EULA acceptance; preserve human worlds and serialize JVM tests. | Existing managed dev server stopped cleanly with user permission. Fourteen issues created; T00/T09a agents started in separate clones. Bridge tests include the narrow shared lease directory (6 pass); client/milestone gates remain distinct. |
| 2026-09-05 | T09a / #2 / PR #16 | Integrated an isolated Linux/WSL runner and same-Paper companion with a shared lease, memory admission, strict reports, and owned-process cleanup. | Merged into main at 140f11c. Clean 946858d positive and deliberate-failure controls produced the expected outcomes and clean shutdown; [calibration evidence](../../../dev/agent-paper-tests.md#accepted-calibration-evidence). Broader T09 gameplay and human gates remain. |
| 2026-09-05 | T01a / #3 / [PR #23](https://github.com/Kav-K/OnlyDragons/pull/23) | Adopted named calibration defaults/ranges/caps and deterministic arithmetic policy within task scope; stable T00 records preserved. See document 02 section 3. | Merged at 1efa7d0 after review and final-head CI. Clean 36a7e23: 30 production tests (9 new resolver tests), zero failures/errors/skips; 18 production-resolver Paper assertions and clean unforced shutdown. See [evidence](#t01a-resolver-validation). T01b equipment integration and human play remain separate. |
| 2026-09-05 | T00 / #1 / PR #15 | Established shared immutable domain contracts without introducing resolver/combat engines or choosing unresolved balance rules. | Clean 53f7e20: 21 production tests, no failures/errors/skips; real Paper passed 29 assertions with clean unforced shutdown. Independent review and final e3a9e68 Windows/Linux CI passed; merged at be920d0. [Versions, hashes, and scope](../../../dev/agent-paper-tests.md#foundation-contract-evidence). |
| 2026-09-05 | MAINT-17 / [#17](https://github.com/Kav-K/OnlyDragons/issues/17), Complete via [PR #19](https://github.com/Kav-K/OnlyDragons/pull/19) | Reject boolean/number equivalence recursively in assertion evidence, even with forged pass flags; preserve integer/float numeric equivalence and JSON structure/order checks. | Merged at 6f0ccfb after review and CI; issue #17 and PR #19 are closed. Implementation 4aa62f4: Ubuntu 24.04 / Python 3.12.3, `python3 -B -m unittest discover -s scripts/agent-tests -p 'test_*.py' -v` passed all 27 tests without skips. Revalidated unchanged stored T09 positive (13 assertions), negative (only `deliberate_failure` rejected), and T00 (29 assertions) reports within their original run windows. No new Paper JVM or gameplay gate. |
| 2026-09-05 | MAINT-18 / [#18](https://github.com/Kav-K/OnlyDragons/issues/18), Complete via [PR #21](https://github.com/Kav-K/OnlyDragons/pull/21) | Disable optional remote-plugin synchronization only for subsequent Symphony worker app servers; preserve repository skills, reviewed MCP tools, host GitHub tool, sandbox and active sessions. Refresh issue/API coordination before expensive verification and require clean committed final runtime evidence. | Merged at e72fb2a after review and CI; issue #18 and PR #21 are closed. Six bridge tests passed and are retained in Linux CI. The Codex 0.153.4 no-model app-server check discovered all three project skills, Context7 (2 tools), Serena (8), and queried Paper docs/Java symbols with clean smoke-process exit. See [operation notes](../../../dev/agent-tools.md). |
| 2026-09-05 | T09b / [#20](https://github.com/Kav-K/OnlyDragons/issues/20), Complete via [PR #27](https://github.com/Kav-K/OnlyDragons/pull/27) | Added the exact pinned protocol client, strict dependency verification, dual reports and runner-only disposable offline mode; default/human authentication is preserved. Both JVM heaps count toward admission and the shared lease covers cleanup. | Merged at 5b0e030 after independent review and final-head CI. Final clean 1dd6ffe: 54 production tests, 2 client tests, 36 Linux runner tests; 25/25 protocol-player Paper assertions plus expected early-exit/timeout failures, all owned cleanup counters zero and no forced exits. [Evidence and extension boundary](../../../dev/agent-paper-tests.md#protocol-player-evidence). #5 is resumed for its own native-damage experiments; no T04 or milestone gate is accepted. |
| 2026-09-05 | T02 / #4 / [PR #24](https://github.com/Kav-K/OnlyDragons/pull/24) | Adopted schema v1 with explicit unsupported-schema/revision rejection, trusted enchant categories/levels and allowlisted named rolls. Added eight compiled calibration factories without changing T00 records or #7 equipment/bootstrap. Lead audit removed the redundant +50 crit-damage bonus (T01 owns baseline 50) and requested a resolvedWeapon projection to prevent duplicate contributions. | [Design and calibration rationale](../02-foundation-plan.md#t02-adopted-item-boundary-gh-4). Final clean 7ebaa6b retains catalog v2 and adds required listener cleanup in scenario `item-codec-v4`: 54 production tests and 35 Paper assertions passed after merging stats/projectile main 586a170. Prior v2/v3 evidence remains in [validation history](#t02-item-validation-evidence). No milestone or human gate advanced. |
| 2026-09-05 | T04 / #5 / [PR #22](https://github.com/Kav-K/OnlyDragons/pull/22), Partial delivery merged | Adopted projectile-hit candidates as the single impact source because real shooterless dragon impacts omit damage events; damage events remain optional cancellation/native guards. Uniform part scaling is the supported interim design until semantic classification is verified. Added companion-owned listener cleanup and preserved measured misses. | Final clean a093877 after main 1efa7d0: 30 production tests, 27 runner tests, 34 real-Paper feasibility assertions and expected exception/abort controls, all three servers clean/unforced. Runtime-head Windows/Linux CI passed. Earlier e38bfaf lifecycle/deliberate controls remain recorded for unchanged cleanup code. Native player-owned suppression is still an acceptance blocker; no dependent dispatch or milestone completion. [Evidence](../../../dev/game-tests/findings/projectile-feasibility.md). |

### T01b equipment validation (GH-7)

T01b's playable slice is **merged** in [PR #29](https://github.com/Kav-K/OnlyDragons/pull/29)
at `a48ebd4`, after independent review and final-head Windows/Linux CI
([run](https://github.com/Kav-K/OnlyDragons/actions/runs/34008900983)). Final clean runtime revision
`4c28474b54e2a2aa25d6242d12525f8783c4ea1b` includes the ordinary merge of main
`9092fbe` (combat PR #26). All eleven scenario registrations and existing
assertion sets are retained with `Map.ofEntries`; both equipment and combat
context sections survived reconciliation. Equipment production code remains
`e208933`; later handoff commits only record evidence/context. Design and
rationale are in document 02's T01b section; [Cursor Play](../../../dev/stats-play.md)
records the short operator procedure.

- **Build/MockBukkit:** `bash ./gradlew build --console=plain` and runner wrapper
  builds passed on JDK 25.0.4.1, with 82 production tests and zero failures,
  errors or skips, plus API isolation. Eight new equipment cases cover cache
  reuse, hand replacement, same-UUID enchant/roll edits, profile identity,
  permission/grant/full-storage rejection, atomic bonus replacement/reset,
  event refresh and quit/disable cleanup. MockBukkit's default inventory view
  lacks `convertSlot`; the event test supplies a bounded view conversion and
  remains explicitly synthetic. The integrated Python runner suite passed all
  36 tests; the strict protocol-client build passed both client tests.
- **Real Paper:** all final runs used Minecraft 26.2 / Paper
  `26.2-121-a2a42c5`, with the accepted EULA, shared lease and memory admission.
  `equipment-stats` (`equipment-stats-v1`) run
  `9f19e62d321b454c95c5a1fb7bc5a3b9` passed 23/23 assertions. Native serialized
  loadouts passed production codec → registry → equipment service → factory:
  all eight resolved damage 100 / crit damage 50 and their declared crit/ferocity
  totals. Same-UUID Vicious III changed ferocity 0→3; a trusted roll changed
  damage 100→102.5 while old snapshots remained unchanged. Offhand-only/invalid
  main-hand items resolved to damage 0 / ferocity 0 / crit damage 50. Repeated
  refresh reused the inspection, source changes allocated a new revision,
  and session removal discarded the cache. Capturing non-player command
  senders verified denial, player-only errors and unchanged status/reload.
  These sender/UUID inputs are synthetic, not logged-in-player evidence.
- **Preserved item regression:** `item-identity` (`item-codec-v4`) run
  `98cd61db8fc24518a0ead74d4f2a8271` passed all 35 assertions with this same
  committed production/companion artifact.
- **Protocol regression, distinct gate:** unchanged approved
  `protocol-player-calibration --test-player protocol-calibration` run
  `656549dbaf65441a9c434bb98043b25f` passed 25/25 assertions and the client
  report. Real Paper observed the expected offline UUID, selected-slot input,
  full-force bow release, player-owned arrow and quit with the new listeners
  active. This is T09b calibration coexistence, not equipment-specific player
  acceptance. No shared player admission or client sequence changed. The runner
  admitted both JVMs against its 2816 MiB combined memory requirement (4703 MiB
  guest available; 3087 MiB effective host available).
- **Cleanup:** all three Paper JVMs and the protocol client exited 0 unforced;
  every scenario cleanup counter was zero. Ports 53997, 44945 and 48809 were
  checked closed afterward. The runner reaped its owned processes and released
  the shared lease. Raw reports remain ignored under `build/reports/agent-paper/`.

All final runs used production SHA256
`afc7374f6240e4866b392239cc8027af07fe13f853de58d4eb9127b37b5fa209`
and companion SHA256
`10fde4aeb997684a01059c5ea433b53dc3409f969ff7efa6125a1b3550b55557`.
The protocol client SHA256 is
`ccab969031782ae9bdd2a1182d83316ac5da1f5278c84c3d3b923406a8af626e`,
using the exact protocol 776 publication, dependency lock and strict verification
metadata integrated by T09b; the run report records all 77 staged client JARs.

Windows/Linux CI passed on runtime head `4c28474`
([PR check run](https://github.com/Kav-K/OnlyDragons/actions/runs/34008634626)).
The earlier `7751adc` runs remain integration history for main `5b0e030`; the
three runs above supersede them for current-main handoff.

**Connected-player acceptance:** completed by [PR #32](https://github.com/Kav-K/OnlyDragons/pull/32) at `73a8cc8`. The fresh shared baseline passed all 47 `equipment-player-v2` assertions and eight required actual received-message checks: non-OP denial and permission-scoped grant, held-slot event-driven refresh, same-UUID inventory edits, offhand exclusion, bonus replacement, death/respawn and quit cleanup. See [T09c baseline](../../../dev/game-tests/findings/t09c-baseline.md). The fixture uses a real offline protocol player; controlled inventory edits, commands and death use supported server APIs and are described in its scope.

**Windows operator build:** the exact default Cursor **Minecraft: Build and test** target passed on clean main `73a8cc8`: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Dev.ps1 Build` exited 0 with `BUILD SUCCESSFUL` in 16 seconds; parsed JUnit recorded 82 tests, zero failures/errors/skips. See [build evidence](../../../dev/game-tests/findings/t09c-baseline.md#build-review-and-ci-evidence).

**Separate unrun observations:** Windows Play/smoke, human visuals and input feel, authenticated-client compatibility and multiplayer. These do not stand in for the now-verified objective equipment gates. T06 must still connect `refresh(Player)` at shot acceptance;
`cached(UUID)` is diagnostic state and must not substitute for that recheck.
No projectile integration, combat effects, player-health attributes, performance
claim or gameplay milestone is accepted here.

### T03 combat validation

[PR #26](https://github.com/Kav-K/OnlyDragons/pull/26), merged at `9092fbe`, owns additive combat services, `CombatEncounter`, and its unit/Paper scenarios.
T00 and T01a are integrated (owner dispatch confirms PR #23 merge `1efa7d0`);
T01a review references in its evidence section are historical.
Existing T00 DTOs, production listener/command registration, pins and other
feature ownership are unchanged. See document 02 section 6 for the service API.
The branch implements no-Strength offense, explicit crit, historical cap,
separate health/contribution, idempotent accepted impacts, validated inherited
children, one completion, and terminal generation rejection. The 0.25 and 0
ferocity HP fractions are test calibration, not researched Hypixel values.

Final clean runtime revision `633d81c2ef3377f9f94623db75808e42fe75ac38`
includes the ordinary merge of actor main `5b0e030`, retaining all ten scenario
registrations and all four owned-resource cleanup requirements. The new runner
invoked both wrapper builds on JDK 25.0.4.1: 74 production tests (20 combat
cases), zero failures/errors/skips, and API isolation passed. The Linux Python
runner contract suite passed all 36 tests. Production combat and its scenario
were unchanged from the independently reviewed `0dd0fa7`; the integrated runner
and companion were rebuilt and exercised together. No protocol client was
needed or started for this synthetic domain-service scenario.

`python3 scripts/agent-tests/paper_test.py --scenario combat-accounting` returned
exit 0 for run `21ddb337ec114a22ab80f693f87df0cb`, Paper
`26.2-121-a2a42c5` / Minecraft 26.2, mechanic `combat-accounting-calibration-v1`.
All 31 assertions passed through the production classloader on the server thread.
The golden critical fixture resolved 210; a calibrated 0.25 child requested 52.5
HP, removed the remaining 20, and credited 210, freezing totals at 230 HP/420
score. Cap boundaries resolved 4,000/6,000/8,000/10,000 for one million max HP;
a 24,000 mitigated parent basis capped once to 6,000 for both parent and child.
Duplicate impacts/procs, simultaneous lethal candidates, late descendants,
recursive children, terminal reset, cancellation and overflow controls passed.
These are synthetic domain inputs on actual Paper, not collision evidence.

All four listener/entity/task/chunk cleanup assertions reported zero retained
resources. The owned JVM exited 0, `clean=true`, `forced=false`; its loopback
port 43475 was confirmed closed afterward, with no owned JVM remaining.
Independent post-run validation checked the strict report against its catalog
and original run window, summed the JUnit XML, and rehashed both deployed JARs
and the pinned Paper JAR. The memory gate admitted 1536 MiB heap plus 1024 MiB
reserve: guest available 6004 MiB and effective host available 3486 MiB
(Windows 1419 MiB plus 2067 MiB conservative resident-cache allowance).
The disposable profile retained authentication and used the shared lease.
Artifact SHA256 values:

- Production: `1ca28a2c3b79115e2b60e842cb748c50acf0aacf3f92a3e98a5098da43a7756f`
- Companion: `8b284e952d4112722604f7905d6619cfbcbf5d4e5f7fcd2ff82381e78595c3fe`

Earlier clean `a4673cf` passed 30 assertions; `0dd0fa7` run
`33d78b17c5e04d00bc7b11e3308269d6` passed 31 before actor integration. The run
above supplies the final integrated evidence. Subsequent evidence/PR updates
are documentation only. Independent code/evidence review passed; final-head CI
and lead merge remain pending.
Physical arrow/native suppression, Windows smoke, authenticated-client/input/
visual/multiplayer and performance gates are unrun.
Next dependency: T05 consumes the frozen pre-cap basis for bounded scheduling;
T08 and the final T04-informed adapter wire player-facing combat later.

### T02 item validation evidence

Final clean runtime revision `7ebaa6b36521913058b578cf27522d3c2e10c463` includes
the ordinary merge of main `586a170` and all eight registered scenarios. The
`item-codec-v4` revision adds an explicit `owned_listeners_removed` requirement
for the merged projectile harness; production code, item schema v1 and item
catalog v2 are unchanged from the reviewed stats/item integration.

Run `3ba703d666dc416782a77f59e2edaccc` returned exit 0 on Java `25.0.4.1`,
Minecraft `26.2`, Paper `26.2-121-a2a42c5`, with all 35 assertions passing.
Both wrapper builds passed with 54 production tests and zero failures/errors/skips.
All eight native byte-round-tripped loadouts resolved through the production
snapshot factory to damage 100/crit damage 50 and their expected crit/ferocity
totals. An edited enchant/roll item resolved to crit chance 5/ferocity 3 without
double-counting contributions. Identity, schema, presentation and rejection
checks passed alongside all four listener/entity/task/chunk cleanup assertions.
The shared lease and memory gate were used; the owned JVM exited 0 without
forcing, its loopback port closed, and no owned JVM remained. Final-head CI and independent review passed; PR #24 merged at `7c6d843`.
Authenticated player inventory and visuals remain
unrun. Subsequent evidence updates are documentation only.

- Production SHA256: `103efd337f5c6a109d3199581d5f9e19317df471c1a11304bdab2f883b426547`
- Companion SHA256: `bf2959c2856cb0b9623193d06e302ee9eaa3573af91905d5eabddcfe9c2915f9`

#### Earlier codec-v2 evidence


Earlier codec-v2 verification revision `2f66cc4bc54ed1131c6c13f65253b928726e4efe` was clean
and included current main `e72fb2a` before verification (ordinary merge, retaining
both context entries). That production/scenario code is `14d9b8b`, including the lead integration audit
corrections; previous catalog-v1 evidence is superseded by this run. The isolated runner command
was `python3 scripts/agent-tests/paper_test.py --scenario item-identity`, using
the provisioned accepted EULA, shared lease and memory admission. Run
`dc41a0c5fc8449bebda25bc20fe15198` returned exit 0 on Java `25.0.4.1`, Minecraft
`26.2`, Paper `26.2-121-a2a42c5`, with mechanic revision `item-codec-v2`.

- **Domain/build:** wrapper production and separate companion builds passed;
  45 production tests, zero failures/errors/skips. Of these, 13 item-domain
  cases cover trusted categories/levels, replacement edits, immutable inputs,
  calibration contributions, allowlisted rolls and explicit rejection. Eleven
  MockBukkit codec cases cover PDC/presentation/type/material/amount and the
  server-thread boundary. The other 21 tests are existing contracts/starter
  checks. API isolation passed. After merging main's stricter report validator,
  its separate Python failure-contract suite also passed all 27 tests without
  skips (`python3 -B -m unittest discover -s scripts/agent-tests -p 'test_*.py' -v`). The companion itself has no JUnit tests;
  its assertions execute on Paper. The integrated Symphony bridge suite also
  passed all six tests after merging main; no bridge code is changed by T02.
- **Actual Paper:** all 32 required assertions passed through the production
  classloader. Eight loadouts preserved resolved data through native ItemStack
  byte serialization. Two granted UUIDs stayed distinct through synthetic
  inventory moves. Renaming an ordinary bow did not grant identity, and editing
  a managed bow's text did not change identity or enchants. Base damage remained
  100 with no duplicate weapon-damage modifier; Vicious and a custom trusted
  roll preserved their modifiers. Catalog v2 contributes no crit-damage bonus
  on any preset, leaving T01 baseline 50 unchanged (expected damage/crit damage
  100/50 with the shared profile). The resolvedWeapon projection retained edited
  Duplex II/Vicious III and the roll with base modifiers exactly once; #7 must
  pass that projection once with only external additional sources. Actual
  cross-task factory/equipment integration remains #7 work. Multiple ultimates
  rejected on write/load;
  invalid levels, unknown IDs/rolls, wrong types/UUIDs, unchecked stats/kinds,
  schema 0/2, mismatched revisions, material and amount rejected explicitly.
- **Cleanup:** loopback port 41017; all three required entity/task/chunk-ticket
  cleanup assertions reported zero retained resources. This item-only scenario
  created no entities/tasks/tickets; its synthetic inventory was cleared in
  `finally`. The owned JVM exited 0, `forced=false`, `clean=true`.
- **Artifacts:** production SHA256
  `0da8e0ba22bc8922279c8e6c8eef929c3ca74443c26a2c351f0f9c3b8f559c66`;
  companion SHA256
  `c7e727011893b486bcd597a900807ddbd8f94d02cabbf4967a9d2925f1b12573`.
  Raw reports remain ignored under `build/reports/agent-paper/<runId>/`.
- **Remaining gates:** Windows smoke and authenticated client inventory moves,
  anvil renames, lore/glint rendering and multiplayer remain unrun. They become
  actionable through #7's grant/equipment integration. No firing/enchant-effect,
  reload/restart persistence, anti-duplication or performance claim is made.
  Lead review/merge precedes dependent #7/#9 integration; milestone gates remain
  unaccepted. Follow-up context-only commits retain this exact runtime artifact.



#### Earlier integrated stats and item evidence (v3)

Clean runtime revision `bba2ea154381f30745633ad654eaa3174e98ee4d` integrates
stats main `1efa7d`. Run `da51644d5f484ef4bb44d745495b459f` passed all 34
`item-codec-v3` assertions on Paper 26.2 build 121, plus 54 production tests
with zero failures/errors/skips. All eight serialized calibration loadouts
resolved through the production snapshot factory to damage 100/crit damage 50
and their expected crit/ferocity totals. The edited enchant/roll item resolved
to crit chance 5/ferocity 3. All cleanup assertions passed; server exit 0,
clean=true, forced=false. This covers codec-to-resolver integration with
synthetic inventories, not equipment events, client input or visuals.

Production SHA256 `103efd337f5c6a109d3199581d5f9e19317df471c1a11304bdab2f883b426547`.
Companion SHA256 `4d3e96c6961e4d398d32976830f1cba2b6b97377654294835308c799e584a569`.

### T01a resolver validation

GH-3 on `symphony/gh-3` is **Complete**, merged through [PR #23](https://github.com/Kav-K/OnlyDragons/pull/23) at `1efa7d0`; issue #3 and PR #23 are closed. Clean runtime-code revision
`36a7e23412c4271fc1fa76a980ceddb4c3740272` includes main `6f0ccfb` and its strict typed
report validator from PR #19, merged normally before final verification. The ordinary
wrapper build and both runner builds passed with JDK 25.0.4.1: 30 production
tests, zero failures/errors/skips, including 9 new resolver behavior tests and
API isolation. The resolver and source collection had all lines/branches covered;
coverage supplements the explicit numeric and rejection assertions. The integrated
runner suite also passed all 27 Python tests without skips.

`python3 scripts/agent-tests/paper_test.py --scenario stats-resolution` returned
exit 0 in run `50acd539aaae416481789e1b711bfa7f` on Paper
`26.2-121-a2a42c5` / Minecraft 26.2, mechanic `stats-calibration-v1`.
All 18 assertions passed. The scenario loaded the resolver from the production
plugin classloader and verified complete snapshots, weapon base exactly once,
crit damage baseline 50, raw crit 175 / ordinary probability 1, raw ferocity 750.5 / effective 500,
and weapon damage 151.5 with step results 101, 151.5, 75.75, 151.5, 151.5.
Input permutation retained the same explanation. Whole-source replacement
changed damage to 111 and removed the source's crit/ferocity/multipliers while
the old snapshot remained immutable. Invalid negative results failed.

Production SHA256:
`7f229dd2de086fb6bb0822abcade29fd5ec0e484e57a9d98d00f5a8e2580d59f`.
Companion SHA256:
`072ad52ffdfdfbb4229b123c292cffdf4594dc2fc6e94fdd80490c8fbf63301f`.
The runner reused the accepted EULA, shared lease, memory gate and disposable
issue-local world on loopback port 43443. All three cleanup assertions passed;
the scenario owned no entities, scheduled tasks or chunk tickets. Paper exited
0 with `forced=false`, `clean=true`. Raw reports stay in ignored
`build/reports/agent-paper/50acd539aaae416481789e1b711bfa7f/`.

These are synthetic domain inputs executed on real Paper. Windows smoke,
authenticated clients, equipment changes, mouse input, visuals and multiplayer
were not run. T01b owns equipment/play integration after T01a and T02 merge;
no gameplay milestone is accepted here. Independent review and final-head CI
passed before merge. No research claims changed: the calibration rationale is recorded in
document 02 rather than recasting document 01's upstream evidence.

The integration lead also ran the exact Cursor Windows Build target on clean
main `7c6d843` (`powershell.exe -NoProfile -ExecutionPolicy Bypass -File
scripts/Dev.ps1 Build`): the wrapper build, all 54 production tests and API
isolation passed. This started no human server and preserved the existing world.
The authenticated #6 worker launched with the optional remote-plugin sync flag
disabled and required MCP tools connected; the earlier cache-sync errors did not
recur in that launch.
