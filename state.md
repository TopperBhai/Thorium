# state.md — Thorium Project Status

**Project:** Thorium (`thorium` / Th-90) — Minecraft client-side optimization mod
**Repo:** `https://github.com/TopperBhai/Thorium` · **Local:** `F:\Toperion Optimization`
**Branch:** `main` · **HEAD:** `3782c32` · **Working tree:** clean
**Mod version:** 0.1.1 · **Last verified:** 2026-10-20

> This document is the **status board**. It records what is built, what is verified, what
> is broken or missing, and what to do next. Requirements → [`spec.md`](./spec.md).
> Working conventions → [`AGENTS.md`](./AGENTS.md).

---

## 1. Status summary

| Dimension | Status | Detail |
| :--- | :--- | :--- |
| Core subsystems | 🟢 Complete | All 18 packages implemented with metrics and toggles |
| Multi-version build | 🟢 Complete | 5 nodes, shared source tree, era-routed mixins |
| Unit tests | 🟢 Green | 97 tests / 24 classes, 0 failures |
| Build | 🟢 Green | `buildAll` produces 5 jars |
| Settings persistence | 🟢 Fixed | `ThoriumConfig` writes `thorium-config.json` atomically; presets and toggles survive a restart (was G-1) |
| 1.21.1 hotkey parity | 🟢 Fixed | 1.21.1 now handles F6 / F7 / F8 via the GLFW key API (was G-5) |
| Android launch crash | 🟡 Mitigated | Native SIGSEGV in `libbinder_ndk.so`; safe-mode kill switch + native-call guards added (see §4.1) |
| Particle ticking injection | 🔴 Broken | `tickParticleList` target exists on **1.21.1 only** — dead on 4/5 versions (G-2) |
| Fabric API hooks parity | 🟡 Partial | `ThoriumFabricHooks` exists only in 1.21.1 (G-9) |
| Documentation | 🟡 Partial | `README.md` / `MODRINTH.md` still stale vs. code (G-6) |
| Performance validation | 🟡 Unverified | No in-game benchmark harness or capture tooling (G-7) |
| Release tagging | 🔴 Missing | No git tags for 0.1.1 (G-11) |

**Overall:** Builds and tests green across all five version nodes, and the core
subsystems are complete. Three of the reported problems are now fixed: **settings
persist across restarts**, **1.21.1 has full hotkey parity**, and **an Android launch
crash now has a one-flag escape hatch**. The remaining headline defect is that the
particle ticking optimisation is silently inert on 4 of 5 versions (G-2).

---

## 2. Build & test baseline (verified 2026-10-20)

| Command | Result |
| :--- | :--- |
| `./gradlew :26.3:compileJava` | ✅ `EXIT=0` |
| `./gradlew :26.3:test` | ✅ `BUILD SUCCESSFUL` — 97 tests, 0 failures, 0 errors, 0 skipped |
| `./gradlew test` | ✅ green on **all five** nodes |
| `./gradlew buildAll` | ✅ `BUILD SUCCESSFUL` — 5 jars in `build/dist/` |

Verified artifacts in `build/dist/`:

| Jar | Size |
| :--- | ---: |
| `thorium-0.1.1+1.21.1.jar` | 171 861 B |
| `thorium-0.1.1+1.21.11.jar` | 167 041 B |
| `thorium-0.1.1+26.1.jar` | 166 247 B |
| `thorium-0.1.1+26.2.jar` | 166 314 B |
| `thorium-0.1.1+26.3.jar` | 166 562 B |

### Build warnings observed (both are silent defects, not noise)

```
:1.21.11:remapJar
  Cannot remap tickParticleList because it does not exist in any of the targets
  [net/minecraft/client/particle/ParticleEngine]
```

The 26.x nodes do **not** print this — they use the plain (non-remapping) Loom flavor,
so the same broken target fails **completely silently** at runtime. See G-2.

Expected deprecation warning during tests (not a failure):
`sun.misc.Unsafe::invokeCleaner has been called by dev.hyperionfps.memory.OffHeap`.

### Note on Gradle task names

The 26.x nodes use `net.fabricmc.fabric-loom` (plain) and therefore have **no `remapJar`
task** — only `:1.21.1` and `:1.21.11` do. Use `assemble` / `build` for the modern nodes.

### Codebase size

| Metric | Count |
| :--- | ---: |
| Shared main sources (`src/main/java`) | 60 files (+`config/ThoriumConfig.java`, `core/ThoriumSafety.java`) |
| Shared test sources (`src/test/java`) | 24 classes / 97 tests |
| Version-specific sources | 18 files/node (26.x), 21 (1.21.1) |
| Version nodes | 5 |
| Git commits | 6 |

---

## 3. Subsystem inventory

| Package | State | Tests | Notes |
| :--- | :--- | :--- | --- |
| `core` | ✅ | `ThreadGovernorTest` | Thread capture, priority elevation, telemetry kill-switch |
| `frame` | ✅ | `FramePacingTest`, `ThoriumFrametimeTrackerTest` | 128-sample tracker, sleep→spin pacer |
| `culling` | ✅ | `CullingEngineTest`, `OcclusionGridTest`, `SectionCullingEngineTest` | Grid, raycast, BVH, frustum cache |
| `memory` | ✅ | `DirectBufferPoolTest`, `IdleJanitorTest`, `ThoriumMemoryOptimizerTest`, `ZeroAllocationPoolTest` | Direct pools, off-heap release, janitor |
| `chunk` | ✅ | `ThoriumChunkUploadPacerTest` | Dual-budget upload pacer |
| `anim` | ✅ | *(via `BeyondSodiumFeaturesTest`)* | Distance-based LOD |
| `audio` | ✅ | *(via `BeyondSodiumFeaturesTest`)* | Spatial sound rate limit |
| `input` | ✅ | *(via `BeyondSodiumFeaturesTest`)* | Telemetry-only counter; **no real pacing logic** |
| `texture` | ✅ | `ThoriumTextureThrottlerTest` | Animated-texture throttling |
| `particle` | ⚠️ | `ParticleBatchingTest` | 5-sheet batcher OK; **ticking injection dead on 4/5 versions (G-2)**; simulator sequential (G-3) |
| `instancing` | ✅ | `EntityInstancingTest` | GPU instance batches |
| `lighting` | ✅ | `AsyncLightEngineTest` | Async light queue + daemon worker |
| `batch` | ✅ | `ThoriumImmediateBatcherTest` | Immediate-mode 2D batching |
| `render` | ✅ | `RenderOptimizationTest` | HUD batching + GL state filter |
| `adaptive` | ✅ | `AdaptiveDistanceTest` | EMA distance governor |
| `env` | ✅ | *(none)* | Launcher/GC probe — read-only, low risk |
| `gui` | ✅ | *(none)* | `ThoriumGuiManager` + per-version screens |
| `version` | ✅ | `VersionBridgeTest`, `GameVersionTest` | Bridge, detection, era routing |
| `mixin` | ✅ | `HyperionMixinPluginTest` | Era router: 5 core + 19 legacy + 16 modern mixins |

---

## 4. Known gaps and discrepancies

Ordered by user impact.

### 4.1 Android launch crash — analysis and mitigation

**Reported environment**

| Property | Value |
| :--- | :--- |
| Launcher | RxLauncher-v3 (PojavLauncher family), PojavLauncher `net.kdt.rxlaunch.debug` |
| Device | vivo V2207, Android 14 (API 34), arm64 |
| GPU | ARM Mali-G52 MC2, OpenGL ES 3 (via `libltw.so` GL translation) |
| RAM / heap | 5 773 MB total, 2 040 MB allocated (`-Xms896M -Xmx2040M`) |
| GC | G1, `MaxGCPauseMillis=100` |
| Java | OpenJDK 21 (Android Runtime Environment) |
| Minecraft | fabric-loader-0.19.5-**1.21.1** |

**Symptom**

```
# A fatal error has been detected by the Java Runtime Environment:
#  SIGSEGV (0xb) at pc=0x0000007df77226c8, pid=22597, tid=22597
# Problematic frame:
#  C  [libbinder_ndk.so+0x116c8]
```

The last Java-level output before the fault is `authlib-injector`'s
`Disabled legacy skin API polyfill`. **No Fabric Loader or Minecraft log line appears**,
so the fault happens very early — during JVM/library initialisation, before the mod's
`client` entrypoint runs.

**Assessment.** `libbinder_ndk.so` is Android's **Binder IPC** library, used by
SurfaceFlinger to hand out window surfaces. A SIGSEGV there is a *native* fault in the
host graphics/IPC stack — it is not a Java exception, and Thorium (pure Java) cannot
itself raise one. The most likely causes on this configuration are:

1. The PojavLauncher GLFW stub / `libltw.so` translation layer creating the Android
   surface, or
2. the `caciocavallo` AWT shim (`-Dawt.toolkit=…CTCToolkit`,
   `-Dcacio.managed.screensize=720x600`) initialising a second window surface.

**What Thorium *can* contribute, and was fixed.** Three Thorium-side risks existed on
this exact code path, all now closed:

| Risk | Status |
| :--- | :--- |
| `GLFW.glfwSwapInterval(0)` was called **without checking for a current GL context**. That call is undefined behaviour without one and can abort the process natively — exactly the kind of failure that would surface as a native SIGSEGV rather than a Java stack trace. | ✅ Fixed — guarded by `glfwGetCurrentContext()` + `try/catch` in all four GLFW-era frame-pacing mixins; the SDL3 variant (26.3) got the same treatment. |
| `ProcessHandle.current().info().commandLine()` was **unguarded**. `ProcessHandle` support is uneven on Android runtimes and can throw instead of returning an empty result; an exception escaping the pre-launch path aborts the launch. | ✅ Fixed — `LauncherEnvironment` now degrades to an empty command line and an empty environment map. |
| The entire `HyperionPrelaunch.onPreLaunch()` body was **unguarded**. Any failure in the probe, the governor or the logging could stop Minecraft from starting. | ✅ Fixed — every step is individually guarded and the whole body is wrapped; a failure now produces a log line, never a crash. |

**Escape hatch — safe mode.** Added `dev.hyperionfps.core.ThoriumSafety`:

| Trigger | Effect |
| :--- | :--- |
| `-Dhyperionfps.safeMode=true` in the JVM arguments | Every mixin is skipped from the first line of mixin bootstrap. The mod cannot touch the game at all. |
| `safeMode` in `thorium-config.json` | All subsystems disabled at client init (before the window is created); the frame-pacing hook becomes a no-op. |
| Settings console → Advanced → **Safe Mode** | Writes the config key; takes effect on the next launch. |

The JVM property is checked first **on purpose**: a player whose game cannot start at
all cannot reach the settings screen, so the property must be able to disable the mod
before anything else runs. If safe mode is on and the game still crashes, Thorium is
proven not to be the cause.

**Requested follow-up from the reporter.** The `hs_err_pid22597.log` file written to
the game directory (`…/files/Fabric_a24baa41/hs_err_pid22597.log`) contains the
native stack trace and the exact faulting instruction, and would confirm whether the
fault is inside the LTW/SurfaceFlinger path or somewhere else. Without that file the
attribution above remains a reasoned hypothesis rather than a proven root cause.

---

### G-1 ✅ RESOLVED — Settings are now persisted

**Was:** There was no config file, no `options.json` integration, and no
`FabricLoader.getConfigDir()` usage anywhere. Every user-facing toggle was an
in-memory `AtomicBoolean` / `volatile boolean`, so the three hardware presets — a
headline feature — reverted to defaults on every restart.

**Now fixed.** New `dev.hyperionfps.config.ThoriumConfig`:

* Persists to `<config-dir>/thorium-config.json`, a flat JSON object of string values.
* **Atomic writes** — written to a sibling `.tmp`, then moved. A crash mid-write
  cannot truncate the config.
* **Lenient parsing** — a hand-rolled reader that never throws; malformed input
  degrades to defaults. No new dependency (Gson is not on every node's compile
  classpath).
* **Forward compatible** — unknown keys are preserved across a save/load cycle.
* **Three-way wiring** — every persisted toggle needs a key constant, an entry in
  `captureFromSubsystems()` and an entry in `applyToSubsystems()`. All three are
  present for all 21 persisted settings.
* The settings console writes on every action (toggle click, preset selection, screen
  close) and restores state at client initialisation, before the first frame.
* Covered by `ThoriumConfigTest` (11 tests) — round-trip, atomicity, unknown-key
  preservation, malformed input, defaults.

**Verification.** 97 tests / 24 classes, 0 failures, on all five version nodes.

---

### G-2 🔴 Particle ticking injection is dead on 4 of 5 versions

**Finding (verified at bytecode level).** `ParticleEngineMixin` injects into
`tickParticleList`:

```java
@Inject(method = "tickParticleList", at = @At("HEAD"), cancellable = true, require = 0)
private void thorium$tickParticleListConcurrently(Collection<Particle> particles, CallbackInfo ci) {
    dev.hyperionfps.particle.ThoriumParticleSimulator.tickParticles(particles);
    ci.cancel();
}
```

`javap -p` on the actual Minecraft jars for every node:

| Minecraft | `createParticle` | `tickParticleList` | Injection applies? |
| :--- | :--- | :--- | :--- |
| 1.21.1 | ✅ | ✅ `private void tickParticleList(Collection<Particle>)` | **yes** |
| 1.21.11 | ✅ | ❌ **removed** (refactored to the `ParticleGroup` API) | **no** |
| 26.1 | ✅ | ❌ **removed** | **no** |
| 26.2 | ✅ | ❌ **removed** | **no** |
| 26.3 | ✅ | ❌ **removed** | **no** |

Minecraft replaced the flat `tickParticleList(Collection<Particle>)` method with a
`ParticleGroup<?>`-based system (`createParticleGroup(ParticleRenderType)`,
`ParticleLimit`) in 1.21.11 and kept it that way through 26.3.

Because the injection uses `require = 0`, it fails **silently**. The only signal is the
`:1.21.11:remapJar` warning quoted in §2 — and the 26.x nodes print nothing at all.

**Impact.** High.
* `ThoriumParticleSimulator.tickParticles(...)` is never invoked on 1.21.11, 26.1, 26.2
  and 26.3 — particles are ticked by vanilla there.
* The **valuable half still works**: the separate `createParticle` injection (distance +
  occlusion particle culling) applies on all 5 versions, because `createParticle` still
  exists everywhere.
* The "Beyond-Sodium" particle optimisation claim in the README is overstated for
  modern versions.

**Fix direction.** Target the new API. Two options:
* **(a) Move the injection to the `ParticleGroup` API** — inject into `ParticleEngine.tick()`
  or the group tick path, iterate the groups' particle collections, and hand them to
  `ThoriumParticleSimulator`. This requires per-version mixins because the group
  internals differ between 1.21.11 and 26.x.
* **(b) Scope the injection to the legacy era only** — move it out of `core/` into
  `legacy/`, so the dead target is never attempted, and add a modern-era equivalent.

Option (a) is the real fix. Whichever is chosen, **add a build-time assertion** so a
missing mixin target fails the build instead of vanishing — see G-12.

---

### G-3 🟡 `ThoriumParticleSimulator` never uses its thread pool

**Finding.** `ThoriumParticleSimulator` constructs a `ForkJoinPool` (`SIMULATION_POOL`,
line 21) but `tickParticles(Collection<Particle>)` (line 34) is a plain sequential loop
using `it.remove()`. The pool is never submitted to.

**Impact.** Low functionally (the code is correct, just single-threaded), but the class
name, the javadoc, and the mixin method name
(`thorium$tickParticleListConcurrently`) all promise concurrency that does not exist.
This is a **trust problem in the source**, not a performance bug — and it is inherited
from the 1.21.1-era design that G-2 has now orphaned on 4 versions.

**Fix direction.** Either (a) implement genuine parallel ticking with the pool and a
correct concurrent removal strategy, or (b) rename the class/method to match reality and
delete the unused pool. Option (b) is cheaper and honest; option (a) is the larger win
on particle-heavy scenes — but only meaningful once G-2 is fixed.

---

### G-4 🟡 `FrustumCache.isVisible(double...)` allocates per call

**Finding.** `FrustumCache.isVisible(minX, minY, minZ, maxX, maxY, maxZ)` constructs
`new AABB(...)` on line 42. The class javadoc claims zero-allocation behaviour.

**Impact.** Low-moderate. This overload is invoked per block entity, adding GC pressure
on exactly the thread the project exists to protect — directly contradicting NFR-1.

**Fix direction.** Replace the `AABB`-based call with a primitive-frustum test
(plane-by-plane against the cached `Frustum`'s six planes), or keep a single reusable
mutable `AABB` instance and mutate it before the test.

---

### G-5 ✅ RESOLVED — 1.21.1 hotkey parity

**Was:** `versions/1.21.1/.../legacy/LegacyKeyboardMixin.java` handled **only**
`Shift+F10` (GLFW path), while `1.21.11` and all three `modern/ModernKeyboardMixin.java`
files handled F6 / F7 / F8. `README.md` and `MODRINTH.md` advertise F6/F7/F8 for all
versions, so a 1.21.1 user pressing F8 got nothing.

**Now fixed.** The 1.21.1 mixin handles the full set using the GLFW key API that
1.21.1 actually uses — its `keyPress` signature is
`(window, key, scancode, action, modifiers)`, not the `InputConstants` key-event API
used from 1.21.11 onwards:

| Key | Action | 1.21.1 |
| :--- | :--- | :--- |
| `F8` | Open Thorium Video Settings Console | ✅ `GLFW_KEY_F8` |
| `F6` | Toggle Nuclear Stutter Monitor HUD | ✅ `GLFW_KEY_F6` |
| `F7` | Toggle Extreme FPS Boost Mode | ✅ `GLFW_KEY_F7` |
| `Shift+F10` | Toggle Extreme FPS Boost Mode | ✅ `GLFW_KEY_F10` + `GLFW_MOD_SHIFT` |

The settings console was already reachable without a hotkey on 1.21.1 via the
**Thorium Settings** button on the title screen and the Options screen
(`LegacyTitleScreenMixin`, `LegacyOptionsScreenMixin`), so the console was never
unreachable — only undiscoverable.

---

### G-6 🟡 Documentation drift

| Claim | Code reality |
| :--- | :--- |
| `README.md`: "117-sample live history graph" | `ThoriumFrametimeTracker.SAMPLE_COUNT = 128` |
| GUI text: "126-sample micro-stutter graph" | Same — 128 |
| `README.md` / `MODRINTH.md`: 4 versions (1.21.1, 26.1, 26.2, 26.3) | 5 nodes — **1.21.11 is missing** from both docs |
| `README.md`: F6/F7/F8 on all versions | 1.21.1 only has `Shift+F10` (G-5) |
| `README.md` benchmark table (320+ FPS, 7.4 ms 1 % low, <28 MB/s) | **No benchmark harness in the repo.** These numbers are unverifiable as written. |
| `MODRINTH.md`: "Supported: 1.21.1, 26.1, 26.2, 26.3" | Missing 1.21.11 |

**Fix direction.** Correct the sample count to 128, add 1.21.11 to both docs, and either
soften the benchmark table to "reported by users" or add the methodology used.

---

### G-7 🟡 No performance measurement tooling

**Finding.** There is no benchmark harness, frametime capture script, comparison
modelling, or automated regression check anywhere in the repository. The `README.md`
benchmark table cites specific numbers (320+ FPS, 7.4 ms 1 % low, 120 MB/s → 28 MB/s
allocation rate) with no supporting artifact.

**Impact.** Moderate. The project's entire value proposition is performance, and there
is no way to verify a change helps rather than hurts. This is the biggest
**process** gap.

**Fix direction.** Add a debug-mode capture path (e.g. `-Dhyperionfps.benchmark=true`)
that writes frametime percentiles (50/95/99/99.9), allocation rate, and culling
counters to a CSV/JSON on exit. Then the README table becomes reproducible.

---

### G-8 🟡 `ThoriumInputPacer` is a stub

**Finding.** `ThoriumInputPacer` exposes only `recordPacedTurn()`, `getFramesPaced()`,
and an enable toggle. There is no timing logic and no budget.

**Impact.** Low. It is a counter that does not pace anything.

**Fix direction.** Either implement real input pacing (a small per-frame input budget
mirroring the chunk upload pacer) or fold it into a telemetry-only role and rename it
to reflect that. Note the spec lists "sub-millisecond input queueing" as a Nuclear Max
behaviour — currently unimplemented.

---

### G-9 🟡 `ThoriumFabricHooks` exists only in 1.21.1 — and the log lies about it

**Finding.** `HyperionFPS.onInitializeClient()` reflectively loads
`dev.hyperionfps.fabric.ThoriumFabricHooks`:

```java
try {
    Class<?> fabricHooks = Class.forName("dev.hyperionfps.fabric.ThoriumFabricHooks");
    fabricHooks.getMethod("init").invoke(null);
} catch (Throwable ignored) {
    LOGGER.debug("Fabric API advanced hooks not loaded (standalone mode)");
}
```

That class exists **only** in `versions/1.21.1/src/`. It is absent from 1.21.11, 26.1,
26.2 and 26.3. So four of five nodes always take the `catch` branch and run in
"standalone mode".

Worse, the summary line that follows hardcodes the flag regardless of what happened:

```java
"fabric-api=integrated",   // printed even when the hooks failed to load
```

**Impact.** Moderate.
* The `fabric-api=integrated` line in the startup log is **incorrect** on 1.21.11 and
  all 26.x nodes — a misleading diagnostic in the one place an operator looks to confirm
  the mod is healthy.
* The 1.21.1 node and the modern nodes do not have feature parity for the Fabric API
  integration path.

**Fix direction.** Track a real boolean (`fabricHooksLoaded`) and print its actual
value; then either port `ThoriumFabricHooks` to the other nodes or drop the reflective
hook and the `fabric-api=integrated` claim entirely.

---

### G-10 🟢 `DefaultVersionBridge.DefaultEntityBridge` culling is a no-op

**Finding.** `DefaultEntityBridge.shouldCullEntity(...)`, `shouldCullBlockEntity(...)`
and `shouldCullParticle(...)` **always return `false`**. The real culling decisions live
in `AsyncOcclusionCuller` / `SectionCullingEngine` and are reached via mixins, not via
the bridge.

**Impact.** Low — the bridge is a facade and the real path works. But the bridge
advertises capabilities it does not implement, which will mislead the next contributor.

**Fix direction.** Either delegate the bridge methods to the real engines, or document
them explicitly as conservative no-ops for version-agnostic callers.

---

### G-11 🟢 No release tags

`git tag` is empty. Version 0.1.1 is recorded only in `gradle.properties`.

**Fix direction.** Tag `0.1.1` at `3782c32` and establish a tag-per-release convention.

---

### G-12 🟢 No build-time guard against dead mixin targets

**Finding.** This is the *root cause* that let G-2 ship undetected.

* `thorium.mixins.json` sets `"defaultRequire: 0"` and every injection carries
  `require = 0`, so a missing target is a silent no-op at runtime.
* The only build-time signal is Loom's remapper, and only for the **remap** nodes
  (`1.21.1`, `1.21.11`). The 26.x nodes use the plain Loom flavor and emit nothing.
* The unit tests never load a mixin, so they cannot catch it either.

**Impact.** Medium — this is a process defect that will keep producing G-2-class bugs.

**Fix direction.**
* Promote the `:1.21.11:remapJar` "Cannot remap …" warning into a **hard build
  failure** (Gradle `doLast` check on the remap task's output).
* Add a **mixin target audit test**: for each declared mixin target method, assert the
  method exists in the Minecraft jar for every node. This can be done at build time with
  a small Gradle task that reads `javap`/`ASM` output per node.
* Alternatively, relax `require = 0` to `require = 1` for targets proven to exist on all
  versions — but that must be done per-injection, deliberately.

**Also note.** `FramePacing`'s boot-window policy **is** correctly wired: all five
`*FramePacingMixin` files call `FramePacing.inBootPhase()` before forcing the swap
interval to 0, and `FramePacingTest` covers the window boundary. No action needed there.

---

### G-13 🟢 Anomaly (observed once, not reproducible) — build dirtied a tracked PNG

**Observation.** Immediately after the first `buildAll` of this session, `git status`
showed `src/main/resources/assets/thorium/icon.png` modified (12 990 → 12 249 bytes)
while every other tracked file was untouched.

**Investigation.** Not reproducible. The file was restored and then re-checked after
each of these, all of which left it **byte-identical** (12 990 B) and git-clean:

| Invocation | Result |
| :--- | :--- |
| `:26.3:processResources` | clean |
| `:26.3:compileJava` | clean |
| `:26.3:classes` | clean |
| `:26.3:jar` | clean |
| `:26.3:build` | clean |
| `:1.21.1:remapJar` | clean |
| `:1.21.11:remapJar` | clean |
| `clean` + `buildAll` from scratch | clean |

**Most likely cause.** The `versions/*/build/` directories present at session start were
produced on 2026-10-17 by an older Gradle/Loom run; the first `buildAll` re-processed
them and the resource pipeline re-encoded the PNG once. The current toolchain does not
reproduce it.

**Action.** No fix required now. **Watch it:** if `git status` ever shows the icon dirty
again, `git checkout -- src/main/resources/assets/thorium/icon.png` and record the
invoking Gradle task. If it recurs, the `expand(templateValues)` configuration in the
per-node `processResources` blocks is the first place to look — binary resources should
be excluded from templating explicitly.

---

## 5. Risk register

| ID | Risk | Severity | Likelihood | Mitigation |
| :--- | :--- | :--- | :--- | :--- |
| R-1 | Mixin injection breaks on a future Minecraft update | High | **High** — already happened (G-2) | `require = 0` + era routing; add the build-time audit from G-12 |
| R-2 | `sun.misc.Unsafe.invokeCleaner` removed in a future JDK | Medium | Low | `OffHeap.available()` degrades to normal GC |
| R-3 | Memory regression from an unnoticed allocation | Medium | Medium | Enforce NFR-1; add allocation counters (G-7) |
| R-4 | Occlusion culling causes visible pop-in | Medium | Medium | Conservative thresholds; proximity guards (12-block BVH guard) |
| R-5 | Stonecutter/Loom toolchain breaks on upgrade | Medium | Low | Configuration cache already disabled; pin plugin versions |
| R-6 | Extreme FPS mode breaks "vanilla visuals" claims | Low | Medium | Mode is explicit and off by default |
| R-7 | Multiplayer server-side detection bans the client | Low | Very Low | 100 % client-side, no packet tampering |
| R-8 | Silent feature loss goes unnoticed (dead code paths, stale logs) | High | High | G-12 build-time audit; startup log must reflect real state |

---

## 6. Backlog (prioritized)

### P0 — before v0.2

1. **G-2** — Re-target the particle ticking injection at the `ParticleGroup` API so it
   works on 1.21.11 and 26.x (currently a no-op on 4 of 5 versions).
2. **G-12** — Add the build-time mixin-target audit so dead targets fail the build.
3. **G-6** — Correct `README.md` and `MODRINTH.md` (sample count 128, add 1.21.11).
4. **G-9** — Fix the `fabric-api=integrated` log lie and give `ThoriumFabricHooks` parity.
5. **G-11** — Tag `0.1.1`.

### P1 — quality & honesty

6. **G-7** — Build the benchmark/capture harness; regenerate the README numbers.
7. **G-3** — Resolve the particle simulator naming/pool mismatch.
8. **G-4** — Remove the per-call `AABB` allocation in `FrustumCache`.
9. **G-10** — Align `DefaultEntityBridge` with the real culling engines or document it.
10. Obtain the `hs_err_pid22597.log` from the Android reporter to confirm the native
    crash attribution in §4.1.

### P2 — feature depth

11. **G-8** — Implement real input pacing (or rename to telemetry-only).
12. Add `versions/1.21.11` and `26.1`/`26.2` to the release CI build.
13. Investigate `LegacyVignetteMixin` (exists only in 1.21.1) — either port or drop.

### Completed in this cycle

| Item | Status |
| :--- | :--- |
| **G-1** — Settings persistence | ✅ `ThoriumConfig` + 11 unit tests |
| **G-5** — 1.21.1 hotkey parity | ✅ F6 / F7 / F8 via the GLFW key API |
| Android launch crash | ✅ Mitigated — safe-mode kill switch + 3 native-call guards |
| `fabric-api=integrated` log lie | ✅ Fixed — now reports the real state |

---

## 7. Changelog

| Commit | Date | Change |
| :--- | :--- | :--- |
| `3782c32` | 2026-10-20 | Bump version to 0.1.1; isolate per-version metadata |
| `8c58beb` | 2026-10-20 | Add Minecraft 1.21.11 target with modern render-state adaptions |
| `764f6d8` | 2026-10-19 | Clean, standard text-based MODRINTH.md |
| `a356e99` | 2026-10-19 | Convert MODRINTH.md to pure markdown |
| `d3cd854` | 2026-10-19 | Add professional Modrinth & GitHub project documentation |
| `169fb27` | 2026-10-18 | Initial commit: multi-version Fabric optimization mod (1.21.1, 26.1, 26.2, 26.3) |

---

## 8. Maintenance log

| Date | Action |
| :--- | :--- |
| 2026-10-20 | `AGENTS.md`, `spec.md`, `state.md` created from a full codebase survey. Build (`:26.3:compileJava`), tests (`:26.3:test`, 79/79 green) and `buildAll` (5 jars) all verified. |
| 2026-10-20 | Bytecode audit of `ParticleEngine` across all 5 Minecraft jars via `javap` found `tickParticleList` exists on **1.21.1 only** — the particle ticking injection is inert on 1.21.11/26.1/26.2/26.3 (G-2). Recorded as the P0 headline defect. |
| 2026-10-20 | Corrected an initial misreading: `FramePacing.inBootPhase()` **is** wired into all five `*FramePacingMixin` files — no action needed. Recorded as a positive finding under G-12. |
| 2026-10-20 | A one-off `icon.png` mutation was observed after the first `buildAll`. Investigated across 8 task invocations including a full `clean` + `buildAll` — **not reproducible**. Recorded honestly as G-13 with the exact reproduction commands, rather than as a confirmed defect. |
| 2026-10-20 | **G-1 fixed** — `ThoriumConfig` added: atomic flat-JSON persistence at `<config-dir>/thorium-config.json`, lenient hand-rolled parser, unknown-key preservation, 21 persisted settings wired across all five settings screens. 11 new unit tests. |
| 2026-10-20 | **G-5 fixed** — 1.21.1 `LegacyKeyboardMixin` now handles F6 / F7 / F8 through the GLFW key API that 1.21.1 actually uses. |
| 2026-10-20 | **Android crash mitigated** — `ThoriumSafety` safe-mode kill switch added (`-Dhyperionfps.safeMode=true` honoured from mixin bootstrap, plus a persisted config key and an Advanced-tab toggle). `glfwSwapInterval` / `SDL_GL_SetSwapInterval` now require a current context. `ProcessHandle` probe and the whole pre-launch body made non-fatal. |
| 2026-10-20 | **G-9 partially fixed** — the startup log now reports the real Fabric API hook state instead of hardcoding `fabric-api=integrated`. |
| 2026-10-20 | Test suite grew from 79 to **97 tests** across 24 classes. `buildAll` green on all five nodes. |
