# T02c focused anvil evidence

[Draft PR #85](https://github.com/Kav-K/OnlyDragons/pull/85), GH-69. This is a focused worker handoff, not milestone acceptance.

Clean runtime/scenario/client/parser revision: `4ed3d2de175d4fde921370630bf5c392efcd08a3`.
Fetched and merged current main `2158a51572d65e7e8856ec77f44b3decc982450c` before verification (already current). Later documentation commits change evidence/context only.

All runs used the permitted isolated runner, Paper 26.2 build121, JDK25, the existing accepted EULA, loopback disposable worlds and protocol-actions-v1. Every result records `worktreeDirty=false`; client and server both exited 0, clean and unforced.

| Scenario | Run ID | Passed assertions |
| --- | --- | --- |
| anvil-boundaries | `827e16ddf56a408eac438e61e58a61a5` | 33 |
| anvil-lifecycle | `fb3cc06758ff466c854e22747e2ebb44` | 39 |
| enchant-anvil | `4e86e5bdba7a4e2187c4e73e38e69e2b` | 60 |

**132 assertions passed.** Each run rebuilt through the wrapper: 311 production, 6 companion and 37 client tests, zero failures/errors/skips. Python verification passed 50 existing action tests and 6 anvil replay tests.

The original player reports were revalidated using `player_actions.validate_report` against their tracked plan SHA256, exact run/pins, original issued timestamp and scenario completion timestamp, followed by `validate_server_journal` and `validate_messages`. All three passed. No timestamps or report content were rewritten.

## What the evidence proves

- All ten max-level books granted by real commands, permission denial, a real placed anvil open and actual container input/rename/result/store/close packets. Received 39-slot previews, cost properties, output digests and fractional XP match native observations. Normal left/right and shift collection each occur.
- Costs at the ten max levels are 9 (Snipe IV plus rename), 10, 10, 10, 12, 20, 4, 20, 20 and 14. Preview is free; native collection alone consumes inputs and levels while preserving fraction .375. UUID/catalog/rolls/durability/prior work/native Power I and Unbreaking III/foreign PDC survive.
- The collected Power VII bow immediately deals exactly 165 production health/contribution damage, leaving the 1000-HP calibration target at 835; native bow-release and collision observations accompany the production result.
- Equal-level book combination, one-of-three right-stack consumption, legacy common-enchant success, v2/v3 new-enchant rejection, cap/no-op/conflicting ultimate/wrong or spoofed book/stacked left rejection.
- Insufficient XP, full inventory, drop/hotbar gestures, late cancellation then retry, repeated consumed-result click, managed→changed-managed and managed→ordinary mutation during the real click, late prepare veto, rename only, Creative exemption and ordinary vanilla repair.
- Close and disconnect return only unconsumed inputs; reconnect binds a new player/view. Direct **service close**, with a positive pending-callback count immediately before close, removes offers/callbacks/sessions and preserves inputs. This is not an assertion that the fixture invoked plugin disable.

Fixture setup and native events remain distinct: later trials install labelled inputs and XP with public APIs; real protocol packets perform extraction. Each rejection is explicitly fully resynchronized before the next fixture replaces inputs. The stacked-left fixture sets max-stack16 so Paper actually receives two bows rather than clamping to one.

## Reproduce and inspect

Run each case from the clean candidate with the provisioned runtime/EULA/lease environment:

```bash
python3 scripts/agent-tests/paper_test.py --scenario anvil-boundaries --test-player protocol-actions-v1 --scenario-timeout 240
python3 scripts/agent-tests/paper_test.py --scenario anvil-lifecycle --test-player protocol-actions-v1 --scenario-timeout 240
python3 scripts/agent-tests/paper_test.py --scenario enchant-anvil --test-player protocol-actions-v1 --scenario-timeout 240
```

Raw reports remain issue-local at `build/reports/agent-paper/<run-id>/{result,scenario,player}.json`; worlds, binaries and logs are not committed. The hashes below identify the original evidence for integration review.

Production JAR SHA256: `ff009b8c467c2186112d5636b4be3d1b25bb2ffa71ea9ecee154fee874cc0f2d`.
Companion JAR SHA256: `6e395a0d3e4b8914e6cb688dcb2a18066002d69502b62fbb066542c6ae731179`. Both hashes match across the three clean runs.

### anvil-boundaries

- `result.json`: `83fb9a6ed6daad62c98265662503a0c015fff186e1f17c11a848c8952187e965`
- `scenario.json`: `1a0c6ec40c00164b39f4a3754daa87ae899dd238328a71e9903a9299ef5abd17`
- `player.json`: `327b312f30aa25c6399065fdd7cc1ce66b2e9ed5e6f93a0ab155a654008d9a4d`

### anvil-lifecycle

- `result.json`: `3931ed33505e5ecb7cedf7c9fb73ccb9eca6144932a54526707cfd016c6c622f`
- `scenario.json`: `0dd04b67bbeb31dbf51392029a03ab7f266929c8feeae9136ba417be0170ce91`
- `player.json`: `c10ce51b27621d5e5e2143acd85dc09fff9648e980f14c8c6e39d5e84adf8291`

### enchant-anvil

- `result.json`: `2f5902eccb906dac1d7ef0c55ce14040c4222f8dda0794cdf470c3b797ed71c5`
- `scenario.json`: `fd5c2b65606f573b0179efdeb47d7fb7bf418d469702dec48f605d62f268dbf2`
- `player.json`: `ba8bcfbafb7ef63229e8ce557ced99ae0707b5d6a286e2a0e019ce5942ce77ec`

## Preserved iteration failures

- `12b9ecd68a9e488891a2821706483af7`: plan ID validation failed before startup; underscore action IDs were corrected to the tracked bounded format.
- `82fc5bd9c0c74c26b0774f461b5b9478`: ten native application transactions passed, but the firing fixture placed the shooter inside the anvil. Incomplete client exit1, owned cleanup unforced. Corrected shot setup later passed all60 in dirty iteration `778d5a48777e40e79713f99d732e010a`.
- `743cc2c968fe4446ac8e1bb338b15f12`: tooltip compile failure before Paper (firingMode belongs to the nested weapon definition); fixed before subsequent builds.
- `9ac14642b4154c4198e6cd99ba57d343`: 39 native lifecycle assertions passed; received conservation correctly failed because the fixture replaced inputs before sending a dedicated rejection resync. Fixed without weakening conservation checks.
- `f3c3bf2b424143d18d6927fd8b7fada1`: stacked-left test failed because native input insertion clamped a normal bow stack to one. The fixture now explicitly permits a stack of two; production rejects that actual input.

## Remaining integration and human gates

Checkpoint `plan` is valid; changed-area selection includes all45 shared cases. No complete shared suite receipt or automated task acceptance is claimed here. Under [owner dispatch](https://github.com/Kav-K/OnlyDragons/issues/69#issuecomment-5565338834), the lead owns the final combined GH68/GH69 cohort, original replay, new-tier/anvil interaction, task checkpoints, latest CI and review before serial acceptance. The worker leaves T02c active/In review and every milestone/human gate unchanged.

Authenticated-client compatibility, Windows smoke, tooltip/GUI appearance and interaction feel remain unrun. Use the [book/anvil rehearsal](../../../dev/enchant-books-play.md), linked to GH68’s shared quickstart. Rewards and armor effects remain disabled.
