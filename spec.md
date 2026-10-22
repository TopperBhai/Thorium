# spec.md — Thorium Product & Technical Specification

**Mod id:** `thorium` · **Codename:** Th-90 · **Version:** 0.1.2
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
| REQ-CULL-9 | Apply storage-specific culling to chest-type block entities (`ChestBlockEntity`, `TrappedChestBlockEntity`, `EnderChestBlockEntity`, `ShulkerBoxBlockEntity`) and item frames (`ItemFrame`, `GlowItemFrame`): a container whose six face neighbours are all opaque full blocks that carry no block entity is invisible from every angle and is skipped; a container beyond the storage distance is skipped only when it is also outside the player's direct focal cone; an item frame beyond the item-frame distance is skipped outright. The container the player currently has open is never skipped. | Must |

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
| REQ-ADAPT-1 | Adjust render distance automatically via an EMA of instantaneous FPS (α = 0.15, 2 s evaluation interval). | Must |
| REQ-ADAPT-2 | Step distance **down** 1 chunk when smoothed FPS < target − 5 for 2 consecutive evaluations. The governor must **never** raise the distance on its own: every change to `Options#renderDistance` makes the client drop and re-download its whole chunk cache. | Must |
| REQ-ADAPT-3 | Clamp target FPS to [20, 360] and distance to [2, 64]. | Must |
| REQ-ADAPT-4 | Propagate light updates asynchronously via a daemon worker (256 tasks per batch, 10 ms idle park). | Should |
| REQ-ADAPT-5 | Detect the host launcher and GC from process evidence (`-Xmx` presence, ZGC/Shenandoah/G1/Parallel/Serial/CMS). | Should |
| REQ-ADAPT-6 | Rate-limit distance changes: at most one applied change per 30 s settle window, and none during the first 10 s of a world (the loading hitch must not trigger a reload). | Must |
| REQ-ADAPT-7 | The adaptive render distance is **opt-in** (`adaptive.distance.enabled` defaults to `false`) and is exposed as a switch in the settings console on every version. | Must |
| REQ-ADAPT-8 | The client must never write `Options#serverRenderDistance`; that field is owned by the server and is set from `ClientboundSetChunkCacheRadiusPacket`. | Must |

### 2.7 Community invite

| ID | Requirement | Priority |
| :--- | :--- | :--- |
| REQ-DISC-1 | Show a one-time invite card over the main menu on the first launch, offering the community invite (`https://discord.gg/TbJtmvJKB3`). | Should |
| REQ-DISC-2 | The card is offered **exactly once**: it is shown when the main menu first ticks and it is never shown again after it has been shown or dismissed. | Must |
| REQ-DISC-3 | The card is opt-out: `discord.invite.enabled` (default `true`) and `discord.invite.shown` are persisted in `thorium-config.json`. | Must |
| REQ-DISC-4 | The invite stays reachable from the settings console (*Community Discord* → opens the link in the browser) on every version. | Must |
| REQ-DISC-5 | The browser hand-off is era-specific (`net.minecraft.Util` / `net.minecraft.util.Util` / `Blaze3D.openUri`) and therefore lives in the per-version `ThoriumDiscordScreen`. | Must |
| REQ-DISC-6 | The invite is recorded as shown the moment the card is opened, so closing the game while the card is on screen does not re-offer it on the next launch. | Must |

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
| REQ-UI-9 | The settings console must stay usable on a short window: the rows scroll inside the band between the header and the footer bar, so a row is never drawn over the footer and every row stays reachable. | Must |
| REQ-UI-10 | Every settings row and every hardware preset must be defined **once** in shared code (`ThoriumSettingRows`, `ThoriumPresets`) and rendered by all five version nodes, so a feature can never exist on one Minecraft version and be missing on another. | Must |
| REQ-UI-11 | A headline feature whose implementation has not landed must be shown as `NOT AVAILABLE`, must refuse the click, and must be reported as `UNWIRED` in the startup subsystem report. | Must |

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

### 2.9 Next-Gen Innovation & Player-Facing Toggles (Headline Set)

| ID | Requirement | Priority |
| :--- | :--- | :--- |
| REQ-HEAD-1 | **Mob Farm Optimizer** (`anim.mob_farm.enabled`): Detect dense mob clusters (>15 entities in 5x5 block area) and decouple/throttle secondary animation bone ticks and limb swings for non-targeted, unnamed mobs. The animation of a clustered mob must be recomputed on **one frame out of three** (the two skipped frames perform no bone or limb-swing work at all), and a mob within **4 blocks** of the camera must never be throttled. The local player, a named mob, a leashed mob and a passenger are exempt: an exempt mob is not counted towards the cluster and protects its whole cell. A cell of a mob farm must also dampen its own ambience: after **6 sound events in 50 ms** the surplus is played at **50 % volume**, and everything beyond twice that is dropped. | Must |
| REQ-HEAD-2 | **Storage Room & Item Frame Culling** (`culling.storage_item_frames.enabled`): Aggressively cull chest-type block entities (`ChestBlockEntity`, `TrappedChestBlockEntity`, `EnderChestBlockEntity`, `ShulkerBoxBlockEntity`) and item frames when enclosed by opaque blocks or at distance, exempting open containers. | Must |
| REQ-HEAD-3 | **Elytra Flight Smoother** (`chunk.elytra_smoother.enabled`): Prioritize chunk mesh uploads within a 45° forward cone along the player velocity vector $\vec{v}$ when speed exceeds 15 m/s, bringing upload pacing parity to 26.x Blaze3D pipelines. A section behind the flight vector is **deferred**, not dropped: the sections inside the forward cone are built first, the neutral ones next, the ones behind the player last. Three guards keep the deferral from ever becoming a visible hole — a section closer than **16 blocks** is never deferred, a section that has waited for more than **500 ms** is released regardless of its priority, and a section that can no longer be scheduled is dropped instead of crashing the frame. | Must |
| REQ-HEAD-4 | **AFK Battery & Power Saver (Stealth AFK™)** (`frame.afk_eco.enabled`): Detect window focus loss or 60 s input inactivity, clamping render rate to 10 FPS and parking via `LockSupport.parkNanos` (cutting CPU/GPU power up to 75%), waking instantly (0 ms) on mouse/key activity. The park budget must be spent in slices, so an input event is noticed within one slice rather than after the whole park, and the frametime measurement must be suspended while the throttle runs — an intentional throttle is not a stutter. | Must |
| REQ-HEAD-5 | **Instant Lag Spike Alert (Nuclear Stutter Hunter™)** (`telemetry.stutter_alert.enabled`): Track 5-phase frame execution breakdown and correlate JVM `GarbageCollectorMXBean` pause events, displaying an instant HUD toast attributing freezes (e.g. `Spike: 58ms [GC: 34ms \| Mobs: 18ms \| Chunk: 6ms]`). | Must |
| REQ-HEAD-6 | **Explosion & Firework Shield** (`particle.explosion_shield.enabled`): Cap high-density particle floods from TNT explosions, Wither spawns, and fireworks when burst rates exceed 250 particles/sec, preventing frame freezes while preserving telegraph cues. The shield watches exactly five categories (`EXPLOSION`, `EXPLOSION_EMITTER`, `FIREWORK`, `CAMPFIRE_SMOKE`, `LARGE_SMOKE`); every other particle is untouched. A continuously refilled token bucket admits at most **250 particles/sec** across them, and a per-one-block-cell bucket admits at most **48 particles/sec** per cell, which removes the quads that are drawn on top of each other. The **first particle of every category inside a burst is protected**: it bypasses the rate limit, the per-cell limit *and* the global creation budget, so the explosion flash and the primary puff always survive. A cell that has been quiet for 250 ms starts a new burst. | Must |
| REQ-HEAD-7 | **Ultra Low-Latency Reflex** (`input.reflex.enabled`): Drain the mouse and keyboard event queue a second time at the tail of the frame - immediately before the game applies the pending cursor movement, which is where the camera matrix is built - so that movement which arrived while the frame was working is applied to the frame the player is looking at instead of the next one. The implementation must **not** touch the tick logic (the movement packets the client sends stay exactly the ones vanilla would send), must **not** re-implement the input pipeline (the game's own routine drains the queue, so every event is still delivered exactly once), and must **measure** what it recovered instead of advertising a fixed latency figure. | Must |
| REQ-HEAD-8 | **No-Lag Weather & Rain** (`render.weather.mode`): Support `VANILLA`, `MINIMAL` and `OFF`. `VANILLA` touches nothing. `MINIMAL` keeps the rain sheets exactly as vanilla draws them but drops the **ground splash particles** - the flood a downpour spawns (`0.225 × (2r+1)² × rainLevel²` per tick) is what costs the frames, and the player cannot tell one splash from the next. `OFF` removes the precipitation layer from the client's screen **without affecting the server precipitation state** and **without affecting the rain sound**. The splash must be dropped at the particle call and never at the method that spawns it, because on every supported version the same method plays the rain sound. | Must |
| REQ-HEAD-9 | **Clean Water & Fast Fog** (`render.clean_water_fog.enabled`): Clamp the underwater fog to a short, crisp distance (the end at most 48 blocks, the start no longer behind the camera) and narrow the distance fog band to at most 24 blocks, so the water reads as a clean, even tint and the horizon resolves sharply. The band is never widened, so at the default render distance nothing changes. This requirement is **visual**: the fog shader does the same fragment work whether the band is 24 or 64 blocks wide, so no fill-rate claim may be made for it - the measurable work reduction of this step comes from the weather side (REQ-HEAD-8). | Must |
| REQ-HEAD-10 | **Purge RAM Now** (`ThoriumMemoryOptimizer.purgeMemoryNow()`): Expose an interactive action button in the settings console that flushes culling caches, clears direct buffer pools, and sweeps heap/off-heap memory, displaying reclaimed megabytes. The number shown must be the **measured** amount - a fabricated minimum is a lie. | Must |

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
| `core` | `RenderThreads`, `ThreadGovernor`, `TelemetryGate`, `ThoriumSafety`, `ThoriumCompatibility`, `ThoriumSubsystemReport`, `ThoriumFeatureToggles` | Client-thread capture, priority elevation, telemetry kill-switch, mod-compatibility probe, subsystem status report, headline feature switches |
| `frame` | `ThoriumFrametimeTracker`, `ThoriumFramePacer`, `FramePacing`, `ThoriumFrameContext`, `ThoriumAfkManager` | Frametime history, sleep→spin pacing, boot-window VSync policy, authoritative per-frame context, AFK power saver |
| `culling` | `AsyncOcclusionCuller`, `StorageCullingEngine`, `SectionCullingEngine`, `OcclusionGrid`, `OcclusionRaycaster`, `ThoriumBvhCuller`, `FrustumCache` | Visibility decisions for sections, entities, block entities, particles, and the storage-room specific chest / item-frame rules |
| `memory` | `DirectBufferPool`, `OffHeap`, `IdleJanitor`, `ThoriumMemoryOptimizer`, `pool/*` | Zero-allocation pools, native memory return, idle sweeping, heap defence |
| `chunk` | `ThoriumChunkUploadPacer`, `ElytraFlightSmoother`, `PlayerVelocityPublisher` | Dual-budget chunk mesh upload scheduling, the flight-vector section order, and the single adapter that reads the player entity |
| `anim` | `MobFarmOptimizer`, `ThoriumEntityLodThrottler` | Mob-farm cluster animation throttling + acoustic dampening, distance-based animation LOD |
| `audio` | `ThoriumSoundGovernor` | Spatial sound rate limiting |
| `input` | `ThoriumReflexController`, `ReflexEventPump`, `ThoriumInputPacer` | Late input sampling (REQ-HEAD-7): the decision, the accounting and the platform seam for draining the event queue. `ThoriumInputPacer` is the leftover telemetry-only counter (no call site — see `state.md` §4.2) |
| `texture` | `ThoriumTextureThrottler` | Animated-texture upload throttling |
| `particle` | `ThoriumParticleBudget`, `ExplosionShield`, `ExplosionParticleFilter`, `ParticleBatchEngine`, `ParticleBatchPool`, `ParticleQuad` | Particle creation budget, explosion & firework shield, sheet batching |
| `adaptive` | `AdaptiveDistanceController`, `RenderDistanceGovernor` | EMA-driven render-distance governor and its binding to the game option |
| `instancing` | `EntityInstancer`, `EntityGroupManager`, `EntityGroup`, `EntityInstanceData` | GPU entity instancing |
| `lighting` | `AsyncLightEngine` | Async light update queue + worker |
| `batch` | `ThoriumImmediateBatcher` | Immediate-mode 2D draw batching policy |
| `render` | `HudBatcher`, `GlStateTracker`, `WeatherFogOptimizer` | HUD draw merging, redundant GL state filtering, the weather-mode and fog-clamp policy (REQ-HEAD-8 / REQ-HEAD-9) |
| `env` | `LauncherEnvironment` | Launcher/GC/heap evidence probe |
| `gui` | `ThoriumGuiManager`, `ThoriumSettingsLayout`, `ThoriumSettingRows`, `ThoriumToastQueue` + per-version screens | HUD state, Extreme FPS toggle, telemetry summary, shared settings-row catalogue, in-game toast notifications |
| `telemetry` | `ThoriumStutterHunter`, `ThoriumPhaseTimer`, `ThoriumGcWatcher` | Per-frame phase breakdown, GC pause correlation, instant stutter attribution |
| `config` | `ThoriumConfig`, `ThoriumPresets` | Atomic settings persistence, hardware preset profiles |
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
| Storage container cull distance | 32.0 (Extreme 16.0) | `StorageCullingEngine` |
| Item frame cull distance | 24.0 (Extreme 12.0) | `StorageCullingEngine` |
| Focal cone half angle | 45° (cos 0.7071) | `StorageCullingEngine` |
| Focal cone minimum distance | 16.0 (Extreme 8.0) | `StorageCullingEngine` |
| Storage enclosure probes | 6 face neighbours, short-circuiting | `StorageCullingEngine` |
| Particle budget / distance | 1500 (Extreme 64) / 1024.0 (Extreme 144.0) | `AsyncOcclusionCuller` |
| Section cull distance | 256.0 (Extreme 128.0) | `SectionCullingEngine` |
| Direct buffer size classes | 2^12 – 2^20, 4 per class | `DirectBufferPool` |
| BlockPos pool size | 64 (ring buffer) | `BlockPosPool` |
| Janitor cadence / idle budget | 60 s / 120 s | `IdleJanitor` |
| Light batch size / idle park | 256 / 10 ms | `AsyncLightEngine` |
| Particle sheets / quads per sheet | 5 / 4096 (40 floats per quad) | `ParticleBatchEngine` |
| Instance batch size / stride | 4096 / 32 bytes | `EntityInstancer` |
| EMA alpha / adjust interval | 0.15 / 2000 ms | `AdaptiveDistanceController` |
| Adaptive settle window / warm-up | 30 s / 10 s per world | `AdaptiveDistanceController`, `ThoriumFrameContext` |
| Render thread priority | `Thread.MAX_PRIORITY` (10) | `ThreadGovernor` |
| AFK Eco target frame rate | 10 FPS | `ThoriumAfkManager` |
| AFK Eco default timeout | 60 s (5 s – 3600 s) | `ThoriumFeatureToggles` |
| Stutter spike threshold | `max(30 ms, 2.5 × avg)` with 12 ms jitter | `ThoriumStutterHunter` |
| Explosion particle burst cap | 250 particles/s (continuously refilled bucket) | `ExplosionShield` |
| Explosion per-cell cap | 48 particles/s per 1×1×1 block cell | `ExplosionShield` |
| Explosion protected particles | first of each category per cell burst | `ExplosionShield` |
| Explosion cell idle window | 250 ms | `ExplosionShield` |
| Priority particle admissions | outside the creation budget | `ThoriumParticleBudget` |
| Mob farm cluster threshold | > 15 entities of one species in a 5×5 block cell | `MobFarmOptimizer` |
| Mob farm animation rate | one frame out of three | `MobFarmOptimizer` |
| Mob farm focus radius | 4 blocks around the camera | `MobFarmOptimizer` |
| Mob farm sound budget | 6 events / 50 ms, dampened to 50 % | `ThoriumSoundGovernor` |
| Elytra flight speed threshold | > 15 m/s | `ElytraFlightSmoother` |
| Elytra forward cone | 45° forward, 45° rear | `ElytraFlightSmoother` |
| Elytra minimum defer distance | 16 blocks from the camera | `ElytraFlightSmoother` |
| Elytra maximum defer window | 500 ms | `ThoriumChunkUploadPacer` |
| Deferred section release budget | 8 sections per frame | `ThoriumChunkUploadPacer` |
| Reflex upload cone | 45° forward | `ThoriumChunkUploadPacer` |
| Late input sample point | `MouseHandler#handleAccumulatedMovement`, at HEAD | `core.ReflexInputMixin` |
| Fresh-input threshold | 0.01 px of cursor movement | `ThoriumReflexController` |
| Underwater fog end (clamped) | 48 blocks (vanilla 96) | `WeatherFogOptimizer` |
| Distance fog band (narrowed) | 24 blocks (vanilla `clamp(viewDistance/10, 4, 64)`) | `WeatherFogOptimizer` |
| Weather splash particle | `ParticleTypes.RAIN`, dropped in `MINIMAL` and `OFF` | `WeatherFogOptimizer` |

### 3.4 The headline feature registry

The ten player-facing "headline" features (REQ-HEAD-1 … REQ-HEAD-10) are delivered
as a sequence of independent steps, so their **configuration** lives in one place:
`dev.hyperionfps.core.ThoriumFeatureToggles`.

| Concern | Owner |
| :--- | :--- |
| The persisted switch (boolean, timeout, weather mode) | `ThoriumFeatureToggles` |
| The persistence itself (key, capture, apply) | `ThoriumConfig` |
| The console row (title, status, click, description) | `ThoriumSettingRows` |
| The widget and the drawing | the per-version `ThoriumVideoSettingsScreen` |
| The behaviour | the feature's own subsystem, which reads its switch from the registry |

Two rules follow from this:

* **A feature is only *wired* once it has a live call site.** Until then the console
  shows `NOT AVAILABLE` and refuses the click, and the startup report lists it as
  `UNWIRED` (REQ-UI-2, REQ-DIAG-1).
* **The registry is the only copy of the switch.** A subsystem reads it; it never
  keeps a second flag that could drift (NFR-7).

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

### 4.3a Late input sampling (REQ-HEAD-7)

The game drains the platform event queue at one point of the frame and then applies
the collected cursor movement to the camera a moment later. Everything the player
does while the frame is working therefore reaches the camera one frame late.

Thorium drains the queue a **second** time, at the last moment at which the result
is still useful:

```
tick ──► MouseHandler#handleAccumulatedMovement ──► camera matrix ──► render
             ▲
             └─ Thorium drains the platform queue here
```

| Rule | Detail |
| :--- | :--- |
| The sample point | `MouseHandler#handleAccumulatedMovement`, at HEAD — the game calls it immediately before it builds the camera matrix on all five nodes |
| The drain | the game's own routine (`GLFW.glfwPollEvents` on 1.21.x, `RenderSystem.pollEvents` on 26.1/26.2, `RenderSystem.pollEvents(SDLEventHandler)` on 26.3), never a re-implementation |
| Exactly once | the queue is *drained*, not copied, so every event is still delivered exactly once — only earlier |
| The tick is untouched | the movement packets the client sends are the ones vanilla would have sent |
| The camera is untouched | the game still applies the movement, with its own smoothing and sensitivity |
| Guard | the sample only runs when the feature is on, the window has the focus and the node can drain its queue |
| Accounting | the recovered cursor movement is measured and reported; the feature never advertises a fixed latency figure |

### 4.3b Weather modes and fog clamps (REQ-HEAD-8, REQ-HEAD-9)

Two independent switches, one shared policy engine (`WeatherFogOptimizer`).

**The weather mode** (`render.weather.mode`) decides what the precipitation layer does:

| Mode | Rain sheets | Ground splash particles | Rain sound | World / server weather |
| :--- | :--- | :--- | :--- | :--- |
| `VANILLA` | drawn | drawn | plays | untouched |
| `MINIMAL` | drawn | **dropped** | plays | untouched |
| `OFF` | **not drawn** | **dropped** | plays | untouched |

**The fog clamps** (`render.clean_water_fog.enabled`) rewrite two fog ranges:

| Range | Vanilla | Thorium |
| :--- | :--- | :--- |
| Underwater fog | `−8 … 96` blocks | `0 … 48` blocks |
| Distance fog band | `clamp(viewDistance/10, 4, 64)` blocks | at most **24** blocks (never widened) |

| Rule | Detail |
| :--- | :--- |
| The splash is dropped at the particle call | `ClientLevel#addParticle(ParticleOptions, double×6)` — this is where the splash particle is spawned; the weather method that calls it plays the rain sound separately, so cancelling the particle call suppresses only the splash and leaves the rain sound intact |
| The precipitation layer is dropped at the render call | one hook per era, on the method that draws the rain sheets |
| The fog is rewritten at the point the game computes it | 1.21.1 reads and writes `RenderSystem`'s shader fog; 1.21.11 hooks the water environment and the band clamp; 26.x rewrites the `FogData` the renderer uploads |
| Lava and powdered snow keep their fog | only water is "clean water" |
| The band is never widened | at the default render distance vanilla already uses a 19 block band, so nothing changes there |

**No fill-rate claim.** The fog shader computes its blend for every fragment whether the
band is 24 or 64 blocks wide. The measurable work reduction of this step is the splash
particle flood, and the console says so.

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
| `input.pacer.enabled` | *telemetry only* — the leftover turn counter; the live Reflex lives under `input.reflex.enabled` | `true` |
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
| `adaptive.distance.enabled` | Adaptive render distance (opt-in — it rewrites a vanilla option) | `false` |
| `discord.invite.enabled` | One-time community invite on the main menu | `true` |
| `discord.invite.shown` | Whether the community invite has already been offered | `false` |
| `anim.mob_farm.enabled` | Mob Farm Optimizer (swarm limb animation & AI throttling) | `true` |
| `culling.storage_item_frames.enabled` | Aggressive culling for chests, shulkers and item frames | `true` |
| `chunk.elytra_smoother.enabled` | Elytra Flight Smoother (velocity-vector mesh prioritizing) | `true` |
| `frame.afk_eco.enabled` | AFK Battery & Power Saver (auto 10 FPS cap & CPU parking) | `true` |
| `frame.afk_eco.timeout_sec` | Inactivity timeout in seconds before AFK Eco activates | `60` |
| `telemetry.stutter_alert.enabled` | Instant Lag Spike Alert (Nuclear Stutter Hunter™ toast) | `true` |
| `particle.explosion_shield.enabled` | Explosion & Firework Shield (capping particle flood spam) | `true` |
| `input.reflex.enabled` | Ultra Low-Latency Reflex (late-frame input sampling) | `true` |
| `render.weather.mode` | No-Lag Weather rendering mode (`VANILLA`, `MINIMAL`, `OFF`) | `MINIMAL` |
| `render.clean_water_fog.enabled` | Clean Water & Fast Fog (underwater fog clamp + distance fog band) | `true` |

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
| `AdaptiveDistanceController.enabled` | `AtomicBoolean` | `false` (opt-in, persisted) |
| `ThoriumGuiManager.HUD_ENABLED` | `AtomicBoolean` | `true` (persisted) |
| `AdaptiveDistanceController.currentDistance` | `volatile int` | `12` (adapted at runtime) |
| `StorageCullingEngine` counters | `AtomicLong` ×4 | `0` (measured at runtime) |
| `ThoriumReflexController.pump` | `volatile ReflexEventPump` | `NONE` (installed per node at client init) |
| `ThoriumReflexController` counters | `AtomicLong` ×3 | `0` (measured at runtime) |

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

**Last verified:** 2026-10-22 (roadmap step 10). `./gradlew test` green on all five
nodes — **317 tests / 42 classes** each, 0 failures, 0 errors, 0 skipped.
`./gradlew buildAll` produced five `0.1.2` jars in `build/dist/`, and the per-node
`fabric.mod.json` resolved its `${id}` / `${version}` / `${minecraft}` /
`${java_version}` placeholders. `MixinTargetAuditTest` passed on every node
(12 `core` + 21–23 `legacy` + 20–21 `modern` mixins per node).

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
