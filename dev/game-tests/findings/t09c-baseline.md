# T09c accepted shared baseline

[PR #32](https://github.com/Kav-K/OnlyDragons/pull/32) integrated T09c and the objective connected-player T01b gate at
`73a8cc8637d8044a1687dff67eab50cab057b5c1`. The integration lead recorded independent review and passing
final-head CI for tested head `78c3de4216b09e1142acb2d40fbc8529eff9e56b`.

## Exact evidence

- Suite: `0fb6adae1173467f87731ae50367f785`; state complete; all 15 declared outcomes verified.
- Tested source: `78c3de4216b09e1142acb2d40fbc8529eff9e56b`.
- Runtime input SHA256: `2649f53c9d19b213097299646e4bf33b3d98f17aa637be62582285f9402df3cf`.
- Input Git-tree SHA256: `2021573aa6a3657233bac61a97644ca1e6b84c6dcadcfcbc5abb59a5df697619`.
- Ignored local receipt: `build/reports/agent-paper-suites/0fb6adae1173467f87731ae50367f785/receipt.json`.
- Receipt SHA256: `b45421bec2e31d589f620ebd5592dc40ea681d32f6211d85991cdc626ca6773f`.
- Minecraft 26.2 / Paper build 121; Java 25.0.4.1; wrapper Gradle 9.7.1.
- Production SHA256: `cc8497b9b64e7f6d494b23a29a552a3e6ea3431269283b3d42fada8b00c3ecda`.
- Companion SHA256: `aa8ad72901f85aca072febd897955275e2599c81db7975433e548c1a50db84f5`.
- Protocol client SHA256: `94388053a34649a8f1659cfb9da3e6d81a60a8de10a9331323fd004d76ae4e59`.
- Client dependency lock SHA256: `619dfdb442e5ba1da7605e400dfc1944979911cb5cba315ca93dc1f5e9f0725e`.
- Strict verification metadata SHA256: `993d2c3d96a6401c76b6fdc17b209391500c602714ba03bec9997c7f52757532`.

The repository receipt validator replayed every raw runner/scenario/player report,
copied JUnit XML, exact staged artifact, declared negative result, resource admission,
and owned process/port cleanup. Each case used a fresh wrapper build and one serialized
disposable profile. Repeated 82-test production runs are one test suite,
not additional distinct tests; each actor build passed the same 7 client tests.

| Case | Declared outcome met | Paper assertion records | Run ID |
| --- | --- | ---: | --- |
| `lifecycle-calibration` | Positive pass | 14 | `1ab29947b5b94c619c13dfb81c482536` |
| `foundation-contracts` | Positive pass | 30 | `1cfb2c49c13543a8a8134b9d7fbcdb57` |
| `stats-resolution` | Positive pass | 19 | `2d65d413fc1148ec81a78ef14e782a9d` |
| `item-identity` | Positive pass | 35 | `730c66fefb924865bf5b5b3301fff61c` |
| `combat-accounting` | Positive pass | 31 | `b6f67d759e8d4d92b20cf7886daeddd8` |
| `projectile-feasibility` | Positive pass | 34 | `cb51aba3435d43959b90de1ce797f3da` |
| `protocol-player-calibration` | Positive pass | 25 | `0b0371a01d44481fad69849128bc129c` |
| `deliberate-failure` | Expected rejection: deliberate-failure | 15 | `9ba94a2fc2144018ba1556310c9a98c6` |
| `projectile-cleanup-failure` | Expected rejection: cleanup-failure | 5 | `5623cb2319e84346ac8ba0e97b243136` |
| `projectile-cleanup-abort` | Expected rejection: cleanup-abort | 5 | `e352b1d747eb472ebdd1aa04c5c4e5d2` |
| `protocol-player-early-exit` | Expected rejection: player-early-exit | 14 | `eea689ade0184b3385401d3abbd509a1` |
| `protocol-player-idle` | Expected rejection: player-idle | 14 | `45971212cdbe441abc1be296fcf11243` |
| `equipment-stats` | Positive pass | 23 | `ec960773d4204aabaea1e942e0a1895d` |
| `equipment-player` | Positive pass | 47 | `38da9c23cece490f951b9838c49dce97` |
| `protocol-player-soak` | Positive pass | 27 | `e12e249383f24ce1843cabec77ef590d` |

Negative controls intentionally fail their runner/scenario contract. Their rows
are accepted controls, not successful gameplay or all-passing assertion sets.

## Build, review and CI evidence

- Raw copied JUnit replay: 82 production tests per case; zero failures, errors or skips.
- Raw copied actor JUnit replay: 7 client tests per actor case; zero failures, errors or skips.
- Integration-lead verification outside the Paper receipt: 130 Linux Python tests passed;
  Windows passed 124 Python tests with 6 Linux-only tests excluded (not Windows passes).
- [Final-head Windows/Linux CI](https://github.com/Kav-K/OnlyDragons/actions/runs/34012362476) was checked by the integration lead
  for exact head `78c3de4216b09e1142acb2d40fbc8529eff9e56b`. CI/review approval is separate from automated receipt replay.
- Windows operator build on clean main `73a8cc8637d8044a1687dff67eab50cab057b5c1`: the integration lead ran `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Dev.ps1 Build`, the exact default Cursor **Minecraft: Build and test** target. It exited 0 with `BUILD SUCCESSFUL` in 16 seconds; parsed JUnit recorded 82 tests with zero failures, errors or skips. This is operator-checkout build evidence; Windows Play/smoke remains unrun.

## Superseded runs and resource fixes

Earlier incomplete/failed baselines were not accepted. The idle negative control
exposed two valid cleanup orderings; `70e1521` added strict evidence checks for
both. The final `78c3de4` runtime also bounded client builds and transient memory
probe retries, rechecked admission after builds, and preserved pre-start failure
reasons. See [build and memory ownership](../../agent-validation.md#build-and-memory-ownership).
The accepted receipt above reran all 15 cases on that final clean runtime;
partial earlier batches are history, not acceptance evidence.

## Scope and remaining gates

`equipment-player-v2` proves its 47 declared assertions and eight required actual
received-message checks with a real disposable offline protocol player: non-OP denial,
permission-scoped grants, held-slot listener refresh, same-UUID item mutation, offhand
exclusion, bonus replacement, death/respawn and quit cleanup. Inventory edits, commands
and death are explicit supported server API controls; client select/draw/release/quit
are actual protocol actions. No direct refresh-only substitute satisfies its event assertions.

`protocol-player-soak` holds the same actor online for at least 40 monotonic seconds
before normal quit and all four resource cleanup checks. Deterministic client tests
cover concurrent callback/disconnect completion without retaining the actor monitor.

Windows Play/smoke, authenticated-client compatibility and multiplayer,
human visuals and input feel are unrun. Future P01–P14 gameplay/performance requirements
and milestones M0–M5 remain pending. This baseline does not accept player-owned dragon
suppression/phase policy, a managed firing adapter, enchant integration or prefire gameplay.
The existing PR #29 and earlier task evidence remains historical context.

Windows exclusions recorded by the lead: `test_paper_test.LifecycleContractTests.test_failure_cleanup_stops_owned_child_preserves_unrelated_process`; `test_paper_test.LifecycleContractTests.test_shared_lease_blocks_second_runner_and_releases_after_failure`; `test_paper_test.LifecycleContractTests.test_sigterm_handler_can_finish_owned_child_cleanup`; `test_paper_test.LifecycleContractTests.test_unresponsive_owned_child_is_forced_and_not_reported_clean`; `test_player_actor.PlayerActorContractTests.test_failed_client_exit_is_cleaned_but_forced_exit_is_not_clean`; `test_player_actor.PlayerActorContractTests.test_lease_covers_two_owned_processes_through_failure_cleanup`.
