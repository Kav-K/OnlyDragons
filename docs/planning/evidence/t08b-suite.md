# T08b frozen damage ranking acceptance

[PR #62](https://github.com/Kav-K/OnlyDragons/pull/62) merged on 2026-09-06
at `114916905c68f5574b2ee437b8db5bd2a187e390`. [Separate lead acceptance](https://github.com/Kav-K/OnlyDragons/pull/62#issuecomment-5561626002)
records independent raw/source review, strict replay, the actual-main task checkpoint
and [current-head Windows/Linux CI](https://github.com/Kav-K/OnlyDragons/actions/runs/34053363879).
Exactly `frozen-damage-ranking` completes; no other requirement or milestone advances.

## Original source and evidence

[Hosted run 34053344463, attempt 1](https://github.com/Kav-K/OnlyDragons/actions/runs/34053344463)
tested `e1724561e67c5e31d05497801cbf296f84876f91` against actual main
`88d27c5c06705f72d576939e3ea012b134260636`. Implementation revision
`bdde815229b6a9b5f90800b8c386b59a4e9ebdf5`, hosted revision, reviewed documentation head
`aac3fe2445b99ed42ba177841e580e4932c94849` and actual merge remain distinct.
The documentation descendants preserve the 265 runtime input files/modes and source/tree
hashes below; this note does not recreate or relabel the original reports.
[Artifact 9995648190](https://github.com/Kav-K/OnlyDragons/actions/runs/34053344463/artifacts/9995648190)
retains the raw reports, staged artifacts, logs and independently captured JUnit XML.

| Identity | Exact value |
| --- | --- |
| Suite | `1396dd6f639e4a7d8761d680b145a2a5` |
| Receipt SHA256 | `59a81bce2fe1c27d895fb9e32aa95cbf5344d3957941fab2b47d3141bb71107a` |
| Source-input SHA256 | `ed48c0faa3d065ea0e5c9e0f8422a1fc0fb70820cafa23f390f30caf12fa95c2` |
| Git-input-tree SHA256 | `710fb2e57c86f6b032bc7392ffd7b296caaea592132b74726e26524f92ad3292` |
| ZIP SHA256 | `e56ae696cb1735275943aaadda8d316efba4d96a275e6aefbc71d874bd325a18` |
| Manifest SHA256 | `1ecab1e9b39e917d844e8a83d413d31c97b594cc1df6999911166e32f715cfc5` |
| Archive SHA256 | `f330b30011ac3efd4cf52180a65dfde3efc35db197c9af9a99a558896e423661` |
| Production JAR SHA256 | `088335e56089aa4057c1b0f2daf5448930cc9549f94fef251b19d9adb2f57e2b` |
| Companion JAR SHA256 | `427805e4f1c17eb961bd7a7eb215d63408814bcfea9d66208c3f3921c54246e8` |
| Client JAR SHA256 | `16e2dbd6b7fa987cb91193d6d41d57741e1baac97aa02df5e041ffc94cdc501f` |

## Verified outcomes and limits

All 33 cases met their declared outcomes: 24 positives and nine intended controls,
38 Paper boots, 1,086 assertion rows (1,076 passing and ten intended failing rows),
1,541.855 seconds. Relevant builds record 231 production / six companion /
28 applicable client tests with zero failures/errors/skips; 920 raw JUnit XML files.
Independent ranking, combat, all restart pairs, source and raw evidence review passed.

| Feature case | Original run ID | Assertion rows |
| --- | --- | --- |
| `dragon-combat` | `04da6309842d44d399c9f063ec3be633` | 49 |
| `dragon-restart-fresh` | `d3cddc9d844f48f29d0808f0ff24d63d` | 30 |
| `dragon-restart-legacy` | `497746ef8d64495ca2dd64e66a13a664` | 32 |
| `dragon-restart-animation` | `544293a9a41f4ce98197206a252a2d4b` | 31 |
| `dragon-ranking` | `b198f07d4d564fea8fa1f74c063e5bfc` | 24 |

| Restart case | First boot | Second boot |
| --- | --- | --- |
| `dragon-restart-fresh` | `d3cddc9d84ed4d0cb38864d800a20b07` | `d3cddc9d847a4d8badb5d7ac4fb60591` |
| `dragon-restart-legacy` | `497746ef8d2143b9974d03493786fc74` | `497746ef8d56437098cb0f1703a435cd` |
| `dragon-restart-animation` | `544293a9a48a4fc181786f311195166b` | `544293a9a46c4540bc5f7cbf58d9befa` |

The ranking case verifies actual distinct-player production damage and received
frozen top-ten/personal-placement output, including UUID-preserving reconnect,
ghost/overkill credit separate from HP, a proc-only lethal and generation isolation.
The late protocol step establishes a post-death release with an unchanged board;
it does not claim a witnessed late collision. Separate deterministic tests preserve
tie/precision/zero/late-impact and once-only delivery boundaries. Retained dragon
combat and restart cases preserve native-death/removal, delayed reward suppression,
reset, persisted arena and prior-generation cleanup coverage. No real rewards are enabled.

All 38 Paper processes exited zero. Twenty clients exited zero and four specified
control clients exited one; all were clean and unforced. Independent raw review and
strict replay verified the exact intended negative causes, owned cleanup, logs,
source/JAR identities and XML evidence. These are original process/report observations,
not a later live probe of hosted ports. The [focused archive](t08b-focused-paper.md)
preserves the worker's final two runs and earlier rejected/dirty iterations separately.

## Separate Windows operator checkpoint

The lead verified Windows `Dev.ps1 Build` on merged main `114916905c68f5574b2ee437b8db5bd2a187e390`:
231 tests, zero failures/errors/skips. Two sequential same-profile smoke boots passed
14 and three command checks, respectively, plus two enabled-plugin checks per boot:
`8593864374534cf9b1c8a7fda6502e78` and `46067b70917842b68e90b468afbc6564`.
The saved arena matched before any second setup; spawn/reset passed and both boots
stopped cleanly. Known Windows OSHI/Perflib system-inventory warnings and a startup
NullPointerException were observed; no plugin error occurred and required checks passed.

Normal `mcdev.cmd play` then completed build/deploy and emitted `MCDEV_READY` for
Minecraft 26.2 / Paper build 121 / JDK 25 at `127.0.0.1:25565`, run
`f21b58fb5a3f46b2b55d9d7e78e279a4`, state ready with no state error. OnlyDragons was enabled;
the separately built Windows JAR SHA256 is
`eb5f03e7ab94d42f1b9f6105177ec50a2ab7a2f9c6baa01e4af2928c5baeec21`.
That local build is separate from the hosted JAR above. The human profile/world and
`online-mode=true` were preserved, with loopback binding and no RCON/query exposure.
This records observed readiness, not a promise that the server remains running.

[The operator procedure](../../../dev/dragon-play.md) uses the production plugin alone.
Authenticated connection, full-client appearance, aiming, animation and chat readability
remain human observations; startup and headless output do not accept them. M0 retains
its prior acceptance; M1–M5, countdown/prefire, ritual and real rewards remain gated.
T08c/#41 remains planned and undispatched pending the first human checkpoint and
lead assignment, with empty completed requirements/evidence. No loot eligibility,
probabilities or production items are selected by accepting this leaderboard.
