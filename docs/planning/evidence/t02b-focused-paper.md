# T02b focused validation — GH-38 / draft PR #46

Runtime source: `90b5cf6775dcdf53003f89d1d5fb2cf7940edf58`, clean, including main
`4cfb7b9525a186c1f6591173a550565fed3efd53`. Later context-only commits do not alter
these runtime inputs. [Implementation PR](https://github.com/Kav-K/OnlyDragons/pull/46).

## Actual checks (6 September 2026)

- JDK 25.0.4.1 root wrapper/API isolation: **109 production tests**, no failures,
  errors or skips; companion build passed. This includes LF/CRLF valid/malformed
  candidates and same-label/different-content retained selections.
- Checkpoint and suite Python tests: **50 + 42 passed**. Sandbox doctor and
  structural plan passed before implementation and after main integration.
- Runtime-head Windows/Linux [CI passed](https://github.com/Kav-K/OnlyDragons/actions/runs/34027215466).
  The first head's Windows failure exposed LF-only test candidate removal; the
  corrected helpers preserve missing-field rejection on both line-ending forms.
- Both local runs used only `scripts/agent-tests/paper_test.py`, the existing
  accepted EULA, shared lease, 2,560 MiB memory admission, authenticated empty
  disposable worlds and loopback. Paper: **26.2-121-a2a42c5**, protocol 776.

| Scenario | Run ID | Actual result |
| --- | --- | --- |
| `dragon-definitions` | `67e4e7bc9f01462386d2fc14f05655d1` | Runner exit 0; all 30 assertions passed; original catalog restored. |
| `dragon-definitions-abort` | `aa231e816af14b1ca86ed9a35dedd286` | Expected runner exit 1; 14 checks passed and only `scenario_exception` failed as intended (`Companion disabled before scenario completion`); original catalog restored after the deliberate 2,000-HP mutation. |

Both owned Paper JVMs exited 0 without forcing; all five resource/listener/entity/
task/chunk cleanup counters were zero. Loopback ports 41615 and 57825 were closed.
The shared `verify_scenario` validator replayed the declared positive/abort
outcomes. Bootstrap checksums and staged plugin hashes were rechecked against
both reports. Raw reports are retained under `build/reports/agent-paper/<runId>/`
in the issue workspace; no logs, worlds or generated binaries are committed.

## Artifact and report identities

Both runs staged the same artifacts:

- Production SHA256: `81c01679f045b98a0c51582407a4078a278717e955089d1fdef6f7ec78413b59`
- Companion SHA256: `12950def731bc36436ccbd1dfa158f49091679f0e13fa23c291ff46be4deedfa`
- Pinned Paper SHA256: `0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54`
- Verified Mojang bootstrap SHA256: `cdacdfb25898de5e4b4b0e5ddcc2722f77067e46605709c2d886c000ebb63ec5`

| Run | result.json SHA256 | scenario.json SHA256 |
| --- | --- | --- |
| Positive | `419a21b13f3e8b74908da17180d501ee18f5885fd264d5eec96295d7bd7a281c` | `da7d2a7e3c41a885c0a5e7a657557fd7c0228a29493453cd675ac65701315c84` |
| Abort | `0ec915cb6c446e22cc861feae378c540ddc635dcfd0578dedef5c8028b3d7360` | `380c48b110c7a466b4e1acb63242d4bc02906239fc99ae5c2a0c452194d54576` |

## Acceptance and next dependency

These are focused production-loader/registry checks using synthetic candidates,
plus a standalone Paper inventory sentinel. No players or live dragons were
spawned, and no loot calculation/grant/currency service exists. The phase contract
is declarative; it cannot widen T06's separately reviewed admission policy.

At the focused runtime revision, the complete selection contained **19 cases**:
the then-current 17-case baseline plus both catalog cases. Final normal merge
`99118728e5110dabb51b2e8ec3bf2a972a0e0221` integrates T04/main
`705de34cfbe497d970067a1ddebaef2a85d75125`, preserving its
`projectile-player-feasibility` case and both catalog cases. The reconciled
selection is **20 cases**. Root wrapper and companion builds and the structural
checkpoint passed after reconciliation; the focused reports above retain
their original source and artifact identities and do not certify that new
combined companion. **No complete suite receipt or T02b automated
acceptance checkpoint is claimed here.** Per the [owner's verification coordination](https://github.com/Kav-K/OnlyDragons/issues/38#issuecomment-5558553088),
the lead will run the catalog/proc combined integration suite and both task
checkpoints on the latest accepted base, independently replay evidence and review
current-head CI before merge. The worker does not duplicate that full local run
or edit the proc branch. T02b remains **In review**, with completed requirements
unclaimed until that acceptance. T08a is the next consumer after its own remaining
prerequisites; full resolved selections, not revision labels alone, carry content.

Windows operator smoke, authenticated-client compatibility and human visuals/feel
remain unrun. These checks do not prove spawning, native death, fight completion,
reward eligibility/delivery or any M0–M5/T11/T12 gate. The earlier local iteration
`8d29e58545684a6eaa5ca9dd2cc01a48` stopped during build preparation before Paper
startup and supplies no runtime evidence.
