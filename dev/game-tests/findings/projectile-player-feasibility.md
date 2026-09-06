# T04 player-owned continuation

Scenario `projectile-player-feasibility`, mechanic `projectile-player-v1`, on
Paper 26.2 build 121 / JDK 25. This continuation preserves the
[earlier shooterless and lifecycle evidence](projectile-feasibility.md).

The fixture uses one unmodified native client bow release along +Z into a real
HOVER dragon. Separate fresh dragons then receive API-spawned native arrows with
the same online Player as shooter: native positive, hit cancellation, damage
cancellation and zero-base-damage controls. Additional part geometry, CIRCLING,
seated-phase and same-tick volley attempts retain actual outcomes, including
misses. The fixture never teleports/replaces arrows or manufactures events.
The fixed protocol client sequence and authentication/profile defaults are unchanged.

Run through the approved lease/memory-gated runner:

```bash
python3 scripts/agent-tests/paper_test.py --scenario projectile-player-feasibility --test-player protocol-calibration
```

Actual results are pending. A fixture implementation or generic player
calibration is not a native dragon suppression pass. Semantic head/body,
natural End flight/landing, authenticated clients, multiplayer, Windows live
smoke, visuals and performance remain unaccepted. #9 stays blocked and M0 is
unaccepted until the integration lead reviews T04 evidence and the design.
