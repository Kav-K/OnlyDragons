---
name: paper-threading-review
description: Review Paper/Bukkit plugin scheduler ownership, asynchronous I/O, stale callbacks, and lifecycle cleanup. Use when changing async work, player sessions, repeating tasks, persistence, shutdown, or Folia compatibility.
---

# Paper threading review

Read `AGENTS.md` and the active workflow; keep this review within the current
user's assigned scope and execution permissions. In OnlyDragons, use public
Paper/Bukkit APIs without NMS or reflection into server internals. Its unattended
Symphony workers test through the permitted isolated Paper runner with the
shared lease and accepted EULA; human profiles/worlds and new EULA acceptance
remain outside their scope. Keep client evidence distinct from server checks.

Determine the actual server target first. Classic Bukkit/Paper uses its server thread for ordinary world and player operations. Folia requires the appropriate entity, region, global, or asynchronous scheduler; a global scheduler is not a universal route back to world access. Do not infer Folia support from `folia-supported: true`.

Trace each changed path from its command/event entry point through scheduled tasks, futures, I/O completion, and shutdown. For uncertain APIs, use Context7 if available and check the resolved dependency source or versioned official API. These current guides explain the distinction: [Paper scheduling](https://docs.papermc.io/paper/dev/scheduler/) and [Paper/Folia support](https://docs.papermc.io/paper/dev/folia-support/).

Check the following where relevant:

- **Ownership:** Identify which thread or region owns each mutable object. A concurrent map does not make the player/world objects it contains safe to access asynchronously. Check asynchronous events individually.
- **I/O boundary:** Capture immutable values on the owning thread, do blocking network/database/file work away from tick execution, then schedule the result onto the correct owner. Do not block a server/region thread on `join()`, `get()`, locks, or an executor that needs that same thread to finish.
- **Stale results:** Use player UUID plus a session/generation token or equivalent validity condition. A UUID alone does not distinguish a new session after reconnect. Revalidate ownership, player/entity validity, current operation, and plugin lifecycle when applying a result.
- **Task lifecycle:** Retain cancellable task handles when needed. Cancel repeating tasks and invalidate callbacks on disable, removal, match end, or disconnect. Account for an entity retiring before an entity-scheduler callback runs. A cancellation request may not interrupt I/O already in progress.
- **Shutdown:** Prevent new work, invalidate pending results, and close resources in an order that cannot deadlock the server thread. Keep waits bounded. Surface failed persistence and background exceptions; do not silently abandon important writes.
- **Timing:** Distinguish tick duration from elapsed time. Use a monotonic elapsed clock for real-time cooldowns when appropriate; preserve deliberately tick-based gameplay behavior.

Return concrete findings with the trigger, affected code, user-visible consequence, and smallest practical fix. Distinguish demonstrated defects from unverified assumptions. Test the relevant race or lifecycle transition when practical; do not claim a threading review proves runtime or Folia compatibility.
