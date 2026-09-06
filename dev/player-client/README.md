# Isolated protocol player

This standalone Java process supplies real Minecraft client packets to the
companion's `protocol-player-calibration` scenario. Run it through
`scripts/agent-tests/paper_test.py --scenario protocol-player-calibration --test-player protocol-calibration`;
see [the runner guide](../agent-paper-tests.md) for operator environment paths.
It cannot choose a remote host, authenticate an account, or follow server
transfer requests. The runner creates a unique 16-character player name and
whitelists its offline UUID in a new loopback-only disposable profile.

The client accepts the run's companion action markers, acknowledges player
loading, teleports and chunk batches, and sends selected-slot, bow-use and
release packets before disconnecting. Only the companion's actual Paper
events establish the input results. `player.json` separately records the
protocol sequence, disconnect, artifact identity and timestamps. The runner
requires both reports, successful client exit and clean owned-process cleanup.

## Dependency provenance

The source of truth is the `testPlayerProtocolLib`, `testPlayerProtocolLibSha256`
and `testPlayerProtocolVersion` keys in `versions.properties`. Both Gradle and
the runner read these pins; the client JAR embeds the same target for its codec
check and report. Existing production/server version keys are unchanged.

- Exact coordinate: `org.geysermc.mcprotocollib:protocol:26.2-20260824.124638-17`.
- Publication: [OpenCollab timestamped artifact](https://repo.opencollab.dev/maven-snapshots/org/geysermc/mcprotocollib/protocol/26.2-SNAPSHOT/protocol-26.2-20260824.124638-17.jar).
- Artifact SHA256: `07ec18ba92c8b4041286eeff2470e08257fd1f383881515cba4a0a9bf6fa98c1`,
  checked against the repository's [SHA256 sidecar](https://repo.opencollab.dev/maven-snapshots/org/geysermc/mcprotocollib/protocol/26.2-SNAPSHOT/protocol-26.2-20260824.124638-17.jar.sha256)
  and enforced again by the runner before staging.
- Reviewed source: [MCProtocolLib 26.2 codec at d55144f5](https://github.com/GeyserMC/MCProtocolLib/blob/d55144f5ccbe089d588b54c04d5c7f3baa853288/protocol/src/main/java/org/geysermc/mcprotocollib/protocol/codec/MinecraftCodec.java),
  declaring Minecraft 26.2 / protocol 776. Its [public client example](https://github.com/GeyserMC/MCProtocolLib/blob/d55144f5ccbe089d588b54c04d5c7f3baa853288/example/src/main/java/org/geysermc/mcprotocollib/protocol/example/MinecraftProtocolTest.java)
  documents the offline session constructor. The source commit records the
  reviewed API; no claim of reproducible upstream JAR/source equivalence is made.
- MCProtocolLib is [MIT licensed](https://github.com/GeyserMC/MCProtocolLib/blob/d55144f5ccbe089d588b54c04d5c7f3baa853288/LICENSE).

`gradle.lockfile` locks the resolved transitive dependency graph. The protocol
module itself is fixed by the exact timestamp declaration and SHA256: Gradle
normalizes timestamped Maven publications to `26.2-SNAPSHOT` in its lock state,
which conflicts with the exact request on the next build. The single-module
lock exclusion does not exclude its transitives. No floating snapshot is
requested. `gradle/verification-metadata.xml` verifies dependency artifacts and
metadata with SHA256; CI and the runner use strict verification. The client
distribution and every staged dependency hash appear in each runtime result.
Dependencies remain on this separate JVM's classpath and never enter a Paper
plugin JAR. JDK 25 comes from the project's operator installation.

Build without a server using the repository wrapper:

```bash
bash ./gradlew -p dev/player-client build installDist --dependency-verification strict
```

Changing the protocol target requires review of the exact publication, codec,
dependency locks and hashes together, followed by another real Paper calibration.
Do not replace the timestamp with `SNAPSHOT`, `latest`, or unverified master.

The [final calibration evidence and extension boundary](../agent-paper-tests.md#protocol-player-evidence)
record the actual positive, early-exit and timeout runs. Future feature scenarios
require reviewed admission and their own real Paper assertions; this client is
currently bounded to the calibration sequence.

## Declared actions and multiple players

The additive `com.kaveenk.onlydragons.playerclient.ActionPlayer` entry point
leaves the legacy calibration entry point and schema unchanged. Its command line
is `runId port reportPath timeoutSeconds behavior planPath planSha256`.
Only the isolated runner starts it. The host is always `127.0.0.1`, transfers
remain disabled, and no account credentials are accepted. The deadline is at
most 300 seconds. `calibrate`, `early-exit`, and `idle` select ordinary execution
or intentional failure controls.

The selected catalog descriptor declares `testPlayerMode: protocol-actions-v1`
and `playerActionPlan`, a tracked JSON file under `dev/game-tests/player-plans`.
The runner stages and hashes its exact bytes. The client independently verifies
that SHA256 and rejects unknown fields, duplicate JSON keys, malformed types,
nonfinite numbers and data outside the bounds below. No JavaScript, expression,
URL, arbitrary host or packet-class name is interpreted from a plan or chat.

```json
{
  "schemaVersion": 1,
  "planId": "example",
  "targets": ["target"],
  "actors": [{
    "id": "alpha",
    "sessions": [{
      "id": "first",
      "steps": [
        {"id": "status", "action": "command", "args": {"command": "onlydragons status"}},
        {"id": "aim", "action": "look", "args": {"yaw": 0, "pitch": 0}},
        {"id": "hit", "action": "attackEntity", "args": {"targetRef": "target"}},
        {"id": "end", "action": "disconnect", "args": {}}
      ]
    }]
  }]
}
```

A plan has 1–4 actors, 1–4 sessions per actor, at most 64 total steps and 16
named targets, and at most 65,536 UTF-8 bytes. IDs match
`[a-z][a-z0-9-]{0,31}` and are unique within their collection. Every session ends
with exactly one terminal step: `reconnect` for nonfinal sessions, `disconnect`
for the final session. No earlier step can be terminal. Actor index determines
the username: `od_` + the first ten run-ID characters + `_` + index (0–3).
The offline UUID is derived from `OfflinePlayer:<username>`. Reconnect preserves
the actor identity and creates fresh connection state, callbacks and receipts.

The companion requests the next declared step with the ordinary system-chat
marker `OD_ACTION:<runId>:<planSha256>:<actorId>:<sessionId>:<stepId>`.
Only the step ID comes from the marker; action arguments come from the verified
plan. A foreign run/actor marker is ignored. A current-actor marker with the
wrong hash, session, ordering or duplicate step fails the entire client cohort.
All action requests require observed login and a submitted loading/teleport ACK.

| Action | Exact `args` fields and bounds |
| --- | --- |
| `selectSlot` | `slot`: integer 0–8 |
| `look` | `yaw`: −180–180; `pitch`: −90–90 |
| `move` | `x`, `z`: −1024–1024; `y`: −64–320; `onGround`: boolean. Each coordinate changes by at most eight blocks from current client position. |
| `command` | `command`: ASCII, at most 256 characters, starts with `onlydragons`, no leading slash or control characters |
| `useItem`, `swing` | `hand`: `main` or `off` |
| `releaseUse`, `swapHands`, `respawn`, `disconnect` | Empty object. Respawn additionally requires a received zero-health packet. |
| `dropItem` | `all`: boolean |
| `attackEntity` | `targetRef`: declared target ID |
| `inventoryClick` | `slot`: integer 5–45 in player window 0; `button`: left/right. Requires a fresh full server inventory snapshot. |
| `interactBlock` | Integer `x`, `z`: −1024–1024; integer `y`: −64–319; `face`: down/up/north/south/west/east; `hand`: main/off; `cursorX`, `cursorY`, `cursorZ`: 0–1; `insideBlock`: boolean |
| `reconnect` | `delayMillis`: integer 100–5000, counted after the actual disconnect callback |

Before an attack, send each applicable connection
`OD_BIND:<runId>:<planSha256>:<targetRef>:<canonicalUuid>`.
A target reference binds once per connection to an immutable UUID. The client
must independently receive that entity's spawn packet and resolve its network
entity ID; removal invalidates the network mapping. It never selects a nearby
entity or accepts a server-supplied numeric network ID. Respawn discards network
mappings; reconnect also discards bindings. A future session must be rebound.

`player.json` schema 2 carries run/plan/artifact identity, the actor name/UUID,
every session, bounded received messages, target bindings, and exact ordered
steps with original normalized arguments, `submittedAtEpochMs` and
`packetTypes`. Rows are recorded after all `Session.send` calls return; terminal
steps have an empty packet list. Attack rows also carry the resolved
`targetUuid` and `networkEntityId`. This is packet-submission evidence, not
proof of a hit, successful command or accepted movement. The independent
companion report must establish actual Paper events and resulting state.
Failures, unexpected disconnects, timeouts and incomplete sessions cannot
produce a passing cohort. All connections and executors are owned by this
one process, including pending reconnects during cancellation.

Inventory/equipment setup, permission attachments and environment preparation
belong to explicit server fixture setup. They do not establish client inventory
clicks. `inventoryClick` sends an actual window-0 click with the latest confirmed
state ID for the last received 46-slot full snapshot. It supplies no optimistic changed-slot
predictions and an empty remote-cursor prediction; Paper computes the real
transaction and synchronizes the resulting state. This avoids inventing the
registry-aware component hashes required by `HashedStack` for tagged items.
In the pinned Paper handler, click/event processing precedes consumption of
those prediction fields and authoritative changes/full-state synchronization.
The companion must observe the actual `InventoryClickEvent`, verify inventory
and cursor contents, then call the public `Player.updateInventory()` API before
requesting the next click. A packet submission is never the acceptance check.

Each click invalidates readiness until a new full server snapshot arrives;
incremental slot/cursor changes are insufficient. Closing or reconnecting with
an outstanding resynchronization fails. Per-session `inventorySnapshots` records
up to 128 snapshots with sequence, container ID, state ID, slot count, receive
time and a SHA256 of the pinned codec's serialized packet bytes, including item
components. A 46-entry `slotSha256` array also records each item's serialization
digest using the same codec as the unchanged-slot comparison. Pinned Paper follows a full refresh with an unchanged offhand-slot
packet that advances its state ID. The client accepts a slot confirmation only
when its item bytes equal those in the full snapshot and its received state ID
is unchanged or advances by one modulo 32768. It records up to 512 such packets
in `inventoryConfirmations`, including sequence, parent snapshot sequence,
container/state/slot, receive time, serialized packet SHA256 and `itemSha256`
matching the full snapshot's slot digest. A changed slot,
cursor or state-less inventory delta still requires a new full refresh; a
confirmation can never settle an outstanding click or restore an invalid view.
Click receipts reference the consumed snapshot's sequence, time and SHA256,
plus the latest confirmation sequence (zero if none). The outgoing ID is the
one actually received, never a predicted ID. New world/connection state
requires a new full snapshot. Crafting slots 0–4, other windows, shift-click,
dragging and creative inventory mutation remain outside this primitive.

Native damage and protocol calibration do not accept the future production
firing/combat/loot adapters; each task still supplies feature-specific assertions
against its actual deployed artifact. Authenticated clients and visuals remain
separate evidence.
