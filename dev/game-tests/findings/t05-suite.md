# T05 reconciliation and pending runtime acceptance

[PR #30](https://github.com/Kav-K/OnlyDragons/pull/30), [issue #8](https://github.com/Kav-K/OnlyDragons/issues/8), remains In review.
The bounded production coordinator and additive suites/acceptance mappings are
preserved. Full physical firing/Duplex/two-bow P08/P09 remain later integration;
production composition wiring belongs to lead-coordinated T08/#11.

## Current source reconciliation

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

Focused build/client/runner checks and the final committed source identity are
recorded below and in PR30's handoff. Changed-area selection is the complete
18-case baseline: the 17 main cases plus `enchants-procs` (12 positives and six
intended failures). Plan validity is distinct from automated readiness.

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
