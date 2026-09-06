# T04 accepted projectile feasibility

[PR #31](https://github.com/Kav-K/OnlyDragons/pull/31) merged at
`705de34cfbe497d970067a1ddebaef2a85d75125` after verification of clean runtime
`96ef4b3725aafe0674c4b057ffe7fd21df263ca7`.
[Hosted run 34026523246](https://github.com/Kav-K/OnlyDragons/actions/runs/34026523246)
passed all 18 declared outcomes: 12 positive cases and six intended negative
controls. [Current-runtime CI 34026483411](https://github.com/Kav-K/OnlyDragons/actions/runs/34026483411)
passed. Independent canonical-path replay and raw evidence review found no
actionable issue: all 52 required player-projectile assertions and shared cleanup
checks were verified, with clear ERROR checks and unforced owned-process cleanup.
Copied JUnit evidence verified 82 production and 28 client tests, with zero
failures, errors or skips.

| Evidence | Identity / SHA-256 |
| --- | --- |
| Suite | `aec2717e2c1d40979e3d482af4d46418` |
| Receipt | `0caf21500ed0cfa7f1b456864acce79424b504f260184554343b25d51a443364` |
| Source input hash | `0ba6eb10a6da7080fca913b1cb89abee6cd147d7afd4c5f89dd75de21e63c938` |
| Git input-tree hash | `aa1eeb2e7111bd41f8163d59d1b899137ba17323343e0c95903529ab01e58ed6` |
| Hosted artifact | ID `9987400888` |
| Artifact ZIP | `f9792a93a8a3639d721ae4e2578a1b5bc555ec213c2c28aa0389b6c023f5589a` |

The three automated requirements are `projectile-observations`,
`projectile-player-observations` and `listener-cleanup`. The full suite retains
the shooterless/pre-spawn/lifetime case, the actual player-owned positive and
suppression/geometry/phase/volley observations, both projectile cleanup controls,
and all T09d cases. Raw receipts, staged artifacts, action plans, JUnit and
client/server reports retain their original identities; no startup-only or
manufactured-event substitute is used. The source hash identifies actual Linux
checkout bytes, not a checkout with different line endings.

## Separate policy acceptance

The lead explicitly accepted `projectile-impact-policy` in
[the independent policy review](https://github.com/Kav-K/OnlyDragons/pull/31#issuecomment-5558603858).
This external decision is recorded separately from the automated run.

The adopted contract uses the first `ProjectileHitEvent` as the sole terminal
candidate, settles external physical-hit vetoes after synchronous handlers,
rechecks generation/liveness and retires accepted or rejected projectiles.
Native suppression must not cancel that physical event or rely on a native
damage event being present. Earlier observed native cancellation remains a veto;
arbitrary later cancellation-setter provenance is not promised.
Actual impacted parts map to their parent with uniform managed scale **1.0**.
Only measured `HOVER`, `CIRCLING` and `SEARCH_FOR_BREATH_ATTACK_TARGET` phases
are initially admitted; other phases require explicit rejection until measured.
There is no semantic-head selector or all-phase/Hypixel-parity claim.

[The detailed findings](../../../dev/game-tests/findings/projectile-player-feasibility.md)
retain missed/failed iterations, measured limits and earlier evidence at their
original revisions. [The foundation contract](../02-foundation-plan.md#6-one-combat-authority)
defines the subsequent production implementation.

## Next boundary

T04 is complete and its actual merge is recorded. T06 is planned and eligible
for the lead's dispatch label because T01b/T02/T04 are integrated; accepted M0
is not a T06 prerequisite. Production P02/P04, firing/ammo/ownership and actual
multiplayer firing remain deferred T06 work. T08/M3 retains connected physical/
proc HP and ghost-score attribution. No production adapter, Tracer, encounter,
ritual or reward feature is accepted by this feasibility result.
Natural End-cycle/all-phase, authenticated-client, human visual/input/feel and
performance evidence retain their separate limits. M0–M5 remain unaccepted.
