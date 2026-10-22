# TODO.md — Thorium Headline Innovation Roadmap (v0.1.2)

**Project:** Thorium (`thorium` / `Th-90`) — Fabric Client-Side Optimization Mod  
**Target Release:** v0.1.2  
**Target Matrix:** 5 Nodes (`1.21.1`, `1.21.11`, `26.1`, `26.2`, `26.3`)  
**Active Stonecutter Node:** `26.3`  
**Execution Contract:** 10 small, atomic, verifiable steps. After **every single step**, all tracking documents (`TODO.md`, `spec.md`, `state.md`, `AGENTS.md`) **must be updated** so that any new AI session immediately inherits full context without ambiguity.

> **Status: all 10 steps are ✅ DONE** (roadmap closed 2026-10-22 — 317 tests / 42
> classes green on all five version nodes, five `0.1.2` jars built, mixin target audit
> green on every node). The next work is the v0.2 backlog in [`PLAN.md`](./PLAN.md),
> not in this file.

---

## The 10 Small Steps Overview

| Step | Headline Feature / Focus | Primary Subsystems / Components | Status | Verification Gate |
| :--- | :--- | :--- | :--- | :--- |
| **Step 1** | **Core Config & Settings Console Matrix** | `ThoriumConfig`, `ThoriumFeatureToggles`, `ThoriumPresets`, `ThoriumSettingRows`, `ThoriumVideoSettingsScreen` (all 5 nodes), `ThoriumMemoryOptimizer` | ✅ Done | `:26.3:test` (155 tests), `test` (5/5), `buildAll` (5 jars) |
| **Step 2** | **AFK Battery & Power Saver (Stealth AFK™)** | `ThoriumAfkManager`, `ThoriumFramePacer`, `WindowContext` | ✅ Done | `ThoriumAfkManagerTest` (12 tests), `test` (5/5), `buildAll` (5 jars) |
| **Step 3** | **Instant Lag Spike Alert (Nuclear Stutter Hunter™)** | `ThoriumFrameContext`, `ThoriumStutterHunter`, `ThoriumGcWatcher`, `ThoriumPhaseTimer`, `ThoriumToastQueue` | ✅ Done | `ThoriumStutterHunterTest` (11 tests), `test` (5/5), `buildAll` (5 jars) |
| **Step 4** | **Storage Room & Item Frame Culling** | `StorageCullingEngine`, `AsyncOcclusionCuller`, block-entity dispatch | ✅ Done | `StorageCullingEngineTest` (30 tests), `test` (5/5), `buildAll` (5 jars) |
| **Step 5** | **Mob Farm & Swarm Optimizer** | `MobFarmOptimizer`, `ThoriumEntityLodThrottler`, `ThoriumSoundGovernor` | ✅ Done | `MobFarmOptimizerTest` (18 tests), `ThoriumSoundGovernorTest` (5 tests), `test` (5/5, 231 tests), `buildAll` (5 jars) |
| **Step 6** | **Explosion & Firework Shield** | `ExplosionShield`, `ExplosionParticleFilter`, `ThoriumParticleBudget` | ✅ Done | `ExplosionShieldTest` (21 tests), `test` (5/5, 252 tests), `buildAll` (5 jars) |
| **Step 7** | **Elytra Flight Smoother & Modern Chunk Upload Parity** | `ElytraFlightSmoother`, `ThoriumChunkUploadPacer`, `ThoriumFrameContext`, per-era chunk hooks | ✅ Done | `ElytraFlightSmootherTest` (17 tests), `ThoriumChunkUploadPacerTest` (12 tests), `test` (5/5, 279 tests), `buildAll` (5 jars) |
| **Step 8** | **Ultra Low-Latency Reflex (Late Input Sampling)** | `ThoriumReflexController`, `ReflexEventPump`, `core/ReflexInputMixin` | ✅ Done | `ThoriumReflexControllerTest` (18 tests), `test` (5/5, 297 tests), `buildAll` (5 jars) |
| **Step 9** | **No-Lag Weather & Clean Water / Fast Fog** | `WeatherFogOptimizer`, `core/WeatherSplashMixin`, per-era fog + weather hooks | ✅ Done | `WeatherFogOptimizerTest` (19 tests), `test` (5/5, 316 tests), `buildAll` (5 jars) |
| **Step 10** | **Multi-Version Verification, Mixin Audit & Final Release Prep** | All 5 nodes, `MixinTargetAuditTest`, documentation sync | ✅ Done | `test` (5/5, 317 tests), `buildAll` (5 jars), mixin audit green on all 5 nodes |

---

## Detailed Step Breakdowns

### 🟦 Step 1: Core Configuration, Presets & Settings UI Matrix — ✅ DONE (2026-10-21)
- [x] **Config Keys in `ThoriumConfig`:**
  - `anim.mob_farm.enabled` (default: `true`)
  - `culling.storage_item_frames.enabled` (default: `true`)
  - `chunk.elytra_smoother.enabled` (default: `true`)
  - `frame.afk_eco.enabled` (default: `true`)
  - `frame.afk_eco.timeout_sec` (default: `60`)
  - `telemetry.stutter_alert.enabled` (default: `true`)
  - `particle.explosion_shield.enabled` (default: `true`)
  - `input.reflex.enabled` (default: `true`)
  - `render.weather.mode` (default: `"MINIMAL"`, options: `VANILLA`, `MINIMAL`, `OFF`)
  - `render.clean_water_fog.enabled` (default: `true`)
- [x] **Config Serialization & Presets:**
  - Implement full 3-place wiring: Key constants + `captureFromSubsystems()` + `applyToSubsystems()`.
  - Update `POTATO`, `BALANCED`, `NUCLEAR` presets to tune these new headline toggles.
  - *Delivered as:* the switches live in the shared `ThoriumFeatureToggles` registry
    (one source of truth for the eight boolean features, the AFK timeout and the
    weather mode), the preset logic moved out of the five per-version screens into the
    shared `ThoriumPresets`, and the row definitions moved into the shared
    `ThoriumSettingRows` catalogue.
- [x] **Settings UI Rows across all 5 Version Nodes:**
  - `versions/1.21.1/.../gui/ThoriumVideoSettingsScreen.java`
  - `versions/1.21.11/.../gui/ThoriumVideoSettingsScreen.java`
  - `versions/26.1/.../gui/ThoriumVideoSettingsScreen.java`
  - `versions/26.2/.../gui/ThoriumVideoSettingsScreen.java`
  - `versions/26.3/.../gui/ThoriumVideoSettingsScreen.java`
  - Add clean setting rows with player-friendly descriptions and badges.
  - Wire the **Purge RAM Now** action button invoking `ThoriumMemoryOptimizer.purgeMemoryNow()`.
  - *Delivered as:* the ten new rows are defined **once** in `ThoriumSettingRows` and
    rendered by all five screens, so a feature can no longer be added to one version
    and forgotten on the others. A feature whose implementation has not landed yet is
    shown as `NOT AVAILABLE` and refuses the click (REQ-UI-2: no fake toggles).
- [x] **Tests & Verification:**
  - Update `ThoriumConfigTest` to verify persistence, preset defaults, and unknown key preservation.
  - Run `./gradlew :26.3:test` and `./gradlew :26.3:compileJava`.
  - *Result:* 155 tests / 33 classes green on 26.3; `./gradlew test` green on all five
    nodes; `./gradlew buildAll` produced five 0.1.2 jars.
  - *New tests:* `ThoriumFeatureTogglesTest`, `ThoriumPresetsTest`,
    `ThoriumSettingRowsTest`, plus six new cases in `ThoriumConfigTest`.
- [x] **Doc Sync:** Update `TODO.md`, `spec.md`, `state.md`, `AGENTS.md`.

> **Note for Steps 2–9.** Each headline feature now has a persisted switch and a
> console row. When a feature's implementation lands it calls
> `ThoriumFeatureToggles.Feature.<NAME>.markWired()`, and the row switches from
> `NOT AVAILABLE` to a working `ON`/`OFF` switch. Until then it is honestly
> reported as `UNWIRED` in the startup subsystem report.

---

### 🟦 Step 2: AFK Battery & Power Saver (Stealth AFK™) — ✅ DONE (2026-10-21)
- [x] Create `dev.hyperionfps.frame.ThoriumAfkManager`:
  - Track last user activity timestamp (mouse move, mouse click, keyboard key event).
  - Track window focus status via `WindowContext` / `Minecraft#isWindowActive()`.
  - When idle time exceeds configured timeout (default 60s) or window is unfocused:
    - Activate eco throttling (target framerate clamped to 10 FPS).
    - Yield CPU cycles via `LockSupport.parkNanos(90_000_000L)`.
  - Wake instantly (0 ms) on any input event or focus gain.
  - *Delivered as:* `ThoriumAfkManager` in shared source, driven from the client frame
    boundary (`Minecraft#runTick`). The 90 ms park is spent in slices of at most 16 ms
    so an input event is noticed within one slice instead of after the whole park —
    GLFW delivers its events on the very thread that parks.
- [x] Hook into frame pacer (`ThoriumFramePacer`) to respect the AFK target FPS.
  - *Delivered as:* `paceFrame()` yields to the eco throttle, and the menus are parked
    directly by the frame context (the pacer itself never runs outside a world).
- [x] Add HUD indicator / eco badge when active.
  - *Delivered as:* `ThoriumGuiManager.getEcoBadge()`, rendered by all five HUD
    renderers as `• Eco: 10 FPS`.
- [x] JUnit test suite: `ThoriumAfkManagerTest` (12 tests).
- [x] Verification: `./gradlew :26.3:test`, `./gradlew test` (5/5), `./gradlew buildAll`.
- [x] Doc Sync: Update `TODO.md`, `spec.md`, `state.md`, `AGENTS.md`.

> **Notes.** The frametime measurement is *suspended* while the eco throttle runs: a
> 90 ms park is an intentional power saving, not a stutter, and letting it into the
> statistics would destroy the Smoothness score and raise the stutter threshold for
> the frames that follow. The HUD keeps showing the last active values and the eco
> badge explains the low framerate.

---

### 🟦 Step 3: Instant Lag Spike Alert (Nuclear Stutter Hunter™) — ✅ DONE (2026-10-21)
- [x] High-resolution timing checkpoints in `ThoriumFrameContext`:
  - Phase 1: Input & client tick (`tickNanos`).
  - Phase 2: Entity & block entity dispatch (`entitiesNanos`).
  - Phase 3: Chunk upload & geometry processing (`chunkUploadNanos`).
  - Phase 4: Terrain & world render (`renderNanos`).
  - Phase 5: Frame pacing & buffer swap wait (`swapNanos`).
  - *Delivered as:* `ThoriumPhaseTimer` tracks disjoint execution intervals without per-frame allocation.
- [x] GC Pause Correlation:
  - Register `NotificationListener` on `ManagementFactory.getGarbageCollectorMXBeans()`.
  - Capture duration and timestamp of Young Gen / Old Gen sweeps without per-frame overhead.
  - *Delivered as:* `ThoriumGcWatcher` maintains a bounded circular buffer of recent GC pauses and correlates pauses falling inside the stutter window.
- [x] Create `dev.hyperionfps.telemetry.ThoriumStutterHunter`:
  - Trigger when a frame meets the stutter condition (`max(30ms, 2.5 * avg) && jitter > 12ms`).
  - Attribute spike to dominant phases: e.g. `[GC: 38ms | Entities: 16ms | Chunk: 6ms]`.
  - Publish toast alert to HUD via `ThoriumToastQueue`.
  - Render toast banner across all 5 version HUD renderers (`ThoriumModernHudRenderer` / `ThoriumFabricHooks`).
- [x] JUnit test suite: `ThoriumStutterHunterTest` (11 tests).
- [x] Verification: `./gradlew :26.3:test`, `./gradlew test` (5/5 nodes green, 178 tests), `./gradlew buildAll` (5 jars).
- [x] Doc Sync: Update `TODO.md`, `spec.md`, `state.md`, `AGENTS.md`.

---

### 🟦 Step 4: Storage Room & Item Frame Culling — ✅ DONE (2026-10-21)
- [x] Create `dev.hyperionfps.culling.StorageCullingEngine`:
  - Specialized culling for chest-type block entities (`ChestBlockEntity`, `TrappedChestBlockEntity`, `EnderChestBlockEntity`, `ShulkerBoxBlockEntity`).
  - Bounded occlusion test: check if container is completely encased in solid opaque blocks.
  - Culling for `ItemFrame` and `GlowingItemFrame` entities when occluded or outside direct focal angle.
  - Exemption: never cull the container currently opened by the player.
  - *Delivered as:* `StorageCullingEngine` is the single entry point for the culling mixins; it applies the storage rules and falls back to `AsyncOcclusionCuller` for everything else, so the feature only ever *adds* a cull.
- [x] Wire call sites in legacy and modern entity / block entity culling mixins.
  - *Delivered as:* all 10 call sites (5 versions × block-entity + entity) now route through `StorageCullingEngine`.
- [x] JUnit test suite: `StorageCullingEngineTest` (30 tests).
- [x] Verification: `./gradlew :26.3:test`, `./gradlew test` (5/5 nodes green), `./gradlew buildAll` (5 jars).
- [x] Doc Sync: Update `TODO.md`, `spec.md`, `state.md`, `AGENTS.md`.

> **Notes.** The enclosure test asks for three things at once: the neighbour must be
> opaque (`canOcclude()`), it must fill its whole cell
> (`isCollisionShapeFullBlock`), and it must not carry a block entity of its own.
> The third clause is what keeps a double chest intact — the neighbouring half is a
> chest, so the pair is never treated as sealed in. The occlusion half of the
> item-frame rule is not re-implemented: the shared raycast in `AsyncOcclusionCuller`
> already covers it, and the storage engine falls through to it.
>
> The class the spec calls `GlowingItemFrame` is called `GlowItemFrame` in Minecraft.

---

### 🟦 Step 5: Mob Farm & Swarm Optimizer — ✅ DONE (2026-10-21)
- [x] Create `dev.hyperionfps.anim.MobFarmOptimizer`:
  - Spatial grouping: grid-hash entities by block position.
  - Detect clusters where > 15 living entities of the same type occupy a 5x5 horizontal area.
  - For clustered mobs outside player crosshair focus (> 4 blocks away):
    - Skip secondary bone/limb swing calculations.
    - Rate-limit animation ticks to 1/3 of normal frequency.
  - Exemptions: Player target, named mobs (`hasCustomName()`), leashed mobs, passengers.
  - *Delivered as:* `MobFarmOptimizer` in shared source. The cluster map is a pair of
    open-addressed primitive tables (one keyed by species + cell, one by cell alone)
    that are cleared and refilled every frame, so nothing on the path allocates. A
    cell holds more than 15 living entities **of the same species** to be a farm.
    The animation of a clustered mob is recomputed on one frame out of three; the
    two skipped frames perform no bone and no limb-swing calculation at all, and
    the per-entity hash spreads the skipped frames so a pen does not freeze in
    unison. A mob within 4 blocks of the camera is never throttled.
  - *Exemptions:* the local player, a named mob, a leashed mob and a passenger are
    exempt. An exempt mob is **not counted** towards the cluster and it **protects
    its whole cell**, so the client can never freeze the legs of the pet the
    player is looking at. The rule lives in one place
    (`MobFarmOptimizer.isExemptEntity`), the facts are gathered by the era hooks.
- [x] Dynamic Acoustic Dampening in `ThoriumSoundGovernor`:
  - When sound emission from a single species cluster exceeds 6 events / 50ms, dampen volume and drop duplicate ambient sound instances.
  - *Delivered as:* a cell that the optimizer reports as a mob farm gets its own
    policy instead of the general one: the first 6 events of a 50 ms window play
    at their normal volume, the next 6 are dampened to 50 %, and everything beyond
    that is dropped as a surplus duplicate. The dampening writes the volume of the
    sound instance itself (through the new `AbstractSoundInstanceAccessor`), so the
    audio thread really plays a quieter sound. The general 4-per-cell rate limit is
    unchanged for everything that is not a mob farm.
- [x] Wire the call sites:
  - *Delivered as:* the animation decision has **one** owner. The five
    `LegacyEntityLodMixin` / `ModernEntityLodMixin` hooks now ask
    `MobFarmOptimizer.shouldSkipAnimation(...)`, which applies the cluster rule and
    then falls back to the distance-based animation LOD — so switching the feature
    off restores the previous behaviour exactly.
  - The modern render pipeline never sees the entity in the animation hook (it
    renders an extracted `EntityRenderState`), so the cluster map is filled by two
    new hooks on `EntityRenderDispatcher#extractEntity`
    (`legacy.LegacyMobFarmEntityMixin`, `modern.ModernMobFarmEntityMixin`), which
    is the last point at which the leash and passenger flags still exist.
    1.21.1 still works on the entity itself and records it in the animation hook.
- [x] JUnit test suite: `MobFarmOptimizerTest` (18 tests) + `ThoriumSoundGovernorTest` (5 tests).
- [x] Verification: `./gradlew :26.3:test` (231 tests, 0 failures), `./gradlew test` (5/5 nodes, 231 tests each), `./gradlew buildAll` (5 jars).
- [x] Doc Sync: Update `TODO.md`, `spec.md`, `state.md`, `AGENTS.md`.

> **Note.** The mixin target audit was extended for this step: it now verifies
> **field** selectors as well as method selectors, so a renamed field can no longer
> break an accessor silently — the same guarantee the audit already gave to
> injections. The new `core.AbstractSoundInstanceAccessor` is the first accessor
> in the codebase and is audited on all five nodes.
>
> **Known limitation, stated honestly.** The exemption is enforced per *cell*, not
> per mob: a cell that holds an exempt mob is not throttled at all. That is
> deliberate — the modern render state does not carry the leash and the passenger
> flags, and resolving them per mob would need an entity identity the render
> pipeline does not hand out. The cost is that one leashed pet can cost a whole
> pen its throttling; the benefit is that a mob the player is looking at can never
> be frozen.


---

### 🟦 Step 6: Explosion & Firework Shield (Particle Flood Capper) — ✅ DONE (2026-10-21)
- [x] Create `dev.hyperionfps.particle.ExplosionShield`:
  - Integrate with `ThoriumParticleBudget`.
  - Track short-term emission rates for high-density particle categories:
    - `EXPLOSION`, `EXPLOSION_EMITTER`, `FIREWORK`, `CAMPFIRE_SMOKE`, `LARGE_SMOKE`.
  - When burst emission exceeds 250 particles/sec, cull redundant overlapping quads within the same 1x1 block area.
  - Crucial safety: allow initial explosion flash and primary puff to pass so visual game feedback remains intact.
  - *Delivered as:* `ExplosionShield` in shared source, split into a **pure policy
    engine** (no Minecraft type at all — the whole decision table is unit-testable
    without a running game) and a **Minecraft adapter**
    (`ExplosionParticleFilter`) that maps the vanilla particle types onto the
    categories and applies the decision. Two independent limits do the work:
    a **global token bucket** (250 particles/sec, refilled continuously, so it is
    a rate and not a cliff) and a **per-cell token bucket** (48 particles/sec per
    one-block cell) that removes the quads which are drawn on top of each other.
- [x] Wire check in particle creation mixins.
  - *Delivered as:* `core/ParticleEngineMixin` now asks
    `ExplosionParticleFilter.shouldCullParticle(options, x, y, z)` instead of calling
    `AsyncOcclusionCuller` directly. The filter applies the shield and then falls
    back to the generic particle culling, so the feature can only ever *add* a
    cull and switching it off restores the previous behaviour exactly. The hook is
    the same `ParticleEngine#createParticle` injection as before, so it is live on
    all five nodes without a new mixin.
- [x] JUnit test suite: `ExplosionShieldTest` (21 tests).
- [x] Verification: `./gradlew :26.3:test` (252 tests, 0 failures), `./gradlew test` (5/5 nodes, 252 tests each), `./gradlew buildAll` (5 jars).
- [x] Doc Sync: Update `TODO.md`, `spec.md`, `state.md`, `AGENTS.md`.

> **Notes.** The safety rule is the interesting part. The first particle of every
> category inside a burst is **protected**: it bypasses the global rate limit, the
> per-cell limit *and* the global creation budget of `ThoriumParticleBudget`. For
> an explosion those two protected particles are the flash and the primary puff —
> and they have to bypass the creation budget too, because a client that is
> drowning in particles has an exhausted budget, and that budget would otherwise
> swallow the flash and make the explosion invisible.
>
> The per-cell limit is a *rate* and not a cumulative count, so a campfire that
> puffs smoke forever is never throttled (it emits far below the limit) while a
> wither that dumps two hundred smoke particles into one block in a single tick is
> cut down to the cell's share. A cell that has been quiet for 250 ms starts a new
> burst, which is what lets the next explosion in the same block get its own flash.
>
> **Known limitation, stated honestly.** The protection is granted per *category
> per cell burst*, not per explosion event. Two explosions inside the same block
> within the same 250 ms window share the burst, so the second flash is an
> ordinary particle and can be dropped when the rate limit is reached. In practice
> a TNT chain explodes in different blocks, so every explosion gets its own flash.

---

### 🟦 Step 7: Elytra Flight Smoother & Modern Chunk Upload Parity — ✅ DONE (2026-10-21)
- [x] Track player velocity vector $\vec{v}$ in `ThoriumFrameContext`:
  - Speed threshold check: active when $||\vec{v}|| > 15 \text{ m/s}$ (fast Elytra, ice boat, sprint-flight).
  - *Delivered as:* `ElytraFlightSmoother` in shared source - a **pure policy engine**
    that holds no Minecraft type. The velocity is read once per world frame by
    `PlayerVelocityPublisher` (the only class in the codebase that touches the player
    entity) and published together with the camera position. The speed is cached, so
    the hot path never takes a square root.
- [x] Directional Chunk Priority in `ThoriumChunkUploadPacer`:
  - Bias section upload ordering: prioritize chunk sections within a $45^\circ$ forward cone along $\vec{v}$.
  - Defer sections behind the player.
  - *Delivered as:* `ThoriumChunkUploadPacer` now owns a **positioned deferred queue**.
    A section whose build was postponed is released in the order the flight vector
    demands: the sections inside the 45° forward cone first, the neutral ones next,
    the sections behind the player last. The classification is a pure function
    (`ElytraFlightSmoother.classify`), so the whole decision table is unit-testable
    without a game.
- [x] Modern 26.x Blaze3D upload hook:
  - Wire `ThoriumChunkUploadPacer.runBudgetedUploads()` into the modern chunk dispatch path (resolving the version disparity where 26.x previously lacked pacing).
  - *Delivered as:* one hook per node, all five of them live:
    `legacy.LegacyChunkUploadPriorityMixin` (1.21.1, 1.21.11) and
    `modern.ModernChunkUploadPriorityMixin` (26.1, 26.2, 26.3) intercept the point at
    which vanilla **schedules** a section build and postpone it when the section lies
    behind the flight vector. On the 26.x nodes this is the pacing parity the roadmap
    asked for: fewer sections are built per frame, so the `uploadTerrainBuffersToGpu()`
    burst shrinks as well.
- [x] JUnit test suite: `ElytraFlightSmootherTest` (17 tests) and
      `ThoriumChunkUploadPacerTest` (12 tests, including the vector-bias test).
- [x] Verification: `./gradlew :26.3:test` (279 tests, 0 failures), `./gradlew test`
      (5/5 nodes, 279 tests each), `./gradlew buildAll` (5 jars).
- [x] Doc Sync: Update `TODO.md`, `spec.md`, `state.md`, `AGENTS.md`.

> **Notes.** The safety rules are what make the feature acceptable. The
> classification uses the **velocity** vector, not the view direction, so a section
> that is behind the player can still be on the screen when the player looks back.
> Three guards keep that from becoming a visible hole: a section closer than 16
> blocks is never deferred, a section that has waited for more than half a second is
> released regardless of its priority, and a section that fails to be scheduled
> (because the world went away) is dropped instead of crashing the frame.
>
> **Known limitation, stated honestly.** The 26.x pipeline copies every chunk mesh
> into shared GPU buffers in one go (`SectionRenderDispatcher#uploadTerrainBuffersToGpu`),
> so the **upload** itself still cannot be split into a per-frame budget the way the
> 1.21.x `Queue<Runnable>` can. On 26.x the pacer therefore budgets the section
> *builds*, which is what fills that upload queue - the burst shrinks, but it is not
> split. The console row says exactly that: "2.5ms Cap" on 1.21.x, "Flight Order" on
> 26.x.

---

### 🟦 Step 8: Ultra Low-Latency Reflex (Late Input Sampling) — ✅ DONE (2026-10-22)
- [x] Create `dev.hyperionfps.input.ThoriumReflexController`:
  - Monitor input polling order.
  - Hook into `core/MinecraftFrameBoundaryMixin`: shift mouse coordinate update and camera angle delta calculation to the tail of the frame boundary wait, immediately prior to camera matrix setup.
  - Reports the measured cursor movement recovered during the same frame and uses that
    measurement, rather than an unsupported fixed latency claim.
  - Verify zero impact on network movement packets (tick logic untouched).
  - *Delivered as:* `ThoriumReflexController` in shared source — a **pure policy
    engine** that holds no Minecraft type, so the whole decision table is unit-testable
    without a running game. It owns the decision (the feature is on, the node can drain
    its queue, the window has the focus), the accounting, and the seam through which
    the platform queue is drained.
- [x] Hook the late sample into the frame:
  - *Delivered as:* the sample point is **`MouseHandler#handleAccumulatedMovement` at
    HEAD** — not `Minecraft#runTick`. On all five nodes the game calls it immediately
    before it builds the camera matrix, and it is the exact point at which the pending
    cursor movement is applied to the camera. The shared hook is `core/ReflexInputMixin`;
    the pending movement is read through the new `core/MouseHandlerAccessor`
    (`accumulatedDX` / `accumulatedDY`, same names on every node).
  - The drain itself is the **game's own routine**, never a re-implementation:
    `GLFW.glfwPollEvents()` on 1.21.1 / 1.21.11 (the game's own `RenderSystem#pollEvents`
    is private there), the public `RenderSystem#pollEvents()` on 26.1 / 26.2, and
    `RenderSystem#pollEvents(SDLEventHandler)` on 26.3 (SDL3 era — the handler is read
    through the new 26.3-only `modern/MinecraftSdlEventHandlerAccessor`). The five
    implementations live in the version-specific source tree under the fixed name
    `dev.hyperionfps.input.ReflexEventPumpImpl`, and the shared controller finds them
    by name, so the shared source never mentions GLFW or SDL.
  - Because the queue is *drained* and not copied, every event is still delivered
    **exactly once** — only earlier.
- [x] Verify zero impact on network movement packets (tick logic untouched):
  - *Delivered as:* the tick is never touched. The hook only drains the platform queue
    a little earlier; the game still applies the movement with its own smoothing and
    its own sensitivity, and the movement packets the client sends are exactly the ones
    vanilla would have sent.
- [x] JUnit test suite: `ThoriumReflexControllerTest` (18 tests).
- [x] Verification: `./gradlew :26.3:test` (316 tests, 0 failures), `./gradlew test`
      (5/5 nodes, 316 tests each), `./gradlew buildAll` (5 jars).
- [x] Doc Sync: Update `TODO.md`, `spec.md`, `state.md`, `AGENTS.md`.

> **The latency claim was corrected, not softened.** The roadmap asked for "8–14 ms
> lower input-to-photon latency", a figure that belongs to NVIDIA Reflex's render-ahead
> pipeline — Minecraft has no render-ahead queue, so the number was never achievable
> here and shipping it would have been a lie. What the feature actually removes is the
> delay between *the moment the player moves the mouse* and *the frame that shows it*:
> the movement that arrives while the frame is working is applied to the frame the
> player is looking at instead of the next one. The controller therefore **measures**
> what it recovered (`getRecoveredPixels()`, `getFreshInputFrames()`) and the console
> badge says *"Same-Frame Input"* rather than a millisecond figure that was never
> measured.
>
> **The leftover input pacer is still a leftover.** `ThoriumInputPacer` was the old
> stub that carried the Reflex promise without a call site. The real feature now
> exists, so the stub is only a counter — its Javadoc now says so, and the startup
> report still lists it as `UNWIRED`. Removing it (and its `input.pacer.enabled` key)
> is a configuration-format change and stays in the backlog as A-12 / G-8.

---

### 🟦 Step 9: No-Lag Weather & Clean Water / Fast Fog — ✅ DONE (2026-10-22)
- [x] Create `dev.hyperionfps.render.WeatherFogOptimizer`:
  - **Weather Rendering Modes:**
    - `VANILLA`: Standard rain sheets and ground splash particles.
    - `MINIMAL`: Lightweight rain sheets; suppress the ground splash particle flood.
    - `OFF`: Disable the precipitation visual layer on client (sound/world weather intact).
  - **Clean Water & Fast Fog:**
    - Clamp underwater fog start/end distances to remove the murky fragment gradient.
    - Narrow the distance fog density curve to a sharp band.
  - *Delivered as:* `WeatherFogOptimizer` in shared source — a **pure policy engine**
    that holds no Minecraft type, so the whole decision table (three weather modes, both
    fog clamps, the guard against unusable ranges) is unit-testable without a running
    game. The Minecraft half lives in the per-era mixins, which are one-line adapters.
- [x] Wire hooks into legacy (`LegacyFogMixin`) and modern fog/weather renderers.
  - *Delivered as:*
    - The **splash particles** are dropped in one shared place:
      `core/WeatherSplashMixin` on `ClientLevel#addParticle(ParticleOptions, double×6)`
      — the method the weather uses on **all five** nodes. The comparison is by
      identity against `ParticleTypes.RAIN`, so it costs one reference comparison on
      the particle path.
    - The **precipitation layer** is dropped in the existing per-era weather mixins
      (`legacy.LegacyWeatherMixin` on 1.21.1 / 1.21.11, `modern.ModernWeatherMixin`
      on 26.1 / 26.2 / 26.3), which now also honour the weather mode and not only
      Extreme FPS mode.
    - The **fog** is rewritten where the game computes it:

      | Node | Clean water | Fast fog |
      | :--- | :--- | :--- |
      | 1.21.1 | `legacy.LegacyFogMixin` (reads and writes `RenderSystem`'s shader fog) | the same hook, on the terrain fog |
      | 1.21.11 | `legacy.LegacyWaterFogMixin` (`WaterFogEnvironment#setupFog`) | `legacy.LegacyFogRendererMixin` (a `@Redirect` on the `Mth#clamp` that produces the band) |
      | 26.1 / 26.2 / 26.3 | `modern.ModernFogMixin` (`FogRenderer#setupFog` → `FogData`) | the same hook (the `renderDistance*` range) |
- [x] JUnit test suite: `WeatherFogOptimizerTest` (19 tests).
- [x] Verification: `./gradlew :26.3:test` (316 tests, 0 failures), `./gradlew test`
      (5/5 nodes, 316 tests each), `./gradlew buildAll` (5 jars).
- [x] Doc Sync: Update `TODO.md`, `spec.md`, `state.md`, `AGENTS.md`.

> **The rain sound lives in the same method as the splash particles.** On every
> supported version the weather spawns the splash particles *and* plays the rain
> sound from one method (`LevelRenderer#tickRain` on 1.21.1,
> `WeatherEffectRenderer#tickRainParticles` on 1.21.11 / 26.1,
> `ClientLevel#tickWeatherEffects` on 26.2 / 26.3). Cancelling that method would
> silence the storm, so the splash is dropped at the **particle call** instead. That
> is why the shared hook sits on `ClientLevel#addParticle` and not on the weather
> method.
>
> **The "lightweight single-pass rain sheets" of the roadmap was corrected, not
> softened.** The rain sheets are a grid of alpha-blended columns whose alpha
> *rises* with the distance, so dropping the far ones would remove the thickest part
> of the curtain — a visible change, not a saving. What MINIMAL actually does is drop
> the splash flood, which is the part that is genuinely expensive: a downpour spawns
> `0.225 × (2r+1)² × rainLevel²` particles **per tick**, several thousand per second.
> The console text now says exactly that.
>
> **The fill-rate claim was removed, not hidden.** The fog shader computes its blend
> for every fragment whether the fog band is 24 blocks wide or 64, so the fog part of
> this feature is **visual**: the water reads cleaner and the horizon resolves
> sharper, but no frame-time claim is made for it. The counters report the work that
> is really removed (the splash particles) and the fog rewrites separately.
>
> **Known limitation, stated honestly.** On 1.21.11 the Extreme FPS fog override only
> reaches the *environmental* fog range — the renderer overwrites the render-distance
> range after the fog environment has run. That gap predates this step and is
> unchanged by it.

---

### 🟦 Step 10: Multi-Version Verification, Mixin Target Audit & Release Prep — ✅ DONE (2026-10-22)
- [x] Full Matrix Compilation & Test:
  - Run `./gradlew test` across all 5 version nodes (`:1.21.1`, `:1.21.11`, `:26.1`, `:26.2`, `:26.3`).
  - Run `./gradlew buildAll` to produce 5 release jars in `build/dist/`.
  - *Result:* **317 tests / 42 classes, 0 failures, 0 errors, 0 skipped on every one of
    the five nodes.** `buildAll` produced the five `0.1.2` jars:
    `thorium-0.1.2+1.21.1.jar` (280 713 B), `thorium-0.1.2+1.21.11.jar` (279 871 B),
    `thorium-0.1.2+26.1.jar` (275 984 B), `thorium-0.1.2+26.2.jar` (276 206 B),
    `thorium-0.1.2+26.3.jar` (276 865 B). The stale `0.1.1` jars an earlier build had
    left in `build/dist/` were removed, so the directory now holds exactly the five
    artifacts of the current version.
- [x] Mixin Target Audit:
  - Verify `MixinTargetAuditTest` completes with 0 failures on all 5 nodes.
  - *Result:* green on all five nodes. The audit covered 12 `core` + 21–23 `legacy` +
    20–21 `modern` mixins per node (33 / 35 / 32 / 32 / 33), and the per-node
    `fabric.mod.json` resolved every `${id}` / `${version}` / `${minecraft}` /
    `${java_version}` placeholder.
  - The audit is **not vacuous**, and that matters as much as the green result: it
    fails the build when the expanded mixin list is empty, when a `${...}` placeholder
    survived the per-node expansion, when a registered mixin was never compiled, when
    an injection selector matches no method on that node's Minecraft jar, when an
    accessor field was renamed, and when a compiled mixin is not registered in the
    configuration.
- [x] Final Documentation Sweep:
  - Update `README.md`, `MODRINTH.md`, `spec.md`, `state.md`, `AGENTS.md`, `TODO.md`.
  - Mark roadmap complete.
  - *Four real drifts were corrected rather than papered over:*
    1. The README advertised **"sub-millisecond input queueing"** for the Nuclear Max
       preset. The leftover `ThoriumInputPacer` counter never earned that claim — the
       real low-latency feature is the same-frame Reflex (step 8), and the console row
       for the counter now says *not available*. The claim was removed, not softened.
    2. `spec.md` said **"eight headline features (REQ-HEAD-1 … REQ-HEAD-9)"** while
       ten exist (REQ-HEAD-10 is the *Purge RAM Now* action), and its component
       inventory was missing `MobFarmOptimizer`, `ElytraFlightSmoother`,
       `PlayerVelocityPublisher` and `ThoriumInputPacer`.
    3. `AGENTS.md` still warned that *"the docs say 4 versions"* (they have said 5 for
       a while) and that a new toggle needs a row in **all five**
       `ThoriumVideoSettingsScreen` files — which §6a replaced with the shared
       `ThoriumSettingRows` catalogue.
    4. The test / mixin / file counts were stale everywhere (316 → **317** tests,
       11 → **12** core mixins, 87 → **89** shared sources, 25 → **27** version-specific
       sources on 1.21.11).
  - The startup log's honesty rules were re-checked against the code: the seven
    subsystems that still have no call site are still reported as `UNWIRED` by
    `ThoriumSubsystemReport`, and the console still refuses their click.

> **The roadmap is closed; the v0.2 backlog is not.** What remains open is unchanged
> and lives in [`PLAN.md`](./PLAN.md): the seven implemented-but-unwired subsystems
> (A-6 … A-12), the benchmark harness (G-7), the release tags (G-11), the remaining
> `AABB` allocation in `FrustumCache` (G-4 / D-6) and the `ThoriumFabricHooks`
> parity gap (G-9).

---

## Operating Protocol for AI Agents

1. **Atomic Execution:** Execute strictly one step at a time.
2. **Compile & Test Gate:** After modifying code, run `./gradlew :26.3:test` (or full `./gradlew test` when version-specific code is touched).
3. **Mandatory Documentation Update:** Before completing each step, update:
   - `TODO.md` (check off completed items, update status table)
   - `spec.md` (update requirements / specifications)
   - `state.md` (update current status board)
   - `AGENTS.md` (update any new conventions, rules, or gotchas)
4. **Hard Stop After Every Step:** After updating all markdown files, **STOP execution immediately** and report the outcome to the user. Do not begin the next step without explicit user instruction.
5. **Context Continuity:** Ensure any new AI session reading these markdown files can immediately understand what was just completed and what the exact next step is.
