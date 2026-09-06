# T05 reconciliation and pending runtime acceptance

[PR #30](https://github.com/Kav-K/OnlyDragons/pull/30), [issue #8](https://github.com/Kav-K/OnlyDragons/issues/8), remains In review.
The bounded production coordinator and additive suites/acceptance mappings are
preserved. Full physical firing/Duplex/two-bow P08/P09 remain later integration;
production composition wiring belongs to lead-coordinated T08/#11.

## Lead integration, runtime acceptance pending

The lead branch combines main `705de34cfbe497d970067a1ddebaef2a85d75125`
with the handed-back PR #30 head `bc9eaba396a4f6a82e7df9c51d4cdbd707d7c143`.
The catalog preserves all 18 main cases plus `enchants-procs` (19 total),
including T04 player-owned observations, T09d actions and every declared control.
Proc implementation is unchanged. The later accepted-T04 ledger merge and
combined runtime verification remain pending; the checks below describe their
original worker inputs, not this new integration.

## Worker source reconciliation

The 6 September bounded resume integrates main
`4cfb7b9525a186c1f6591173a550565fed3efd53` (PR44 plus PR45) through an ordinary
merge. Additive conflict resolution retains all 17 main cases and `enchants-procs`,
all four cleanup assertions, generic actor admission, the reviewed client,
corrected console ERROR matching and T09d's actual completion ledger. No proc
arithmetic, trusted catalog, production listener or unrelated branch is changed.

Fresh actual-sandbox doctor returned ready: 16 context/fixture files readable,
JDK 25.0.4.1, existing accepted EULA readable, shared lease writable/available;
errors/waiting empty. Guest memory was 8223 MiB and effective host memory 6589 MiB
against 2816 required. This establishes access only, not runtime acceptance.

## Focused verification on clean merge 581e0e3

Exact tested source: `581e0e394a485724d932ab627bff1e2f79a9dfc2`.
Final fetch still resolved main to `4cfb7b9525a186c1f6591173a550565fed3efd53`.

- `bash ./gradlew build --console=plain`: passed including API isolation.
  Gradle reused unchanged production test results: 136 tests, zero failures,
  errors or skips. This resume made no production/test Java edits.
- `bash ./gradlew -p dev/game-tests build --console=plain`: passed; the combined
  companion recompiled successfully. It has no standalone unit test sources.
- `bash ./gradlew -p dev/player-client build installDist --dependency-verification
  strict --console=plain`: passed; 28 client tests ran with zero failures/errors/skips.
- `python3 -B -m unittest discover -s scripts/agent-tests -p 'test_*.py'`:
  238 tests passed. Symphony discovery under `scripts/symphony/tests`: 25 passed.
- `checkpoint.py plan --project .`: plan-valid, `automatedReady=false`,
  `acceptanceApproved=false`. No acceptance receipt supplied or replay claimed.
- Additive comparison against main verified every prior scenario definition,
  suite membership and other task's progress. The runner/error-matcher regression
  files are byte-identical to main; all four cleanup assertions remain present.

`paper_suite.py --changed-since origin/main --plan` selects all 18 cases (12
positive, six intended failures): lifecycle-calibration, foundation-contracts,
stats-resolution, item-identity, combat-accounting, projectile-feasibility,
protocol-player-calibration, deliberate-failure, projectile-cleanup-failure,
projectile-cleanup-abort, protocol-player-early-exit, protocol-player-idle,
equipment-stats, equipment-player, protocol-player-soak,
headless-player-primitives, headless-player-cleanup-abort and enchants-procs.
Selection alone is not runtime evidence; the future combined branch must plan
again after integrating the other features.

| Identity | SHA256 |
| --- | --- |
| Current actual source inputs | `f34a88a468c6d74d3c8955811c19f04ae8b772700c5aabb33e74c7a6aa20be8d` |
| Production JAR (build output, not staged to Paper) | `851df13d2721eb4d4079d86741375a1613d66e53cad0b49c9265457a32ca0257` |
| Companion JAR (build output, not staged to Paper) | `7f4fd73a9f73e1843f4e21ec3b9fe8d192ecc83dbdb860a6c236648e9cb159d6` |

Final documentation-head CI is recorded in PR30's handoff; historical CI is not
claimed for this changed head. This evidence-only follow-up does not change
runtime inputs or require repeating these focused checks.

## Runtime acceptance held

Lead raw-log review found Yggdrasil public-key and PaperVersionFetcher ERROR lines
in historical receipt `b7fd6bc562374f6f83b15da1f192caac`. The former console matcher
missed Paper's `[time ERROR]` format. PR44 fixes that matcher with regression
coverage; no proc arithmetic defect was identified by that audit. The historical
receipt's automated-ready flag does **not** establish acceptance.

The [exact old cases, source/artifact hashes and assertions](t05-suite-pr35.md)
are retained as historical observations, alongside the [PR34 record](t05-suite-pr34.md)
and [initial implementation evidence](t05-initial-evidence.md). None certify
these changed inputs.

Per the [latest bounded owner instruction](https://github.com/Kav-K/OnlyDragons/issues/8#issuecomment-5558552873),
this worker runs focused local checks and hands back a clean existing PR30.
No new full Paper suite or automated acceptance replay is claimed. After accepted
T04 integration, the lead intends a separate combined T02b/T05 integration with
one complete current suite, independent raw replay and both task checkpoints.
That runtime gate remains pending; local checks and CI cannot replace it.

Windows Play/smoke, human input/visuals, authenticated multiplayer and performance
remain unrun. Physical P08/P09 and production composition are objective later
integration requirements. No gameplay milestone is accepted.
