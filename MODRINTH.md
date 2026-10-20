# Thorium - Client-Side Optimization Engine

Thorium is a client-side performance and frame-pacing optimization engine for Minecraft. It is engineered to eliminate micro-stutters, smooth out frametime spikes, and boost FPS without changing vanilla graphics or gameplay.

While most mods only focus on maximum peak FPS, Thorium focuses on 1% and 0.1% frame lows, ensuring smooth and consistent gameplay even when flying fast with Elytras or exploring crowded bases.

---

## Key Features

### 1. Built-in Stutter Monitor HUD (Press F6)
A clean, real-time performance overlay showing live frametimes, average and max ms spikes, frame stability score, and active RAM usage.

### 2. Budgeted Chunk Upload Pacer
Spreads chunk mesh uploads across frames to eliminate freezing and hitching when loading new terrain or flying with an Elytra. Available on Minecraft 1.21.1 and 1.21.11; the 26.x upload pipeline is not hooked yet and the console says so.

### 3. Occlusion & Frustum Culling
Automatically skips rendering hidden chunks and concealed block entities (like chests and hoppers) before they reach the GPU, significantly increasing FPS in large bases. The camera context is published once per rendered frame on every supported version.

### 4. Proactive Memory Management
Performs smart memory cleanups during idle moments to prevent sudden Java Garbage Collection (GC) lag spikes during gameplay.

### 5. Dynamic Entity LOD
Optimizes animations and tick updates for distant mobs, keeping gameplay smooth even near massive mob farms.

### 6. Modern Video Settings Screen (Press F8)
A clean, translucent settings menu with 3 built-in hardware presets. A switch that cannot change anything on your version is shown as *not available* rather than pretending to work.

### 7. Honest Compatibility
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
