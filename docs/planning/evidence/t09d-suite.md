# T09d hosted fixture evidence

On 6 September 2026, reviewed runtime commit
`3047a56b0ed0c634034841b776752e71b55ac857` in [PR #44](https://github.com/Kav-K/OnlyDragons/pull/44)
passed the complete 17-case suite on GitHub-hosted Ubuntu 24.04.
[Manual workflow 34024646724, attempt 1](https://github.com/Kav-K/OnlyDragons/actions/runs/34024646724)
and [current-runtime CI 34024649057](https://github.com/Kav-K/OnlyDragons/actions/runs/34024649057)
passed. The suite ran from 09:29:03.334 to 09:39:44.687 UTC (641.353 seconds).
All five T09d automated requirements are verified; final documentation-head CI
and the actual PR merge remain pending. No merge revision is claimed here.

## Exact replay inputs

| Input | SHA-256 or identity |
| --- | --- |
| Suite | `6965c082edde47d0b5ae646b52983e28` |
| Source input hash | `7838164ed8bb9526c6304ceff5c400ee4e3a1f970bc4c037700d430832b36ee5` |
| Git input-tree hash | `8dd57e9f4506e9beea9b41561ab224bd5c18953e83150b111386b52264daedfb` |
| Receipt | `82282bce5951e2d8b1b7d12b5359668513f981b639ffd80ec2589121b5a08897` |
| Artifact ZIP | `435635efc52e9801e798fde6e9788c51850ec6502e3d2b7e5b43a6bef6cde5db` |
| Hosted manifest | `ebbeacc97d3c6e31e6b5973e6462ad90652ceee320e2ecae745f3ab6a3856d18` |
| Exported evidence tar | `e456ce1b327633f1e8dcc397a097b508d3cf625f46cab287e004fb9cce98fd8c` |
| Production JAR | `afc7374f6240e4866b392239cc8027af07fe13f853de58d4eb9127b37b5fa209` |
| Companion JAR | `5c8ce188d06551d5540467cd63cf0583571fd9dd29b528c90092dfe310f58d29` |
| Protocol-client JAR | `16e2dbd6b7fa987cb91193d6d41d57741e1baac97aa02df5e041ffc94cdc501f` |

The workflow's `OnlyDragons-Paper-34024646724-1` artifact (ID `9986846902`,
147,313,734 bytes) contains 931 unchanged evidence paths, with 210 stored files
and safe internal hardlinks for duplicate bytes. GitHub retention expires
20 September 2026; the lead downloaded and independently restored the artifact
at its original canonical path, `/tmp/onlydragons-paper-ci/34024646724-1/OnlyDragons`.
Its receipt is `build/reports/agent-paper-suites/6965c082edde47d0b5ae646b52983e28/receipt.json`.
Raw reports, copied JUnit XML, staged JARs, action plans, bootstrap bytes and cleanup
were replayed without rewriting receipts. The strict suite validator and
`checkpoint.py acceptance --automated --task T09d` both exited 0 against base
`799c01dd5e9da35cf17bf29ddb671ebc66ab4b3b`; independent source review found no
actionable issue. The checkpoint retained `acceptanceApproved: false`.
The source hash identifies the hosted Linux checkout's actual bytes; a checkout
with different line endings is not interchangeable with that evidence.

## Recorded outcomes

| Case | Expected outcome met | Recorded Paper assertions |
| --- | --- | ---: |
| lifecycle-calibration | Positive | 15 |
| foundation-contracts | Positive | 31 |
| stats-resolution | Positive | 20 |
| item-identity | Positive | 36 |
| combat-accounting | Positive | 32 |
| projectile-feasibility | Positive | 35 |
| protocol-player-calibration | Positive | 26 |
| deliberate-failure | Intended rejection | 16 |
| projectile-cleanup-failure | Intended exception | 6 |
| projectile-cleanup-abort | Intended abort | 6 |
| protocol-player-early-exit | Intended client exit | 15 |
| protocol-player-idle | Intended timeout | 15 |
| equipment-stats | Positive | 24 |
| equipment-player | Positive | 48 |
| protocol-player-soak | Positive, including 40-second online interval | 28 |
| headless-player-primitives | Positive | 43 |
| headless-player-cleanup-abort | Intended abort | 23 |

All 11 positive cases exited 0; all six negative controls exited 1 with their
declared failure and strict cleanup conditions. Negative assertion counts include
the intended failed assertion, where applicable; they are not all-pass totals.
All owned servers exited 0, unforced and clean. Actor cleanup also passed,
including the deliberate client exit/abort cases. Copied JUnit evidence verified
82 production and 28 client tests with zero failures, errors or skips.
The runtime change also passed 238 Linux runner/checkpoint tests and Windows/Linux CI.

The new positive run `73d45b5871b54f61b9d4ea6426680319` used two distinct real
protocol identities across three sessions and 27 declared steps. It proved
received permission/grant/stats messages, four real inventory-click transactions
with PDC/slot/cursor checks, movement/look, hands, drop, block interaction, native
damage cancellation/health cohorts, drawn-bow damage, death/respawn and reconnect.
The abort run `8d6bd76631504dbda4a5c34187a76234` proved both real commands before
abort, exact interrupted client reports, restored blocks/permissions and owned
entity/task/listener/chunk-ticket cleanup.

Paper 26.2 build 121, protocol 776 and Temurin 25.0.4.1+1-LTS remained pinned.
Every profile used verified Paper/Mojang bootstrap inputs; missing/corrupt inputs
have fail-closed regression coverage. Hosted provisioning downloads those exact
public artifacts once; this does not claim that the whole job ran without network.
The initial hosted attempt stopped at setup-java's unsupported four-part version
parser. The fix reads the existing exact installer URL/SHA/build, safely extracts
and checks the JDK identity before any suite launch; it changes no version pin.

## Remaining boundaries

Generic native damage and player calibration do not implement or accept T06's
production multiplayer firing, T08/M3 HP/ghost-score attribution, T04's remaining
dragon findings, P01–P14, a managed encounter, a ritual or real rewards.
Authenticated clients, human visuals/input feel, Windows operator Play and
performance gates retain their separate unrun status. M0–M5 remain unaccepted.
Downstream workers use T09d only after the reviewed fixture PR actually merges.
