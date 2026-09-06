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
