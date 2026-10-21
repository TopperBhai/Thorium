# PLAN.md — Thorium v0.2 Roadmap

**Project:** Thorium (`thorium` / Th-90) — Minecraft client-side performance mod
**Base:** `main` @ `948ec68` · **Current version:** 0.1.2 · **Plan horizon:** v0.1.2 → v0.2
**Companion docs:** [`spec.md`](./spec.md) (requirements) · [`state.md`](./state.md) (status) · [`AGENTS.md`](./AGENTS.md) (working rules)

> **What this document is.** `spec.md` says *what Thorium must do*. `state.md` says
> *what is built and broken*. This file says **what we build next**, in order, with
> enough precision that an agent or contributor can pick up any item and execute it
> without re-deriving the analysis.
>
> **How to read the priorities.** `P0` = ships in v0.2. `P1` = ships in v0.2 if
> capacity allows, otherwise v0.3. `P2` = v0.3+. `P3` = backlog / research.

---

## 0.0 Status update — 2026-10-21

The first slice of Milestone 1 has landed (uncommitted). What changed:

| Plan item | Outcome |
| :--- | :--- |
| **B-2** mixin target audit | ✅ `MixinTargetAuditTest` — a dead mixin target now **fails the build**. It found two dead targets: `tickParticleList` (G-2) and `Window#updateVsync` on 26.2 (not in any gap list). |
| **A-5** particle ticking | ✅ Resolved the honest way: the injection is gone, the class is gone, and the *real* bug it hid — vanilla's particle-limit bookkeeping was skipped — is fixed. `ThoriumParticleBudget` (a live creation budget) replaces it. |
| **A-3** frame context | ✅ `ThoriumFrameContext` is the single authoritative per-frame publisher. Per-entity publication is gone. New hooks: `core/MinecraftFrameBoundaryMixin` (client frame), `Legacy/ModernLevelRendererFrameMixin` (world frame). |
| **A-1** frame pacer | ✅ Wired to `Minecraft#runTick`, drift-free deadline, persisted target-FPS cap in the console. |
| **A-2** adaptive render distance | ✅ Bound to the game's own render-distance option, with a 60 s user-override yield. |
| **A-3b** fog on 1.21.11 | ✅ Ported to the new `AtmosphericFogEnvironment#setupFog` API. |
| **A-13 / D-4** subsystem report | ✅ The startup log prints the live state of every subsystem and names the ones with no call site. |
| **D-1 / D-2** coexistence & fast-math safety | ✅ Competing renderers and shader packs are detected; the fast-math overrides are off by default and no longer truncate `double` results. |
| **D-5 / D-6** honesty fixes | ✅ `DefaultEntityBridge` delegates to the real engines; the block-entity path now distance-tests before it allocates. |
| **C-1** documentation | ✅ `README.md` / `MODRINTH.md` corrected, the unverifiable benchmark table removed, a per-version feature matrix added. |

**Still open:** A-4 (chunk upload pacer on 26.x) and A-6 … A-12 (the seven subsystems
without a call site), E-1 … E-3 (benchmark harness), and F-1 … F-8.

---

## 0. Executive summary — read this first

Thorium has a **wiring problem, not a feature problem**.

The codebase contains 59 shared source files and 24 test classes covering 21
persisted subsystems. The unit tests are green on all five version nodes. But a
large fraction of those subsystems are **never invoked by the running game**. They
are implemented, unit-tested, exposed in the settings console with a live ON/OFF
toggle, persisted to `thorium-config.json` — and then never called.

Concretely, verified by exhaustive call-graph inspection of
`src/main/java/` + `versions/*/src/`:

| Subsystem | Spec requirement | Called from the game? |
| :--- | :--- | :--- |
| `ThoriumFramePacer.paceFrame()` | REQ-FRAME-4 (zero-jitter pacer) | ❌ **never** — tests only |
| `ThoriumFramePacer.setTargetFps()` | REQ-FRAME-5 (target ≤0 ⇒ uncapped) | ❌ **never** — no caller at all |
| `AdaptiveDistanceController.checkAndAdjust()` | REQ-ADAPT-1/2 (auto render distance) | ❌ **never** — tests only |
| `AdaptiveDistanceController.setDistanceConsumer()` | REQ-ADAPT-2 | ❌ **never** — tests only |
| `ThoriumInputPacer.recordPacedTurn()` | G-8 / Nuclear Max "input queueing" | ❌ **never** — tests only |
| `SectionCullingEngine.shouldRenderSection()` | REQ-CULL-1 (chunk-section culling) | ❌ **never** |
| `ParticleBatchEngine` | REQ-CHUNK-6 (5-sheet particle batching) | ❌ **never** |
| `EntityInstancer` / `EntityGroupManager` | REQ-CHUNK-7 (GPU entity instancing) | ❌ **never** |
| `AsyncLightEngine.enqueue()` | REQ-ADAPT-4 (async light propagation) | ❌ **never** — worker idles |
| `GlStateTracker.shouldBindTexture/shouldUseProgram` | REQ-CHUNK-9 (redundant GL state filter) | ❌ **never** — GUI only *reads* counters |
| `ThoriumImmediateBatcher.shouldBatchDraw()` | REQ-CHUNK-8 (HUD draw merging) | ❌ decision path never called (only `flush`) |
| `ThoriumChunkUploadPacer.runBudgetedUploads()` | REQ-CHUNK-1 (budgeted uploads) | ⚠️ **legacy nodes only** — dead on 26.1/26.2/26.3 |
| `ParticleEngineMixin` → `tickParticleList` | REQ-CHUNK-6 | ⚠️ **1.21.1 only** — dead on 4/5 (G-2) |

### 0.1b The per-node picture is *not* uniform — this matters

`AsyncOcclusionCuller.onFrameBegin(camX, camY, camZ, frustum, level)` is the
**only** publisher of the camera context (camera position, `currentLevel`,
`FrustumCache`, dynamic distance scale). Where it is called from decides whether
culling works *at all*:

| Node | `onFrameBegin` called from | Per frame? | Consequence |
| :--- | :--- | :--- | :--- |
| **1.21.1** | `LegacyLevelRendererFrameMixin` → `LevelRenderer#renderLevel` HEAD | ✅ once | Correct. Full culling works. |
| **1.21.11** | **nowhere** — `LegacyLevelRendererFrameMixin` is **not registered** in `versions/1.21.11/build.gradle.kts` | ❌ never | `currentLevel` stays `null` → occlusion grid, raycast and BVH checks are skipped; `FrustumCache.activeFrustum` stays `null` → **frustum culling is dead**; dynamic distance scale frozen at 1.0. **Only distance culling runs.** |
| **26.1 / 26.2 / 26.3** | `ModernEntityCullingMixin` → `EntityRenderDispatcher#shouldRender` | ❌ **once per entity** | Frustum works, but `ThoriumBvhCuller.onFrameBegin()` clears the whole cluster cache *per entity*, the dynamic distance scale steps ±0.05 *per entity* (collapsing to 0.35 within one frame), and `AdaptiveDistanceController.onFrame()` receives a **per-entity microsecond delta** instead of a frame delta. |

Two further era gaps found in the same pass:

* `LegacyFogMixin` is registered on 1.21.1 but **not on 1.21.11** → Extreme FPS
  Mode's fog suppression silently does nothing on 1.21.11.
* `LegacyVignetteMixin` exists only on 1.21.1 (see G-5).

**Only these are genuinely live today:**

* `AsyncOcclusionCuller` — entity / block-entity / particle culling.
  **Distance culling on all 5 nodes; frustum + occlusion culling only on 1.21.1**
  (see §0.1b)
* `HudBatcher` begin/end boundaries (all 5 nodes)
* `ThoriumEntityLodThrottler.onFrame()` — entity animation LOD (all 5 nodes)
* `ThoriumSoundGovernor` — spatial sound rate limit (all 5 nodes)
* `ThoriumTextureThrottler` — animated-texture throttle (all 5 nodes)
* `FramePacing` boot-window VSync policy (all 5 nodes)
* `Mth` fast-math overrides, `TelemetrySuppressor`, `MinecraftFramerateUncapper`,
  `SmartLeavesCulling` (all 5 nodes)
* Extreme-mode fog / weather / shadow / clouds suppression — **except fog on
  1.21.11**, where `LegacyFogMixin` is not registered (A-3b)

**The consequence:** the settings console shows 21 working switches for what are
really **9 working switches**. The README advertises GPU entity instancing,
particle sheet batching, async light propagation, adaptive render distance and
GL-state filtering. None of those run. `state.md` G-3/G-4/G-8 found fragments of
this problem; it is materially larger than recorded.

### The three-word thesis for v0.2

> **Wire it. Verify it. Then sell it.**

1. **Wire it** — connect the 11 dead subsystems to real Minecraft call sites (§3).
2. **Verify it** — build-time mixin-target audit + benchmark harness, so a dead
   hook can never ship again silently (§4, §6).
3. **Sell it** — rewrite the README/MODRINTH from *measured* numbers, not
   aspirational ones (§5).

**Estimated effort:** §3 ≈ 2–3 weeks, §4 ≈ 3–4 days, §5 ≈ 2 days,
§6 ≈ 2–3 weeks, §7 ≈ 3–4 weeks. A focused solo contributor reaches v0.2 in
roughly **6–8 weeks**.

---

## 1. Scope of v0.2

### 1.1 Goals

* **G-A** — Every `Must` requirement in `spec.md` §2 is either **live in-game** or
  explicitly and visibly marked as *not implemented*. No silent gaps.
* **G-B** — A dead mixin target **fails the build**, not the runtime.
* **G-C** — The README's performance table is **reproducible from a checked-in
  harness**.
* **G-D** — The settings console exposes **only** switches that change behaviour.

### 1.2 Non-goals for v0.2

* No shader packs, no visual overhauls, no gameplay modification (`spec.md` §1.4).
* No server-side work.
* No new external dependency on any version node (AGENTS.md golden rule 6).
* No new Minecraft version target until v0.2 ships (1.21.11 was the last one
  added; adding a sixth node mid-cycle is a tax, not a feature).

---

## 2. Workstream overview

| # | Workstream | Priority | Effort | Depends on |
| :--- | :--- | :--- | :--- | :--- |
| **A** | Wire the dead subsystems | **P0** | 2–3 wk | — |
| **B** | Build-time mixin-target audit | **P0** | 3–4 d | — |
| **C** | Documentation honesty pass | **P0** | 2 d | A, B |
| **D** | Safety & compatibility hardening | **P0/P1** | 1 wk | — |
| **E** | Benchmark & regression harness | **P1** | 2–3 wk | A, B |
| **F** | New feature depth | **P1/P2** | 3–4 wk | A, E |
| **G** | Process, CI, release engineering | **P1/P2** | 1 wk | B |

---

## 3. Workstream A — Wire the dead subsystems (P0)

> **Definition of done for this workstream:** every subsystem listed in §0.1 either
> has a live call site in every node it claims to support, **or** is removed /
> renamed / hidden from the console. The startup log prints a one-line table of
> *subsystems actually active this session*.

### A-1. Frame pacer — make `ThoriumFramePacer` real ⚠️ *highest user-visible win*

**Problem.** `paceFrame()` is never called. `setTargetFps()` has no caller, so
`targetFrameTimeNanos` is permanently `0` — even if it were called, it would
immediately return to uncapped mode.

**Plan.**

1. Add `ThoriumFramePacer.setTargetFps(int)` callers:
   * A new **Target FPS** control in the settings console (General tab) with
     `Uncapped / 30 / 60 / 120 / 144 / 240`.
   * Persist as a new `ThoriumConfig` key `frame.pacer.targetFps`
     (integer, `0` = uncapped) — remember the **three-place wiring rule**
     (key constant + `captureFromSubsystems()` + `applyToSubsystems()`,
     AGENTS.md §10.4).
2. Call `ThoriumFramePacer.paceFrame()` from a **frame boundary** hook:
   * Modern: `ModernHudBatchingMixin` already runs `ThoriumFrametimeTracker.recordFrame()`
     at HUD extraction. Add the pacer call there — but **only** if that hook is
     guaranteed to execute exactly once per rendered frame. Verify this first:
     `Hud.extractRenderState` is not called on every frame in 26.x (it is a
     *render-state extraction* stage, skipped when the HUD is hidden by
     `GuiGraphicsExtractor` conditions). **Do not assume.**
   * Prefer a dedicated, unambiguous hook:
     `net.minecraft.client.Minecraft#runTick` (modern) / `Minecraft#run` (legacy)
     — or `LevelRenderer#renderLevel` if a per-render-frame hook is required.
   * **Action required:** locate the real per-frame method with
     `javap -p <minecraft-client.jar> | grep -i "runTick\|renderLevel"` per node
     and record the choice in `state.md`.
3. Guard the pacer: skip when a screen is open, when the game is unfocused
   (unless the user opted in), and when `ThoriumSafety.isSafeMode()`.
4. `paceFrame()` currently does `nextFrameDeadlineNanos = System.nanoTime() + target`,
   which drifts and never recovers a missed deadline. Replace with an absolute
   deadline derived from a monotonic anchor, with a **bounded catch-up** (skip
   forward if more than N deadlines were missed — never spiral).

**Acceptance.** With target 60 FPS, measured frametime deviation over 5 000
frames stays under ±1.2 ms median, and the HUD reports a visible drop in
`avg`/`max` compared to the pacer disabled.

**Tests.** `ThoriumFramePacerTest` — deadline advance, uncapped mode, missed
deadline recovery, zero/negative target.

---

### A-2. Adaptive render distance — connect `AdaptiveDistanceController`

**Problem.** `checkAndAdjust()` is never called and `distanceConsumer` is never
registered, so REQ-ADAPT-1/2/3 are all inert.

**Plan.**

1. Register the consumer at client init:
   `AdaptiveDistanceController.getInstance().setDistanceConsumer(d -> <update GameOptions.renderDistance>)`.
   The GameOptions access is **version-specific** → the consumer body belongs in
   `versions/<mc>/src/.../gui/` or a small per-version adapter class. Do **not**
   import `GameOptions` into `src/main/java/` (AGENTS.md golden rule 3).
2. Call `checkAndAdjust(now)` from the same frame boundary introduced in A-1,
   **not** from `onFrameBegin` (which is called per *entity* in the modern mixin —
   see `ModernEntityCullingMixin`, which calls `AsyncOcclusionCuller.onFrameBegin(...)`
   inside `shouldRender`. That is a **per-entity** call, not a per-frame call —
   this is a latent correctness bug worth fixing in A-3).
3. Respect a user-set render distance: if the player manually changes the render
   distance in vanilla Options, the adaptive controller must **yield** rather than
   fight them. Add a `userOverride` flag with a re-arm timeout (e.g. 60 s).

**Acceptance.** Render distance visibly steps down after sustained sub-60 FPS and
back up when headroom returns; the HUD shows the current adaptive distance.

**Tests.** Extend `AdaptiveDistanceTest` with: consumer is invoked, cooldown is
respected, user override yields, clamps hold.

---

### A-3. Fix the frame-boundary architecture ⚠️ *highest bug density*

**Problem.** There is no single, authoritative per-frame context publisher. The
per-node reality is in §0.1b:

* **1.21.1** — `LegacyLevelRendererFrameMixin` injects at `LevelRenderer#renderLevel`
  HEAD. Correct.
* **1.21.11** — the equivalent mixin **does not exist and is not registered**.
  `onFrameBegin` never runs, so the camera context is never published and most
  of the culling engine silently degrades to "distance only".
* **26.x** — the only publisher is `ModernEntityCullingMixin`, which runs
  **once per entity**. Everything downstream of `onFrameBegin` is therefore
  executed N times per frame with N = entities considered.

The consequences on 26.x are concrete, not theoretical:

* `ThoriumBvhCuller.onFrameBegin()` clears the entire cluster-occlusion cache on
  **every entity test** → the BVH cache never survives a single entity, so the
  O(1) cluster skip never hits (REQ-CULL-5 is effectively dead).
* The dynamic distance scale steps by ±0.05 / ±0.02 **per entity call**
  (REQ-CULL-6) → with 200+ entities visible it collapses to the 0.35 floor
  within one frame, so every entity is culled at 35 % of the intended distance.
* `AdaptiveDistanceController.onFrame(deltaNanos)` receives a **per-entity**
  delta (microseconds), so the EMA believes FPS is in the millions → the
  controller would step render distance **up** every time (once A-2 wires it).
* Five volatile fields are written per entity — cache-line ping-pong between
  the render thread and the culler daemon.

`AsyncOcclusionCuller.onFrameBegin(camX, camY, camZ, frustum, level)`
is invoked from `ModernEntityCullingMixin` — i.e.
**once per entity considered for rendering**, not once per frame. It does the
following on *every* such call:

* writes 5 volatile fields (cache-line ping-pong across the render thread and the
  culler daemon),
* `OcclusionGrid.onCameraUpdate(...)` and `FrustumCache.update(frustum)`,
* `ThoriumBvhCuller.onFrameBegin()` → **clears the whole BVH cluster cache**,
* recomputes the delta-time EMA → **`AdaptiveDistanceController.onFrame(deltaNanos)`
  is fed a per-entity delta, not a per-frame delta.** With 300 entities visible
  the EMA is updated 300× per frame, each sample being microseconds. The adaptive
  controller therefore always believes FPS is in the millions and steps distance
  **up** aggressively.
* applies the dynamic distance scale ±0.05 / ±0.02 per call → the scale can
  collapse to 0.35 within a single frame.

This is a **real, shipping bug** that directly undermines REQ-CULL-6 and
REQ-ADAPT-1.

**Plan.**

1. Introduce a single authoritative per-frame entry point in shared source:
   `dev.hyperionfps.frame.ThoriumFrameContext.beginFrame(camX, camY, camZ, frustum, level, deltaNanos)`.
2. **Add a `ModernLevelRendererFrameMixin`** for 26.x that injects at the modern
   equivalent of `LevelRenderer#renderLevel` HEAD. Verify the target method
   name per node with `javap -p` — the 26.x render pipeline was reworked
   (`renderLevel` may be split into `renderLevel` / `renderEntities` /
   `renderChunkLayer`). Register it in `versions/26.{1,2,3}/build.gradle.kts`
   under `"mixin_modern"`.
3. **Add a `LegacyLevelRendererFrameMixin` for 1.21.11** and register it in
   `versions/1.21.11/build.gradle.kts` under `"mixin_legacy"`. The 1.21.11
   `LevelRenderer#renderLevel` signature differs from 1.21.1's — check it with
   `javap` before copying.
4. **Remove the `onFrameBegin` call from `ModernEntityCullingMixin`** and replace
   it with a read-only
   `AsyncOcclusionCuller.shouldCullEntity(entity, camX, camY, camZ)` that reads
   the camera state published by the frame context.
5. Move the dynamic-distance-scale update into the frame context, driven by a
   real **frame** delta, with a per-frame change cap (e.g. ≤ ±0.05 per frame).
6. Rate-limit `ThoriumBvhCuller.onFrameBegin()` to once per frame.

**Acceptance.** The HUD's dynamic-distance-scale value changes at most once per
frame and converges monotonically; `AdaptiveDistanceController.getSmoothedFps()`
reports a plausible value (30–500) in a normal scene; `getClusterCulledCount()`
is non-zero on 26.x; frustum culling is demonstrably active on 1.21.11.

**Tests.** New `ThoriumFrameContextTest` — scale converges to 1.0 on fast
frames, collapses on slow frames, is rate-limited per frame, and publishes the
camera state exactly once per call.

---

### A-3b. Port `LegacyFogMixin` to 1.21.11 (P0, tiny)

**Problem.** `LegacyFogMixin` is registered on 1.21.1 but **not on 1.21.11**
(compare the two `mixin_legacy` lists in `versions/*/build.gradle.kts`). Extreme
FPS Mode's fog suppression therefore does nothing on 1.21.11 while the console
advertises it on all versions.

**Plan.** Copy `LegacyFogMixin` into `versions/1.21.11/.../mixin/legacy/`,
verify the `setupFog` target signature via `javap -p` for 1.21.11, and register
it in `versions/1.21.11/build.gradle.kts`.

**Acceptance.** Toggling Extreme FPS Mode on 1.21.11 visibly removes the fog.

---

### A-4. Chunk upload pacer on the modern nodes ⚠️ *direct G1 enabler*

**Problem.** `ThoriumChunkUploadPacer.runBudgetedUploads(queue)` is called from
`LegacyChunkUploadBudgetMixin` (1.21.1 and 1.21.11 only). There is **no**
`ModernChunkUploadBudgetMixin`. REQ-CHUNK-1/2 — the "zero chunk hitching" claim
and goal **G1** — are dead on 26.1, 26.2 and 26.3.

**Plan.**

1. Identify the modern chunk-mesh upload call site per node. The 26.x pipeline
   changed substantially; the upload queue is no longer a `Queue<Runnable>`.
   Use `javap -p` + the `net/` decompile reference to locate the modern equivalent.
2. Write `versions/26.{1,2,3}/src/main/java/dev/hyperionfps/mixin/modern/ModernChunkUploadBudgetMixin.java`
   that funnels the modern upload queue through
   `ThoriumChunkUploadPacer.runBudgetedUploads(...)`.
3. If the modern pipeline's shape makes the `Queue<Runnable>` pacer inapplicable,
   **generalise the pacer** to a `ThoriumUploadBudget` that accepts an abstract
   `Supplier<Runnable>`-style source, keeping the dual budget
   (≤ 2.5 ms **and** ≤ 8 sections) intact.
4. Register the mixin in `versions/26.{1,2,3}/build.gradle.kts` under
   `"mixin_modern"` (AGENTS.md §4, step 2).
5. **Verify the target exists on every 26.x node before registering** — this is
   exactly the G-2 failure mode (see Workstream B).

**Acceptance.** Crossing a chunk border produces no frame above the 99th
percentile; the HUD's "chunk uploads yielded" counter is non-zero during normal
play on 26.x.

---

### A-5. Particle ticking on the modern nodes (finishes G-2)

**Problem.** G-2: `tickParticleList` exists on 1.21.1 only. 1.21.11+ uses the
`ParticleGroup<?>` API. `ThoriumParticleSimulator.tickParticles(...)` therefore
never runs on 4/5 nodes.

**Plan.**

1. `javap -p` each of the 1.21.11 / 26.1 / 26.2 / 26.3 client jars and map the
   `ParticleGroup` API surface (`createParticleGroup`, `ParticleLimit`, group tick
   entry point).
2. Write `ModernParticleTickMixin` per modern node that intercepts the group tick
   and hands each group's particle collection to `ThoriumParticleSimulator`.
3. Keep the legacy injection for 1.21.1 by **moving** it out of `mixin/core/`
   into `mixin/legacy/` (option (b) in `state.md` G-2) so the dead target is
   never attempted on modern nodes.
4. Resolve **G-3** at the same time: `ThoriumParticleSimulator` constructs a
   `ForkJoinPool` it never uses. Either
   * (a) implement genuine parallel ticking with a correct concurrent-removal
     strategy (partition the collection, collect dead particles into a
     thread-local list, remove on the owning thread), or
   * (b) rename the class/method to `ThoriumParticleSequentialTicker` and delete
     the unused pool.
   * **Recommendation:** (b) now, (a) later as a measured P2 feature. A pool that
     is never submitted to is a trust problem in the source; the honest rename is
     cheaper and strictly correct.
5. If the modern group API makes (2) impractical, fall back to (b)-style
   *culling-only* on modern nodes and document that particle ticking is
   vanilla there — but say so in the README.

**Acceptance.** `PARTICLES_SIMULATED_ASYNC` is non-zero on all five nodes, or the
README explicitly states that particle ticking is vanilla on 1.21.11+.

---

### A-6. Section culling — connect `SectionCullingEngine`

**Problem.** `shouldRenderSection(AABB)` is never called. REQ-CULL-1 is inert.

**Plan.** Inject into the chunk-section render submission path
(`LevelRenderer#renderSection` / `SectionRenderDispatcher` /
`ChunkRenderDispatcher` — names differ per era). Call
`SectionCullingEngine.shouldRenderSection(box)` and cancel on `false`.

**Risk.** Section culling that hides visible sections is the single most
visible failure mode for this mod (R-4 in `state.md` §5). Mitigation: keep the
existing 256-block distance cap, never cull the camera's own section
(REQ-CULL-7), and ship a "Section culling" toggle that defaults **off** until it
is validated on all five nodes.

**Acceptance.** `SECTIONS_CULLED` is non-zero in a normal scene on all nodes;
no visible missing terrain in a 10-minute play test per node.

---

### A-7. GPU entity instancing — connect `EntityInstancer`

**Problem.** `EntityInstancer` / `EntityGroupManager` / `EntityGroup` /
`EntityInstanceData` are fully implemented and unit-tested, but **never called**.
REQ-CHUNK-7 is inert.

**Plan.**

1. Read `EntityInstancer` and confirm the intended data path (32-byte stride,
   4096 instances per batch, per-entity model/texture/transform data).
2. Locate the entity render submission point per era and add an era-specific mixin
   that:
   * groups entities by (model, texture, render state) into `EntityGroup`s,
   * fills `EntityInstanceData` from the per-entity transform,
   * submits one instanced draw per group instead of N draws.
3. **Honesty gate:** if the modern (26.x) render-state pipeline makes instancing
   impractical, scope the feature to the legacy era, **hide the toggle on nodes
   where it does nothing**, and say so in the README. A toggle that lies is worse
   than no toggle (REQ-UI-2: *zero fake toggles*).

**Acceptance.** Draw-call count for a mob cluster drops measurably (captured by
the Workstream E harness); visually identical to vanilla.

---

### A-8. Particle sheet batching — connect `ParticleBatchEngine`

**Problem.** `ParticleBatchEngine` (5 sheets × 4096 quads, `ParticleBatchPool`,
`ParticleQuad`) is never called. REQ-CHUNK-6 is inert.

**Plan.** Inject into the particle render submission path per era; acquire quads
from `ParticleBatchPool`, fill them, submit per sheet, flush at end of frame.
Reuse the A-5 findings about the modern particle API — both features touch the
same subsystem and should be designed together.

**Acceptance.** Particle draw calls drop in a dense scene; no visual regression.

---

### A-9. Async light propagation — connect `AsyncLightEngine`

**Problem.** `AsyncLightEngine` is initialised (a daemon worker is started) but
`enqueue(...)` is never called, so the worker **idles forever**. REQ-ADAPT-4 is
inert. The startup log even claims *"Async Light Propagation Engine online: daemon
worker active"* — a second log lie next to the `fabric-api=integrated` one.

**Plan.**

1. Locate the client light-update path per era (`LevelRenderer#setLightDirty` /
   `LightTexture` / `ChunkRenderDispatcher#setLightDirty`).
2. On a light-dirty notification, `enqueue(posKey, lightType, level)` instead of
   doing the work inline.
3. **Critical caveat:** Minecraft's light engine maintains strongly-consistent
   state. Moving light updates off-thread is the highest-risk change in this
   roadmap — incorrect light causes visible corruption and can desync chunk
   snapshots. **Recommendation:** ship this as an *opt-in* toggle, default
   **off**, and only enable it if the per-era implementation can be made
   provably equivalent. If it cannot, **remove the subsystem** and correct the
   startup log (see D-4).

**Acceptance.** Either light updates are off-thread with zero visible corruption
across all five nodes, or the subsystem is removed and the log is honest.

---

### A-10. GL state filtering — connect `GlStateTracker`

**Problem.** `GlStateTracker.shouldBindTexture/shouldUseProgram` are never called.
REQ-CHUNK-9 is inert. The HUD's *"GL Skips"* number is always `0` — a live
example of a diagnostic that can never be non-zero.

**Plan.** Inject into the render-state / `RenderSystem` texture-bind and
shader-bind call sites per era; consult `GlStateTracker` before the redundant
bind. **Bypass entirely when a shader pack is present** (D-3).

**Acceptance.** `GL_SKIPS` is non-zero in a normal GUI-heavy scene.

---

### A-11. HUD draw merging — connect `ThoriumImmediateBatcher.shouldBatchDraw`

**Problem.** `flushActiveBatch()` is called, but `shouldBatchDraw(...)` — the
decision that actually merges quads — is never called. REQ-CHUNK-8 is inert and
the *"Immediate HUD Batching"* console toggle changes nothing.

**Plan.** Inject into the 2D draw path (`GuiGraphicsExtractor` / `GuiGraphics`
`blit`/`fill`/`drawString` — names differ per era) and route through
`shouldBatchDraw(textureId)`; flush when the texture/shader changes or at frame
end.

**Acceptance.** `HUD_BATCHER.getDrawCallsSaved()` is non-zero when the HUD is on.

---

### A-12. Real input pacing — implement or rename `ThoriumInputPacer`

**Problem.** G-8. `ThoriumInputPacer` is a counter with no timing logic, no
budget, and no caller. The console shows *"Reflex Low-Latency Input — ON"* and
*"NVIDIA Reflex-style input synchronization"* — both claims are false.

**Plan (pick one, be honest):**

* **(a) Implement it.** Mirror the chunk upload pacer: a small per-frame input
  budget (e.g. ≤ 0.5 ms of input sampling per frame), injected at the mouse-turn
  sampling site per era. Publish the *actual* sampled delta to the camera
  rotation instead of the one-frame-stale value. This is the real Reflex
  behaviour and it is achievable client-side.
* **(b) Rename + hide.** Make it telemetry-only, rename the class and the console
  label to *"Input Turn Counter"*, and stop claiming Reflex.

**Recommendation:** (a) for Nuclear Max preset only, with (b) as the fallback for
nodes where the input API cannot be hooked safely.

---

### A-13. Subsystem activation report — make the log tell the truth

**Problem.** The startup log reports a *static* list of subsystem states
(`"culling=active (sections+entities)"`, `"instancing=ready"`,
`"particle-batching=ready"`, `"async-lighting=active"`) regardless of what is
actually enabled, wired, or present on this node.

**Plan.** Build a `ThoriumSubsystemReport` in `src/main/java/` that each
subsystem contributes a live status line to (enabled? wired? node-supported?
metric value?). Print a single aligned table at client init and expose it on
`F3+H` / a new debug screen. This single change would have caught every gap in
§0.1 immediately.

**Acceptance.** The report distinguishes `ACTIVE` / `DISABLED` / `UNWIRED` /
`UNSUPPORTED-ON-NODE`.

---

## 4. Workstream B — Build-time mixin-target audit (P0)

> G-12. This is the **root cause** that let G-2 ship undetected. Without it, every
> new mixin added in Workstream A can silently die.

### B-1. Promote the Loom remapper warning to a hard failure

For the remap nodes (`1.21.1`, `1.21.11`), Loom prints
`Cannot remap <method> because it does not exist`. Capture that output and fail
the build.

**Implementation sketch** (per remap node's `build.gradle.kts`):

```kotlin
tasks.named("remapJar") {
    doLast {
        val log = logger.lifecycle  // or capture the remapper's output
        // Fail when the remapper reported an unresolvable target.
    }
}
```

Prefer a deterministic approach: run `javap` over the Minecraft jar and assert
every declared mixin target exists, rather than scraping log text.

### B-2. Mixin target audit task (the real fix)

Add a Gradle task `auditMixinTargets` that, **per node**:

1. Reads the node's expanded `thorium.mixins.json`.
2. Reads every mixin class and extracts each `@Inject`/`@Redirect`/`@ModifyArg`/
   `@WrapOperation` `method` value.
3. Resolves the target class (from the mixin's `@Mixin(...)` annotation) and
   checks the target method exists in the node's Minecraft jar via ASM or
   `javap -p`.
4. **Fails the build** with a per-node report of missing targets.

**Making it fast and dependency-free.** `javap` from the JDK is available and
needs no new dependency. A rough approach:

```bash
javap -p -classpath <minecraft-client.jar> net.minecraft.client.particle.ParticleEngine \
  | grep -q "tickParticleList" || echo "MISSING TARGET"
```

A Gradle `doLast` that shells out to `javap` per (mixin, node) is acceptable at
this scale (~40 mixins × 5 nodes). A pure-ASM implementation is nicer but adds
an ASM dependency — check the legacy nodes first (AGENTS.md golden rule 6).

**Where to hook it.** `check` depends on `auditMixinTargets`; `buildAll` therefore
inherits it.

### B-3. Relax `require = 0` deliberately, per injection

Once the audit exists, injections whose target is **proven** to exist on all
five nodes can drop `require = 0` and use the default `require = 1`. This turns
a future Minecraft update's breaking change into a **loud crash at startup**
instead of a silent no-op — acceptable for genuinely stable targets.

Keep `require = 0` for anything version-fragile.

### B-4. Runtime self-check

At client init, compare the mixin router's `applied=` count against the
`eligible=` count. A mismatch means a mixin failed to apply. Print a **warning
with the name of each non-applied mixin** (the plugin already tracks
`ROUTED_IN`/`ROUTED_OUT`/`APPLIED`; add a per-mixin applied set).

**Acceptance.** Deliberately pointing a mixin at a non-existent method fails
`./gradlew buildAll` on **all five** nodes.

---

## 5. Workstream C — Documentation honesty pass (P0)

> G-6. Cheap to do, high impact on trust.

### C-1. Fix the factual errors

| Document | Current claim | Correct value |
| :--- | :--- | :--- |
| `README.md` | "117-sample live history graph" | 128 (`ThoriumFrametimeTracker.SAMPLE_COUNT`) |
| GUI tooltip | "126-sample micro-stutter graph" | 128 |
| `README.md`, `MODRINTH.md` | 4 versions | **5** — add 1.21.11 |
| `README.md` | F6/F7/F8 on all versions | 1.21.1 now has F6/F7/F8 (G-5 fixed) |
| `MODRINTH.md` | "Supported: 1.21.1, 26.1, 26.2, 26.3" | add 1.21.11 |

### C-2. Rewrite the benchmark table — or delete it

The README cites **320+ FPS, 7.4 ms 1 % low, 120 MB/s → 28 MB/s allocation**
with no supporting artifact in the repository. Either:

* **Preferred:** generate the numbers from the Workstream E harness and publish
  the raw CSV alongside the table (methodology: hardware, MC version, scene,
  seed, warm-up duration, sample count, mod list).
* **Acceptable:** delete the numbers and replace with a *methodology* section
  that tells users how to measure it themselves with the harness.

Until the harness exists, mark the table **"reported by users, not independently
verified"**.

### C-3. Make the feature list match reality

After Workstream A, regenerate the feature list **from the subsystem report**
(A-13) rather than from the source tree. A feature is "supported" only if it is
live on the node in question.

### C-4. Add a per-node feature matrix

A table in the README mapping each feature to the five nodes, generated by hand
from the subsystem report:

| Feature | 1.21.1 | 1.21.11 | 26.1 | 26.2 | 26.3 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Entity/particle distance culling | ✅ | ✅ | ✅ | ✅ | ✅ |
| Frustum + occlusion culling | ✅ | ⬜ (A-3) | ⬜ (A-3) | ⬜ | ⬜ |
| Chunk upload pacer | ✅ | ✅ | ⬜ (A-4) | ⬜ | ⬜ |
| Particle ticking | ✅ | ⬜ (A-5) | ⬜ | ⬜ | ⬜ |
| Extreme-mode fog suppression | ✅ | ⬜ (A-3b) | ✅ | ✅ | ✅ |
| … | | | | | |

---

## 6. Workstream D — Safety & compatibility hardening (P0/P1)

### D-1. Sodium / Embeddium / Lithium coexistence ⚠️ *high user impact*

`spec.md` §1.4 says Thorium is *"compatible-by-coexistence, not a superset"* —
but nothing detects a competing renderer. Sodium does its own chunk, entity and
particle culling; running both means **double culling, contradictory visibility
decisions, and doubled raycast cost**.

**Plan.** Detect the presence of `sodium`, `embeddium`, `rubidium`, `magnesium`,
`lithium` and `oclite` via `FabricLoader.getModContainer(...)` at client init.
When one is present, **auto-disable the overlapping Thorium subsystems**
(entity/block-entity/particle culling, section culling, BVH) and log exactly what
was disabled and why. Leave frame pacing, telemetry, sound governor and texture
throttling enabled.

Add a console toggle: *"Auto-yield to Sodium"* (default **on**).

**Why this matters:** it prevents the most common class of "Thorium broke my
game" report, and it is honest.

### D-2. Shader-pack detection → disable GL-state filtering and fast math

The `Mth` fast-math mixin **globally replaces** `Mth.sin`, `Mth.cos`, `Mth.atan2`,
`Mth.invSqrt` on **all five nodes**. That is risky:

* Shader packs and some resource packs depend on vanilla math precision.
* `Mth` is used in client-side physics prediction — approximations can produce
  visible jitter or desync-like rubber-banding.
* `atan2`/`invSqrt` are truncated to `float` precision by the bridge
  (`math.atan2((float) y, (float) x)`) — a real precision loss for a
  `double`-returning method.

**Plan.**

1. Detect a loaded shader pack (`oculus`, `iris`, `optifine` shader support).
2. When detected: disable the fast-math overrides **and** the GL-state filter.
3. **Default the fast-math toggle OFF for the Balanced preset** and ON only for
   Nuclear Max, with a clear console warning. Today it is unconditionally on.
4. Replace the `double`→`float` truncation in `atan2`/`invSqrt` with a
   double-precision path, or restrict the overrides to the `float` overloads only.

### D-3. Render-thread stall watchdog

Add a daemon watchdog thread that samples the render thread's last-progress
timestamp. If the render thread makes no progress for > 500 ms, log a warning
naming the last-enabled subsystem, and (optionally, configurable) disable the
newest-enabled subsystem as a self-heal measure.

This turns a silent freeze into a diagnosable event — directly addressing the
Android crash class of report (`state.md` §4.1).

### D-4. Audit every startup log claim

`state.md` G-9 found the `fabric-api=integrated` lie (partly fixed). Audit the
rest:

* `"Async Light Propagation Engine online: daemon worker active"` — the worker
  is running but **never receives work** (A-9).
* `"culling=active (sections+entities)"` — section culling is not wired (A-6).
* `"instancing=ready"` / `"particle-batching=ready"` — neither is wired (A-7/A-8).
* `"fastmath=enabled"` — unconditionally true, and unsafe under shader packs (D-2).

Replace all of these with values read from the subsystem report (A-13).

### D-5. `DefaultEntityBridge` honesty (G-10)

`DefaultVersionBridge.DefaultEntityBridge.shouldCullEntity/shouldCullBlockEntity/shouldCullParticle`
always return `false`. Either delegate to the real engines or document them as
conservative no-ops. Small, cheap, removes a trap for the next contributor.

### D-6. `FrustumCache` per-call allocation (G-4)

`FrustumCache.isVisible(minX, minY, minZ, maxX, maxY, maxZ)` allocates
`new AABB(...)` on every call — per block entity, on the render thread. This
directly violates NFR-1. Replace with a primitive plane-by-plane test against
the cached frustum's six planes, or reuse a single mutable `AABB`.

---

## 7. Workstream E — Benchmark & regression harness (P1)

> G-7. *"The project's entire value proposition is performance, and there is no way
> to verify a change helps rather than hurts. This is the biggest **process** gap."*

### E-1. In-game capture mode

`-Dhyperionfps.benchmark=true` enables a capture mode that:

* writes frametime **percentiles** (50 / 95 / 99 / 99.9) over the session,
* writes **allocation rate** (bytes/frame, sampled via
  `ThreadMXBean.getThreadAllocatedBytes` on the render thread),
* writes all subsystem counters (sections culled, entities culled, particles
  culled, GL skips, draw calls saved, chunk uploads yielded, stutter count),
* writes a **JSON + CSV** file to `<game-dir>/thorium-bench/` on exit.

**Design constraints:** the capture path must not allocate per frame. Use
pre-allocated `float[]` / `long[]` arrays and write once at exit.

**Test seam:** the percentile computation must be a pure function over a
`float[]` so it can be unit-tested without Minecraft.

### E-2. A/B comparison harness

A script (`tools/thorium-bench.ps1` or a Gradle task) that:

1. Launches the client with the harness enabled on a **fixed seed** and a
   **fixed camera path** (a scripted fly-through of a saved world).
2. Runs N sessions: vanilla, Thorium-Balanced, Thorium-Nuclear.
3. Emits a comparison table (mean, p50, p95, p99, p99.9, allocation rate).

Even a crude version (a fixed world, a fixed camera, 3 minutes per session)
turns the README table from a claim into a measurement.

### E-3. CI

Add a GitHub Actions workflow:

* `build` matrix over the five nodes (`./gradlew buildAll`),
* `test` on all five nodes (`./gradlew test`),
* `auditMixinTargets` (Workstream B) as a required check,
* a **publish** job uploading `build/dist/*.jar` to a GitHub release on tag.

**Note:** the legacy nodes need `fabric-loom-remap` and Java 21; the modern nodes
need Java 25. Use a matrix with per-node `java-version`.

### E-4. Allocation-rate regression gate (P2)

Once E-1 exists, add a test that runs a synthetic frame loop and asserts the
allocation rate is below a threshold. This turns NFR-1 from a convention into an
enforced invariant.

---

## 8. Workstream F — New feature depth (P1/P2)

> Everything here assumes Workstream A has landed — otherwise these features add
> more dead code.

### F-1. GPU-side frame timing via timer queries (P2, high value)

CPU frametime ≠ frametime. `GL_TIMESTAMP` / `GL_ARB_timer_query` gives true GPU
time. Expose **GPU time**, **CPU time** and **their delta** in the HUD. This
turns Thorium's HUD into a genuine diagnostic tool and makes "is this CPU- or
GPU-bound?" answerable at a glance — the single most useful thing for the
target audience (competitive players, low-spec hardware owners).

Requires a `VersionBridge.RenderBridge` extension and an era-specific mixin per
GL/GLES binding. Guard with a capability probe (`glGetString(GL_EXTENSIONS)`).

### F-2. Hardware-aware auto-preset (P1)

`spec.md` §4.2 defines three presets but the user must pick one manually. Add an
**auto-detect** that reads:

* GPU vendor/renderer string (`glGetString(GL_RENDERER)`),
* VRAM size,
* CPU core count and clock,
* total RAM and the JVM's `-Xmx`,
* the current screen resolution and refresh rate.

Then recommend a preset, showing the reasoning in the console. Also expose it as
a config key `preset.autoDetect`.

### F-3. Per-subsystem A/B from the HUD (P2)

Allow toggling individual subsystems with a hotkey while playing, so a user can
find the exact cause of a regression on *their* hardware without editing
config files. Persist nothing; session-only.

### F-4. Persisted frametime history / session report (P2)

Write a rolling frametime history to disk at shutdown and render a **heatmap**
(time-of-day × frametime) on the settings screen. Gives the user a visual
"where does my jank come from?" tool, and produces the artefact needed to file a
useful bug report.

### F-5. Memory-pressure auto-purge (P1, cheap)

`ThoriumMemoryOptimizer.purgeMemoryNow()` exists but is manual. Add a
**watchdog**: when heap usage exceeds a configurable percentage (e.g. 85 %) and
is not falling, trigger the purge automatically. `LauncherEnvironment` already
detects the GC and heap settings — use them to pick sensible thresholds per GC.

This is a genuine G2 ("eliminate GC-induced stutter") lever that is currently
unused.

### F-6. Async chunk decode / mesh build (P2, ambitious)

Chunk *decode* (NBT → block states) and *mesh build* are the two biggest client
CPU spikes. Minecraft already has a chunk-building thread pool; Thorium could
extend the budget pacer to cover the *decode* phase as well. This is a large
change with real crash risk — keep it as a research spike until Workstream A is
solid.

### F-7. Better entity LOD — freeze distant poses (P2)

`ThoriumEntityLodThrottler` currently throttles animation *ticks*. Extend it to
freeze the *pose* of entities beyond the LOD tier (skip pose extraction), which
removes the render-thread cost of matrix building for distant mobs.

### F-8. Light-engine diagnostics (P2)

`AsyncLightEngine` currently only has a queue. Add a **light-update budget
counter** and a HUD readout of pending light jobs, so users can see whether
light is the bottleneck — even if A-9 is not shipped, the diagnostic alone is
valuable and honest.

---

## 9. Workstream G — Process, CI, release engineering (P1/P2)

### G-1. Fix the tag/release gap (G-11)

`git tag` is empty. Tag `0.1.1` at the current commit, adopt
`v<major>.<minor>.<patch>` tags, and add a release checklist derived from
`spec.md` §7.

### G-2. Adopt `spec.md` §7 as a release gate

Automate every item in `spec.md` §7:

1. `./gradlew test` green on all five nodes.
2. `./gradlew buildAll` → five jars in `build/dist/`.
3. `fabric.mod.json` metadata resolves per node.
4. The mixin router log shows the expected `eligible/skipped/applied` counts.
5. README + MODRINTH list the correct matrix and hotkeys.
6. `state.md` reflects the shipped state.

Items 1–4 are scriptable; 5–6 are human checks.

### G-3. Extend `state.md` with a requirement-coverage matrix

`state.md` is a good status board but does not map requirements → status. Add a
matrix derived from `spec.md` §2 with a per-node column:

```
REQ-FRAME-4  | live: 26.x | live: 1.21.x | test: ThoriumFramePacerTest
```

This is what makes it possible to see at a glance that REQ-CHUNK-6 is dead on
four nodes — a fact currently buried in a prose paragraph.

### G-4. `ThoriumFabricHooks` parity (G-9)

`ThoriumFabricHooks` exists **only** in `versions/1.21.1/`. Port the useful
parts (chunk-unload cache eviction, dimension-change sweep, key bindings) to all
nodes — or delete the reflective hook entirely. The 1.21.11 node already has
`ThoriumModernHudRenderer`, so the HUD is covered; it is the **lifecycle events**
(chunk unload eviction, world-change sweep) that are missing on 4/5 nodes.

`AsyncOcclusionCuller.evictChunk(x, z)` exists and is never called on 4/5
nodes → the block-entity occlusion cache grows unbounded there.

### G-5. `LegacyVignetteMixin` — port or drop (P2)

Exists only in 1.21.1, not registered anywhere meaningful. Either port to the
modern era or delete.

---

## 10. Milestone plan

### Milestone 1 — "Make it real" (weeks 1–3) → v0.2.0-alpha

* A-1 frame pacer · A-3 frame-context fix (incl. the 26.x and 1.21.11 hooks) ·
  A-3b `LegacyFogMixin` port · A-13 subsystem report
* A-4 modern chunk upload pacer · A-5 modern particle ticking
* B-1/B-2 mixin target audit · B-4 runtime self-check
* D-2 fast-math safety · D-4 log honesty · D-5/D-6 small honesty fixes
* C-1 factual doc corrections

**Gate.** All five nodes build; `auditMixinTargets` green; the subsystem report
shows no `UNWIRED` entry for any feature the console advertises.

### Milestone 2 — "Make it measurable" (weeks 4–5) → v0.2.0-beta

* E-1 benchmark capture · E-2 A/B harness · E-3 CI
* D-1 Sodium coexistence · D-3 stall watchdog
* A-6 section culling (toggle default off) · A-9 async light (opt-in) ·
  A-10 GL state filter
* C-2 benchmark table rewrite · C-3/C-4 feature matrix

**Gate.** The README table is generated from a harness run; the CI matrix is
green; the per-node feature matrix matches the subsystem report.

### Milestone 3 — "Make it deep" (weeks 6–8) → v0.2.0

* A-7 entity instancing · A-8 particle batching · A-11 HUD draw merging ·
  A-12 input pacing
* F-2 auto-preset · F-5 memory-pressure auto-purge
* G-1 tags · G-2 release gate · G-3 coverage matrix · G-4 Fabric hooks parity

**Gate.** `spec.md` §2 requirements are all live or explicitly marked; `state.md`
updated; `v0.2.0` tagged and published.

### Post-v0.2 (v0.3) — research backlog

* F-1 GPU timer queries · F-3 HUD A/B · F-4 session heatmap · F-6 async chunk
  decode · F-7 pose freezing · F-8 light diagnostics
* A-9 alternative: make async light provably correct, or retire it
* A-7 alternative: true GPU instancing on the 26.x render-state pipeline

---

## 11. Definition of done (applies to every item)

Per `AGENTS.md` §2:

```bash
./gradlew :26.3:test        # 0 failures
./gradlew test              # green on ALL FIVE nodes
./gradlew buildAll          # 5 jars in build/dist
./gradlew auditMixinTargets # no missing mixin targets on any node
```

Plus, for anything touching shared source (`src/main/java/`):

* No Minecraft-version-specific import in shared source (AGENTS.md rule 3).
* No new dependency without checking the legacy nodes resolve it (rule 6).
* A unit test for every new subsystem or behaviour change (AGENTS.md §9).
* `spec.md` updated if behaviour changed; `state.md` updated if status changed;
  `AGENTS.md` updated if conventions changed (AGENTS.md §11).

---

## 12. Appendix A — Requirement coverage matrix (current state)

Legend: **L** = live on all 5 nodes · **P** = partial (some nodes) · **D** = dead
(implemented but unwired) · **✗** = not implemented.
**As of 2026-10-21** — see §0.0 for what changed.

| REQ | Requirement | Status | Plan |
| :--- | :--- | :--- | :--- |
| FRAME-1 | Zero-alloc frametime ring buffer | **L** | — |
| FRAME-2 | Stutter classification | **L** | — |
| FRAME-3 | Smoothness Stability Index | **L** | — |
| FRAME-4 | Sleep→spin frame pacer | **L** | ✅ A-1 |
| FRAME-5 | Uncapped execution | **L** | ✅ A-1 |
| FRAME-6 | Focus-loss 30 FPS cap override | **L** | — |
| FRAME-7 | Boot-window VSync off | **L** | — |
| FRAME-8 | Implausible delta rejection | **L** | — |
| FRAME-9 | User-set target FPS, persisted | **L** | ✅ A-1 |
| FRAME-10 | One frame context per frame | **L** | ✅ A-3 |
| CULL-1 | Chunk-section distance culling | **D** | A-6 |
| CULL-2 | Entity/BE/particle distance culling | **L** | — |
| CULL-3 | 16³ occlusion grid | **L** | ✅ A-3 |
| CULL-4 | Bounded voxel raycast | **L** | ✅ A-3 |
| CULL-5 | Hierarchical BVH culling | **L** | ✅ A-3 |
| CULL-6 | Dynamic distance scale | **L** | ✅ A-3 |
| CULL-7 | Never cull camera's section | **✗** | A-6 |
| CULL-8 | Memoized BE occlusion + eviction | **P** (evictChunk never called on 4/5) | G-4 |
| MEM-1..4 | Direct pools, off-heap return, janitor, ring pools | **L** | — |
| MEM-5 | `purgeMemoryNow()` | **L** (manual) | F-5 (auto) |
| MEM-6 | Never free a foreign buffer | **L** | — |
| CHUNK-1 | Dual-budget chunk uploads | **P** (legacy only) | A-4 |
| CHUNK-2 | Yield remaining uploads | **P** (legacy only) | A-4 |
| CHUNK-3 | Animated-texture throttle | **L** | — |
| CHUNK-4 | Spatial sound rate limit | **L** | — |
| CHUNK-5 | Entity animation LOD | **L** | ✅ A-3 |
| CHUNK-6 | Particle sheet batching | **D** | A-8 |
| CHUNK-7 | GPU entity instancing | **D** | A-7 |
| CHUNK-8 | HUD draw merging | **D** | A-11 |
| CHUNK-9 | Redundant GL state filter | **D** | A-10 |
| ADAPT-1 | EMA-driven distance governor | **L** | ✅ A-2 |
| ADAPT-2 | Step down/up rules | **L** | ✅ A-2 |
| ADAPT-3 | Clamp FPS/distance | **L** | ✅ A-2 |
| ADAPT-4 | Async light propagation | **D** | A-9 |
| ADAPT-5 | Launcher/GC detection | **L** | — |
| UI-1 | Frosted HUD | **L** | — |
| UI-2 | Honest controls (no fake toggles) | **L** | ✅ A-13 — unwired switches are shown as *not wired* |
| UI-3 | Three presets | **L** | F-2 (auto-pick) |
| UI-4 | Hotkeys | **L** | — |
| UI-5 | Settings console from Video Settings | **L** | — |
| UI-6 | Live preview/explanation | **P** | — |
| UI-7 | One-line telemetry summary | **L** | ✅ A-10, A-13 |
| UI-8 | Safe Mode | **L** | — |
| PLAT-1 | 5 versions, one tree | **L** | — |
| PLAT-2 | Era routing | **L** | — |
| PLAT-3 | Graceful degradation | **L** | B (make loud) |
| PLAT-4 | VersionBridge | **L** | ✅ D-5 |
| PLAT-5 | Loader ≥ 0.16.0 + Fabric API | **L** | — |
| PLAT-6 | Telemetry disabled | **L** | — |
| PLAT-7 | One-command build | **L** | — |
| PLAT-8 | Persist settings | **L** | — |
| PLAT-9 | Guarded startup | **L** | — |
| PLAT-10 | Guarded native calls | **L** | — |
| COMP-1 | Yield to competing renderers | **L** | ✅ D-1 |
| COMP-2 | Shader packs disable fast math | **L** | ✅ D-2 |
| DIAG-1 | Live subsystem report | **L** | ✅ A-13 |
| DIAG-2 | Build-time mixin target audit | **L** | ✅ B-2 |
| QUAL-1 | Tests per subsystem | **L** | — |
| QUAL-2 | Plain-JVM JUnit 5 | **L** | — |
| QUAL-3 | All 5 nodes green | **L** | — |
| QUAL-4 | Three-doc contract | **L** | G-3 (matrix) |

**Summary: 47 live · 8 partial · 7 dead · 1 not implemented.**

---

## 13. Appendix B — New `spec.md` requirements proposed for v0.2

Add these to `spec.md` §2 when they are implemented:

| ID | Requirement | Priority |
| :--- | :--- | :--- |
| REQ-DIAG-1 | The startup log and a debug screen must report the **actual** live/wired state of every subsystem, per node. | Must |
| REQ-DIAG-2 | Every mixin target must be verified to exist at build time; a missing target fails the build. | Must |
| REQ-DIAG-3 | The console must not expose a toggle that has no effect on the running node. | Must |
| REQ-BENCH-1 | A benchmark capture mode must record frametime percentiles, allocation rate and subsystem counters. | Should |
| REQ-COMP-1 | Thorium must detect competing renderers (Sodium/Embeddium family) and yield overlapping subsystems automatically. | Must |
| REQ-COMP-2 | Shader-pack presence must disable GL-state filtering and fast-math overrides. | Must |
| REQ-SAFE-1 | A render-thread stall watchdog must detect and report freezes > 500 ms. | Should |
| REQ-FRAME-9 | The frame pacer must support a user-set target FPS, persisted across restarts. | Must |

---

## 14. Appendix C — Quick-reference: where each change goes

| Change | Location |
| :--- | :--- |
| New policy class (no MC API) | `src/main/java/dev/hyperionfps/<pkg>/` |
| Mixin targeting the same method on all versions | `src/main/java/dev/hyperionfps/mixin/core/` |
| Mixin targeting a version-specific method | `versions/<mc>/src/main/java/dev/hyperionfps/mixin/{legacy,modern}/` |
| Era-specific GameOptions / render-state access | `versions/<mc>/src/main/java/dev/hyperionfps/gui/` |
| Test for a shared class | `src/test/java/dev/hyperionfps/<same-package>/` |
| New persisted setting | `ThoriumConfig`: key constant + `captureFromSubsystems()` + `applyToSubsystems()` (**all three**) |
| New mixin registration | `thorium.mixins.json` template **and** the node's `build.gradle.kts` `mixin_legacy`/`mixin_modern` |
| New telemetry counter | `AtomicLong` getter + an entry in `ThoriumGuiManager.getTelemetrySummary()` |
| Behaviour change | update `spec.md` |
| Status change | update `state.md` |
| Convention change | update `AGENTS.md` |

---

## 15. Appendix D — Task index

| ID | Task | Priority | Est. | Blocked by |
| :--- | :--- | :--- | :--- | :--- |
| A-1 | Wire the frame pacer | P0 | 2 d | — |
| A-2 | Wire adaptive render distance | P0 | 2 d | A-1 |
| A-3 | Fix the per-frame frame-context | P0 | 3 d | B-2 |
| A-3b | Port `LegacyFogMixin` to 1.21.11 | P0 | 0.5 d | B-2 |
| A-4 | Chunk upload pacer on 26.x | P0 | 3 d | B-2 |
| A-5 | Particle ticking on modern nodes | P0 | 3 d | B-2 |
| A-6 | Section culling wiring | P1 | 3 d | B-2 |
| A-7 | Entity instancing wiring | P1 | 4 d | A-3, B-2 |
| A-8 | Particle sheet batching wiring | P1 | 3 d | A-5 |
| A-9 | Async light wiring (opt-in) | P1 | 3 d | B-2 |
| A-10 | GL state filter wiring | P1 | 2 d | D-2, B-2 |
| A-11 | HUD draw merging wiring | P1 | 2 d | B-2 |
| A-12 | Input pacing — implement or rename | P1 | 2 d | — |
| A-13 | Subsystem activation report | P0 | 2 d | — |
| B-1 | Fail build on remap warning | P0 | 1 d | — |
| B-2 | Mixin target audit task | P0 | 3 d | — |
| B-3 | Deliberate `require = 1` | P2 | 1 d | B-2 |
| B-4 | Runtime applied/non-applied report | P0 | 1 d | — |
| C-1 | Fix factual doc errors | P0 | 0.5 d | — |
| C-2 | Benchmark table rewrite | P1 | 1 d | E-1 |
| C-3 | Feature list from the report | P1 | 0.5 d | A-13 |
| C-4 | Per-node feature matrix | P1 | 1 d | C-3 |
| D-1 | Sodium coexistence | P0 | 2 d | — |
| D-2 | Shader-pack safety for fast math | P0 | 2 d | — |
| D-3 | Render-thread stall watchdog | P1 | 2 d | — |
| D-4 | Audit startup log claims | P0 | 1 d | A-13 |
| D-5 | `DefaultEntityBridge` honesty | P1 | 0.5 d | — |
| D-6 | `FrustumCache` allocation | P1 | 0.5 d | — |
| E-1 | Benchmark capture mode | P1 | 4 d | — |
| E-2 | A/B comparison harness | P1 | 4 d | E-1 |
| E-3 | CI matrix | P1 | 2 d | B-2 |
| E-4 | Allocation regression gate | P2 | 2 d | E-1 |
| F-1 | GPU timer queries | P2 | 5 d | — |
| F-2 | Hardware auto-preset | P1 | 3 d | — |
| F-3 | HUD per-subsystem A/B | P2 | 2 d | A-13 |
| F-4 | Session heatmap | P2 | 4 d | E-1 |
| F-5 | Memory-pressure auto-purge | P1 | 2 d | — |
| F-6 | Async chunk decode | P2 | 10 d | — |
| F-7 | Distant pose freezing | P2 | 4 d | — |
| F-8 | Light-engine diagnostics | P2 | 2 d | — |
| G-1 | Tag 0.1.1 + tag convention | P1 | 0.5 d | — |
| G-2 | Automate `spec.md` §7 gate | P1 | 2 d | B-2, E-3 |
| G-3 | Requirement coverage matrix | P1 | 1 d | A |
| G-4 | `ThoriumFabricHooks` parity | P2 | 3 d | — |
| G-5 | `LegacyVignetteMixin` port or drop | P2 | 1 d | — |

**Total estimate: ≈ 100 working days of focused effort** across P0+P1+P2, which
compresses to roughly 6–8 calendar weeks for one focused contributor, or 3–4
weeks for two.

---

*End of plan. When work lands, move the corresponding row from §15 into
`state.md` §6 (Backlog) and update §12 (coverage matrix) in this file.*
