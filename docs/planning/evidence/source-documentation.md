# Whole-code source documentation

This maintenance follows the completed aimed-Tracer checkpoint in PR92 and its
accepted/deployed record in PR93. It documents existing implementation contracts;
it does not introduce mechanics, rebalance profiles, advance gameplay requirements
or accept a human milestone. The verified human server remains available.

## Scope and ownership

The comparison baseline is `684dd1064a271ab03da7ca078ead450462ca028e`.
The integration branch preserves ordinary worker commits and merge history:

| Partition | Scope | Handoff |
| --- | --- | --- |
| Symphony GH94 | 67 production domain/application files and 23 corresponding tests | [PR95](https://github.com/Kav-K/OnlyDragons/pull/95), `a6226db4aad5f59f91bd29da16af91dc1fae2bae` |
| Paper adapters | 34 production bootstrap/command/listener/service/adapter files and 16 tests | `5c96e7b0c8bf91443dd09b64094fd37ddc3ff3c7` |
| Fixtures and tools | 68 Java files across real-Paper scenarios, protocol client, lab source sets and standalone probes | `c12669bb37f1fed24d1920a2f3e2c9cdf71a9f26` |
| Operations and routing | 30 Python files, 12 PowerShell scripts, five CMD entry points, seven shell scripts, the Serena launcher and seven Gradle scripts | Lead-owned documentation and source-first workflow |

All **208 maintained Java files** are covered. The compiler-tree inventory finds
**321 named types and 796 explicit public/protected/interface APIs**, all with
documentation. Nontrivial internals and fixture oracles explain ownership,
ordering, input capture, accepted/rejected effects, units and cleanup. Generated
sources, downloaded dependencies and the vendor Gradle wrapper are excluded from
rewriting. Formal API-tag polish is reviewed separately from executable changes.

The [source contract map](../../../dev/source-contracts.md) provides the entry
points. AGENTS, Cursor, Symphony and the shared workflow now route routine bounded
work through the assigned issue, current delivery status, source contracts/tests
and relevant design sections. New scope, cross-cutting decisions and missing
design context still require the full planning entries. All existing authorization,
threading, fixture ownership, evidence and milestone gates remain in force.

## Verification boundaries

The lead independently compared parsed package/import/module/type syntax for all
208 Java files against the baseline, with no executable changes. Worker lexical
comparisons provide a second check. Python AST comparison excludes docstrings;
all 30 Python files retain executable syntax. All 12 PowerShell scripts parse
under Windows PowerShell 5.1, and their non-comment token kind/text/flags match
the baseline. Every one of their 29 functions has native help and parameter
documentation. CMD non-comment lines and CRLF are preserved. Removing the exact
reviewed shell/JavaScript/Gradle comment insertions restores their original source.

The integrated Windows builds use JDK `25.0.4.1+1` and the checked-in wrapper:

| Build/evidence layer | Result |
| --- | --- |
| Production JUnit/MockBukkit | 329 tests across 39 suites; zero failures/errors/skips |
| Companion JUnit | Six tests; zero failures/errors/skips |
| Protocol-client JUnit | 37 tests across eight suites; zero failures/errors/skips |
| Linux runner regression tests | 294 tests; passed without skips |
| Linux Symphony bridge/retention tests | 25 tests; passed without skips |
| Static delivery/no-weakening check | Plan valid; all 48 catalog cases and baseline gates preserved |

Javadoc generation includes private members for production, companion, client,
their tests, both Java-8 lab source sets and the standalone development tools:
nine documentation indexes. A temporary, uncommitted Gradle init script exposes
test/lab/tool documentation tasks and raises diagnostic limits; it does not
suppress doclint or change repository configuration. Compilation/Javadoc errors
must be resolved before the combined PR is accepted. Missing formal tags or
field/default-constructor comments remain nonfatal diagnostics where the contract
is already described in prose; constructors are not added just to silence them.
Current final-head CI and exact combined acceptance are recorded on the combined
PR before merge. This record remains **In review** until that acceptance.

No new Paper cohort was run solely for comments. Javadoc changes still alter
source-byte identity: PR92's original 48-case runtime evidence and PR93's context
record retain their original revisions, inputs and artifacts. The documentation
builds are not relabeled runtime receipts and do not establish new client visuals.

## Independent review and discovered boundaries

Reviewers checked core domain capture/Tempo/HP/ranking/Tracer contracts, Paper
ownership/admission/cleanup, fixture intent versus observation, and Python evidence
and process semantics against actual code. Corrections make explicit that:

- Manual-gate references are structurally validated; a URL is not fetched human approval.
- Exact plan/receipt schemas and bounded setup payloads have different field policies.
- Aimed-Tracer part geometry has an existential witness check, separate from the
  all-observed-samples native velocity check.
- A logical combat target ID differs from the native dragon UUID.
- Queue capacity rejection differs from pre-commit due-tick overflow.
- Managed-combat observer removal uses consumer equality and is not generally
  idempotent when equal consumers have multiple registrations.

The last behavior and two other preexisting boundary cases are preserved for
separate [review in #97](https://github.com/Kav-K/OnlyDragons/issues/97).
The stale shortbow-list Tracer description is separately tracked in
[#96](https://github.com/Kav-K/OnlyDragons/issues/96). Neither issue is dispatched;
no gameplay or command-output correction is hidden in the documentation proof.
