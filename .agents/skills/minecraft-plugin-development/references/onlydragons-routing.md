# OnlyDragons mechanic and evidence routing

Use this reference only when the checkout contains the three OnlyDragons planning
documents under `docs/planning/`. Paths below are relative to the repository root.
Other projects and generated starters use their own brief. The planning documents
describe intended behavior; inspect code and document 03's delivery ledger for
what exists. A section or task name does not assign that work.

Read the three documents under the shared-context contract in `AGENTS.md`, then
return to these sections as the assigned implementation or review requires:

| Concern | Design and evidence locations |
| --- | --- |
| Domain/Paper boundaries and test-harness isolation | `docs/planning/02-foundation-plan.md` section 2; `docs/planning/03-agent-tasks-and-validation.md` T00 and T09 |
| Stats, PDC item schemas, weapon/enchant identity | Document 02 sections 3–5; document 03 T01/T02 and deterministic acceptance matrix |
| Physical collision authority, multipart dragons, native damage | Document 02 section 6; document 03 T04 and real-Paper scenarios P01–P04/P13 |
| Damage versus contribution, proc ancestry, snapshot/expiry timing | Document 02 sections 5–8; document 03 T03/T05 and deterministic acceptance matrix |
| Real arrow identity, firing, homing, chunk/lifecycle ownership | Document 02 section 9; document 03 T06/T07 and scenarios P05–P11 |
| Prefire, reproducible traces, human input, performance | Document 02 sections 10/12; document 03 T09/T10 and sections 5–7 |
| Test-dragon catalogs and inert versioned table/item bindings | Document 02 T02b contract; document 03 T02b; `src/main/resources/encounters/README.md`; no live phase or reward acceptance |
| Later summon transactions and progression | Document 02 section 11; document 03 T11/T12 and their dependencies |

`docs/planning/01-research.md` carries source dates, confidence, and unresolved
upstream behavior. Keep research, adopted design, current implementation, and
actual evidence distinct. Update the affected documents through document 03's
shared-context update protocol rather than maintaining a second mechanics spec
inside this skill. `versions.properties` remains the current compatibility pin.
