# T08b frozen ranking: focused worker evidence

[Draft PR #62](https://github.com/Kav-K/OnlyDragons/pull/62) / GH-40 is In review; focused verification is not final task/milestone acceptance.
Runtime/source revision **`bdde815229b6a9b5f90800b8c386b59a4e9ebdf5`** includes
current main **`88d27c5c06705f72d576939e3ea012b134260636`** by ordinary fetch/merge
(already up to date). Both final runs began and ended with unchanged clean inputs.
Later context/PR-link commits are documentation-only. No pins or combat arithmetic changed.

## Final focused results — 6 September 2026

JDK 25.0.4.1, Paper 26.2/build 121 (`26.2-121-a2a42c5`), protocol 776.
The runner invoked the wrapper builds; both reports verify **231 production,
6 companion and 28 client tests**, zero failures/errors/skips. The production
suite includes the real binary-fraction lethal case where HP reaches zero while
stored credit and its earlier strict-increase stamp remain unchanged.

| Scenario | Run ID | Result |
| --- | --- | --- |
| `dragon-ranking` | `0053e7feda804a4381c2a756cfdcf80e` | 24/24 Paper assertions, 10 required received-message matchers, complete 17-action two-actor plan; pass |
| `dragon-combat` | `a8b6204ec1d2483181cf4671c1721fc1` | 49/49 retained Paper assertions and connected command output; pass |

Reproduce each with `python3 scripts/agent-tests/paper_test.py --scenario NAME
--test-player protocol-actions-v1 --scenario-timeout 180` (join the displayed
command onto one line). Use only the provisioned accepted EULA, disposable
loopback profiles, shared lease and memory gate. Final guest/host availability
was 7665/5493 MiB and 7710/5528 MiB against a 2816 MiB reservation. Paper and client
exit codes were all 0, clean and unforced. Both server logs have no ERROR/Exception
matches. The first Paper process exited before the second began; post-run socket
checks found ports 36053 and 55847 closed. Cleanup assertions returned owned
resources/listeners/entities/tasks/chunk tickets to baseline.

The new fixture sends real bow-use/release packets. Server setup supplies gear,
bonuses and aim positions; the resulting real owned arrows exercise production
physical claims and accounting. Its independently matched native probe requires
positive initial damage -> zero settled/final damage on the accepted projectile,
actor and dragon UUID alongside the exact domain/native HP oracle.

First fight: Alpha credits 600/removes 600 HP, reconnects under the same UUID;
Beta credits 600/removes the remaining 400 HP. Alpha ranks #1 by the earlier
strict-increase stamp, Beta #2, with the exact result/Selection retained. The
second fight has Beta 100 credit/HP and Alpha 1200 credit/900 HP, including a
proc-only lethal completion. Later cancelled/revived native death, administrative
follow-up death and reset publish no board. No reward is granted. The retained
dragon-combat fixture additionally covers native animation/removal, delayed reward
suppression with an unmanaged positive control, stale subscription handles,
consumer isolation, spawn failures and reset/removal cleanup.

Raw `player.json` was separately machine-compared against the exact expected
ordered board lines for each actor: first 600/600, then 1200/100, with the correct
personal #1/#2 line. Each actor receives **exactly eight board lines**: two headers,
four ranked rows and two personal rows. Alpha's first session gets no board;
both qualifying completions arrive after reconnect. There are no extra boards
for diagnostic generations. Names label UUIDs and do not enter ordering.
Direct replay/reentry/failure/stale tests are pure adapter tests in the presenter
package; live presenter mutation is inaccessible to production consumers.
The late protocol step proves a post-death bow release cannot change the frozen
board; it does not claim a witnessed late collision. Existing domain and retained
combat tests cover rejected post-death impacts separately.

## Artifact and raw-report identity

Both final runs staged the same binaries; their on-disk plugin hashes were
independently recomputed after cleanup:

- Production: `088335e56089aa4057c1b0f2daf5448930cc9549f94fef251b19d9adb2f57e2b`
- Companion: `427805e4f1c17eb961bd7a7eb215d63408814bcfea9d66208c3f3921c54246e8`
- Client: `16e2dbd6b7fa987cb91193d6d41d57741e1baac97aa02df5e041ffc94cdc501f`

| Raw report | SHA256 |
| --- | --- |
| ranking `result.json` | `ae3c46ea9517d3739b8199ba9759eecdcfd2d9e6d085389b56754b6042841399` |
| ranking `scenario.json` | `5e66ad60e9a67055f9669ce2ce0f9dfe14c21e9a9da5020d245b6d7ef0f58230` |
| ranking `player.json` | `4e2b5b889045a96315dfae5402cb21ae251039e71dde6f7d1723737c6ff56a8e` |
| combat `result.json` | `5000afe01d235a674e25fc51b8327a73593089480666fa88bebfa5a95a7263e9` |
| combat `scenario.json` | `45b1cf54f33102e33921164a1d7af66588ff8be88a884598c9b2f78bb682eeb6` |
| combat `player.json` | `9a279bd5cb16d29117dd3f4bbb7fb9116652296d35dae638a38c4b0b180a6424` |

Raw reports/worlds remain ignored under `build/reports/agent-paper/RUN_ID` and
`run/agent-tests/RUN_ID` in the GH-40 checkout. They are retained for lead replay,
not committed or relabeled as hosted cohort evidence.

## Scope, prior iterations and remaining gates

`checkpoint.py plan --project .` passes at start and after main integration.
The changed-area suite plan retains all 32 baseline cases plus `dragon-ranking`
(33 total), with explicit ranking coverage/acceptance bindings and no removed
gates. Per the owner dispatch, the worker ran only the new ranking scenario and
the directly affected dragon-combat regression. **The complete hosted suite
receipt, independent raw/source replay, automated acceptance checkpoint and
current CI remain lead-owned and pending.** No local full-suite receipt is claimed.
Human Windows Build/Play/smoke, authenticated multiplayer, chat readability and
visual/feel checks remain unrun; no performance or M1–M5 acceptance is claimed.
T08c is the next dependent consumer after lead acceptance/integration; real rewards
remain disabled. No researched upstream mechanic changed, so document 01 is unchanged.

Earlier attempts are preserved as iteration evidence only:

- `1b758b43b3704c6a8d4bf0a7282558a7`: preflight rejected the missing empty `targets`
  field; no Paper/client launched. Registration was corrected in `8227d44`.
- `5fdf9d9fd8284fbe935bfcdee5d55b8c`: companion build failed when the private-API
  review fix changed source after the production build. No Paper/client launched.
- `db96c5d778e04029a0afca943ab4537e` and `8dfecd741dda4d64aeddd671fdbefcb1`:
  ranking reported 24 passing assertions and clean JVM shutdown, but source
  refinements arrived during these runs. Their initial clean-worktree headers
  do not establish frozen final inputs; neither is final acceptance evidence.
  The final unchanged `bdde815` runs above supersede them.
