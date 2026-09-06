# OnlyDragons: research and mechanic reference

Research baseline: **5 September 2026**. This is a living evidence reference;
implementation progress is tracked in the [delivery ledger](03-agent-tasks-and-validation.md#current-delivery-status).

Agents maintain this document when relevant evidence changes. Preserve the
source/date/confidence of a claim, distinguish observations from OnlyDragons
decisions, and explain material corrections. Follow the [context update protocol](03-agent-tasks-and-validation.md#shared-context-update-protocol).

Read next: [foundation design](02-foundation-plan.md) and [agent tasks and validation](03-agent-tasks-and-validation.md).

## Evidence and scope

The target is the public End island's dragon encounter. Dungeon M7 dragons, Kuudra hit phases, and Endstone Protectors are different encounters; reports about one cannot establish the behavior of another.

Hypixel closed its official wiki in July 2026. Its former mechanic URLs now redirect to the closure announcement. This research therefore uses official staff announcements for documented changes, the current community wiki for descriptions, and dated player discussions for observed behavior. Search snippets from the retired wiki are not treated as live specifications. [Official closure announcement](https://hypixel.net/threads/end-of-the-official-hypixel-wiki-july-2026.6112020/)

Confidence labels below mean:

- **Documented:** a published description or staff statement supports the claim. This does not reveal Hypixel's private implementation.
- **Reported:** a player describes an observation; useful evidence, without controlled reproduction.
- **Unresolved:** sources conflict, or do not specify an interaction precisely enough to implement it faithfully.
- **OnlyDragons decision:** a rule we choose and test ourselves; not a claim about Hypixel.

## 1. Encounter structure

The ordinary encounter uses eight Summoning Eyes and an egg-hatching sequence. The community reference lists **seven regular variants**, rather than five:

| Variant | HP | Selection chance |
| --- | ---: | ---: |
| Protector | 9,000,000 | 16% |
| Old | 15,000,000 | 16% |
| Wise | 9,000,000 | 16% |
| Unstable | 9,000,000 | 16% |
| Young | 7,500,000 | 16% |
| Strong | 9,000,000 | 16% |
| Superior | 12,000,000 | 4% |

Their defense, movement, and attacks differ. Loot is personal and depends on contribution and eyes placed; variant-specific armor/fragments and rare rewards have separate eligibility and chance rules. The listed Primal Dragon is alpha content, not an eighth regular selection. **Documented community reference:** [Ender Dragon](https://hypixelskyblock.minecraft.wiki/w/Ender_Dragon).

**OnlyDragons decision:** use a configurable variant registry. A five-variant launch roster remains possible; select the subset and rebalance its probabilities later. Do not bake an array of five bosses into the combat foundation. Defer exact loot tables and the full attack roster until combat and spawning are reliable.

## 2. Enchantments and offensive stats

| Mechanic | Researched behavior | Consequence for the design |
| --- | --- | --- |
| Dragon Tracer I–V | Dragon homing acquisition radii are **2 / 4 / 6 / 8 / 10 blocks**. The public description does not specify its steering algorithm. [Dragon Tracer](https://hypixelskyblock.minecraft.wiki/w/Dragon_Tracer) | Level primarily controls acquisition radius. Turn rate, hitbox selection, line of sight, and reacquisition must be explicit OnlyDragons rules. |
| Duplex I–V | Adds **one second arrow**, dealing **4 / 8 / 12 / 16 / 20%** of the first arrow's damage. Current detail also describes increased fire damage taken for 60 seconds. It does not add five arrows at level V. [Duplex](https://hypixelskyblock.minecraft.wiki/w/Duplex) | Create a real extra projectile. Separate its damage scaling from any future fire-debuff system. |
| Fatal Tempo I–V | Each qualifying hit increases ferocity by **10 / 20 / 30 / 40 / 50%**, up to a **200% boost**; the effect lasts three seconds after the last attack. The community description says ferocity strikes also build the effect. [Fatal Tempo](https://hypixelskyblock.minecraft.wiki/w/Fatal_Tempo) | Requires temporary combat state, expiry, and explicit proc eligibility. It is not itself a fixed 25% double-hit roll. |
| Ultimate enchant restriction | An item has one ultimate enchant at a time. Both Duplex and Fatal Tempo are ultimate enchants. [Ultimate Enchantments](https://hypixelskyblock.minecraft.wiki/w/Ultimate_Enchantments) | Default proposal: separate bows; retain the option to swap while a temporary buff is active. |
| Ferocity | Each point represents a 1% chance of another hit; whole hundreds guarantee additional hits. The documented cap is 500. At 250, there are two guaranteed extra hits and a 50% chance of a third extra hit. It applies to arrows and melee. [Ferocity](https://hypixelskyblock.minecraft.wiki/w/Ferocity) | Store a numeric stat and resolve a bounded hit count, not a boolean “double damage” flag. |
| Snipe | I–IV adds 1% per level per ten blocks. A note says distance is measured between the player and the arrow at impact, despite the description saying distance traveled. Rounding and corner cases are unspecified. [Bow enchantments](https://hypixel-skyblock.fandom.com/wiki/Enchantments/Bow) | Make the distance policy explicit; never accidentally reward repeated homing loops. |
| Overload I–V | Adds crit chance/damage and enables mega critical hits above 100% crit chance, with level-dependent bonus damage. [Overload](https://hypixelskyblock.minecraft.wiki/w/Overload) | Preserve raw crit chance above 100%; clamp the ordinary crit probability separately. Include an extension point immediately. |
| Attack Speed | Most shortbows use a tick-based cooldown, described as `ceil(10 / (1 + AS/100))`. Current documentation includes values above 100 and notes input-method limitations. [Attack Speed](https://hypixelskyblock.minecraft.wiki/w/Bonus_Attack_Speed) | A shortbow is a custom firing mode. Merely adding bow enchants will not reproduce rapid volleys. Test click/hold behavior with a real client. |

### Other enchants worth including or reserving

**Power** is a useful first ordinary damage modifier. Current levels I–VII use 8/16/24/32/40/50/65%. **Vicious** supplies flat ferocity, which is especially useful because a multiplicative buff cannot create ferocity from zero. These are small, testable additions rather than separate progression systems. [Enchantments](https://hypixelskyblock.minecraft.wiki/w/Enchantments)

Older guides recommend **Dragon Hunter**. It was renamed **Gravity**, with its target category broadened to Airborne mobs in the August 2025 update. Its current table differs from old Dragon Hunter values. Use an alias if we want familiar terminology; choose and label a historical or current balance profile instead of mixing both. [Official 0.23.3 announcement](https://hypixel.net/threads/hypixel-skyblock-0-23-3-foraging-changes-mob-types-and-more.5963817/), [current Gravity reference](https://hypixelskyblock.minecraft.wiki/w/Gravity)

Reserve **Infinite Quiver** for ammunition consumption and **Flame** for a damage-over-time layer. Defer Soul Eater, Rend, Ferocious Mana, pets, Terror armor, Terminator-specific spread/abilities, and equipment swapping tricks. They introduce stored kills, mana sharing, gear set state, and other systems beyond the first dragon-combat slice. Shortbow support and configurable primary-arrow count should nevertheless make future weapons possible.

## 3. Ferocity, damage caps, and ghost damage are separate concerns

The ordinary ferocity hit-count rule can be expressed as:

```text
F = effective ferocity, bounded by the chosen profile
extraHits = floor(F / 100) + Bernoulli((F mod 100) / 100)
```

At 25 ferocity this produces the requested 25% chance of a second hit. At 100 it always produces one extra hit. This formula establishes hit count; it does not establish the amount of boss health or leaderboard score each extra hit must change. [Ferocity reference](https://hypixelskyblock.minecraft.wiki/w/Ferocity)

### Published boss cap

Hypixel's March 2021 staff post describes progressively reduced damage with a final cap at 1% of boss maximum HP. Its numerical example establishes **output thresholds**, not a simple clamp on raw damage. For a boss with maximum health `H`, an equivalent piecewise mapping consumes raw-damage bands:

| Raw damage consumed in this band | Health damage multiplier |
| --- | ---: |
| First `0.004 H` | 1 |
| Next `0.020 H` | 0.1 |
| Next `0.200 H` | 0.01 |
| Next `2.000 H` | 0.001 |
| Remaining damage | 0 |

The maximum output is `0.010 H`. This is an interpretation of the published example and an appropriate **versioned historical profile**, not proof of every current internal interaction. [Staff explanation and example](https://hypixel.net/threads/march-27-incoming-balance-changes-round-1.4047790/)

The current community dragon guide repeats the cap mechanism and explains why many smaller hits can outperform a single larger hit. It also states that dragons have no ordinary invincibility frames. These are reasons to test simultaneous impacts on a real dragon rather than relying on vanilla generic-mob behavior. [Dragon fights guide](https://hypixelskyblock.minecraft.wiki/w/Tutorial:Dragon_Fights_Guide)

### What players mean by “ghost damage”

The term is used inconsistently for at least three observations:

1. A displayed hit number exceeds the actual HP reduction.
2. The contribution leaderboard credits damage that is partly or wholly absent from HP reduction.
3. A particular proc is ineffective against a particular boss, despite an animation or number appearing.

Those observations require different implementations. A defense-bypassing “true damage” stat is another concept and should not be used as a synonym for health damage.

| Discussion | What it establishes | Limitation |
| --- | --- | --- |
| [Dragon damage discussion, April 2025](https://hypixel.net/threads/how-can-i-deal-more-dmg-against-dragons.5894429/) | Participants explicitly disagree: one describes End ferocity as ghost damage; another says that applies only to M7. Prefire and the cap are central to the advice. | No controlled side-by-side HP and score measurements. |
| [Continuation of that discussion](https://hypixel.net/threads/how-can-i-deal-more-dmg-against-dragons.5894429/page-2) | Further participants describe ghost damage on ordinary dragons and golems. | More reports, not an independently measured coefficient. |
| [Dragon guide discussion, February 2021](https://hypixel.net/threads/a-guide-to-dragons.3873733/page-2) | Participants distinguish leaderboard credit from damage that kills the boss, and describe arrows falling onto the hatch location. | Historical Bonemerang/swap-era behavior predates many changes. |

**Conclusion:** there is enough evidence to justify a separate score ledger, but not enough to claim “Hypixel ferocity always removes exactly X% HP.” The prototype should expose a documented health multiplier for ferocity and show health damage and contribution separately. We can compare normal, reduced-health, and score-only profiles without rebuilding the engine.

Fatal Tempo should have a bounded feedback path: extra hits may build tempo, as the reference describes, while **extra hits never roll more ferocity hits themselves**. The exact delay between strikes, cross-weapon eligibility, and Duplex secondary-arrow proc scaling still need declared rules. Anecdotes about full-strength ferocity from a weak Duplex arrow are not sufficient to make that a hidden default.

## 4. Prefire is a physical and timing requirement

The researched strategy involves firing upward so arrows arrive around the egg's hatch, rather than waiting for a visible dragon and then shooting. The key gameplay requirement is continuity between a pre-spawn projectile and its later hit. [Historical player description](https://hypixel.net/threads/a-guide-to-dragons.3873733/)

OnlyDragons should guarantee that:

- The arrow exists before the dragon, retains its UUID and trajectory, and can acquire a target that appears later.
- A hatch event does not clear the volley, recreate it at the boss, or convert it into delayed automatic damage.
- Homing does not ignore blocks, snap distant misses onto the boss, or retroactively award hits.
- Multiple arrows reaching the real hitbox on the same tick remain distinct impacts.
- Its trace explains launch, acquisition, collision, credited damage, and eventual removal.

The exact hatch duration, spawn transform, steering strength, and meaning of “permanent” are design choices. The recommended contract is a persistent physical arrow throughout an encounter, including its prefire window, with explicit termination on impact, arena exit, reset, or server shutdown. Durable airborne arrows across restarts would require a separate recovery design and are not silently assumed.

## 5. Paper feasibility and evidence limits

The local project pins **Paper API 26.2.build.121-stable and Java 25**. Its downloaded API sources were read for this plan. They expose `AbstractArrow.setLifetimeTicks`, `getLifetimeTicks`, `setCritical`, and `setDamage`; `EnderDragonPart.getParent`; and cancellable projectile/bow events. Lifetime control and disk persistence are different concerns.

Paper's PDC supports namespaced metadata on items and entities. It is a suitable storage boundary for item definitions, enchant levels, shot ownership, and schema versions. Lore should be a presentation of that data. [Paper PDC documentation](https://docs.papermc.io/paper/dev/pdc/)

**Observed locally, T02 / GH-4, 5 September 2026:** on pinned Paper
26.2-121-a2a42c5, the production item codec preserved all eight calibration
loadouts through `ItemStack.serializeAsBytes`/`deserializeBytes`; two bow UUIDs
remained distinct across a synthetic inventory move. Copied names/lore/glint
did not grant managed identity to an ordinary bow. Schema/type/revision and
trusted enchant/roll rejection controls passed. This establishes the item
metadata boundary, not authenticated inventory/anvil behavior, restart recovery,
combat effects or protection against privileged plugins forging PDC.
See [T02 evidence](03-agent-tasks-and-validation.md#t02-item-validation-evidence).

The projectile hit event's cancellation semantics require care: cancelling an entity hit prevents its normal collision action, while cancellation does not generally prevent block collision. The plan must not cancel every hit and assume vanilla impact behavior remains. [Paper ProjectileHitEvent API](https://jd.papermc.io/paper/26.2/org/bukkit/event/entity/ProjectileHitEvent.html)

World/entity mutation belongs on the server thread; asynchronous work is for immutable report data and file I/O. [Paper scheduling documentation](https://docs.papermc.io/paper/dev/scheduler/)

API signatures do **not** prove multipart collision delivery, seated-dragon arrow behavior, exact event ordering, or same-tick damage behavior. The implementation plan starts with a bounded real-Paper experiment for those uncertainties. MockBukkit remains valuable for lifecycle, commands, inventory metadata, and event routing, but it is not a physics simulator.

### T07 public API and fixture boundary (Paper 121)

**Inspected, 6 September 2026:** the resolved pinned API binary exposes
`AbstractArrow.getLifetimeTicks`/`setLifetimeTicks`. Public plugin tickets are
unique per plugin/chunk, requiring shared reference counting for multiple
OnlyDragons consumers. T07 therefore uses a shared broker and does not remove
pre-existing same-plugin tickets. MockBukkit's plugin-ticket call aborted its
initial test; pure broker tests use an injected native boundary and actual ticket
behavior is verified separately on Paper. [T07 contract and evidence status](evidence/t07-tracer.md).
This adds no claim about Hypixel steering, long-term persistence or performance.

### T06 native ammunition API inspection (Paper 121)

**Inspected, 6 September 2026:** the pinned API marks the bow event's
`setConsumeItem` as nonfunctional. The matching Paper `a2a42c5` patch dispatches
that event after ammunition drawing. T06 therefore distinguishes Paper's native
debit from shortbow-owned reservation, instead of relying on that setter and
charging twice. [Exact API/source and pending survival-inventory verification](evidence/t06-firing.md#calibration-and-native-ammo-discovery).
This is implementation-source evidence, not a gameplay pass or Hypixel claim.

The pinned disconnect patch defers handling to a following tick. T06's clean
native-input trial observes a child before actual quit; a kick call alone proves
no kick event. [Exact source, observations and reviewed layer-specific lifecycle
coverage](evidence/t06-firing.md#validation-state) retain the synthetic pending-group
regression separately from actual client disconnect/quit evidence.

### T04 measured collision boundary (Paper 121)

**Observed on exact pin, 2026-09-05:** real shooterless arrows delivered multipart
`ProjectileHitEvent`s without damage events, including three distinct same-tick
impacts and an arrow created before the dragon. Cows established hit-before-damage
ordering and distinct cancellation behavior. Unchanged dragon HP does not prove
native suppression because the shooterless native control also lost no HP.
Part names are identical; a small-part aim hit a different part and is not a
head-hit pass. See the [bounded findings and remaining gates](../../dev/game-tests/findings/projectile-feasibility.md).
These observations are OnlyDragons test evidence, not Hypixel mechanics or
player-owned dragon damage proof.

**Player-owned continuation, exact pin:** clean runtime `7b3a172` passed 52
real-Paper assertions with a real protocol Player. The unmodified native bow
release lost 3.25 dragon HP; separate API-spawned arrows owned by that online
player established native-positive and hit/damage-cancellation/zero-damage
controls. Three same-tick impacts emitted only one native damage event. Zero
base damage and seated collisions omitted damage events and left rebounding
arrows. Actual 1×1×1 and 5×3×5 parts lost 4 and 2 HP respectively, but no public
semantic part identifier was established. See [identities, geometry, exact
artifacts and limits](../../dev/game-tests/findings/projectile-player-feasibility.md).
This is Paper evidence, not a Hypixel mechanic or human/authenticated-client pass.

The recovery continuation at clean `77c94a2` reproduced all 52 player
assertions and the preserved 34 shooterless assertions in a complete 16-case
suite. Native controls lost 2.75/2 HP, suppression controls zero, and three
same-tick impacts emitted one damage event. Delayed quit and the separate
feature early-exit control met their declared outcomes. [Fresh receipt, geometry
identities and scope](../../dev/game-tests/findings/projectile-player-feasibility.md#recovery-verification-on-current-main)
retain unsupported semantic/all-phase conclusions and historical results.

### T01b measured equipment boundary (Paper 121)

**Observed locally, GH-7, 2026-09-05:** production equipment inspection on pinned
Paper resolved all eight native byte-round-tripped calibration bows to damage
100 / crit damage 50 and their declared crit/ferocity totals. Validated edits
preserving UUID changed cached enchant/roll contributions; repeated refreshes
and inactive offhand bows did not add weapon stats. Previously returned snapshots
remained unchanged. These are synthetic inventory/UUID and command-sender tests,
with MockBukkit event tests recorded separately. The unchanged protocol-player
calibration also passed with this bootstrap, but does not establish equipment
command/input or authenticated visual acceptance. See [T01b evidence and scope](03-agent-tasks-and-validation.md#t01b-equipment-validation-gh-7).
No upstream Hypixel claim or combat timing decision changes here.

### T09b measured protocol-player boundary (Paper 121)

**Observed on exact pin, 2026-09-05:** the timestamped MCProtocolLib 26.2 /
protocol 776 client joined as a real Paper Player in a disposable loopback
offline profile. Paper observed the expected UUID, selected-slot change,
full-force bow release, native player-owned Arrow, and quit. Positive and
early-exit/timeout controls all produced their expected results and clean
owned-process/resource cleanup from runtime revision `1dd6ffe`. See
[reports, hashes and scope](../../dev/agent-paper-tests.md#protocol-player-evidence).
This supplies a bounded actor for future T04/#5 and equipment/#7 experiments;
it does not establish player-owned dragon damage, human visuals, authentication
or multiplayer behavior, and changes no researched Hypixel mechanic.

### T05 measured proc and enchant boundary (Paper 121)

**Observed on exact pin, 2026-09-05:** clean `a7a8cd7` exercised the production
proc coordinator over 72 owned Paper scheduler ticks. Stable bounded children,
captured crit/mitigated damage, explicit capacity rejection, Tempo expiry/swap
eligibility and session/target cleanup passed. A native serialized item also
passed codec → stat factory → effects → combat without a second Vicious bonus.
See [T05 evidence](03-agent-tasks-and-validation.md#t05-enchant-and-proc-validation).
These are OnlyDragons calibration fixtures, not Hypixel measurements, native
physical-hit proof or authenticated-client evidence. Gravity/Overload formulas
and ferocity health coefficients remain unresolved; no upstream claim changed.

### T08a native dragon initialization and terminal ownership (Paper 121)

**Observed in pinned Paper fixtures, 6 September 2026:** AI-disabled native
multipart geometry remained near origin even with the parent at arena y=100.
Native HOVER with AI enabled updates parts and advances native death animation;
this is a development projection, not custom flight or a Hypixel movement claim.

[Pinned Paper CraftEntity source](https://github.com/PaperMC/Paper/blob/a2a42c5b12249aaba42a347327fd930a1f94af06/paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftEntity.java)
confirms `isValid()` includes liveness. HP zero/ANIMATING does not prove removal.
The backend uses the immediate public removal event plus `getRemovalReason()`;
fixtures independently verify retained ownership/tickets during animation and
actual UUID disappearance after reset/restart. [Exact results and superseded
iterations](evidence/t08a-focused-paper.md) preserve the earlier failed validity
assumptions. Full-suite and human acceptance remain pending in [T08a status](03-agent-tasks-and-validation.md#t08a--managed-real-dragon-backend-and-development-controls).

## Decisions still open

1. **Resolved during review:** the user confirmed one ultimate enchant per bow, with swapping supported.
2. The user selected the T05b level-based OnlyDragons playtest policy: no active Tempo 100% proc HP, active I–V 90/80/70/60/50%, with full credit. These coefficients are not researched Hypixel constants; qualitative balance remains a human check. [Implementation contract](02-foundation-plan.md#t05b-level-based-ghost-policy-gh-66-in-progress).
3. Which five variants, if five remains the desired launch scope? This does not block the stats foundation.
4. Confirm encounter-long arrow continuity versus literal persistence through shutdown and world reload.
5. Choose current versus historical naming/balance where Hypixel has changed. The recommended default is current documented descriptions, with explicit exceptions for the desired dragon experience.
