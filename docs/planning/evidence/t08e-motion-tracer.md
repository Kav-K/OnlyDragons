# T08e motion and Tracer — GH-65

In review in [PR #75](https://github.com/Kav-K/OnlyDragons/pull/75); no final acceptance or milestone claim. Branch `symphony/gh-65`
includes main `7a380408e02fe59d1820163bd7cfa68169e85e4b`. Production/profile ownership
is described in document02; owner comments5561944060/5561951211 declare calibration.

## Iteration evidence (6 September 2026)

All runs use the isolated runner, accepted EULA and shared lease/memory gate on
Paper26.2-121/JDK25. Dirty runs are development evidence only.

- `5c1e6bb9887b4457b3666b8d9203733d`: no Paper boot. Two overlapping Gradle writers
  (manual build and runner build) collided in binary test results. Subsequent commands
  are serialized; no environment reset or test weakening was needed.
- `38f0d95651de42d6b82a91fb45704e2c`: no Paper boot. Explicit catalog numeric oracle
  correctly rejected the new tenth preset without expectations. Added its100/50/0/0
  totals to domain/item/equipment tables, preserving all old entries.
- `cf66027db3024cd98956ec3060aba42b`, `dragon-motion-experiment`:10 assertions passed;
  production236 tests, zero failures/errors/skips. HOVER velocity displacement0;
  public position adapter500 steps at non-origin(160,100,160) stayed within radius16.
  Maximum parent step0.1592332248072047, part step0.33660788014927184, part corner
  offset9.43166734215813. Production SHA256
  `d32500463e7e18c03420871a996291f0262355f31d4eeea5d2e2476bc5677beb`, companion
  `3b8852d170b08f714cf439edb2cb5d9913664bca781af8623b56551b2c9e94da`.
- `23a78c73f48e4dd8b0ddab81e1f74038`, `dragon-flight`: production adapter traversed
  four chunks at center(160,100,-160) for480 sampled ticks per radius16/24/48.
  Radius16 uses route4: path82.33069, max step0.20613381, yaw2.86480713 degrees.
  Radius24/48 use route8: path83.63398, max step0.20155117, yaw1.43240356 degrees.
  Actual parent/eight-part bounds, stationary mode, reset and cancelled movement
  cleanup passed. Production SHA256
  `a3b452546f771eecc4626a93d4149542dd4db3d70d70878263545e1d0eaa6fce`, companion
  `55efe294093f93ce27d2e9273b03c22d59cd94ba8e1c0820786afb9bb5847f98`.

Both Paper processes exited0 unforced with clean owned cleanup. Raw reports live
under `build/reports/agent-paper/<runId>`; these are not complete suite receipts.
The12-block route envelope exceeds the measured9.432 part-corner envelope and
allows stopping/rotation margin. Safety remains checked against actual geometry.

- `61a7ab13429044d1898c2b49133704b3`: first returning-volley scenario failed
  unwatched damage-probe and wall-control predicates; genuine returning collisions,
  exact880HP/120credit and original targetless UUID acquisition passed.
- `2cd6f1e5739c4bd195dbe0b0cc020596`: wall moved ahead of the swept dragon surface;
  its native block collision passed. Moving lethal froze1000HP/credit, retained
  parent tickets during animation, then removed tickets with no rewards. Received
  bound-dragon packets:87 updates, path50.9842, maximum received step0.604612.
  This staged fixture still lacked probe.watch and ran beyond the default60s bound;
  it failed overall. These observations do not establish acceptance.
- `8fdbf4fe34ea4fada5b9aa0052832f0b`: watched native positive damage2 reached
  nativeHP198; both owned collision sentinels had initial2.5, cancelled/settled0.
  Real grace collision occurred at original launch age1 and domainHP900.
  Every reached feature assertion passed, but the default60s runner deadline
  aborted before terminal completion. Next runs explicitly use the catalog's180s
  scenario bound. Inputs are committed before further acceptance runs.
- Python runner/receipt regressions:274 tests passed after adding all new cases to
  both mandatory full-suite areas and retaining restart-specific assertions in
  the mandatory first-phase declaration. No phase assertion was removed.

## Clean focused candidate

Runtime revision `eab1c015b4ccbbd459e72721afa79782405a6512`, worktree clean in every
result, integrated main `7a38040`. Commands use `paper_test.py` with the registered
player mode and explicit scenario bound (180s for moving/Tracers/restart,150s flight).
Production SHA256 `25de23ef2d8945d15fb445a60accb99c05302e515695393b2a63725ad5ad3bad`;
companion `677dbf3d9ce86447fd6b4194e9dea114e320c89417caefd4eaa4b61d5cbf5e60`;
client `e746d90284ab1f5b5dbe43beed034621916706239d0907d2416e1c2e74e0f971`.
Each wrapper build:238 production/6 companion tests; player builds31 tests, all
zero failures/errors/skips. Python274 and checkpoint plan pass.
[Windows/Linux CI](https://github.com/Kav-K/OnlyDragons/actions/runs/34060942946) passes;
its Paper integration job is skipped and supplies no full-cohort evidence.

| Run | Focused result |
| --- | --- |
| `74f697f2ea2c4d6f9d65e0e7a04a7cf8` | moving-Tracer25/25 assertions plus required bound-UUID motion receipt |
| `7a2ef51089dc41c1a913632663bf1468` | flight18/18 including cancellation, redirected yaw and lost AI |
| `5bdc2712366e4328828516f3a7694858` | moving restart then registered as fresh:18/13 phase assertions |
| `382d243682de47a7bbb698dc8372df45` | preserved-v1 Tracer58/58 including captured trace revision |
| `217783b824924f0a819c6a3c54c62a76` | intended abort only: exit1 with `scenario_exception`; all ticket/task cleanup controls pass |

Every owned server/client exited0 unforced with clean cleanup, including both
restart boots and the expected-negative server. Raw files are under
`build/reports/agent-paper/<runId>` in GH-65; no worlds/logs are committed.

Native player use/release packets produced the returning primary/Duplex collisions:
exact880 domainHP,176 nativeHP and120 contribution. Independent public Paper velocity
observations matched applied steering toward current native parts. A native-positive
control used a public-API spawned arrow, distinct from protocol release: actual
native damage2 lowered its moving unregistered dragon to198HP. Owned native-damage
sentinels observed positive initial damage, cancelled/zero final damage, alongside
production accounting. These are separate event and service assertions.

The grace collision occurred at original launch age1 and900HP. Managed lethal froze
1000 HP/credit, stopped controller steps, retained parent tickets through native death
and then released all parent/projectile reservations with no loot/XP. Native death
animation remains native behavior; stopped route state does not suppress animation.
Received bound-dragon motion measured87 updates, path50.98423472, maximum received
step0.60461149. This aggregates server ticks and is not rendered-client smoothness.
Clean flight radii16/24/48 reproduced the preceding measured routes across4 chunks.

## Integration handoff and remaining gates

The lead requested an explicit `dragon-restart-moving` variant after reviewing
the shared restart class. The previous fresh catalog pointed to the moving plans;
its passing result above remains historical. The descendant restores the original
stationary fresh catalog and adds a separate motion constructor flag/registration.
The moving first boot now also exercises the human default `spawn` command.
Both final variants pass at clean code candidate
`778b425e77fdba1d883c0976290d3ca31e2f8b0d`:

- `0221c074485e475186e8687803558574`, `dragon-restart-moving`:18/13 phase
  assertions, including default spawn and motion before shutdown. Memory admission
  waited normally before both boots; both were eventually admitted and completed.
- `07e04373f37b42ee825488b628f9065e`, restored stationary `dragon-restart-fresh`:
  17/13 phase assertions, retaining the original stationary fresh coverage.

Every phase had server/client exit0, unforced, clean cleanup. Production/client
SHA256 remain the preceding values; final companion SHA256 is
`671e7c59d5a0890d8055f09182ccca999c2d0653f8890bb3c0bca6ecd7d04a48`.
Python274/checkpoint plan and [candidate Windows/Linux CI](https://github.com/Kav-K/OnlyDragons/actions/runs/34061616735)
pass. The plan explicitly reports automatedReady=false/acceptanceApproved=false;
it is not an accepted task checkpoint. Final fetch still resolved main `7a38040`;
merging it was a no-op. Later documentation-only commit(s) do not alter these inputs.

[Owner comment5562281395](https://github.com/Kav-K/OnlyDragons/issues/65#issuecomment-5562281395)
assigns the combined #64/#65/current-main cohort after #66 to the lead. The worker
must not duplicate that full suite. Complete suite receipt/replay, both task
checkpoints, combined-main/current-CI and independent integration review remain
pending there. Human full-client smoothness, aiming/return visibility, Windows
Play smoke and authenticated compatibility remain separate and unrun.

API ownership: `DragonBackend` owns route/phase/footprint-ticket lifetime; immutable
`DevelopmentDragonService.motion()` is diagnostic only. `OwnedProjectile` captures
trusted profile/original launch origin; T07 owns all projectile ticking/retirement.
`EntityMotionObservation` + `entity_motion.py` add only optional session motion and
required bound-UUID matchers. Preserve #64's separate UI fields/helpers when merging.
No countdown/prepare API is added: T10 still owns managed admission/hatch continuity.
