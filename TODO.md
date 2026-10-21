# TODO.md — Thorium Headline Innovation Roadmap (v0.1.2)

**Project:** Thorium (`thorium` / `Th-90`) — Fabric Client-Side Optimization Mod  
**Target Release:** v0.1.2  
**Target Matrix:** 5 Nodes (`1.21.1`, `1.21.11`, `26.1`, `26.2`, `26.3`)  
**Active Stonecutter Node:** `26.3`  
**Execution Contract:** 10 small, atomic, verifiable steps. After **every single step**, all tracking documents (`TODO.md`, `spec.md`, `state.md`, `AGENTS.md`) **must be updated** so that any new AI session immediately inherits full context without ambiguity.

---

## The 10 Small Steps Overview

| Step | Headline Feature / Focus | Primary Subsystems / Components | Status | Verification Gate |
| :--- | :--- | :--- | :--- | :--- |
| **Step 1** | **Core Config & Settings Console Matrix** | `ThoriumConfig`, `ThoriumFeatureToggles`, `ThoriumPresets`, `ThoriumSettingRows`, `ThoriumVideoSettingsScreen` (all 5 nodes), `ThoriumMemoryOptimizer` | ✅ Done | `:26.3:test` (155 tests), `test` (5/5), `buildAll` (5 jars) |
| **Step 2** | **AFK Battery & Power Saver (Stealth AFK™)** | `ThoriumAfkManager`, `ThoriumFramePacer`, `WindowContext` | ✅ Done | `ThoriumAfkManagerTest` (12 tests), `test` (5/5), `buildAll` (5 jars) |
| **Step 3** | **Instant Lag Spike Alert (Nuclear Stutter Hunter™)** | `ThoriumFrameContext`, `ThoriumStutterHunter`, `GarbageCollectorMXBean` | ⏳ Pending | `StutterHunterTest`, attribution parsing |
| **Step 4** | **Storage Room & Item Frame Culling** | `StorageCullingEngine`, `AsyncOcclusionCuller`, block-entity dispatch | ⏳ Pending | `StorageCullingTest`, occlusion distance test |
| **Step 5** | **Mob Farm & Swarm Optimizer** | `MobFarmOptimizer`, `ThoriumEntityLodThrottler`, `ThoriumSoundGovernor` | ⏳ Pending | `MobFarmOptimizerTest`, cluster grouping test |
| **Step 6** | **Explosion & Firework Shield** | `ExplosionShield`, `ThoriumParticleBudget` | ⏳ Pending | `ExplosionShieldTest`, quad cap verification |
| **Step 7** | **Elytra Flight Smoother & Modern Chunk Upload Parity** | `ThoriumChunkUploadPacer`, `ThoriumFrameContext`, 26.x Blaze3D hooks | ⏳ Pending | `ChunkUploadPacerTest`, vector bias test |
| **Step 8** | **Ultra Low-Latency Reflex (Late Input Sampling)** | `ThoriumReflexController`, `MinecraftFrameBoundaryMixin`, GLFW/Input | ⏳ Pending | `ReflexInputTest`, polling order test |
| **Step 9** | **No-Lag Weather & Clean Water / Fast Fog** | `WeatherFogOptimizer`, `LegacyFogMixin`, modern fog hooks | ⏳ Pending | `WeatherFogTest`, fog clamping test |
| **Step 10** | **Multi-Version Verification, Mixin Audit & Final Release Prep** | All 5 nodes, `MixinTargetAuditTest`, documentation sync | ⏳ Pending | `./gradlew test` (5/5), `./gradlew buildAll` |

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

### 🟦 Step 3: Instant Lag Spike Alert (Nuclear Stutter Hunter™)
- [ ] High-resolution timing checkpoints in `ThoriumFrameContext`:
  - Phase 1: Input & client tick (`phaseTickNanos`).
  - Phase 2: Entity & block entity dispatch (`phaseEntitiesNanos`).
  - Phase 3: Chunk upload & geometry processing (`phaseChunkUploadNanos`).
  - Phase 4: Terrain & particle draw (`phaseRenderNanos`).
  - Phase 5: Frame pacing & buffer swap wait (`phaseSwapNanos`).
- [ ] GC Pause Correlation:
  - Register `NotificationListener` on `ManagementFactory.getGarbageCollectorMXBeans()`.
  - Capture duration and timestamp of Young Gen / Old Gen sweeps without per-frame overhead.
- [ ] Create `dev.hyperionfps.telemetry.ThoriumStutterHunter`:
  - Trigger when a frame meets the stutter condition (`max(30ms, 2.5 * avg) && jitter > 12ms`).
  - Attribute spike to dominant phase: `[GC: 38ms | Mob Animations: 16ms | Terrain: 6ms]`.
  - Publish toast alert to HUD (`ThoriumGuiManager`).
- [ ] JUnit test suite: `ThoriumStutterHunterTest`.
- [ ] Verification: `./gradlew :26.3:test`.
- [ ] Doc Sync: Update `TODO.md`, `spec.md`, `state.md`, `AGENTS.md`.

---

### 🟦 Step 4: Storage Room & Item Frame Culling
- [ ] Create `dev.hyperionfps.culling.StorageCullingEngine`:
  - Specialized culling for chest-type block entities (`ChestBlockEntity`, `TrappedChestBlockEntity`, `EnderChestBlockEntity`, `ShulkerBoxBlockEntity`).
  - Bounded occlusion test: check if container is completely encased in solid opaque blocks.
  - Culling for `ItemFrame` and `GlowingItemFrame` entities when occluded or outside direct focal angle.
  - Exemption: never cull the container currently opened by the player.
- [ ] Wire call sites in legacy and modern entity / block entity culling mixins.
- [ ] JUnit test suite: `StorageCullingEngineTest`.
- [ ] Verification: `./gradlew :26.3:test`.
- [ ] Doc Sync: Update `TODO.md`, `spec.md`, `state.md`, `AGENTS.md`.

---

### 🟦 Step 5: Mob Farm & Swarm Optimizer
- [ ] Create `dev.hyperionfps.anim.MobFarmOptimizer`:
  - Spatial grouping: grid-hash entities by block position.
  - Detect clusters where > 15 living entities of the same type occupy a 5x5 horizontal area.
  - For clustered mobs outside player crosshair focus (> 4 blocks away):
    - Skip secondary bone/limb swing calculations.
    - Rate-limit animation ticks to 1/3 of normal frequency.
  - Exemptions: Player target, named mobs (`hasCustomName()`), leashed mobs, passengers.
- [ ] Dynamic Acoustic Dampening in `ThoriumSoundGovernor`:
  - When sound emission from a single species cluster exceeds 6 events / 50ms, dampen volume and drop duplicate ambient sound instances.
- [ ] JUnit test suite: `MobFarmOptimizerTest`.
- [ ] Verification: `./gradlew :26.3:test`.
- [ ] Doc Sync: Update `TODO.md`, `spec.md`, `state.md`, `AGENTS.md`.

---

### 🟦 Step 6: Explosion & Firework Shield (Particle Flood Capper)
- [ ] Create `dev.hyperionfps.particle.ExplosionShield`:
  - Integrate with `ThoriumParticleBudget`.
  - Track short-term emission rates for high-density particle categories:
    - `EXPLOSION`, `EXPLOSION_EMITTER`, `FIREWORK`, `CAMPFIRE_SMOKE`, `LARGE_SMOKE`.
  - When burst emission exceeds 250 particles/sec, cull redundant overlapping quads within the same 1x1 block area.
  - Crucial safety: allow initial explosion flash and primary puff to pass so visual game feedback remains intact.
- [ ] Wire check in particle creation mixins.
- [ ] JUnit test suite: `ExplosionShieldTest`.
- [ ] Verification: `./gradlew :26.3:test`.
- [ ] Doc Sync: Update `TODO.md`, `spec.md`, `state.md`, `AGENTS.md`.

---

### 🟦 Step 7: Elytra Flight Smoother & Modern Chunk Upload Parity
- [ ] Track player velocity vector $\vec{v}$ in `ThoriumFrameContext`:
  - Speed threshold check: active when $||\vec{v}|| > 15 \text{ m/s}$ (fast Elytra, ice boat, sprint-flight).
- [ ] Directional Chunk Priority in `ThoriumChunkUploadPacer`:
  - Bias section upload ordering: prioritize chunk sections within a $45^\circ$ forward cone along $\vec{v}$.
  - Defer sections behind the player.
- [ ] Modern 26.x Blaze3D upload hook:
  - Wire `ThoriumChunkUploadPacer.runBudgetedUploads()` into the modern chunk dispatch path (resolving the version disparity where 26.x previously lacked pacing).
- [ ] JUnit test suite: `ElytraFlightSmootherTest`.
- [ ] Verification: `./gradlew :26.3:test`.
- [ ] Doc Sync: Update `TODO.md`, `spec.md`, `state.md`, `AGENTS.md`.

---

### 🟦 Step 8: Ultra Low-Latency Reflex (Late Input Sampling)
- [ ] Create `dev.hyperionfps.input.ThoriumReflexController`:
  - Monitor input polling order.
  - Hook into `core/MinecraftFrameBoundaryMixin`: shift mouse coordinate update and camera angle delta calculation to the tail of the frame boundary wait, immediately prior to camera matrix setup.
  - Slashes input-to-photon latency by 8–14 ms.
  - Verify zero impact on network movement packets (tick logic untouched).
- [ ] JUnit test suite: `ThoriumReflexControllerTest`.
- [ ] Verification: `./gradlew :26.3:test`.
- [ ] Doc Sync: Update `TODO.md`, `spec.md`, `state.md`, `AGENTS.md`.

---

### 🟦 Step 9: No-Lag Weather & Clean Water / Fast Fog
- [ ] Create `dev.hyperionfps.render.WeatherFogOptimizer`:
  - **Weather Rendering Modes:**
    - `VANILLA`: Standard rain sheets and ground splash particles.
    - `MINIMAL`: Lightweight single-pass rain sheets; suppress ground splash particle flood.
    - `OFF`: Disable precipitation visual layer on client (sound/world weather intact).
  - **Clean Water & Fast Fog:**
    - Clamp underwater fog start/end distances to remove murky fragment shader overhead.
    - Optimize distance fog density curve.
- [ ] Wire hooks into legacy (`LegacyFogMixin`) and modern fog/weather renderers.
- [ ] JUnit test suite: `WeatherFogOptimizerTest`.
- [ ] Verification: `./gradlew :26.3:test`.
- [ ] Doc Sync: Update `TODO.md`, `spec.md`, `state.md`, `AGENTS.md`.

---

### 🟦 Step 10: Multi-Version Verification, Mixin Target Audit & Release Prep
- [ ] Full Matrix Compilation & Test:
  - Run `./gradlew test` across all 5 version nodes (`:1.21.1`, `:1.21.11`, `:26.1`, `:26.2`, `:26.3`).
  - Run `./gradlew buildAll` to produce 5 release jars in `build/dist/`.
- [ ] Mixin Target Audit:
  - Verify `MixinTargetAuditTest` completes with 0 failures on all 5 nodes.
- [ ] Final Documentation Sweep:
  - Update `README.md`, `MODRINTH.md`, `spec.md`, `state.md`, `AGENTS.md`, `TODO.md`.
  - Mark roadmap complete.

---

## Operating Protocol for AI Agents

1. **Atomic Execution:** Execute only one step at a time.
2. **Compile & Test Gate:** After modifying code, run `./gradlew :26.3:test` (or full `./gradlew test` when version-specific code is touched).
3. **Mandatory Documentation Update:** Before completing each step, update:
   - `TODO.md` (check off completed items, update status table)
   - `spec.md` (update requirements / specifications)
   - `state.md` (update current status board)
   - `AGENTS.md` (update any new conventions, rules, or gotchas)
4. **Context Continuity:** Ensure any new AI session reading these markdown files can immediately understand what was just completed and what the exact next step is.
