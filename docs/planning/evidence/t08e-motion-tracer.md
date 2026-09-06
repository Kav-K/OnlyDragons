# T08e motion and Tracer — GH-65

In progress; no final acceptance or milestone claim. Branch `symphony/gh-65`
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

## Outstanding gates

Moving native-positive and owned collision/suppression; returning real volleys and
honest no-enchant/blocked/range controls; original pre-spawn UUID acquisition;
terminal/death/disable ownership; received motion; current-main clean runtime build,
complete suite receipt/replay and task checkpoint. Human full-client smoothness,
aiming/feel and authenticated compatibility remain separate and unrun.
