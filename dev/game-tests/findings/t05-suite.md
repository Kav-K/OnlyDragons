# T05 integrated suite evidence

PR [#30](https://github.com/Kav-K/OnlyDragons/pull/30), issue [#8](https://github.com/Kav-K/OnlyDragons/issues/8), remains In review.
The preserved implementation now participates in shared changed-area selection
and automated acceptance. This is the bounded production proc coordinator;
physical firing/Duplex and end-to-end two-bow P08/P09 remain later integration.

## Source and access

Clean runtime source: `ee1721a4f8dbbb2c96d1df4f60023f6b1e504ac1`, including
ordinary merge `f99f739` of main `5e8cfbfc7c9bfc7bc395e76f1e608f5853cd78be`.
A fresh fetch after the suite confirmed the same main. The old read-only skill
merge blocker is resolved by integrated PR #34; no sandbox bypass was used.
Actual worker doctor returned ready: 16 context/fixture files readable,
JDK 25.0.4.1, existing EULA readable, shared lease writable/available, errors
and waiting empty. Memory: 6890 MiB guest, 5141 MiB effective host against
2816 MiB with the protocol player. This doctor is access evidence only.

## Observed verification

- Wrapper production build passed: 136 tests (54 T05), zero failures/errors/skips,
  plus API isolation. Companion builds passed; its assertions run on Paper.
- Protocol client build passed all 7 tests; exact pinned publication and strict
  dependency verification retained.
- `python3 -B -m unittest discover -s scripts/agent-tests -p 'test_*.py'`:
  130 passing tests. The simulated transient memory-probe message is test output.
- `checkpoint.py plan --project .`: plan-valid. Changed-area selection included
  every existing case plus T05 because shared catalog/harness registration changed.
- `paper_suite.py --changed-since origin/main`: exit 0; all 16 cases met their
  declared outcomes (11 positive, five intended negative), Paper 26.2 build 121,
  Minecraft 26.2 / JDK 25.0.4.1. No missing, skipped or busy case counted as passed.
- `paper_suite.py --validate <receipt>`: exit 0, verified suite.
- `checkpoint.py acceptance --project . --base origin/main --receipt <receipt>
  --automated --task T05`: exit 0, `automatedReady: true`,
  `acceptanceApproved: false`. External review/CI is independently checked.

Receipt: `build/reports/agent-paper-suites/8ca121b5b6ef4a3a8addf1514bcb3f4c/receipt.json`.
Raw run reports, logs, copied JUnit and artifact evidence stay ignored locally.

| Identity | SHA256 |
| --- | --- |
| Receipt | `ed9028ae8c01bcfc8b7eb59881b2d9fcb93b40e0456dace20123cf756e5008ef` |
| Relevant source inputs | `2af9207b57b15df4714345f16ac592cd9ced85e4120cdcf154f573514ed04da6` |
| productionSha256 | `851df13d2721eb4d4079d86741375a1613d66e53cad0b49c9265457a32ca0257` |
| gameTestsSha256 | `b01ff7210830a877cf7388d582b0127876fbb6ce09574bda1ade98e00a49fea1` |

All cases used the same production/companion bytes. The receipt records the
client and all staged dependency hashes for actor cases and verifies exact inputs.

| Case | Run ID | Runner exit | Report assertions |
| --- | --- | --- | --- |
| `lifecycle-calibration` | `e5e46575cabb4b9784e3d8c6f0f297c8` | 0 | 14 |
| `foundation-contracts` | `f38cd7adbf4146928e0cb11a16a3aaa3` | 0 | 30 |
| `stats-resolution` | `a07177610f454e27a32e059fb9c2fe1d` | 0 | 19 |
| `item-identity` | `dc0146213e0c44b7bff67b1cf1fc41b2` | 0 | 35 |
| `combat-accounting` | `8813dacd9e7a4f1fa2cce366fd961b31` | 0 | 31 |
| `projectile-feasibility` | `219cded7384042dc8aee20c1b9e7f4e5` | 0 | 34 |
| `protocol-player-calibration` | `e3f1465cfb7f4c9d997360f612a6647c` | 0 | 25 |
| `deliberate-failure` | `925bfa85f3b6413a90601148a4f0e2a0` | 1 | 15 |
| `projectile-cleanup-failure` | `3fa8192fa6454ddcb669ab0f2ea3316a` | 1 | 5 |
| `projectile-cleanup-abort` | `14cf38908c774b6aa53b6fbf62dfd4f7` | 1 | 5 |
| `protocol-player-early-exit` | `f00e2403f8284b978d8ce1ed56e09dc4` | 1 | 14 |
| `protocol-player-idle` | `96de094620fe4c62b197ad9c2812b23b` | 1 | 14 |
| `equipment-stats` | `8e7f63407fce49798cf70cf7969a8c43` | 0 | 23 |
| `equipment-player` | `359adb8f01ec4259ac9b03c7ae29068f` | 0 | 47 |
| `protocol-player-soak` | `fb07faa77ba14eb58cb4a2a9cc31445c` | 0 | 27 |
| `enchants-procs` | `91ff51ab03c74a8098da926fd113b5b6` | 0 | 38 |

Negative exits are accepted only for their declared assertion/exception/abort/
early-exit/timeout outcome. Every report retained all four cleanup assertions at
zero. All 16 Paper JVMs exited 0, clean and unforced; all five clients were reaped
cleanly and unforced (the two negative clients exited 1 as intended). Every
recorded loopback port was confirmed closed after the suite. Tests used disposable
issue-local worlds, existing accepted EULA, shared lease and memory admission.
Only catalog-declared actor cases used the explicit offline protocol mode.

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

Runtime-head Windows/Linux CI passed on `ee1721a`:
[PR checks](https://github.com/Kav-K/OnlyDragons/actions/runs/34014552319),
[branch checks](https://github.com/Kav-K/OnlyDragons/actions/runs/34014550674).
Later evidence-only commits must retain the relevant source hash; final-head CI
is linked in PR30's handoff instead of recursively changing this tested record.
