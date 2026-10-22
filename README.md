<div align="center">

# ☢️ Thorium
### *Nuclear-Grade Client Optimization Engine for Minecraft*

[![Fabric](https://img.shields.io/badge/Modloader-Fabric-blue?style=for-the-badge&logo=fabric)](https://fabricmc.net/)
[![Minecraft Versions](https://img.shields.io/badge/Minecraft-1.21.1%20%7C%201.21.11%20%7C%2026.1%20%7C%2026.2%20%7C%2026.3-brightgreen?style=for-the-badge&logo=minecraft)](https://modrinth.com)
[![License](https://img.shields.io/badge/License-Proprietary-red?style=for-the-badge)](https://github.com/TopperBhai/Thorium)
[![GitHub](https://img.shields.io/badge/GitHub-Repository-181717?style=for-the-badge&logo=github)](https://github.com/TopperBhai/Thorium)

<br/>

**Thorium** is an advanced, client-side rendering and frame-pacing optimization engine engineered from the ground up to **eliminate micro-stutters, flatten frametime spikes, and maximize framerates** without compromising vanilla Minecraft’s visual charm.

</div>

---

## 🚀 Why Thorium?

Most optimization mods only focus on raw peak FPS numbers while ignoring the real culprit behind choppy gameplay: **frametime inconsistency and garbage-collection hitching**. 

Thorium prioritizes **1% and 0.1% frame lows**. Whether you are loading massive chunk sections with an Elytra, navigating heavily crowded mob farms, or playing on low-spec hardware, Thorium ensures every frame arrives with sub-millisecond precision.

---

## ⚡ 3 Tuned Performance Presets

Thorium provides three pre-configured hardware profiles built directly into the settings console:

| Profile | Target Hardware | Core Behavior |
| :--- | :--- | :--- |
| 🥔 **Potato** | Integrated GPUs & Low-Spec Laptops | Aggressive culling, throttled distant entity animations, maximum frame throughput squeeze. |
| ⚡ **Balanced** *(Default)* | Everyday Gaming Desktops & Laptops | Optimal balance of silky-smooth 1% low frametimes, native memory compaction, and full vanilla visual fidelity. |
| ☢️ **Nuclear Max** | Dedicated GPUs & High-Refresh Displays (144Hz+) | Uncapped frame execution pipeline, predictive thread pacer, same-frame input sampling, and extreme micro-stutter suppression. |

---

## 🔬 Core Architectural Pillars

### 1. 📊 Built-In Nuclear Stutter Monitor (`F6`)
Track your gameplay fluidity in real time with an ultra-clean, frosted-glass telemetry HUD inspired by RivaTuner and Sodium:
* **Frametime Sparkline:** 128-sample live history graph with colored thresholds (Cyan: >120 FPS, Green: 60–120 FPS, Amber: 30–60 FPS, Red: Stutter Spike).
* **Smoothness Score %:** Live stability rating calculating exact frame delivery consistency.
* **Telemetry Data:** Current FPS, Frametime (ms), Average ms, Max Spike ms, Detected Hitch Count, and Active RAM Usage.

### 2. 🛡️ Budgeted Chunk Upload Pacer & Elytra Flight Smoother
* Eliminates the infamous "chunk loading hitch" when flying with Elytra or teleporting.
* Spreads heavy chunk mesh uploads across consecutive frames within strict microsecond time budgets rather than freezing the render thread.
* **Elytra Flight Smoother** — while you are flying faster than 15 m/s, the chunk sections *behind* your flight vector are postponed and the ones inside a 45° forward cone are built first, so the world keeps up with you instead of stalling behind. A section that has waited for half a second is always released, so the deferral can never become a hole in the world.
* **Minecraft 1.21.1 and 1.21.11** — the 26.x pipeline copies every chunk mesh into shared GPU buffers in one go, so the upload itself cannot be split. On 26.x Thorium budgets the section *builds* instead, which is what fills that upload queue. The console says exactly which one it is doing.

### 3. 👁️ Asynchronous Frustum & Occlusion Culling
* Hierarchical Bounding Volume (BVH) grid and frustum test discards non-visible chunk sections and hidden block entities (chests, hoppers, banners) before they ever reach the GPU pipeline.
* Massive FPS gains in mega-bases, storage rooms, and trading halls.
* **Storage & Item Frame Culling** — chests, trapped chests, ender chests, shulker boxes and item frames get their own visibility rules: a container sealed in opaque blocks on all six sides is invisible from every angle and is skipped, a container past 32 blocks is skipped when it is also outside your direct focal cone, and an item frame past 24 blocks is skipped outright. The container you currently have open is never skipped, and neither is the other half of a double chest.
* The camera context is published **once per rendered frame** on every supported version, so the distance governor behaves the same everywhere.

### 4. 🧹 Proactive Idle Janitor & Zero-Allocation Memory Pools
* Java Garbage Collection (GC) pauses are the primary cause of sudden in-game stutter.
* Thorium recycles direct off-heap native memory buffers and executes micro-cleanups during idle moments (pause screen, inventories, and chunk lulls), preventing random GC freezes during combat or exploration.

### 5. 🏃 Dynamic Entity & Animation LOD
* Intelligently throttles tick rates and skeletal animation updates for distant entities that are far away from your crosshair.
* Keeps 100% full animation quality for nearby mobs and players while dramatically reducing CPU overhead during mob-heavy scenes.
* **Mob Farm Optimizer** — a 5×5 block cell holding more than 15 living mobs of the *same* species is a farm, and the animation of a mob in it is recomputed on one frame out of three (the two skipped frames do no bone and no limb-swing work at all). A mob within 4 blocks of the camera keeps its full animation, and your local player, named mobs, leashed mobs and passengers are exempt. A farm also gets its own acoustic policy: after 6 sound events in 50 ms the surplus plays at half the volume, and everything beyond twice that is dropped.

### 6. 🎨 Modern Frosted-Glass Video Settings GUI (`F8`)
* Clean Sodium-style layout with **100% interactive controls** — a switch that cannot change anything is shown as *not wired* / *not available* instead of pretending to work.
* Real-time preview card explaining what each toggle does and its hardware performance impact.
* Translucent dark acrylic backdrop keeping the live 3D game world visible behind menus.

### 7. 🌧️ No-Lag Weather & Clean Water / Fast Fog
* **Three weather modes** (*Settings → Advanced → Weather Rendering*): `VANILLA` changes nothing, `MINIMAL` keeps the rain but drops the thousands of ground splash particles a downpour spawns, and `OFF` removes the rain from your screen entirely. The weather in the world, on the server and the rain sound are never touched — only what is drawn on your client.
* **Clean Water & Fast Fog** (*Settings → Advanced → Clean Water & Fast Fog*): the underwater fog is clamped to a short, crisp distance and the distance fog band is narrowed, so the water reads clean and the horizon resolves sharply. This one is **visual** — the fog shader does the same work either way, so no frame-time claim is made for it.

### 8. 💥 Explosion & Firework Shield
* A TNT chain, a wither spawn or a firework show can no longer drown the client in particles.
* Two continuously refilled rate limits do the work: at most 250 tracked particles per second across the whole world, and at most 48 per second per one-block cell — which is what removes the quads that are drawn on top of each other.
* **The explosion flash always survives.** The first particle of every burst bypasses the rate limit *and* the global particle budget, so the feedback a player needs is never the thing that gets dropped.

### 9. ⚡ Ultra Low-Latency Reflex
* Minecraft drains the platform event queue once per frame and applies the collected cursor movement afterwards, so everything you do *while* the frame is working reaches the camera one frame late.
* Thorium drains the queue a **second** time, at the last moment at which the result is still useful — immediately before the game builds the camera matrix — so the movement is applied to the frame you are looking at.
* The queue is *drained*, never copied: every event is still delivered **exactly once**, only earlier. The tick is never touched, so the movement packets the client sends are exactly the ones vanilla would have sent.

### 10. 🔋 AFK Battery & Power Saver (Stealth AFK™)
* When the window loses focus — or you stop touching the mouse for a while — the client throttles itself to 10 FPS and parks the render thread instead of burning battery.
* The park is spent in 16 ms slices, so a mouse move, a click or a key press wakes it **immediately** instead of after the whole park.

### 11. 🎯 Instant Lag Spike Alert (Nuclear Stutter Hunter™)
* Every frame is broken down into its five phases, and JVM garbage-collection pauses are correlated with the stutter window.
* When a freeze happens, a toast names the dominant cause: `Spike: 58ms [GC: 34ms | Mobs: 18ms | Chunk: 6ms]`.

### 12. 🧭 Honest Compatibility
* Detects a competing renderer (Sodium, Embeddium, Rubidium, Magnesium, Lithium, Oclite) and **stands down** the overlapping culling subsystems so the two never fight over visibility. Can be turned off in *Advanced → Auto-Yield to Sodium*.
* Detects a shader pack (Iris, Oculus, OptiFine) and **disables** the fast-math overrides, because shader packs depend on the exact precision of vanilla's maths.

### 13. 💬 Community
* A single, dismissible invite card appears on the main menu the **first** time you launch the game — and never again. The link stays available forever in *Settings → General → Community Discord*, and the prompt can be switched off entirely there.

---

## 🗺️ Per-version feature matrix

| Feature | 1.21.1 | 1.21.11 | 26.1 | 26.2 | 26.3 |
| :--- | :---: | :---: | :---: | :---: | :---: |
| Entity / block-entity / particle culling | ✅ | ✅ | ✅ | ✅ | ✅ |
| Frustum + occlusion culling | ✅ | ✅ | ✅ | ✅ | ✅ |
| Storage & item frame culling | ✅ | ✅ | ✅ | ✅ | ✅ |
| Dynamic distance governor | ✅ | ✅ | ✅ | ✅ | ✅ |
| Zero-jitter frame pacer + target FPS cap | ✅ | ✅ | ✅ | ✅ | ✅ |
| Adaptive render distance (opt-in) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Chunk upload pacer (GPU upload budget) | ✅ | ✅ | ⬜ | ⬜ | ⬜ |
| Elytra flight smoother (flight-vector section order) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Mob farm optimizer (swarm animation + acoustic dampening) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Explosion & firework shield | ✅ | ✅ | ✅ | ✅ | ✅ |
| Ultra low-latency reflex (same-frame input) | ✅ | ✅ | ✅ | ✅ | ✅ |
| AFK battery & power saver | ✅ | ✅ | ✅ | ✅ | ✅ |
| Instant lag spike alert (stutter attribution) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Extreme-mode fog suppression | ✅ | ✅ | ✅ | ✅ | ✅ |
| No-Lag Weather (rain modes + splash suppression) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Clean Water & Fast Fog (underwater fog + fog band) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Particle budget | ✅ | ✅ | ✅ | ✅ | ✅ |
| Fast-math overrides (opt-in) | ✅ | ✅ | ✅ | ✅ | ✅ |
| GPU entity instancing | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| Particle sheet batching | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |

`✅` = live in-game · `⬜` = not available on that version (the console says so).

> **Adaptive render distance is opt-in** (*Settings → Advanced → Adaptive Render
> Distance*). It lowers the render distance when the framerate falls below target, and
> never raises it on its own: changing the render distance makes Minecraft re-download
> the whole chunk cache, so raising it automatically would reload the world every few
> seconds.

The startup log prints the same information for the running session, so you can always
see what Thorium actually did:

```
Subsystem states:
  frame-context            ACTIVE   - 4211 client frames observed
  camera-context           ACTIVE   - 3902 world frames observed
  frame-pacer              ACTIVE   - 144 FPS target
  entity-culling           ACTIVE   - distance + frustum + occlusion, scale 0.95
  ...
  gpu-entity-instancing    UNWIRED  - EntityInstancer has no call site
```

---

## 📊 Performance

Thorium ships **no benchmark harness yet**, so the numbers below are *not* published
as measurements — a table of figures nobody can reproduce is worse than no table at
all. What Thorium does publish is the instrumentation: the HUD shows live FPS,
frametime, average, maximum spike, hitch count, the smoothness score and the dynamic
distance scale, and every subsystem exposes its own counters.

To measure Thorium on your own hardware:

1. Note the vanilla numbers in the same scene (F3 gives frametime and the debug
   screen the FPS cap).
2. Enable Thorium, use the same world, seed and camera path.
3. Compare the **1 % and 0.1 % frame lows**, not the average — that is the metric
   Thorium is built around.

| Metric | Vanilla Minecraft | Thorium (Balanced / Nuclear) |
| :--- | :---: | :---: |
| **Average FPS** | *measure it* | *measure it* |
| **1 % Low Frametime** | *measure it* | *measure it* |
| **0.1 % Low Frametime** | *measure it* | *measure it* |
| **Elytra Chunk Hitching** | Frequent freezes | Budgeted on every version |
| **Allocation rate** | *measure it* | *measure it* |

> An in-game capture harness that writes frametime percentiles and the allocation
> rate to a file is on the roadmap; until it exists, this section stays empty on
> purpose.

---

## ⌨️ Default Keybindings

All hotkeys can be rebound in **Options → Controls → Key Binds → Thorium**:

* `F6` — **Toggle Nuclear Stutter Monitor HUD** (On / Off)
* `F7` — **Toggle Extreme FPS Boost Mode**
* `F8` — **Open Thorium Video Settings Console** *(also accessible via standard Video Settings)*

A dedicated **Thorium Settings** button is also added to the main menu and the Options
screen, so the console is reachable on every version even if a hotkey conflicts.

---

## 🛟 Safe Mode (Emergency Escape Hatch)

If Thorium causes a crash or a rendering problem on your device, you can disable every
optimisation without uninstalling the mod:

* **From the game:** Settings console → **Advanced** tab → **Safe Mode** → restart.
* **From the launcher:** add `-Dhyperionfps.safeMode=true` to the JVM arguments. This
  is honoured before Minecraft loads a single class, so it works even when the game
  cannot start at all.

With safe mode active, the startup log prints
`Thorium SAFE MODE is ON - every mixin and optimisation is disabled.`

---

## 🧩 Compatibility & Installation

### Requirements:
* **Fabric Loader:** `0.16.0+` (or latest Fabric for 26.x)
* **Minecraft Version Support:**
  * `1.21.1`, `1.21.11` (Classic Era, obfuscated)
  * `26.1`, `26.2`, `26.3` (Modern Unobfuscated Engine)
* **Java:**
  * Java 21 for Minecraft 1.21.x
  * Java 25 for Minecraft 26.x
* **Mods:** Fabric API is recommended. Sodium / Embeddium and shader packs are supported —
  Thorium detects them and stands down the features that would conflict.

### How to Install:
1. Download the jar matching your exact Minecraft version from the [Versions tab](https://modrinth.com).
2. Place the downloaded `.jar` file into your `.minecraft/mods` folder.
3. Launch Minecraft with your Fabric profile and enjoy stutter-free gaming!

---

## ❓ Frequently Asked Questions

<details>
<summary><b>Is Thorium client-side or server-side?</b></summary>
Thorium is 100% client-side. You do not need it installed on the server to join multiplayer servers, realms, or LAN games.
</details>

<details>
<summary><b>Can I use Thorium on servers like Hypixel or multiplayer SMPs?</b></summary>
Yes. Thorium strictly optimizes local rendering, memory allocation, and client-side frame pacing. It contains zero gameplay modifications, cheats, or packet tampering, making it safe for all multiplayer networks.
</details>

<details>
<summary><b>How is Thorium different from other optimization mods?</b></summary>
While other mods focus strictly on chunk meshing or shaders, Thorium focuses on the full pipeline: frame pacing, GC stutter prevention, budgeted section uploads, and real-time telemetry diagnostics with a sleek modern user interface.
</details>

<details>
<summary><b>Does Thorium change how Minecraft looks?</b></summary>
No. In Balanced and Nuclear modes, vanilla lighting, textures, block models, and visual fidelity remain completely identical to vanilla Minecraft.
</details>

---

<div align="center">

Crafted with precision for competitive players, speedrunners, and builders who demand uncompromising smoothness.

**[Report an Issue](https://github.com/TopperBhai/Thorium/issues)** • **[GitHub Repository](https://github.com/TopperBhai/Thorium)**

</div>
