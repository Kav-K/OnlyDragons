# Checkpoint 2: combat feedback, volleys and enchant books

**6 September 2026 — T05b accepted; the remaining checkpoint features and human outcomes remain pending.**

The first managed dragon/ranking slice is integrated on main `36b420c`.
The user now prioritizes a persistent dragon health bar, smoother bounded flight,
visible Tracer volleys, held-fire shortbows, level-dependent Ferocity ghost damage,
all ten custom enchants as XP-cost anvil books, and readable in-game presentation.
Armor needs a future category/slot boundary only; no armor stats or effects are selected.
Real rewards remain disabled. This scoped work does not release ritual, economy or
T11/T12 gates, and it accepts no M1–M5 milestone or unobserved human outcome.

## Tasks and integration order

| Task / issue | Integrated prerequisites | Owned scope and acceptance requirements |
| --- | --- | --- |
| T08d / [#64](https://github.com/Kav-K/OnlyDragons/issues/64) | T08a, T08b | Shared text formatter and one owned dragon boss bar; `dragon-bossbar`, `combat-presentation`. |
| T08e / [#65](https://github.com/Kav-K/OnlyDragons/issues/65) | T08a, T07 | Bounded real dragon motion and versioned moving-target Tracer integration; `bounded-dragon-flight`, `moving-dragon-tracer`. |
| T05b / [#66](https://github.com/Kav-K/OnlyDragons/issues/66) | T05, T08a, T08b | Player-wide Tempo/Ferocity HP policy and immutable provenance; `fatal-tempo-ghost-scaling`, P08/P09: accepted through [PR #73](https://github.com/Kav-K/OnlyDragons/pull/73). |
| T03b / [#67](https://github.com/Kav-K/OnlyDragons/issues/67) | T05b, T06 | Overload/Gravity, captured decisions and all-ten trusted descriptors; `expanded-bow-enchantments`. |
| T06c / [#70](https://github.com/Kav-K/OnlyDragons/issues/70) | T03b | Authoritative Infinite Quiver ammo and owned Flame DOT; `quiver-flame-integration`. |
| T06b / [#68](https://github.com/Kav-K/OnlyDragons/issues/68) | T03b, T08e, T06c | Actual held-fire tiers and useful separate-ultimate loadouts; `held-shortbow-loadouts`. |
| T02c / [#69](https://github.com/Kav-K/OnlyDragons/issues/69) | T03b, T08d, T06c | Metadata-safe books, recipes and real anvil transactions; `enchant-books-anvil`. |

T05b is complete after PR #73 merged at `29f0cf3969e7d256b2f678a54f846d836ca6db9f`;
[its 34-case hosted evidence](evidence/t05b-suite.md) accepts exactly
`fatal-tempo-ghost-scaling`, P08 and P09. T03b/#67 is active and lead-assigned:
T05b and T06 are integrated, with the Symphony label applied by the lead after
context reconciliation. T08d/#64 and T08e/#65 remain In review pending their
combined current-input cohort. T06c/#70, T06b/#68 and T02c/#69 remain planned.
These six unfinished tasks retain empty completed requirements/evidence; the
eight other new automated requirements remain unaccepted. T03b then T06c
establish effects before T06b/T02c expose the complete experience.

T10 retains every prior dependency and additionally consumes T08d, T08e, T05b
and T06b. T06b brings T03b/T06c transitively. Anvil application is independent of
prefire, so T02c is not an artificial prerequisite for T10. T11 still requires
T10 and accepted M3; T12 still requires T11 and its product/economy decisions.
T08c remains undispatched pending lead sequencing; this is not loot implementation.

## Recorded mechanic and application contracts

- [T05b Tempo ordering](https://github.com/Kav-K/OnlyDragons/issues/66#issuecomment-5561937297):
  sample the player's active bonus and HP policy before each eligible impact.
  Ordinary, Duplex and Fatal Tempo impacts independently resolve Ferocity.
  No active Tempo gives 100% proc HP; active I–V gives 90/80/70/60/50%, always
  with full 100% credit. After an accepted eligible captured FT source, including
  approved FT children, add `10 × level` percentage points up to +200%, refresh
  expiry by 60 ticks, and adopt that source's level even when lower. Expire before
  the boundary hit: a newly admitted physical FT impact after expiry is unbuffed
  and uses 100% proc HP; previously queued children keep their captured policy.
  Children retain their immutable pre-hit HP policy and never recurse. Store active
  buff/proc-policy provenance separately from `fatalTempoSourceLevel`, whose existing
  meaning is refresh eligibility. Later held gear must not rewrite either identity.
- [T03b v2 enchant profiles](https://github.com/Kav-K/OnlyDragons/issues/67#issuecomment-5561931960)
  record Overload I–V and Gravity I–VI values, ordering and AIRBORNE classification,
  plus Infinite Quiver X / Flame II descriptors. Capture one primary mega-crit
  decision; descendants inherit it without rerolling. Keep every existing enchant
  and old calibration profile explicit; an unsupported consumer is not advertised active.
- [T08e proposed v2 Tracer calibration](https://github.com/Kav-K/OnlyDragons/issues/65#issuecomment-5561944060)
  records acquisition 8/16/24/32/40 blocks, retention +8, an 18-degree turn limit,
  three ticks of original-group ballistic grace and release on lost line of sight.
  This is adopted implementation calibration pending measured validation, not an
  accepted gameplay result. Preserve v1, freeze profile provenance, retain native
  arrow motion/gravity and real collisions. Public-API dragon movement must prove
  actual multipart bounds and collision follow it; never substitute automatic hits.
- [T02c anvil rules](https://github.com/Kav-K/OnlyDragons/issues/69#issuecomment-5561933219)
  record ordinary `2 × resulting level` / ultimate `4 × resulting level` XP costs,
  +1 for an actual rename, preserved prior-work metadata and no custom escalation.
  Ordinary vanilla recipes remain intact. Use validated PDC, not lore/native glint,
  as custom-enchant authority. Preserve item UUID, durability, names, foreign data
  and applicable native enchants. Preview cannot charge or mutate the inputs;
  extraction must revalidate and consume inputs/XP exactly once.
- [T06c ammunition/fire profile](https://github.com/Kav-K/OnlyDragons/issues/70#issuecomment-5561949977)
  records Infinite Quiver's 5% per level up to 50%, Flame I/II's three 20-tick-spaced
  strikes at 3/6% of captured accepted physical credit, bounded per-owner-session
  burns and stronger refresh, and Duplex's nonstacking fire-vulnerability policy.
  Preserve the exact limits/source/expiry rules in that contract; DOT never rolls
  Ferocity or builds/refreshes Tempo. Recorded values still require implementation
  and independent actual-Paper validation.

One ultimate per bow remains settled: Duplex and Fatal Tempo belong on separate
bows, and swapping is supported. The ten requested IDs are Dragon Tracer, Duplex,
Fatal Tempo, Power, Vicious, Snipe, Overload, Gravity, Infinite Quiver and Flame.
No new armor application, reward items, acquisition economy or extra dragon types
are implied. The recorded issue contracts supply the calibration decisions for
implementation. T05b has the scoped acceptance above; the other new effects still require their gates.

## Shared ownership and feature evidence

T08d publishes the formatter for T02c/T06b; T08e publishes motion/phase/ticket
ownership. Their shared dragon service/bootstrap edits are lead-coordinated.
T05b publishes proc provenance before T03b/T06c; T03b owns trusted descriptors,
with serial integration of T06c/T06b changes to the existing firing authority.

Reuse the existing Paper/player fixtures. Add only missing boss-bar observations,
actual held-input evidence and the bounded real-anvil container/rename/extraction
path needed for these features. No new general framework or competing damage listener.
Each ticket owns independent value oracles, real event/packet effects, negative and
lifecycle controls, additive coverage, a clean source-bound receipt, independent
review and current CI. Shared harness changes retain the full-suite policy and
one local Paper lease. Preserve every earlier fixture, evidence identity and gate.
Human checks concern actual visual readability, aim/feel and authenticated-client
behavior; numerical damage, XP/items, ownership and cleanup stay automated.
