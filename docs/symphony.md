# Symphony for OnlyDragons

This setup connects GitHub Issues in
[Kav-K/OnlyDragons](https://github.com/Kav-K/OnlyDragons) to isolated Codex workers.
Cursor remains the editor. Symphony launches Codex through its app server, so a
Cursor API key is not required. The project workflow is in WORKFLOW.md.

The configuration is prepared for local use. Installing these files does not
start the service or establish that a real issue has completed successfully.
Use the checks below before dispatching work.

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
directory so it can commit under workspace-write restrictions. It leaves the
remaining sandbox policy intact. Gradle's cache stays inside each issue
workspace. Revalidate this adapter when upgrading Codex or Symphony.

Run its protocol, path-boundary, and subprocess cleanup tests in WSL:

```bash
python3 -m unittest discover -s scripts/symphony/tests -v
```

## Dispatch an issue

1. Create or review an issue in this repository with a concrete requested
   change, expected behavior, and acceptance checks. Community-authored issues
   can be selected after the owner reviews their scope.
2. As the repository owner, add the **symphony** label to authorize that issue
   for a worker. The issue must remain open. The service polls every 15 seconds
   and runs one worker at a time, with at most 20 turns per agent invocation.
3. The worker prepares an isolated checkout, implements on **symphony/gh-N**,
   reads the three shared planning documents, runs appropriate checks, updates
   affected project context with status and evidence, pushes the branch, and
   opens a **draft PR**. Its context entry remains In review until accepted.
4. It comments with the result and verification, then removes only the symphony
   label. The issue stays open for human review. The worker does not merge PRs,
   publish releases, deploy plugins, or start a server.
5. Review the PR, run remaining Windows smoke and in-game checks, then decide
   whether to merge. To request rework, add clear owner guidance to the issue
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

The server lab and Cursor server tasks are Windows scripts. A Linux worker
must report them as unrun. After checking out the reviewed branch in your
Windows development checkout, run the relevant operator checks, for example:

```powershell
.\gradlew.bat build --console=plain
.\mcdev smoke
```

Use the smoke profile, preserve personal worlds, and follow README.md for
manual gameplay checks. Do not use server-wide /reload or plugin unloaders.
Only the operator should choose when to start a server or accept the EULA for
a fresh environment.

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
