# Shared Minecraft agent tools

OnlyDragons carries its agent guidance and client configuration in Git. A fresh
clone receives the same skills and planning context as the main Cursor checkout.
Installations, credentials, indexes, and logs remain local.

## Guidance available to each agent

| Skill | Coverage |
| --- | --- |
| `minecraft-plugin-development` | Paper/Bukkit architecture, commands, events, permissions, Adventure, configuration, persistence, sessions, arenas, encounters, progression, and build integration. Eight implementation references plus an OnlyDragons planning route. |
| `paper-runtime-validation` | Meaningful domain/MockBukkit tests, real Paper scenarios through the existing mcdev lab, version matching, client evidence, and profiling. |
| `paper-threading-review` | Scheduler ownership, async I/O, stale callbacks, player sessions, repeating tasks, persistence, and shutdown cleanup. |

The committed `.agents/skills/` directory is supported by both
[Codex skills](https://learn.chatgpt.com/docs/build-skills) and
[Cursor skills](https://cursor.com/docs/skills). Read the relevant repository
copy when a same-named user skill also appears. Preserve its references and
license/provenance when updating it. Do not load every reference for every task.

The project's concrete combat, physical-arrow, item/PDC, multipart dragon,
proc-budget, and validation requirements remain in the three planning documents.
The development skill's `references/onlydragons-routing.md` points to them.
Generic examples do not authorize extra work or override public-API/threading
rules. Symphony workers run feature scenarios through the authorized isolated
Paper runner in `dev/agent-paper-tests.md`; they do not use human profiles or
accept new terms. Client input and visuals need separate evidence.

## Connected tools

| Tool | Use and boundary |
| --- | --- |
| Context7 | Resolve a library and query its documentation. Include the repository's pinned version. Confirm unmatched or ambiguous APIs with official docs and resolved Java signatures. Only documentation queries go to this remote service. |
| Serena 1.7.0 | Local Java symbol overview, definitions, references, file/pattern search, and current-project confirmation. The launcher exposes eight navigation tools. Use native client tools for edits, Git, and builds. |
| Existing project tools | Gradle wrapper, JUnit/MockBukkit, API-isolation checks, coverage, Cursor Java/debug tasks, and the Windows mcdev lab. These remain the verification pipeline. |

No new paid service or credential is needed for these tools. Context7's free
unauthenticated endpoint can be rate-limited. An optional key for higher limits
is available from the [Context7 dashboard](https://context7.com/dashboard);
configure it in local client settings using an environment variable, never in
committed configuration. See [Context7 setup](https://github.com/upstash/context7).
GitHub access for Symphony is separate; see [Symphony credentials](../docs/symphony.md#credentials).

The project MCP files are `.codex/config.toml` and `.cursor/mcp.json`. These use
the existing `context7` and `serena` server names to override project-specific
behavior. Global connections for other projects are unchanged. Codex loads its
project configuration only in trusted projects; review the configuration when
opening a new checkout. See [Codex MCP](https://learn.chatgpt.com/docs/extend/mcp)
and [Cursor MCP](https://cursor.com/docs/mcp).

Open the repository root in the client and start a new agent session or restart
its MCP connections after installing. Check that both servers are connected and
that all three skills appear. This task's already-running tool inventory does
not change just because configuration was written.

## Local prerequisites and readiness

On the configured Windows machine, the launcher uses the installed
`%LOCALAPPDATA%\MinecraftAgentTools` Serena/Python and vscode-java bundle, and
finds the JDK matching `versions.properties`. Node.js must be on the client's PATH.

From the project root:

```powershell
node scripts/agent-tools/serena-launch.mjs --check
```

On Linux x86_64, `Symphony.cmd install` provisions the pinned tools in WSL,
including Java, Serena, and the vscode-java 1.56.0 distribution of Eclipse JDT LS.
The separate installer can also run in Linux with Python 3.11–3.14, curl, tar,
sha256sum, and Node.js available:

```bash
bash scripts/agent-tools/install-serena.sh
node scripts/agent-tools/serena-launch.mjs --check
```

The separate installer supplies Serena/JDT LS, not the project JDK. For another
Windows host, install Python 3.11–3.14, `serena-agent==1.7.0`, a matching JDK, and
the server/lombok directories from the official vscode-java 1.56.0 distribution.
Use the overrides below if their locations differ. The current Windows machine
already has those dependencies.

| Optional environment variable | Meaning |
| --- | --- |
| `ONLYDRAGONS_AGENT_RUNTIME` | Directory containing `serena-venv`, `java`, and `jdtls-vscode-java-1.56.0` on Linux. |
| `ONLYDRAGONS_SERENA_BIN` | Serena executable path. |
| `ONLYDRAGONS_SERENA_PYTHON` | Python executable in the matching Serena installation. |
| `ONLYDRAGONS_JAVA_HOME` | Explicit JDK root; its major version must match `versions.properties`. |
| `ONLYDRAGONS_JDTLS_ROOT` | vscode-java distribution root containing `server/` and `lombok/`. |

The launcher anchors Serena to the checkout and creates a unique, ignored
`.serena/runtime/<platform>/<session>/` configuration and index. It copies the
public project settings and overlays platform-correct Java/Gradle paths there.
It leaves `.serena/project.yml` and global Serena settings untouched, and uses
a 1 GB Java index heap. Runtime caches/logs stay local; committed planning docs
are the shared agent context. Review disk use when many sessions accumulate;
remove only inactive session directories after their clients have stopped.

## Symphony discovery and verification

Symphony supplies the operator checkout's reviewed MCP configuration explicitly
to its isolated Codex app server. The launcher path and runtime come from that
checkout, while Serena's project is the assigned issue clone. Worker skill
discovery reads the clone's `.agents/skills/`; it does not depend on the Windows
user profile. A changed MCP command in an issue branch does not replace the
operator's configuration. Install/review tool updates in the operator checkout
before dispatching workers that need them.

Each worker app-server invocation disables Codex's optional `remote_plugin`
feature with `--disable remote_plugin`. Concurrent workers share a local
`CODEX_HOME`; automatic account-plugin synchronization was racing over that
home's plugin cache. The worker still loads the three repository skills and
the reviewed Context7/Serena configuration, while Symphony supplies its own
`github_api` tool through the app-server protocol. Authentication, model
selection, and sandbox policy retain their existing paths. The setting applies
when the next worker starts; it does not change active workers or personal
Codex/Cursor settings. Optional account-synchronized plugins are outside the
unattended worker tool set.

The Codex 0.153.4 CLI reports `remote_plugin` as a stable feature; the generated
worker command resolves it to `false`. The bridge regression suite preserves
host-supplied tools and launch policy and runs in Linux CI without starting a
model or MCP service. A local unauthenticated app-server check with the feature
disabled still discovered all three repository skills, connected Context7 (two
tools) and Serena (eight), and returned documentation and Java lifecycle symbols.

`Symphony.cmd check` checks local tool prerequisites alongside auth and GitHub.
For an actual MCP smoke check, use an expendable clean issue clone under the
operator checkout's `.symphony/workspaces/` and run this from that clone in WSL
with the operator's provisioned Codex on PATH:

```bash
export ONLYDRAGONS_SOURCE=/absolute/path/to/operator/OnlyDragons
export PATH="$ONLYDRAGONS_SOURCE/.symphony/runtime/node_modules/.bin:$PATH"
python3 "$ONLYDRAGONS_SOURCE/scripts/agent-tools/check-codex-tools.py"
```

This checks all three repository skills, MCP connection/tool inventories, a
public Paper documentation lookup, and the plugin's Java lifecycle symbols.
It uses a separate unauthenticated Codex home and an ephemeral session; no model
turn, GitHub mutation, or Minecraft server is started. Results stay under the
clone's ignored `.serena/runtime/codex-check-*/` directory. It is an integration
check, not a substitute for plugin build or gameplay evidence.

When changing the bridge, also run:

```bash
python3 -m unittest discover -s scripts/symphony/tests -v
```

Keep exact tool/version updates and observed results in the shared context
change record. A configured connection alone is not proof it initialized or
returned useful symbols/docs.
