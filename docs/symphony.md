# Symphony for OnlyDragons

This setup connects GitHub Issues in
[Kav-K/OnlyDragons](https://github.com/Kav-K/OnlyDragons) to isolated Codex workers.
Cursor remains the editor. Symphony launches Codex through its app server, so a
Cursor API key is not required. The project workflow is in WORKFLOW.md.

The configuration is prepared for local use. Installing these files does not
start the service or establish that a real issue has completed successfully.
Use the checks below before dispatching work.

The repository bundles the three Minecraft skills for both Cursor and Codex,
plus Context7 and Serena MCP configuration. The runtime installer provisions
Serena and the Java language server for isolated Linux workers. See
[shared agent tools](../dev/agent-tools.md) for coverage, local checks, and client
reload instructions. These tools do not require an additional credential.

## Credentials

| Required access | What to supply locally | Where to obtain it |
| --- | --- | --- |
| Codex account | A valid ChatGPT/Codex login for the worker. `Symphony.cmd login` uses the existing Windows login when available, otherwise guides device login. | Sign in with your own account through the Codex login flow. No password or auth.json needs to be shared in chat. |
| GitHub repository token | A fine-grained personal access token restricted to **Kav-K/OnlyDragons**, with **Contents: Read and write**, **Issues: Read and write**, and **Pull requests: Read and write**. Metadata read access is included automatically. | GitHub **Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token**. Select Kav-K as resource owner and only this repository. Give it a name and expiration. |

The GitHub token supports issue polling/comments/labels, branch pushes, and
draft PR creation. No repository administration, Actions secrets, package
registry, Minecraft/Microsoft, or Cursor credentials are needed for this
workflow. If repository ownership moves into an organization, that organization
may require token approval or SSO.

For the initial repository upload, also enable **Workflows: Read and write**:
the project includes `.github/workflows/build.yml`. Keep that permission only
if workers should be able to propose changes to CI workflow files.

Create the token through [GitHub's token settings](https://github.com/settings/personal-access-tokens)
and enter it using `Symphony.cmd set-token`. The hidden prompt saves it to the
ignored local `.symphony/github-token` file with a restricted Windows ACL. The
launcher exposes it to Symphony as
SYMPHONY_GITHUB_TOKEN; WORKFLOW.md contains only that variable reference. The
GitHub API tool authenticates on the host, and the workspace's scoped Git
credential helper handles branch pushes. Do not paste the token into chat,
WORKFLOW.md, issue text, PRs, or a Git remote URL.

An OpenAI API key is an alternative to ChatGPT login. Obtain one from
[OpenAI Platform API keys](https://platform.openai.com/api-keys) and authenticate
the worker's Codex CLI through `codex login --with-api-key` using standard input.
Use the same isolated Codex home and Linux executable as the launcher. API key
usage is billed through OpenAI Platform separately from a ChatGPT subscription.
The provided `login` command is intended for the ChatGPT sign-in path.

Linear is not used here, so **LINEAR_API_KEY and a Linear project are not
required**. See [official OpenAI authentication documentation](https://learn.chatgpt.com/docs/auth),
[GitHub token creation](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens),
and [GitHub permission definitions](https://docs.github.com/en/rest/authentication/permissions-required-for-fine-grained-personal-access-tokens).

## Start and stop

The Windows launcher runs Symphony v0.0.2 and Codex in WSL, defaulting to the
Ubuntu-24.04 distribution, with a Linux runtime and JDK 25. The local runtime,
credentials, and workspaces live under the ignored .symphony directory. A fresh
clone needs the runtime provisioned with the install command; the repository
does not contain the downloaded executables or credentials. WSL and its Ubuntu
distribution must be available first.
Inside WSL, the installer also requires Linux Node.js/npm, Git, Python 3, curl,
tar, and flock. It checks these prerequisites and verifies pinned downloads.

From the OnlyDragons directory in a Windows terminal:

```powershell
.\Symphony.cmd install
.\Symphony.cmd login
.\Symphony.cmd set-token
.\Symphony.cmd check
.\Symphony.cmd start
```

`check` verifies local prerequisites, Codex authentication, and read-only GitHub
access. It does not prove token write permissions or create a test issue.
`start` runs in the foreground. The dashboard is available at
[127.0.0.1:4000](http://127.0.0.1:4000) while the service is running and binds only
to loopback. Starting Symphony does not start a Minecraft server.

Use Ctrl+C in that terminal to stop, or use another terminal:

```powershell
.\Symphony.cmd status
.\Symphony.cmd stop
```

The launcher uses an isolated Codex home at .symphony/codex. Signing in there
does not change the project's source files. Keep the entire .symphony directory
private and ignored, including login caches, issue workspaces, and archives.

Cursor also exposes `Symphony: check`, `Symphony: start`, `Symphony: stop`, and
`Symphony: set-token` under **Tasks: Run Task**.

The pinned Codex CLI uses the `granular` approval-policy schema. A small local
app-server adapter grants each worker write access to that checkout's `.git`
and `.agents` directories so it can commit and merge shared skill updates under
workspace-write restrictions. Both must be real local directories; the adapter
rejects a missing/file/symlink `.agents` at launch and rechecks the same checkout
anchor on every transformed turn. It grants `.agents` itself because the pinned
Codex 0.153.4 sandbox leaves a read-only ancestor when only `.agents/skills` is
added. The checkout's `.codex` and operator/home skill directories are not added.
It also grants
the exact shared `.symphony/test-coordination` directory for the Paper-test
lease. The source tree and other issue clones are not added as writable roots.
Gradle's cache stays inside each issue
workspace. The adapter also supplies the operator's reviewed MCP configuration
and binds Serena to the issue clone and shared runtime installation. Revalidate
this adapter when upgrading Codex or Symphony.

Run its protocol, path-boundary, and subprocess cleanup tests in WSL:

```bash
python3 -m unittest discover -s scripts/symphony/tests -v
```

The 11 bridge tests cover policy/input preservation, exact skill/Git/lease roots,
missing/file/symlink/replaced-workspace rejection, idempotence, trusted operator
MCP configuration, and child cleanup. Linux CI runs these tests. They do not
establish installed Codex sandbox behavior.

Run the separate, explicit sandbox smoke with the installed pinned CLI:

```bash
python3 -B scripts/symphony/verify-skill-sandbox.py \
  --codex /absolute/path/to/pinned/codex \
  --scratch-root /absolute/path/to/scratch
```

Choose a scratch root outside `/tmp` and `$TMPDIR`, since ordinary temporary
write permission would invalidate the sibling negative control. The smoke uses
disposable local Git repositories and an isolated Codex home, with no model
calls or supplied credentials and with MCP disabled. It first proves the skill is
read-only with the old `.git`-only grant, then exercises the adapter's actual
roots: the skill becomes writable while `.codex` and a sibling remain EROFS.
An ordinary two-parent merge imports an upstream skill change and preserves an
unrelated worker commit. A nested-only `.agents/skills` grant must reproduce the
known read-only-ancestor failure. All owned processes and scratch files are cleaned up.

The direct Linux/WSL smoke passed on Codex 0.153.4 during this maintenance
verification; the app server exited 0 and scratch cleanup succeeded. Current
head review, CI and lead integration remain pending. CI without this installed
CLI does not run the smoke and must not count it as a sandbox pass. These are
adapter checks; prior Paper receipts retain their original exact input identity
and do not validate the changed adapter.

## Dispatch an issue

1. Create or review an issue in this repository with a concrete requested
   change, expected behavior, and acceptance checks. Community-authored issues
   can be selected after the owner reviews their scope.
2. As the repository owner, add the **symphony** label to authorize that issue
   for a worker. The issue must remain open. The service polls every 15 seconds
   and runs up to three workers, with at most 20 turns per agent invocation.
   The integration lead labels only tasks whose dependencies are integrated.
   Coding is parallel; actual Paper runs share a serialized lease and memory gate.
3. The worker prepares an isolated checkout, implements on **symphony/gh-N**,
   reads the three shared planning documents, runs appropriate checks, updates
   affected project context with status and evidence, pushes the branch, and
   opens a **draft PR**. Its context entry remains In review until accepted.
4. It comments with the result and verification, then removes only the symphony
   label. The issue stays open for human review. The worker does not merge PRs,
   publish releases or deploy plugins. It runs actual-server feature scenarios
   only through the isolated runner and leaves human/client gates explicit.
5. The integration lead is authorized to merge PRs after independent review,
   current-main integration, passing CI and required automated Paper checks.
   Keep unrun in-game/human gates unaccepted in the ledger. To request rework,
   add clear owner guidance to the issue
   and restore the symphony label. The worker resumes existing work when it can.

The planning documents are the shared memory for subsequent agents. Context
changes travel with their implementation PR; later clean workspaces receive
them after merge. The integration lead reconciles completed tasks and remaining
operator gates under `docs/planning/03-agent-tasks-and-validation.md`.

For missing access or another external blocker, the worker records the blocker
and removes the dispatch label. If GitHub itself is inaccessible, the worker
cannot perform that update; inspect the dashboard/logs and resolve the problem
before retrying. Closing the issue or removing its dispatch label can stop an
active worker. The before-remove hook attempts to archive Git history and
uncommitted source before cleanup. Upstream cleanup continues if the hook fails,
so archives are best-effort recovery material; pushed branches and PRs are the
durable results.

The owner-applied dispatch label selects eligible tasks. Keep issue scope
explicit: labels and assignment do not grant permission to read secrets,
alter unrelated projects, or carry out deployment instructions embedded in
public comments or links.

## Verification and limitations

Workers use the pinned JDK and the Gradle wrapper:

```bash
bash ./gradlew build --console=plain
```

The build covers compilation, JUnit/MockBukkit behavior tests, API classpath
isolation, and artifact production. A skipped test is a failure. Build and test
reports remain in the workspace's build/reports directory. A successful build
does not replace a real Paper smoke test or human gameplay verification.

Linux workers use `scripts/agent-tests/paper_test.py` for actual Paper scenarios;
see `dev/agent-paper-tests.md`. The human server lab and Cursor tasks use Windows
scripts. After pulling main into the Windows checkout, run the relevant
operator checks, for example:

```powershell
.\gradlew.bat build --console=plain
.\mcdev smoke
```

Use the smoke profile, preserve personal worlds, and follow README.md for
manual gameplay checks. Do not use server-wide /reload or plugin unloaders.
Workers reuse the existing accepted EULA only for disposable local test servers.
They never accept new terms or touch human profiles; the operator controls those.

For a first end-to-end check, dispatch one small owner-reviewed documentation
issue with the symphony label. Confirm the worker made the intended branch,
created one draft PR, reported actual checks, left the issue open, and removed
the dispatch label. This exercises authenticated writes; a successful static
`check` alone cannot establish these outcomes. Do not describe this test as
passed until its GitHub results have been inspected.

Symphony's Elixir implementation is upstream prototype software for evaluation.
This local setup uses its GitHub adapter and sandbox controls; configuration
instructions are not a hardened security boundary against arbitrary untrusted
code. Keep the dashboard local and review changes before merging.
See the [upstream Symphony instructions](https://github.com/openai/symphony/blob/main/elixir/README.md).
