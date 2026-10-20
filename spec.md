# spec.md — Thorium Product & Technical Specification

**Mod id:** `thorium` · **Codename:** Th-90 · **Version:** 0.1.1
**Package:** `dev.hyperionfps` · **Loader:** Fabric · **Type:** Client-side optimization mod

> This document defines *what Thorium is and must do*. It is the requirement source of
> truth. Implementation status lives in [`state.md`](./state.md); build/working
> conventions live in [`AGENTS.md`](./AGENTS.md).

---

## 1. Product definition

Thorium is a **client-side rendering and frame-pacing optimization engine** for
Minecraft. It reduces **frametime variance** — the difference between a smooth frame
and a hitching frame — rather than chasing peak FPS alone.

### 1.1 Problem

Most optimization mods target average FPS. Players experience something different:
micro-stutters, chunk-loading freezes, GC hitches and entity-farm spikes. The visible
symptom is *jank*, not a low frame counter. Thorium treats 1% and 0.1% frame lows as the
primary metric.

### 1.2 Positioning

| Dimension | Thorium |
| :--- | :--- |
| Side | 100% client. Server does not need it. |
| Gameplay | **Zero gameplay modification.** No cheats, no packet tampering, no reach/fly. |
| Visuals | Vanilla lighting, textures, block models and fidelity preserved in Balanced & Nuclear modes. |
| Multiplayer | Safe on Hypixel, Realms, LAN and public SMPs. |
| Target user | Competitive players, speedrunners, builders, and low-spec hardware owners. |

### 1.3 Goals

* **G1** — Eliminate chunk-loading and Elytra hitches.
* **G2** — Eliminate GC-induced stutter by removing per-frame allocation.
* **G3** — Sub-millisecond frame pacing control with predictable delivery.
* **G4** — Cull invisible work (sections, entities, block entities, particles) before it
  reaches the GPU.
* **G5** — Make performance *observable* via a real-time telemetry HUD.
* **G6** — Ship one codebase across 5 Minecraft versions without forking the logic.

### 1.4 Non-goals

* Shader packs, visual overhauls, or any gameplay-affecting feature.
* Server-side optimization.
* Replacing Sodium/Iris — Thorium is compatible-by-coexistence, not a superset.
* Modifying Minecraft's networking, physics, or world state.

---

## 2. Requirements

Format: `REQ-<AREA>-<n>` · **Must** = required for v0.2 · **Should** = desirable.

### 2.1 Frame pacing & telemetry

| ID | Requirement | Priority |
| :--- | :--- | :--- |
| REQ-FRAME-1 | Track frametime history in a fixed circular buffer with **zero allocation per frame**. | Must |
| REQ-FRAME-2 | Classify a frame as a stutter when its duration exceeds `max(30 ms, 2.5 × rolling average)` **and** jitter vs. the previous frame exceeds 12 ms. | Must |
| REQ-FRAME-3 | Compute a **Smoothness Stability Index** (0–100 %) as the percentage of non-stutter frames in the history window. | Must |
| REQ-FRAME-4 | Provide a hybrid sleep→spin frame pacer that parks until 1.2 ms before the deadline, then spins, to hit the target frame time precisely. | Must |
| REQ-FRAME-5 | Support uncapped frame execution (target ≤ 0 or ≥ 260 FPS ⇒ no pacing). | Must |
| REQ-FRAME-6 | Override vanilla's focus-loss 30 FPS cap, overridable via `-Dhyperionfps.throttle_on_focus_loss=true`. | Should |
| REQ-FRAME-7 | Force VSync off during a 30 s boot window so the main menu renders at full speed. | Should |
| REQ-FRAME-8 | Discard implausible frame deltas (≤ 0 ns or > 500 ms) from statistics. | Must |
| REQ-FRAME-9 | The frame pacer must support a user-set target FPS (uncapped / 30 / 60 / 120 / 144 / 240), persisted across restarts. | Must |
| REQ-FRAME-10 | The camera context and every per-frame governor must be published **exactly once per rendered frame** — never once per entity. | Must |

### 2.2 Culling & visibility

| ID | Requirement | Priority |
| :--- | :--- | :--- |
| REQ-CULL-1 | Cull chunk sections beyond a configurable distance, scaled by a dynamic distance factor. | Must |
| REQ-CULL-2 | Cull entities, block entities and particles beyond distance thresholds, with stricter thresholds in Extreme mode. | Must |
| REQ-CULL-3 | Perform occlusion testing via a coarse 16×16×16 section grid, probing section centres then two opposite corners. | Must |
| REQ-CULL-4 | Perform bounded voxel raycasting (max 12 steps, 2 consecutive solid blocks ⇒ occluded) using a pooled `BlockPos`. | Must |
| REQ-CULL-5 | Provide hierarchical BVH cluster culling with a 12-block proximity guard so nearby entities are never cluster-culled. | Should |
| REQ-CULL-6 | Maintain a **dynamic distance scale** that shrinks when frame time exceeds 20 ms and grows when it falls below 14 ms, clamped to [0.35, 1.0]. | Must |
| REQ-CULL-7 | Never cull the camera's own chunk section. | Must |
| REQ-CULL-8 | Memoize block-entity occlusion results in a bounded cache with an explicit eviction path. | Must |

### 2.3 Memory & GC

| ID | Requirement | Priority |
| :--- | :--- | :--- |
| REQ-MEM-1 | Provide size-classed pools of direct `ByteBuffer`s (4 KiB → 1 MiB, 4 per class) with hit/miss/oversized/discarded/trimmed accounting. | Must |
| REQ-MEM-2 | Return native memory to the OS deterministically via `Unsafe.invokeCleaner`; degrade gracefully when unavailable. | Must |
| REQ-MEM-3 | Sweep idle pooled buffers on a daemon thread (60 s cadence, 120 s idle budget). | Must |
| REQ-MEM-4 | Provide thread-local ring-buffer pools for `BlockPos`, `Vec3`, `Matrix4f` and `Quaternion` so hot paths never allocate. | Must |
| REQ-MEM-5 | Expose a manual `purgeMemoryNow()` that clears culling caches, clears pools, sweeps and triggers `System.gc()`, returning MB reclaimed. | Should |
| REQ-MEM-6 | Never free a buffer the pool does not own (foreign/non-direct buffers are rejected). | Must |

### 2.4 Chunk & workload budgeting

| ID | Requirement | Priority |
| :--- | :--- | :--- |
| REQ-CHUNK-1 | Spread chunk mesh uploads across frames under a **dual budget**: ≤ 2.5 ms AND ≤ 8 sections per frame. | Must |
| REQ-CHUNK-2 | Yield remaining uploads to the next frame and count yielded frames for telemetry. | Must |
| REQ-CHUNK-3 | Throttle animated-texture uploads (every 2nd tick normally, every 4th tick in Extreme mode). | Must |
| REQ-CHUNK-4 | Rate-limit spatial sounds to 4 per 8×8×8 cell per 50 ms tick, never suppressing `MASTER`, `MUSIC`, `RECORDS`, `PLAYERS` or `VOICE`. | Should |
| REQ-CHUNK-5 | Throttle entity animation by distance LOD, exempting players, named entities and passengers. | Must |
| REQ-CHUNK-6 | Batch particles into 5 texture sheets (terrain, opaque, translucent, lit, custom) at 4096 quads per sheet. | Should |
| REQ-CHUNK-7 | Batch entity rendering into GPU instances, 4096 instances per batch at 32 bytes per instance. | Should |
| REQ-CHUNK-8 | Merge HUD/GUI draw calls when texture **and** shader IDs are unchanged. | Must |
| REQ-CHUNK-9 | Filter redundant OpenGL state changes (texture binds, program binds, blend, depth, cull). | Must |

### 2.5 Adaptive behaviour

| ID | Requirement | Priority |
| :--- | :--- | :--- |
| REQ-ADAPT-1 | Adjust render distance automatically via an EMA of instantaneous FPS (α = 0.15, 2 s cooldown). | Must |
| REQ-ADAPT-2 | Step distance down 1 chunk when smoothed FPS < target − 5; step up 1 chunk when > target + 10. | Must |
| REQ-ADAPT-3 | Clamp target FPS to [20, 360] and distance to [2, 64]. | Must |
| REQ-ADAPT-4 | Propagate light updates asynchronously via a daemon worker (256 tasks per batch, 10 ms idle park). | Should |
| REQ-ADAPT-5 | Detect the host launcher and GC from process evidence (`-Xmx` presence, ZGC/Shenandoah/G1/Parallel/Serial/CMS). | Should |

### 2.6 User interface

| ID | Requirement | Priority |
| :--- | :--- | :--- |
| REQ-UI-1 | Provide a frosted-glass HUD showing FPS, frametime, average, max spike, hitch count, smoothness % and RAM usage. | Must |
| REQ-UI-2 | Provide a video settings console whose controls are **honest**: a control that cannot change anything on the running version must be shown as *not available* / *not wired*, never as a working switch. | Must |
| REQ-UI-3 | Provide three hardware presets: **Potato**, **Balanced** (default), **Nuclear Max**. | Must |
| REQ-UI-4 | Bind a toggle to the HUD, a toggle to Extreme FPS mode, and a key to open the settings console. | Must |
| REQ-UI-5 | Expose the settings console from the standard Video Settings screen as well as its own hotkey. | Should |
| REQ-UI-6 | Show a live preview/explanation of what each toggle does and its hardware impact. | Should |
| REQ-UI-7 | Surface a one-line diagnostic telemetry summary combining memory, culling state, GL skips and batched draws. | Must |
| REQ-UI-8 | Provide a **Safe Mode** escape hatch, both as a settings toggle and as a `-D` system property, that disables every mixin and optimisation. | Must |

### 2.7 Platform & compatibility

| ID | Requirement | Priority |
| :--- | :--- | :--- |
| REQ-PLAT-1 | Support Minecraft 1.21.1, 1.21.11, 26.1, 26.2, 26.3 from a single shared source tree. | Must |
| REQ-PLAT-2 | Route mixins by era at launch: `core.*` always, `legacy.*` pre-26.1, `modern.*` 26.1+. | Must |
| REQ-PLAT-3 | Degrade gracefully — a failed mixin injection must never crash the game (`defaultRequire: 0`). | Must |
| REQ-PLAT-4 | Abstract engine differences behind a `VersionBridge` (window/render/entity/math sub-bridges). | Must |
| REQ-PLAT-5 | Require Fabric Loader ≥ 0.16.0 and Fabric API. | Must |
| REQ-PLAT-6 | Keep all telemetry disabled by policy — no session data leaves the client. | Must |
| REQ-PLAT-7 | Build all version targets from one command and aggregate jars into `build/dist`. | Must |
| REQ-PLAT-8 | Persist all user-facing settings to disk so presets and toggles survive a restart. | Must |
| REQ-PLAT-9 | Never let an optimisation failure prevent the game from starting: every pre-launch and startup step is individually guarded. | Must |
| REQ-PLAT-10 | Never call a native graphics function without a current context; guard it so it cannot abort the process on unusual GL stacks. | Must |
| REQ-COMP-1 | Thorium must detect competing renderers (Sodium/Embeddium family) and yield the overlapping subsystems automatically. | Must |
| REQ-COMP-2 | Shader-pack presence must disable the fast-math overrides. | Must |
| REQ-DIAG-1 | The startup log must report the **actual** live state of every subsystem, distinguishing ACTIVE / IDLE / DISABLED / UNWIRED / UNSUPPORTED. | Must |
| REQ-DIAG-2 | Every mixin target must be verified to exist at build time; a missing target fails the build. | Must |

### 2.8 Quality

| ID | Requirement | Priority |
| :--- | :--- | :--- |
| REQ-QUAL-1 | Every subsystem ships with unit tests covering policy, toggles, null-safety and accounting. | Must |
| REQ-QUAL-2 | Tests are plain-JVM JUnit 5 — no Minecraft bootstrap required. | Must |
| REQ-QUAL-3 | All 5 version nodes must compile and test green before any release. | Must |
| REQ-QUAL-4 | Documented conventions live in `AGENTS.md`; requirements in `spec.md`; status in `state.md`. | Must |

---

## 3. Architecture

### 3.1 Layer model

```
┌──────────────────────────────────────────────────────────────┐
│  Presentation     gui/            HUD, Video Settings screen  │
├──────────────────────────────────────────────────────────────┤
│  Integration      mixin/          core/* (all versions)      │
│                                   legacy/*, modern/* (per mc) │
├──────────────────────────────────────────────────────────────┤
│  Version facade   version/        VersionBridge, GameVersion  │
├──────────────────────────────────────────────────────────────┤
│  Policy engines   frame/ culling/ chunk/ memory/             │
│                   anim/ audio/ texture/ particle/ instancing/ │
│                   lighting/ batch/ render/ adaptive/         │
├──────────────────────────────────────────────────────────────┤
│  Runtime core     core/           RenderThreads, ThreadGovernor│
│                   env/            LauncherEnvironment         │
└──────────────────────────────────────────────────────────────┘
```

**Dependency direction is strictly downward.** Policy engines never import mixins or GUI
code. Mixins call *into* policy engines; policy engines never call mixins.

### 3.2 Component inventory

| Package | Key classes | Responsibility |
| :--- | :--- | :--- |
| `core` | `RenderThreads`, `ThreadGovernor`, `TelemetryGate`, `ThoriumSafety`, `ThoriumCompatibility`, `ThoriumSubsystemReport` | Client-thread capture, priority elevation, telemetry kill-switch, mod-compatibility probe, subsystem status report |
| `frame` | `ThoriumFrametimeTracker`, `ThoriumFramePacer`, `FramePacing`, `ThoriumFrameContext` | Frametime history, sleep→spin pacing, boot-window VSync policy, authoritative per-frame context |
| `culling` | `AsyncOcclusionCuller`, `SectionCullingEngine`, `OcclusionGrid`, `OcclusionRaycaster`, `ThoriumBvhCuller`, `FrustumCache` | Visibility decisions for sections, entities, block entities, particles |
| `memory` | `DirectBufferPool`, `OffHeap`, `IdleJanitor`, `ThoriumMemoryOptimizer`, `pool/*` | Zero-allocation pools, native memory return, idle sweeping, heap defence |
| `chunk` | `ThoriumChunkUploadPacer` | Dual-budget chunk mesh upload scheduling |
| `anim` | `ThoriumEntityLodThrottler` | Distance-based animation LOD |
| `audio` | `ThoriumSoundGovernor` | Spatial sound rate limiting |
| `texture` | `ThoriumTextureThrottler` | Animated-texture upload throttling |
| `particle` | `ThoriumParticleBudget`, `ParticleBatchEngine`, `ParticleBatchPool`, `ParticleQuad` | Particle creation budget, sheet batching |
| `adaptive` | `AdaptiveDistanceController`, `RenderDistanceGovernor` | EMA-driven render-distance governor and its binding to the game option |
| `instancing` | `EntityInstancer`, `EntityGroupManager`, `EntityGroup`, `EntityInstanceData` | GPU entity instancing |
| `lighting` | `AsyncLightEngine` | Async light update queue + worker |
| `batch` | `ThoriumImmediateBatcher` | Immediate-mode 2D draw batching policy |
| `render` | `HudBatcher`, `GlStateTracker` | HUD draw merging, redundant GL state filtering |
| `env` | `LauncherEnvironment` | Launcher/GC/heap evidence probe |
| `gui` | `ThoriumGuiManager` + per-version screens | HUD state, Extreme FPS toggle, telemetry summary |
| `version` | `VersionBridge`, `DefaultVersionBridge`, `GameVersion`, `Era`, `GameVersionDetector`, `VersionBridgeRegistry` | Version abstraction and detection |
| `mixin` | `HyperionMixinPlugin`, `core/*`, `legacy/*`, `modern/*` | Engine hooks and era routing |

### 3.3 Key constants

| Constant | Value | Location |
| :--- | :--- | :--- |
| Frametime sample window | 128 (power of two) | `ThoriumFrametimeTracker` |
| Stutter threshold | `max(30 ms, 2.5 × avg)` with 12 ms jitter | `ThoriumFrametimeTracker` |
| Frame pacing spin margin | 1.2 ms | `ThoriumFramePacer` |
| Boot VSync window | 30 000 ms uptime | `FramePacing` |
| Chunk upload budget | 2 500 000 ns **and** 8 sections | `ThoriumChunkUploadPacer` |
| Raycast step cap | 12 | `OcclusionRaycaster` |
| Occlusion probe block | 2 consecutive solid blocks | `OcclusionRaycaster` |
| BVH proximity guard | 144.0 (12 blocks) | `ThoriumBvhCuller` |
| Dynamic distance scale range | 0.35 – 1.0; step −0.05 / +0.02 | `AsyncOcclusionCuller` |
| Distance scale triggers | > 20 ms shrink, < 14 ms grow | `AsyncOcclusionCuller` |
| Entity cull distance | 64.0 (Extreme 24.0) | `AsyncOcclusionCuller` |
| Block entity cull distance | 48.0 (Extreme 16.0) | `AsyncOcclusionCuller` |
| Particle budget / distance | 1500 (Extreme 64) / 1024.0 (Extreme 144.0) | `AsyncOcclusionCuller` |
| Section cull distance | 256.0 (Extreme 128.0) | `SectionCullingEngine` |
| Direct buffer size classes | 2^12 – 2^20, 4 per class | `DirectBufferPool` |
| BlockPos pool size | 64 (ring buffer) | `BlockPosPool` |
| Janitor cadence / idle budget | 60 s / 120 s | `IdleJanitor` |
| Light batch size / idle park | 256 / 10 ms | `AsyncLightEngine` |
| Particle sheets / quads per sheet | 5 / 4096 (40 floats per quad) | `ParticleBatchEngine` |
| Instance batch size / stride | 4096 / 32 bytes | `EntityInstancer` |
| EMA alpha / adjust interval | 0.15 / 2000 ms | `AdaptiveDistanceController` |
| Render thread priority | `Thread.MAX_PRIORITY` (10) | `ThreadGovernor` |

---

## 4. Behavioural specification

### 4.1 Extreme FPS Mode

A single global mode that trades visual fidelity for throughput.

| Aspect | Extreme ON | Extreme OFF (default) |
| :--- | :--- | :--- |
| Entity cull distance | 24.0 | 64.0 |
| Block entity cull distance | 16.0 | 48.0 |
| Section cull distance | 128.0 | 256.0 |
| Particle budget | 64 | 1500 |
| Animated texture upload | every 4th tick | every 2nd tick |
| Entity LOD | 20/12/8 m tiers | 32/16 m tiers |
| Fog / weather / clouds | disabled by mixins | vanilla |

Toggle: `ThoriumGuiManager.toggleExtremeFpsMode()` → `AsyncOcclusionCuller.setExtremeFpsMode(boolean)`.
State change is logged. The toggle is in-memory only.

### 4.2 Hardware presets

| Preset | Target | Behaviour |
| :--- | :--- | :--- |
| **Potato** | Integrated GPU / low-spec laptop | Aggressive culling, throttled distant entity animations, maximum frame throughput. |
| **Balanced** *(default)* | Everyday desktop / laptop | Silky 1 % lows, native memory compaction, full vanilla visual fidelity. |
| **Nuclear Max** | Dedicated GPU / 144 Hz+ | Uncapped frame execution, predictive thread pacer, sub-millisecond input queueing, extreme micro-stutter suppression. |

Presets are applied from the video settings screen (per-version `ThoriumVideoSettingsScreen`).

### 4.3 Frame pipeline (per frame)

```
1. Minecraft thread capture (once, at construction)
2. FramePacing boot-window check ── VSync override if uptime < 30 s
3. ThoriumFrameContext.beginClientFrame() ── Minecraft#runTick hook (exactly once per
   client frame): frametime sample + ThoriumFramePacer.paceFrame()
4. ThoriumFrameContext.beginWorldFrame(camera, frustum, level) ── LevelRenderer hook
   (exactly once per rendered world frame): camera context, dynamic distance scale,
   BVH cache flush, adaptive-distance EMA, entity LOD
5. Culling decisions  ── entities / block entities / particles
6. ThoriumChunkUploadPacer.runBudgetedUploads(queue) ── ≤2.5 ms AND ≤8 sections
7. Particle + entity batching ── flush sheets / instance groups
8. HUD batching + GL state filtering
9. ThoriumFrametimeTracker.recordFrame() ── update stutter statistics
```

> **The frame boundary is authoritative.** Everything that must happen once per frame
> hangs off `ThoriumFrameContext`. Before it existed, the modern entity-culling hook
> published the camera context *once per entity*, which flushed the BVH cache, stepped
> the dynamic distance scale and fed the adaptive-distance EMA hundreds of times per
> frame.

### 4.4 Hotkeys

| Key | Action | Availability |
| :--- | :--- | :--- |
| `F6` | Toggle Nuclear Stutter Monitor HUD | All versions |
| `F7` | Toggle Extreme FPS Boost Mode | All versions |
| `Shift+F10` | Toggle Extreme FPS Boost Mode (alias) | All versions |
| `F8` | Open Thorium Video Settings Console | All versions |

> Every version also exposes a **Thorium Settings** button on the title screen and on
> the Options screen, so the console is reachable even without a working hotkey.

---

## 5. Data & telemetry model

### 5.1 Persisted state

User settings are persisted to a single flat JSON file —
`<config-dir>/thorium-config.json` — written atomically via a temporary file and an
atomic move. Unknown keys are preserved on load, so downgrading and re-upgrading does
not silently drop settings.

| Key | Meaning | Default |
| :--- | :--- | :--- |
| `preset` | Active hardware preset (`POTATO`/`BALANCED`/`NUCLEAR`/`CUSTOM`) | `BALANCED` |
| `safeMode` | Disables every Thorium mixin and optimisation | `false` |
| `hud.enabled` | Nuclear Stutter Monitor HUD | `true` |
| `culling.extremeFps` | Extreme FPS Mode | `false` |
| `culling.enabled` | Master culling switch | `true` |
| `culling.section.enabled` | Chunk-section culling | `true` |
| `culling.occlusionGrid.enabled` | Occlusion grid | `true` |
| `culling.bvh.enabled` | BVH cluster culling | `true` |
| `anim.entityLod.enabled` | Entity animation LOD | `true` |
| `frame.pacer.enabled` | Zero-jitter frame pacer | `true` |
| `frame.pacer.targetFps` | Frame cap for the pacer; `0` = uncapped | `0` |
| `compat.autoYield` | Stand down the culling for a competing renderer | `true` |
| `math.fast.enabled` | Fast-math overrides of `Mth` (off under a shader pack) | `false` |
| `input.pacer.enabled` | Reflex-style input pacing | `true` |
| `chunk.uploader.enabled` | Budgeted chunk upload pacer | `true` |
| `texture.throttler.enabled` | Animated-texture throttling | `true` |
| `texture.throttler.divider` | Texture throttle rate | `2` |
| `batch.immediate.enabled` | Immediate-mode 2D batching | `true` |
| `batch.hud.enabled` | HUD draw-call merging | `true` |
| `audio.governor.enabled` | Spatial sound governor | `true` |
| `particle.simulator.enabled` | *removed* — replaced by `particle.budget.enabled` | — |
| `particle.budget.enabled` | Particle creation budget and culling | `true` |
| `particle.batcher.enabled` | Particle sheet batching | `true` |
| `entity.instancer.enabled` | GPU entity instancing | `true` |
| `lighting.async.enabled` | Async light engine | `true` |
| `adaptive.distance.enabled` | Adaptive render distance | `true` |

Persistence rules:
* The file is written on every user action (toggle click, preset selection, screen close).
* A missing or corrupt file degrades silently to compiled defaults — it never prevents
  the game from starting.
* The format is a flat JSON object of string values, parsed by a hand-rolled reader so
  the mod adds **no new dependency** to any version node.

### 5.2 Safe mode

Safe mode is an emergency escape hatch for platforms where Thorium's hooks interact
badly with the host graphics stack (software rasterisers, Android OpenGL translation
layers, freshly released drivers).

| Trigger | Effect |
| :--- | :--- |
| `-Dhyperionfps.safeMode=true` | Honoured from the first line of mixin bootstrap, **before any Minecraft class is touched**. Every mixin is skipped. |
| `safeMode` key in the config file | Read during client initialisation, i.e. before the window is created. Disables all subsystems and makes the frame-pacing hook a no-op. |

The two sources are additive. The property is checked first on purpose: a player whose
game cannot start at all must be able to recover without editing files. Safe mode is
also exposed as a toggle in the settings console (Advanced tab).

### 5.3 Runtime-only state

The following state is intentionally **not** persisted — it is either a per-session
measurement or a value the game owns:

| State holder | Type | Default |
| :--- | :--- | :--- |
| `AsyncOcclusionCuller.dynamicDistanceScale` | `volatile double` | `1.0` (adapted at runtime) |
| `AsyncOcclusionCuller.enabled` | `volatile boolean` | `true` (persisted) |
| `SectionCullingEngine.ENABLED` | `AtomicBoolean` | `true` (persisted) |
| `OcclusionGrid.ENABLED` | `AtomicBoolean` | `true` (persisted) |
| `ThoriumBvhCuller.enabled` | `volatile boolean` | `true` (persisted) |
| `ThoriumChunkUploadPacer.enabled` | `volatile boolean` | `true` (persisted) |
| `ThoriumFramePacer.enabled` | `volatile boolean` | `true` (persisted) |
| `ThoriumMemoryOptimizer` dedup / off-heap | `AtomicBoolean` ×2 | `true` |
| `ThoriumTextureThrottler.enabled` | `AtomicBoolean` | `true` (persisted) |
| `ThoriumEntityLodThrottler.enabled` | `volatile boolean` | `true` (persisted) |
| `ThoriumSoundGovernor.enabled` | `volatile boolean` | `true` (persisted) |
| `ThoriumImmediateBatcher.enabled` | `AtomicBoolean` | `true` (persisted) |
| `HudBatcher.enabled` | `AtomicBoolean` | `true` (persisted) |
| `EntityInstancer.enabled` / `ParticleBatchEngine.enabled` | `AtomicBoolean` | `true` (persisted) |
| `AsyncLightEngine.enabled` | `AtomicBoolean` | `true` (persisted) |
| `AdaptiveDistanceController.enabled` | `AtomicBoolean` | `true` (persisted) |
| `ThoriumGuiManager.HUD_ENABLED` | `AtomicBoolean` | `true` (persisted) |
| `AdaptiveDistanceController.currentDistance` | `volatile int` | `12` (adapted at runtime) |

### 5.4 External inputs

| Input | Effect |
| :--- | :--- |
| `-Dhyperionfps.gameVersion=<v>` | Overrides detected Minecraft version (testing seam). |
| `-Dhyperionfps.throttle_on_focus_loss=true` | Re-enables vanilla's focus-loss 30 FPS cap. |
| `hyperionfps.prelaunch` | Set by `HyperionPrelaunch` to mark the prelaunch phase. |
| `fabric.telemetry.enabled=false`, `minecraft.telemetry.enabled=false`, `fabric.disable_telemetry=true` | Set by `HyperionPrelaunch` to disable telemetry upstream. |

### 5.5 Telemetry policy

`TelemetryGate.allowed()` **always returns `false`**. The mixin returns
`TelemetryEventSender.DISABLED`. This is a hard policy decision, not a setting. No
session data, telemetry or analytics leaves the client under any configuration.

---

## 6. Non-functional requirements

| ID | Requirement |
| :--- | :--- |
| NFR-1 | **Zero allocation** in per-frame paths. Pooled/primitive types only. |
| NFR-2 | **No blocking** on the render thread. No `synchronized`, no locks, no I/O. |
| NFR-3 | **Bounded work** — every loop and ray has a hard maximum. |
| NFR-4 | **Graceful degradation** — a subsystem failing must not break the game. |
| NFR-5 | **Daemon-only background threads** — the game can always exit. |
| NFR-6 | **Deterministic** — no wall-clock dependence in policy decisions (use nanoTime deltas). |
| NFR-7 | **Single source of truth** — no duplicated logic between shared and version-specific trees. |
| NFR-8 | **Testable** — policy logic must be reachable without a live Minecraft instance. |

---

## 7. Release requirements

A release is valid only when:

1. `./gradlew test` is green on **all five** version nodes.
2. `./gradlew buildAll` produces five jars in `build/dist/`.
3. `fabric.mod.json` metadata resolves per node (`${id}`, `${version}`, `${minecraft}`,
   `${java_version}`).
4. The mixin router log line shows the expected `eligible`/`skipped`/`applied` counts for
   the running version.
5. The mixin target audit (`MixinTargetAuditTest`) passes on all five nodes — a dead
   target fails the build instead of the runtime.
6. `README.md` and `MODRINTH.md` list the correct version matrix and hotkeys.
7. `state.md` reflects the shipped state.

---

## 8. Glossary

| Term | Meaning |
| :--- | :--- |
| **Frame low** | The FPS achieved at the 1 % / 0.1 % slowest frames. Thorium's real metric. |
| **Stutter** | A frame exceeding `max(30 ms, 2.5 × average)` with > 12 ms jitter. |
| **Smoothness Stability Index** | Percentage of non-stutter frames in the 128-sample window. |
| **Era** | `LEGACY` (Minecraft < 26.1, obfuscated) or `UNOBFUSCATED` (≥ 26.1). |
| **Extreme FPS Mode** | Global low-fidelity, high-throughput mode. |
| **Node** | One Stonecutter subproject = one Minecraft version target. |
| **Era family** | The `core` / `legacy` / `modern` mixin package prefixes used for routing. |
