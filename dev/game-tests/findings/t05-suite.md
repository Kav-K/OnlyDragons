# T05 current integrated suite evidence

[PR #30](https://github.com/Kav-K/OnlyDragons/pull/30), [issue #8](https://github.com/Kav-K/OnlyDragons/issues/8), remains In review.
The bounded production coordinator and additive suites/acceptance mappings are
preserved. Full physical firing/Duplex/two-bow P08/P09 remain later integration;
production composition wiring belongs to lead-coordinated T08/#11.

## Source and recovery verification

Clean runtime `5284b66f086d3762bf9e3b269c50f57dd24e88fa` includes PR35 main
`1deb9a804480522738192f795914fc424ab2d330`. Its complete 16-case suite finished
before the infrastructure interruption. On 6 September, ordinary merge
`e6c2b500cd80eaee105f33dba9a68fb60420fd4e` integrated current main
`799c01dd5e9da35cf17bf29ddb671ebc66ab4b3b`. These later changes are planning-only;
full receipt replay and task acceptance verified identical relevant inputs on the
merged clean branch. The receipt retains its actual runtime/base identities.
No new Paper execution is claimed for this recovery replay.

Fresh actual-sandbox doctor returned ready: 16 readable context/fixture files,
Java 25.0.4.1, existing accepted EULA readable, shared lease writable/available,
errors/waiting empty. Guest memory was 9222 MiB and effective host memory 5208 MiB
against 2816 required. This is preflight only. Prior skill-write and network
blockers are resolved without changing authentication or sandbox boundaries.

## Verification

- Complete changed-area selection: all 16 cases below, including five intended
  failures. Saved raw reports, copied JUnit, source/artifact hashes and owned
  cleanup passed fresh `paper_suite.py --validate` replay.
- Every case's wrapper build records 136 production tests, zero failures/errors/
  skips; actor builds also record seven client tests. API isolation and pinned
  client dependency verification passed in those builds.
- Fresh `python3 -B -m unittest discover -s scripts/agent-tests -p 'test_*.py'`:
  135 tests passed after recovery.
- `checkpoint.py plan --project .`: plan-valid.
- `checkpoint.py acceptance --project . --base origin/main --receipt
  build/reports/agent-paper-suites/b7fd6bc562374f6f83b15da1f192caac/receipt.json
  --automated --task T05`: exit 0, automated-ready, acceptanceApproved false.
  This checks all current selected cases and T05 requirements; CI/review remain
  independent external gates.

Receipt: `build/reports/agent-paper-suites/b7fd6bc562374f6f83b15da1f192caac/receipt.json`.
Raw reports/build outputs remain ignored; exact identities follow.

| Identity | SHA256 |
| --- | --- |
| Receipt | `ec1840213dd4d202fbb775aa34624d74e383677aad497b120c8af37485be3072` |
| Relevant source inputs | `87c3c92e408de81b9d8e6c6be072af741aa9376670f3902f135f091e4ceea7c8` |
| Production artifact | `851df13d2721eb4d4079d86741375a1613d66e53cad0b49c9265457a32ca0257` |
| Companion artifact | `b01ff7210830a877cf7388d582b0127876fbb6ce09574bda1ade98e00a49fea1` |

| Case | Run ID | Runner exit | Assertions |
| --- | --- | --- | --- |
| `lifecycle-calibration` | `78d90e4b78f346c8a9dec13f601c2e51` | 0 | 14 |
| `foundation-contracts` | `e5c9372dac1447a5ad62edc5335a6a1a` | 0 | 30 |
| `stats-resolution` | `be9fffc477dd4655bb571d7dbf310249` | 0 | 19 |
| `item-identity` | `976975e9b60e4864ad111aedd4a343bb` | 0 | 35 |
| `combat-accounting` | `031596ffe8c7460cb2abc01e2001a390` | 0 | 31 |
| `projectile-feasibility` | `ef79de9a8d8146679f1d69d400d8c744` | 0 | 34 |
| `protocol-player-calibration` | `963bd9911ad6478d8b9b58dd4e86d384` | 0 | 25 |
| `deliberate-failure` | `e99b83c8fb67453ab0febafce419fd97` | 1 | 15 |
| `projectile-cleanup-failure` | `5aa0703453924237a59237bbba05d44c` | 1 | 5 |
| `projectile-cleanup-abort` | `68551e45a456404ea07175aac3cd77fe` | 1 | 5 |
| `protocol-player-early-exit` | `3b7e28e042bf456d9c8c42d09ce952b6` | 1 | 14 |
| `protocol-player-idle` | `878731c8ae9f4ae493637857bc1dca91` | 1 | 14 |
| `equipment-stats` | `55f5c3dcacdd409e964d997a0f6d3c17` | 0 | 23 |
| `equipment-player` | `f3105f57df6142548899355cc24ae271` | 0 | 47 |
| `protocol-player-soak` | `262f1b30fdf64172af65857923732ee7` | 0 | 27 |
| `enchants-procs` | `1e895c9e73c743368be4c65704b43f64` | 0 | 38 |

All 16 Paper processes exited cleanly and unforced; all five actor clients were
reaped cleanly and unforced, with intended negative client exits distinguished.
All four cleanup counters were zero in every scenario. The runner used the
existing EULA, disposable issue-local worlds, loopback ports and shared lease/
memory admission. Only declared actor cases used the explicit offline mode.

## T05 observations and remaining scope

`enchants-procs-v1` passed 38/38 assertions on 72 actual scheduler ticks. Five
stable children inherited 75 mitigated damage and crit without recursion; whole
group capacity rejection exposed five rejected children. First due delivery was
exactly two ticks later. Accounting was 525 contribution / 99,475 remaining HP.
Captured Duplex and its child retained 15 damage. Tempo reached +200%, expired
exactly at its boundary and was not refreshed by Duplex. Stale sessions, late quit,
ended target, terminal close and late callbacks respected production rejection.
A real byte-serialized item traversed codec → stats → effects → combat: Vicious V
remained 5 once, Power/Snipe bonuses were 0.4/0.04 and child damage remained 72
after an item edit. Controlled ferocity, zero-base Tempo and Snipe displacement
checks passed. No Gravity/Overload formula or ghost-health coefficient was chosen.

These are synthetic settled impacts/session tokens through the real coordinator,
not native projectile firing or production player-listener integration. P08/P09
remain objective later adapter/integration work. Windows Play/smoke, human
input/visuals, authenticated multiplayer and performance remain unrun.
No gameplay milestone is accepted. Lead review and serial merge remain required.

Final-head CI and the final current-main check are recorded in PR30's handoff.
The [previous PR34 suite record](t05-suite-pr34.md) and
[initial implementation evidence](t05-initial-evidence.md) retain historical
source identities and results; neither substitutes for the current replay.
