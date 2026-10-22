# Thorium - Client-Side Optimization Engine

Thorium is a client-side performance and frame-pacing optimization engine for Minecraft. It is engineered to eliminate micro-stutters, smooth out frametime spikes, and boost FPS without changing vanilla graphics or gameplay.

While most mods only focus on maximum peak FPS, Thorium focuses on 1% and 0.1% frame lows, ensuring smooth and consistent gameplay even when flying fast with Elytras or exploring crowded bases.

---

## Key Features

### 1. Built-in Stutter Monitor HUD (Press F6)
A clean, real-time performance overlay showing live frametimes, average and max ms spikes, frame stability score, and active RAM usage.

### 2. Budgeted Chunk Upload Pacer & Elytra Flight Smoother
Spreads chunk mesh uploads across frames to eliminate freezing and hitching when loading new terrain or flying with an Elytra. On 1.21.1 and 1.21.11 the GPU upload itself is split into a per-frame budget; the 26.x pipeline copies every mesh into shared GPU buffers in one go, so there Thorium budgets the section builds instead. On every version the Elytra Flight Smoother postpones the chunk sections behind your flight vector while you are flying faster than 15 m/s, so the world ahead of you is built first.

### 3. Occlusion & Frustum Culling
Automatically skips rendering hidden chunks and concealed block entities (like chests and hoppers) before they reach the GPU, significantly increasing FPS in large bases. The camera context is published once per rendered frame on every supported version.

Chests, trapped chests, ender chests, shulker boxes and item frames get their own rules: a container sealed inside opaque blocks is invisible from every angle and is skipped, a container past 32 blocks is skipped when it is also outside your direct focal cone, and an item frame past 24 blocks is skipped outright. The container you currently have open is never skipped.

### 4. Proactive Memory Management
Performs smart memory cleanups during idle moments to prevent sudden Java Garbage Collection (GC) lag spikes during gameplay.

### 5. Dynamic Entity LOD & Mob Farm Optimizer
Optimizes animations and tick updates for distant mobs, keeping gameplay smooth even near massive mob farms.

A 5x5 block cell holding more than 15 living mobs of the same species counts as a farm: the animation of a mob in it is recomputed on one frame out of three, so the two skipped frames do no bone and no limb-swing work at all. A mob within 4 blocks of the camera keeps its full animation, and your local player, named mobs, leashed mobs and passengers are exempt. A farm also gets its own acoustic policy — after 6 sound events in 50 ms the surplus plays at half volume, and everything beyond twice that is dropped.

### 6. Modern Video Settings Screen (Press F8)
A clean, translucent settings menu with 3 built-in hardware presets. A switch that cannot change anything on your version is shown as *not available* rather than pretending to work.

### 7. No-Lag Weather & Clean Water / Fast Fog
Three weather modes: `VANILLA` changes nothing, `MINIMAL` keeps the rain but drops the thousands of ground splash particles a downpour spawns, and `OFF` removes the rain from your screen while the world's weather, the server's weather and the rain sound stay exactly as they are. The clean-water switch clamps the underwater fog to a short, crisp distance and narrows the distance fog band — a visual change, not a frame-time claim.

### 8. Explosion & Firework Shield
A TNT chain, a wither spawn or a firework show can no longer drown the client in particles. Two continuously refilled rate limits do the work: at most 250 tracked particles per second across the world, and at most 48 per second per one-block cell. The explosion flash always survives — the first particle of every burst bypasses the rate limit and the global particle budget, so the feedback a player needs is never the thing that gets dropped.

### 9. Ultra Low-Latency Reflex
Minecraft drains the platform event queue once per frame and applies the collected cursor movement afterwards, so everything you do while the frame is working reaches the camera one frame late. Thorium drains the queue a second time, immediately before the game builds the camera matrix, so the movement is applied to the frame you are looking at. The queue is drained, never copied: every event is still delivered exactly once, only earlier, and the tick is never touched — the movement packets the client sends are exactly the ones vanilla would have sent.

### 10. AFK Battery & Power Saver
When the window loses focus — or you stop touching the mouse for a while — the client throttles itself to 10 FPS and parks the render thread instead of burning battery. A mouse move, a click or a key press wakes it immediately.

### 11. Instant Lag Spike Alert
Every frame is broken down into its five phases and garbage-collection pauses are correlated with the stutter window. When a freeze happens, a toast names the dominant cause: `Spike: 58ms [GC: 34ms | Mobs: 18ms | Chunk: 6ms]`.

### 12. Honest Compatibility
Thorium detects a competing renderer (Sodium, Embeddium, ...) and stands down the overlapping culling so the two never fight over visibility, and it disables its fast-math overrides while a shader pack (Iris, Oculus, OptiFine) is loaded.

---

## Performance Profiles

* **Potato Mode:** Designed for low-end laptops and integrated GPUs. Maximizes FPS by aggressively reducing rendering overhead.
* **Balanced Mode (Recommended):** The ideal balance for everyday gaming. Delivers stable 60+ FPS frametimes with full vanilla graphics.
* **Nuclear Max Mode:** Built for high-refresh rate monitors (144Hz+) and dedicated GPUs. Uncaps frame pacing for ultra-low latency response.

---

## Hotkeys

* **F6:** Toggle Stutter Monitor HUD (On / Off)
* **F7:** Toggle Extreme FPS Boost Mode
* **F8:** Open Thorium Video Settings

A dedicated **Thorium Settings** button is also present on the main menu and the
Options screen.

---

## Safe Mode

If Thorium causes a crash or a rendering issue on your device, disable every
optimisation without uninstalling:

* In-game: **Thorium Settings → Advanced → Safe Mode**, then restart.
* Launcher: add `-Dhyperionfps.safeMode=true` to the JVM arguments. This is honoured
  before Minecraft loads, so it works even if the game cannot start.

---

## Compatibility

* **Modloader:** Fabric Loader
* **Supported Minecraft Versions:** 1.21.1, 1.21.11, 26.1, 26.2, 26.3
* **Safe for Multiplayer:** Yes, 100% client-side. Safe to use on Hypixel, Realms, and public SMP servers.
* **Other mods:** Sodium / Embeddium and shader packs are supported. Thorium detects them and disables the features that would conflict.

---

## How to Install

1. Download the Thorium .jar file matching your Minecraft version.
2. Place the file inside your `.minecraft/mods` folder.
3. Launch Minecraft using your Fabric profile.
