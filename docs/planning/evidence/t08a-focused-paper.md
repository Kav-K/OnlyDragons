# T08a focused validation — GH-39

Final runtime: `abb906c24307fa25ec2448b649c80392fba90422`, clean, with main
`706ab412e62645da2357e73cadfb2c020fec64fb` fetched and normally merged (already
current). Later documentation commits do not change the runtime artifact.

This is focused worker evidence, **not a complete-suite receipt or T08a acceptance**.
[Lead delivery decision](https://github.com/Kav-K/OnlyDragons/issues/39#issuecomment-5560936209)
assigns the full current-head hosted cohort and final checkpoint to the lead.
The final delta makes subscription retirement idempotent, including removal
observed through the public removal-reason API. Combat was rerun on that head;
the other checks retain their actual earlier revision, not relabeled evidence.

## Checks on 6 September 2026

All runs used `python3 scripts/agent-tests/paper_test.py --scenario SCENARIO
--test-player protocol-actions-v1 --scenario-timeout 180`, with the accepted
EULA, shared lease/memory gate and disposable loopback profiles. Paper was
26.2 build 121 / a2a42c5, JDK 25.0.4.1. Each built the wrapper artifact:
221 production, 6 companion and 28 protocol-client tests, no failures/errors/skips.
The 270 Python harness tests passed before the subsequent Java-only fixes;
the Python runner/test inputs are unchanged. Structural checkpoint plan passes.

| Scenario | Clean revision | Run ID | Assertions / outcome |
| --- | --- | --- | --- |
| `dragon-combat (final)` | `abb906c` | `18e03aa0414743e682ae7830f08d85c4` | 49: all passed |
| `dragon-combat` | `c2592eb` | `af512813fd0d42bbb8611fc5060d6a82` | 49: all passed |
| `dragon-restart-fresh` | `c2592eb` | `efa8fae58d38448a8e19838e4d8c5ea2` | 30: all passed |
| `dragon-restart-legacy` | `c2592eb` | `449d5c7d3d994a03a4a4fcf1fbcaa7c6` | 32: all passed |
| `dragon-restart-animation` | `c2592eb` | `4165e3da3d714331a9aecb4816b6674e` | 31: all passed |
| `practice-combat` | `c2592eb` | `5710a1cbbc854787811586bdef6c2bbe` | 85: all passed |
| `practice-lifecycle` | `c2592eb` | `7733e70b01014eeea08f621d2f3c9ddc` | 25: all passed |
| `owned-firing` | `c2592eb` | `a7529a229a6c4eb9aab528d5313b9673` | 115: all passed |
| `same-profile-restart` | `c2592eb` | `58aec12ef2ab41e8863e34e6761e19c6` | 35: all passed |
| `same-profile-restart-abort` | `c2592eb` | `22562079a6a84bb8a5df61e7707e2735` | 35: 34 passed; exactly one intended abort assertion |

All owned Paper JVMs exited 0 without forcing. All client processes were reaped
without forcing; positive clients exited 0. The intentional second-boot abort
client exited 1 as expected. Its only failing assertion was `scenario_exception`
with `java.lang.IllegalStateException: Companion disabled before scenario completion`.
The existing strict `verify_continuity` and `verify_scenario` helpers replayed both
phases successfully under their declared positive/cleanup-abort expectations.
This does not construct or imply a full-suite receipt.

## Feature observations

The 49-assertion combat case uses actual connected commands and player-owned
native bow arrows, including cancellation, positive native-damage suppression,
same-tick lethal ordering, one frozen result, late-arrow retirement, proc lethal
HP/credit separation, one notification, stale/equal subscription handles,
read-only reentry rejection, isolated callback failure, repeated failed spawn/reset,
cancelled/revived death and a later administrative death, removal during arrow
retirement, retained tickets during animation, and actual UUID disappearance.
Managed drops/XP remain zero while the unmanaged native-dragon XP control fires.
The unrelated cow retains its captured native health. All subscription/resource
cleanup assertions pass; no ranking or actual reward grant is implemented.

Fresh and legacy restarts query the production arena before any second-boot setup,
compare the full recorded immutable selection, load recorded old native chunks,
verify the old UUID absent, then use ordinary spawn/reset commands. Initial,
saved and restarted configuration bytes/hashes are retained in each report's
`config-initial.yml` and `phase-N/config-before.yml` / `config-after.yml`.
Legacy greeting and extra settings survive. The animation case leaves production
ownership/ticket demand active at fixture completion; server stop begins 212 ms
later, and boot two proves old UUID absence. Fixture cleanup does not reset the
first production encounter. Permission denial changes neither config nor memory.

The Paper directory fixture demonstrates an observable failed **read** and
restoration of its own saved bytes. Actual atomic-replacement failure is separately
injected by a production-boundary unit test while the old file remains readable;
it verifies original bytes, prior in-memory arena and temporary-file cleanup.

## Artifact and report identities

Final production SHA-256: `4f9fc4e7ff1c1d83a0ac730a06bd8c6900a35f82406525292c25536704db73a4`.
Final companion SHA-256: `a6a740d520ace4cd14a6b1f1ad30507f8de5002adb3a7d78cc6d8b22a43fb6e9`.
Earlier c2592eb production SHA-256:
`044f9b18b7ef73bf08bd82feb2983783e2da5b88ad1480e4b1cf097f45b1d30c`;
companion SHA-256:
`a6a740d520ace4cd14a6b1f1ad30507f8de5002adb3a7d78cc6d8b22a43fb6e9`.

Original reports remain under `build/reports/agent-paper/RUN_ID/`; generated
reports, profiles, worlds and logs are excluded from commits. Parent result hashes:

| Run ID | result.json SHA-256 |
| --- | --- |
| `18e03aa0414743e682ae7830f08d85c4` | `94e31afd208653b8807eab37e128ea201e94775abb42105c635b9bd03edeb9b2` |
| `af512813fd0d42bbb8611fc5060d6a82` | `49ea37fb5d3eaf822cd6f8e45cca31e2e56853006eea9d9d04d6699d442cb884` |
| `efa8fae58d38448a8e19838e4d8c5ea2` | `d0659fad0a66baa24d9073afea48b31267e4d7ca706c293e3390113947ecf408` |
| `449d5c7d3d994a03a4a4fcf1fbcaa7c6` | `29cf1c41533bd4a85a5c8d6be2ea026eecb81919855f289d90fb98c94d07c4f6` |
| `4165e3da3d714331a9aecb4816b6674e` | `ea4653a8b370b12da325873fcc0504ec3fa1f82d67ebbc04f1205161389adb1d` |
| `5710a1cbbc854787811586bdef6c2bbe` | `2f6511eda41f77788542893c291eed25ac95c0f66151f07f13e4e40bfbdba9d3` |
| `7733e70b01014eeea08f621d2f3c9ddc` | `2bde39dbf829f1af70d82e925aa85ae66475766dc63de567e6e247d323ec5d06` |
| `a7529a229a6c4eb9aab528d5313b9673` | `d8769e1e7dbbfd299137efa873ab56d403b975437a4809ba77cf53aa009feacd` |
| `58aec12ef2ab41e8863e34e6761e19c6` | `d2c00b21a8154318475e9ab348b0a53e620b36a72bd21b5a866585b69ff3b8b7` |
| `22562079a6a84bb8a5df61e7707e2735` | `045d9a8eb0b827919cec75f7b71bee6dd79312803b3ad268b676778e019ba7a1` |

## Earlier iterations and remaining gates

Dirty fresh restart `c557e255c7114219ab6cf4fe1858b5f7` passed 28 assertions.
Dirty AI-disabled combat `c6520c55fe894404956ff589c11261cd` failed its first
physical claim because native parts remained near origin; cleanup passed.
Dirty native-HOVER `d93caa45689d49ff9d121e9d2c093ab3` passed its then-36 assertions.
Clean 713f859 run `e84e6b1c0d9a4cb6a37ab69b49e669ff` failed six of 49 assertions:
notification predicates conflated liveness with removal, and the cow fixture
assumed 20 HP. Its `!isValid()` reset assertions are not removal proof.
The corrected 1ac565f combat `0db70d6dfba644238cfabcbbcf5ae2d1` (49), fresh
`c289460c7012405695f8105cd50f451b` (30) and legacy
`9151cb7938d849a9aa825e7aa71a7247` (32) passed before the later public-removal
fallback and exact provenance assertions. These remain historical results.

The changed-area plan selects all 32 registered cases because shared boundaries
changed. Full current-input hosted suite, its receipt and the T08a acceptance
checkpoint remain lead-owned and pending. The attempted task acceptance command
without a receipt correctly rejects with `Acceptance requires --receipt and --base`;
static plan success is not gameplay acceptance. Independent final-head review/CI,
Windows build/smoke, authenticated Cursor Play, chat readability, native visuals,
aiming feel and performance are not established by these focused runs.
[Operator procedure](../../../dev/dragon-play.md). T10 physical prefire/M3 and T11
remain gated; no altar, progression, later roster, ranking/preview or rewards
acceptance follows from this direct-spawn development loop.
