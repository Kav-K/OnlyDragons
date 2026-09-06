# T09e focused restart evidence

Clean runtime and validation inputs: `dabf5bfcab8375fa623d72d2a086837f972457bd`; source input SHA-256 `8192026fb930b012a2151fc5024c6c95bf3da6c36dada1a828967cda7a3d92a8`.
Current main `3c35a85bfa5d6bf6788af7c26d59891cfb852175` and lead-reviewed PR #51 `d8dffde3080b253a7d061a4de392d95ae050173a` are ancestors. Later documentation-only commits do not change these inputs. [Draft PR #50](https://github.com/Kav-K/OnlyDragons/pull/50).

Pinned Minecraft 26.2 / Paper build 121 / JDK 25. Both runs used only the isolated runner, existing accepted EULA, one disposable profile per case, loopback, one lease across two boots and fresh combined server/client memory admission before each boot.

253 Python tests passed with no skips, including full replay failure/provenance controls and no-weakening phase bindings. The structural checkpoint and 23-case plan pass. Linux and Windows CI passed on the clean runtime commit (PR workflow 34030882838). Each actual run built production, companion and client with the wrapper: 163 production tests and 28 client tests, zero failures/errors/skips; the companion has no separate unit-test source.

These are focused strict raw replays through the same `paper_suite.verify_case` entry point used by the full suite. They are not complete suite receipts or task acceptance. The lead started the full 23-case hosted cohort on this exact frozen commit; full receipt/export replay, task checkpoint and independent final acceptance remain lead-owned pending gates. T09e remains In review.

## Focused outcomes

The calibration edits and saves the starter configuration as explicit server setup. Two protocol actors send real permission-allowed/denied reload commands and status commands. Production greeting observations establish the first reload and independently loaded second-boot configuration. It proves generic persisted configuration and fixture cleanup, not managed-dragon recovery.

### same-profile-restart

Parent `4b9b9b8895f74e0b91eeac64cf872856`, port `42781`; runner exit `0`; strict replay accepted `positive` with 35 assertion rows.
Raw report: `build/reports/agent-paper/4b9b9b8895f74e0b91eeac64cf872856/result.json` (SHA-256 `7533942404046a84f41bb0b08b6d5753b9da5dc5639582407d7517efd98bae64`).
Focused replay/JUnit copy: `build/reports/agent-paper-suites/ec24c0aa306d417d9109aea25cff1943/focused.json`.
Same world UUID `99e44920-cfdc-4764-87d3-5c065ae77f91`. Actor names `od_4b9b9b8895_0` and `od_4b9b9b8895_1` retain the parent-derived roster across both plans.
Lease window (epoch ms): `1788694861024..1788694963015`.

| Phase | Nonce | Server PID/start ticks | Client PID/start ticks | Phase window (epoch ms) |
| --- | --- | --- | --- | --- |
| 1 | `4b9b9b889525474c9f6c7d01d8ad2a64` | `485/965657` | `556/969084` | `1788694875953..1788694923678` |
| 2 | `4b9b9b88959c42c8b384055cf882fe2e` | `599/970600` | `671/972761` | `1788694927849..1788694959559` |

Initial/boot-one-before and final restored config SHA-256: `e96b719a41aabb020d39e9b1097f71c8d331153163543e43739f4c878ed5b853`.
Boot-one saved/boot-two-before config SHA-256: `fea8276bd76e6b8066f1909920007dcf56f0ab8e026b2ed7f3c1c961ecc127e6`.
Both phases have clean, unforced server exit 0 and reaped client cleanup. Replay verifies client stop before server stop, phase-one completion before phase-two start, memory admission, unchanged artifact hashes, config snapshot bytes, world identity, command/message journals and all catalog-bound assertions.

### same-profile-restart-abort

Parent `cb768309d4f949b59b79b8f45e0d00be`, port `53215`; runner exit `1`; strict replay accepted `cleanup-abort` with 35 assertion rows.
Raw report: `build/reports/agent-paper/cb768309d4f949b59b79b8f45e0d00be/result.json` (SHA-256 `7afeaaace4ea8e272ed9fc6d385c191827746a3183fd4da983df4288bd733336`).
Focused replay/JUnit copy: `build/reports/agent-paper-suites/1f3ef51f6d1145e0bc7910c218b7f16e/focused.json`.
The only accepted error is `Failed scenario assertions: scenario_exception` in phase two, with connected actors and live owned resources before abort. Phase one passes normally.
Same world UUID `a01d25c5-abe4-4a63-9fdc-e74f543e0256`. Actor names `od_cb768309d4_0` and `od_cb768309d4_1` retain the parent-derived roster across both plans.
Lease window (epoch ms): `1788695078687..1788695176175`.

| Phase | Nonce | Server PID/start ticks | Client PID/start ticks | Phase window (epoch ms) |
| --- | --- | --- | --- | --- |
| 1 | `cb768309d4bc448a9aa205e13d57c2b4` | `498/986405` | `569/989735` | `1788695091793..1788695136034` |
| 2 | `cb768309d4ca49df99ab6741604f7d5a` | `612/991145` | `684/993340` | `1788695140748..1788695172578` |

Initial/boot-one-before and final restored config SHA-256: `e96b719a41aabb020d39e9b1097f71c8d331153163543e43739f4c878ed5b853`.
Boot-one saved/boot-two-before config SHA-256: `126bf59113b4430ac1411203b7feee55e94fe01b68901a7e9d5117d21bd838ab`.
Both phases have clean, unforced server exit 0 and reaped client cleanup. Replay verifies client stop before server stop, phase-one completion before phase-two start, memory admission, unchanged artifact hashes, config snapshot bytes, world identity, command/message journals and all catalog-bound assertions.

## Shared binary and plan identity

Production JAR: `7d4b76920240c012be9422329cd225da58676f07f84d188229f1eb5dc1856c14`.
Companion JAR: `41e277e8c511d4fec98f5e8d6973d7a528846f076f35e27972da99973d84761b`.
Client JAR: `16e2dbd6b7fa987cb91193d6d41d57741e1baac97aa02df5e041ffc94cdc501f`.
Phase 1 plan `restart-phase-1-v1`: `f20984a15ea05b75e5f5440e4b601058e68d49738b04b27a428f552e335462cf`.
Phase 2 plan `restart-phase-2-v1`: `7625f743cdba120938ad29275fd99a08fc9f378e5e3698ee6f2da05876225191`.

Full client-library and bootstrap hashes, process start/stop windows, status probes, memory decisions, plans, phase context, exact configuration snapshots and logs remain in the raw parent/phase reports. No worlds or credentials are committed or admitted to hosted export.

## Remaining gates and historical runs

Windows operator Play/smoke, authenticated multiplayer compatibility and full-client visuals/feel are unrun human gates. No managed-dragon, firing or later milestone acceptance follows. M0/T02b/T05 retain the lead’s separately accepted state; T06 remains active.

Historical iteration runs `986a72a3d931443fa104188500ea635d` and `065ced37098b4b6180d13b9188e0d65c` at `efc9714` passed their focused replay (82 production/28 client tests), but preceded main integration and the evidence containment fix. Positive run `f3471d5831f745b696c9dbbce0ee32b2` at `7998d1b` passed the runner before the Windows synthetic-root test fix and PR51 integration; it is not final acceptance evidence.
