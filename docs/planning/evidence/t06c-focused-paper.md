# T06c focused Paper evidence

Runtime revision: `016bd69d66f27921051ae31d4a4aea6844cb6f02` (clean for every final run).
Main integrated: `e95447ae2d41f249a732edd0193e16dd46b3f0b3`; fetch and ordinary
merge returned already up to date. Later commits only record documentation.
Pinned Paper `26.2-121-a2a42c5`, JDK 25, accepted existing EULA, disposable loopback
profiles, shared lease and memory gate. Expanded-bow waited for memory before
starting; no limit was bypassed.

Each row passed every assertion, production 299 and companion 6 Java tests with
zero failures/errors/skips. Each protocol run also passed 33 client Java tests.
All server exits were unforced, clean, exit 0; all five client exits were also
unforced, clean, successful exit 0. All owned resource cleanup assertions passed.
Reports remain in this GH-70 workspace under
`build/reports/agent-paper/<runId>/{result,scenario}.json` with build/server/player
logs alongside. These are focused raw reports, not a complete suite receipt.

| Scenario | Run ID | Assertions |
| --- | --- | ---: |
| owned-flame | `6fb480cb804747d3b0d5246c26095d9a` | 61 |
| quiver-ammo | `d00b9b3af2e044e1bb067aa08fc0aa1f` | 22 |
| owned-firing | `8358af58397e431c9d99d8eb2f09a730` | 115 |
| expanded-bow | `f686a0210c114fc2bafd7d4843599902` | 49 |
| tempo-ghost | `1cb6a27a31724a028e1fd4c2be128eae` | 31 |
| equipment-stats | `9b27e56d64894817b2c47ab949f51119` | 25 |

Every final run staged the same artifacts:

- Production SHA-256: `7b1681e59f2e3568fc2ddd5e4f46722b8c3eeffd3cb17ecfc65dae71dedb726a`.
- Companion SHA-256: `e1db009a07087b70e6eabd2ee3e763e4db337ca8644b350e9ec211b62a069294`.

Result JSON SHA-256 values:

- `6fb480cb804747d3b0d5246c26095d9a`: `13034ef94c2e2ded308aadf4f748b91aee711ff1186009cdac4e20d40afd3435`.
- `d00b9b3af2e044e1bb067aa08fc0aa1f`: `5316e1c7574d615a6774d9af60331d6deef640128e2f1109b16d15e5db37ecb5`.
- `8358af58397e431c9d99d8eb2f09a730`: `4390fa95d5f426ad5f46229f2b7d847a12370ac76fdfdb8827c723b7b8459e38`.
- `f686a0210c114fc2bafd7d4843599902`: `e6d40e1f98b35edf0459f8bf23d6aacae33f9206f60724d777310ca63a60ff22`.
- `1cb6a27a31724a028e1fd4c2be128eae`: `7868f730299c46c0a54a796a27e61292143c0047fd390c12fe90850139788bf7`.
- `9b27e56d64894817b2c47ab949f51119`: `caf81b66c714a554d3679a99f3c39231bbc3dc3a2429e4c784572b54807f8958`.

## Observable feature proof

`quiver-ammo` runs the deployed `OwnedBowService` class with its existing seeded
random-source constructor and normal listener in an isolated arena. It proves
strict level I/X chance boundaries, survival admission with a real arrow, one
trigger charge for Duplex, retained-primary settlement, draw/shortbow veto
refunds, permission/empty-inventory rejection, native Infinity non-debit
conservation, unrelated player inventory and lifecycle cleanup. Unit tests cover
all ten levels and invalid samples. This seeded class probe is distinguished
from stock-plugin wiring.

`owned-flame` uses the stock plugin's firing/combat services, actual protocol
releases/shortbow inputs and independently observed physical collisions.
Its stock IQ X sample was `0.9694521599943878`, saved=false, with inventory
64 -> 63 and exactly one captured settlement trace. A protocol slot change to
an ordinary no-Flame item kept the live session; the in-flight Flame II arrow
still produced three 6-damage strikes at +20/+40/+60, total HP/credit 118.

Independent arithmetic covers weaker/stronger refresh and preserved cadence;
two owners with 1.5 strongest vulnerability, 1.1 after owner exit, exclusive
1200-tick expiry; full-HP/full-credit fire from capped physical credit (11.8
total); native managed suppression and an unmanaged positive control; veto;
no recursive Ferocity or Tempo extension; lethal fire clipping 6 credit to
5 remaining HP, 106 total credit; and frozen late results. Actual native dragon
death removal completes before the reward assertion, with one native death,
empty event drops/XP and zero source-UUID XP. The existing dragon-combat fixture
retains the separate unmanaged native-XP observer positive control for the lead's
combined cohort.

Quit, arena exit, stale captured session, reset, death and disable are exercised.
Reset/death explicitly await accepted primary AND Duplex physical collisions and
assert live burn/vulnerability before cleanup; disable asserts a live burn.
Death uses public server health setup followed by actual client respawn input,
with the established three-tick packet-observation delay. Protocol actors prove
these inputs and server behavior, not authenticated/full-client appearance.

The four existing regressions preserve prior firing ownership/cancellation,
v3 expanded-enchant rejection/capture, Tempo/Ghost accounting, and all 27
calibration definitions' equipment totals (within 25 scenario assertions).

## Preserved iterations

All iteration runs below used dirty main `e95447ae`; none replaces clean evidence.

| Run | Result and correction |
| --- | --- |
| `2efa9b039cbb4be5b4da9ee1777b8e1b` | Pre-start build failure: new Flame fixture start omitted checked exception declaration; no Paper started. |
| `dafb472d02bd4f9681701efd2adfcdfe` | Quiver PASS 22; clean unforced server/client cleanup. Earlier companion artifact, iteration only. |
| `236e841cce154cf8b57fbf0657b73705` | Flame failed required assertions after client rejected respawn before observing death; clean unforced server/client cleanup. |
| `3eb2185fba3a40c6a4592cccc7072a86` | Flame PASS 53 after packet delay; superseded by stronger live-state/swap/stock-IQ/reward assertions in final run. |

Earlier unit iterations corrected an availability assertion to query exact v3,
MockBukkit unsupported live-arrow APIs, and fixture arrow-slot initialization.
The unsupported live-arrow test was not counted as a pass; actual emission is
covered by Paper. Final 299 tests have zero skips.

## Delivery gates

`checkpoint.py plan --project .` and `paper_suite.py --changed-since origin/main
--plan` pass (42 selected cases). Direct calls to the existing selection function
also prove isolated edits to OwnedBowService, ManagedCombatService,
CombatEncounter, CalibrationLoadouts and ProcCoordinator select both feature
scenarios. The complete cohort receipt and task acceptance checkpoint remain
lead-owned and unrun locally, per the explicit GH-70 dispatch/final-review
instructions; static plans are not acceptance.

[Lead final source/raw review](https://github.com/Kav-K/OnlyDragons/issues/70#issuecomment-5563820672)
clears runtime 016bd69 and both final feature reports, preserving full-cohort
acceptance as a separate gate. Current CI, lead combined cohort/replay/task
checkpoint/merge, Windows smoke, authenticated multiplayer and visual/feel
checks remain pending. No milestone or `quiver-flame-integration` completion
is claimed. Next consumers are T06b/#68 and T02c/#69 after integration.
