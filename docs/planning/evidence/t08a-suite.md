# T08a managed test-dragon acceptance

[PR #60](https://github.com/Kav-K/OnlyDragons/pull/60) merged on 2026-09-06
at `889a3db3ca7d43ebdd98bae262a61cccf20cca3a`. [Separate lead acceptance](https://github.com/Kav-K/OnlyDragons/pull/60#issuecomment-5561197301)
records independent raw/source review, strict replay, actual-main task checkpoint
and [current-head CI](https://github.com/Kav-K/OnlyDragons/actions/runs/34049734429).
Exactly `managed-dragon-development` completes; no milestone advances.

## Original source and evidence

[Hosted run 34049768254, attempt 1](https://github.com/Kav-K/OnlyDragons/actions/runs/34049768254)
tested `854287c4017f284b9263d54affd57fca02c033df` against `706ab412e62645da2357e73cadfb2c020fec64fb`.
Implementation revision `abb906c24307fa25ec2448b649c80392fba90422` and reviewed head
`854287c4017f284b9263d54affd57fca02c033df` remain distinct from the actual merge. Documentation
descendants preserve the 258 runtime input files/modes and the source/tree
hashes below; they do not relabel or recreate the original reports.
[Artifact 9994510091](https://github.com/Kav-K/OnlyDragons/actions/runs/34049768254/artifacts/9994510091)
retains the raw reports, staged artifacts, logs and independently captured JUnit XML.

| Identity | Exact value |
| --- | --- |
| Suite | `61a6e1ac737c42278823f2a7dccb86c1` |
| Receipt SHA256 | `28ce2c3f041bea8fa2aec0075108cbfbbc16b487d754a6c1553decdf7f105d47` |
| Source-input SHA256 | `c38c308f98c3b02e0678687ee6fb157d6d95511e52e2fa41f48c07db49ec622a` |
| Git-input-tree SHA256 | `b5a34f279adeb294994f677b3a5fcf38e5fb2d596b630a8681f070a67eb1943b` |
| ZIP SHA256 | `e39b81b04c86998f7c3dcc14331f6d75a269b1aa57179ab2bda485f47055739f` |
| Manifest SHA256 | `50a06c50139442b459ee7bb3b74162804a7884823bc2fb25aaf123e42806438a` |
| Archive SHA256 | `34d5702e0c339426e3f272a4da9938a4c86c1b64eb22af1f20dfa532b763e264` |
| Production JAR SHA256 | `4f9fc4e7ff1c1d83a0ac730a06bd8c6900a35f82406525292c25536704db73a4` |
| Companion JAR SHA256 | `a6a740d520ace4cd14a6b1f1ad30507f8de5002adb3a7d78cc6d8b22a43fb6e9` |
| Client JAR SHA256 | `16e2dbd6b7fa987cb91193d6d41d57741e1baac97aa02df5e041ffc94cdc501f` |

## Verified outcomes and limits

All 32 cases met their declared outcomes: 23 positives and
9 intended controls, 37 Paper boots, 1062 assertion rows
(1052 passing and 10 intended failing rows), 1211.733 seconds.
Relevant builds record 221 production / 6 companion /
28 applicable client tests, with zero failures/errors/skips; 826 raw XML files.

| Feature case | Original run ID | Assertion rows |
| --- | --- | --- |
| `dragon-combat` | `a7941736cc954c3a90a86549e9b632b9` | 49 |
| `dragon-restart-fresh` | `ac7a6c248f0546f1b2bd50a1aad828c6` | 30 |
| `dragon-restart-legacy` | `df36b025781844698a4c0e5759ede1b3` | 32 |
| `dragon-restart-animation` | `b7aa32e7342e47198b49434ae57e03af` | 31 |

Independent raw review found no blocker. Dragon combat has 49 passing assertions, a real 50-step actor (17 commands, 16 uses, 16 releases, disconnect), a positive native projectile event reduced from 2.5 to 0 damage while production projection changes native HP 200 to 180 for 100 domain damage, ten accepted 100-damage hits totaling 1000 HP/credit, and a proc lethal totaling 1000 HP/1200 credit. Actual received output distinguishes frozen completion during native animation from later removal. Subscription, stale-handle, cancellation/revival, later administrative death, nonordinary removal, reset and resource cleanup assertions pass. Managed rewards remain suppressed with an effective unrelated XP positive control. Fresh, legacy and animation restarts have 30, 32 and 31 assertions: the same profile, world UUID, actor UUID, saved-config hash and selected definition survive both boots; prior native UUIDs are absent after loading their chunks; legacy settings remain; animation shutdown occurs at tick 2. No ritual, ranking, payout or human-play acceptance is inferred.

All 37 Paper processes exited 0; client exits were 19 at 0 and
4 intended-control exits at 1. All 37 original Paper boots exit 0, clean and unforced. All 23 clients stop cleanly and unforced: 19 exit 0 and four expected control clients exit 1. Recorded Paper lifetimes do not overlap; each new two-boot case holds one lease through both sequential boots. Available Linux memory is at least 14705 MiB against required 2560/2816 MiB. All original server logs contain clean shutdown and no ERROR/Exception matches. The nine negative cases retain their declared causes (ten intended failed assertion rows); baseline positives and controls remain present. Independently rehashed raw reports/logs, 826 JUnit XML, staged JARs and 258 source files agree with the original receipt; all three JUnit categories have zero failures/errors/skips. Aggregate source/tree identity and checkpoint validation are the lead's separately completed strict replay; CI and merge metadata remain lead-owned.
These are original report/log observations, not a later live probe of hosted ports.

The [focused archive](t08a-focused-paper.md) preserves earlier failed/dirty checks,
the liveness/removal correction, bounded final rerun and original hashes.
[The operator procedure](../../../dev/dragon-play.md) uses the production plugin
alone: explicit setup, ordinary kit/bow, native hits, status/last/result and scoped reset.
No new human Windows Build/Play, authentication, chat/aiming/visual or performance
result is claimed. Direct spawning does not accept countdown/prefire or M3.

T08b/#40 is lead-assigned and awaiting its label, with empty completed requirements
and evidence. It consumes the immutable result/Selection and bounded completion
subscription; it must prove unique credited-damage ranking and received output itself.
Reuse mature fixtures and add only necessary feature cases. M0 retains its prior
acceptance; M1–M5, ranking, loot simulation, ritual and real rewards remain gated.
