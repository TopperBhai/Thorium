# state.md — Thorium Project Status

**Project:** Thorium (`thorium` / Th-90) — Minecraft client-side optimization mod
**Repo:** `https://github.com/TopperBhai/Thorium` · **Local:** `F:\Toperion Optimization`
**Branch:** `main` · **HEAD:** `948ec68` (+ uncommitted v0.2 groundwork) · **Working tree:** modified
**Mod version:** 0.1.1 · **Last verified:** 2026-10-21

> This document is the **status board**. It records what is built, what is verified, what
> is broken or missing, and what to do next. Requirements → [`spec.md`](./spec.md).
> Working conventions → [`AGENTS.md`](./AGENTS.md).

---

## 1. Status summary

| Dimension | Status | Detail |
| :--- | :--- | :--- |
| Core subsystems | 🟢 Complete | All 18 packages implemented with metrics and toggles |
| Multi-version build | 🟢 Complete | 5 nodes, shared source tree, era-routed mixins |
| Unit tests | 🟢 Green | 117 tests / 28 classes, 0 failures on all five nodes |
| Build | 🟢 Green | `buildAll` produces 5 jars |
| Mixin target audit | 🟢 Fixed | A dead mixin target now **fails the build** (G-12) |
| Frame context | 🟢 Fixed | One authoritative per-frame publisher on all five nodes (A-3) |
| Frame pacer | 🟢 Fixed | `ThoriumFramePacer.paceFrame()` is wired to `Minecraft#runTick` (A-1) |
| Adaptive render distance | 🟢 Fixed | Wired to the game's own render-distance option (A-2) |
| Particle ticking | 🟢 Resolved | The broken re-implementation of vanilla's tick loop is gone (G-2/G-3) |
| Frame pacing on 26.2 | 🟢 Fixed | `Window#updateVsync` never existed on 26.2 — re-targeted (new) |
| Settings persistence | 🟢 Fixed | `ThoriumConfig` writes `thorium-config.json` atomically; presets and toggles survive a restart (was G-1) |
| 1.21.1 hotkey parity | 🟢 Fixed | 1.21.1 now handles F6 / F7 / F8 via the GLFW key API (was G-5) |
| Android launch crash | 🟡 Mitigated | Native SIGSEGV in `libbinder_ndk.so`; safe-mode kill switch + native-call guards added (see §4.1) |
| Mod coexistence | 🟢 Fixed | Sodium/Embeddium and shader packs are detected; Thorium stands down (D-1/D-2) |
| Startup log honesty | 🟢 Fixed | A live subsystem report replaces the hardcoded claims (A-13/D-4) |
| Dead subsystems | 🔴 Open | 7 implemented subsystems still have **no call site** — see §4.2 |
| Performance validation | 🟡 Unverified | No in-game benchmark harness or capture tooling (G-7) |
| Release tagging | 🔴 Missing | No git tags for 0.1.1 (G-11) |

**Overall:** Builds and tests green across all five version nodes. The two headline
defects of the previous cycle — the particle ticking injection that was dead on four
of five versions, and the frame context that was published once per *entity* — are
fixed, and a build-time audit now makes the first class of bug impossible to ship
again. What remains is a documented list of subsystems that are implemented and tested
but not yet connected to the game (see §4.2), plus the missing benchmark harness.

---

## 2. Build & test baseline (verified 2026-10-21)

| Command | Result |
| :--- | :--- |
| `./gradlew :26.3:compileJava` | ✅ `EXIT=0` |
| `./gradlew :26.3:test` | ✅ `BUILD SUCCESSFUL` — 117 tests, 0 failures, 0 errors, 0 skipped |
| `./gradlew test` | ✅ green on **all five** nodes (117 tests each) |
| `./gradlew buildAll` | ✅ `BUILD SUCCESSFUL` — 5 jars in `build/dist/` |

Verified artifacts in `build/dist/`:

| Jar | Size |
| :--- | ---: |
| `thorium-0.1.1+1.21.1.jar` | 198 943 B |
| `thorium-0.1.1+1.21.11.jar` | 195 770 B |
| `thorium-0.1.1+26.1.jar` | 194 027 B |
| `thorium-0.1.1+26.2.jar` | 194 245 B |
| `thorium-0.1.1+26.3.jar` | 194 191 B |

### Build warnings observed

The `Cannot remap tickParticleList because it does not exist in any of the targets`
warning is **gone** — the dead injection was removed (G-2). The remap nodes are clean.

Expected deprecation warning during tests (not a failure):
`sun.misc.Unsafe::invokeCleaner has been called by dev.hyperionfps.memory.OffHeap`.

### Note on Gradle task names

The 26.x nodes use `net.fabricmc.fabric-loom` (plain) and therefore have **no `remapJar`
task** — only `:1.21.1` and `:1.21.11` do. Use `assemble` / `build` for the modern nodes.

### Codebase size

| Metric | Count |
| :--- | ---: |
| Shared main sources (`src/main/java`) | 64 files |
| Shared test sources (`src/test/java`) | 28 classes / 117 tests |
| Version-specific sources | 19 files/node (26.x), 21 (1.21.1), 20 (1.21.11) |
| Version nodes | 5 |
| Git commits | 6 (this cycle's work is uncommitted) |
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
| `particle` | ✅ | `ParticleBatchingTest`, `BeyondSodiumFeaturesTest` | Creation budget + culling live on all 5 nodes; sheet batching still unwired (§4.2) |
| `instancing` | ✅ | `EntityInstancingTest` | GPU instance batches |
| `lighting` | ✅ | `AsyncLightEngineTest` | Async light queue + daemon worker |
| `batch` | ✅ | `ThoriumImmediateBatcherTest` | Immediate-mode 2D batching |
| `render` | ✅ | `RenderOptimizationTest` | HUD batching + GL state filter |
| `adaptive` | ✅ | `AdaptiveDistanceTest` | EMA distance governor |
| `env` | ✅ | *(none)* | Launcher/GC probe — read-only, low risk |
| `gui` | ✅ | *(none)* | `ThoriumGuiManager` + per-version screens |
| `version` | ✅ | `VersionBridgeTest`, `GameVersionTest` | Bridge, detection, era routing |
| `mixin` | ✅ | `HyperionMixinPluginTest`, `MixinTargetAuditTest` | Era router: 6 core + 19-21 legacy + 17 modern mixins; every target audited at build time |

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

### G-2 ✅ RESOLVED — The dead particle ticking injection is gone

**Was:** `ParticleEngineMixin` injected into `tickParticleList`, a method that only
exists on 1.21.1. On 1.21.11 and the three 26.x nodes the injection was a silent
no-op, so `ThoriumParticleSimulator.tickParticles(...)` never ran — and on the one
node where it *did* run, it was **actively harmful**.

**The bug that was hiding behind it.** Vanilla's tick loop does three things:

```java
this.tickParticle(particle);                                     // crash-report wrapped
if (!particle.isAlive()) {
    particle.getParticleLimit().ifPresent(l -> this.engine.updateCount(l, -1));
    iterator.remove();
}
```

`ThoriumParticleSimulator` did the first and the last, but **not** the second. The
per-limit counters therefore only ever grew, and `hasSpaceInParticleLimit()` compares
them against the limit — so after enough particles had died, the engine stopped
creating particles of that type entirely, for the rest of the session.

**Now fixed.**

* The dead injection was removed from the core mixin. The particle **culling** hook
  (`createParticle`) stays and is live on all five nodes.
* `ThoriumParticleSimulator` was deleted. It never used the `ForkJoinPool` it created,
  it did the same work as vanilla, and it broke vanilla's bookkeeping.
* It is replaced by `ThoriumParticleBudget` — an honest, live feature: it caps how
  many particles may be **created** per budget window (1500, or 64 in Extreme FPS
  mode) and counts the rejections.
* The console row is now *"Particle Budget & Culling"* instead of the false
  *"Multi-Threaded Particle Sim"*.

**Verification.** The mixin target audit (G-12) is green on all five nodes and the
`Cannot remap tickParticleList` build warning is gone.

---

### G-3 ✅ RESOLVED — The particle simulator's naming/pool mismatch

Resolved together with G-2: the class, its unused `ForkJoinPool` and the misleading
`thorium$tickParticleListConcurrently` hook name are gone. Particle *ticking* is
vanilla's job on every version; Thorium decides which particles are allowed to exist
in the first place.

---

### G-4 🟡 `FrustumCache.isVisible(double...)` still allocates (mostly mitigated)

**Finding.** `FrustumCache.isVisible(minX, minY, minZ, maxX, maxY, maxZ)` constructs
`new AABB(...)` on every call.

**What was done.** The block-entity culling path now performs the **distance test
first**, so only block entities that survive the distance cut-off (48 blocks, 16 in
Extreme mode) reach the frustum test and allocate. That removes the overwhelming
majority of the per-frame allocations.

**What is still open.** Minecraft's `AABB` is immutable in every supported version
(`public final double minX` plus copy-on-write setters), so a reusable instance is
not possible. The remaining allocations need either a primitive plane test — which
requires access to `Frustum`'s private planes — or an era-specific accessor mixin.
Tracked as PLAN D-6.

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

### G-6 ✅ RESOLVED — Documentation drift

| Claim | Code reality | Now |
| :--- | :--- | :--- |
| `README.md`: "117-sample live history graph" | `ThoriumFrametimeTracker.SAMPLE_COUNT = 128` | ✅ corrected to 128 |
| `README.md` / `MODRINTH.md`: 4 versions | 5 nodes — 1.21.11 was missing | ✅ 1.21.11 listed |
| `README.md`: F6/F7/F8 on all versions | 1.21.1 only had `Shift+F10` (G-5) | ✅ true since G-5 |
| `README.md` benchmark table (320+ FPS, 7.4 ms 1 % low, <28 MB/s) | No benchmark harness in the repo | ✅ **removed** — the table now says *"measure it"* and explains how |
| `MODRINTH.md`: "Supported: 1.21.1, 26.1, 26.2, 26.3" | Missing 1.21.11 | ✅ corrected |

**Also added.** A per-version feature matrix in `README.md` that states, for every
feature, which versions support it — and the startup log now prints the same
information for the running session (see A-13 / D-4).

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

### G-10 ✅ RESOLVED — `DefaultVersionBridge.DefaultEntityBridge` was a no-op

**Was.** `DefaultEntityBridge.shouldCullEntity(...)`, `shouldCullBlockEntity(...)` and
`shouldCullParticle(...)` **always returned `false`**. The bridge advertised
capabilities it did not have, which misleads the next contributor.

**Now.** All three delegate to `AsyncOcclusionCuller`, which is the component the
mixins use as well — so a caller of the bridge and a caller of the mixin reach the
same decision.

---
### G-11 🟢 No release tags

`git tag` is empty. Version 0.1.1 is recorded only in `gradle.properties`.

**Fix direction.** Tag `0.1.1` at `3782c32` and establish a tag-per-release convention.

---

### G-12 ✅ RESOLVED — The build-time guard against dead mixin targets

**Was.** This was the *root cause* that let G-2 ship undetected.

* `thorium.mixins.json` sets `"defaultRequire: 0"` and every injection carries
  `require = 0`, so a missing target is a silent no-op at runtime.
* The only build-time signal was Loom's remapper, and only for the **remap** nodes
  (`1.21.1`, `1.21.11`). The 26.x nodes used the plain Loom flavor and emitted nothing.
* The unit tests never loaded a mixin, so they could not catch it either.

**Now.** New `src/test/java/dev/hyperionfps/mixin/MixinTargetAuditTest.java` runs once
per version node (the shared test tree is compiled into every node) and:

1. reads the **expanded** `thorium.mixins.json` from the classpath, so it covers
   exactly the mixins that will be loaded, including the per-node
   `mixin_legacy` / `mixin_modern` expansions;
2. parses every mixin class with ASM to recover its `@Mixin` target and its injection
   method selectors;
3. reads the target class from the node's Minecraft jar and walks the inheritance
   chain, mirroring how Mixin resolves a target method;
4. **fails the build** for any selector that does not match a method.

A second test runs the audit in reverse: every mixin class compiled into the node must
be registered in the configuration, otherwise it is dead code that can never apply.

The Minecraft classes are handed to the test through the `thorium.compileClasspath`
system property, because Loom puts the Minecraft jar on the compile classpath but not
on the test runtime classpath.

**What it found on the day it was written:** the dead `tickParticleList` target (G-2)
and — a defect that was *not* in any gap list — `Window#updateVsync`, which does not
exist on 26.2 at all, so the frame pacing override was inert on that version. Both
are fixed.

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

### 4.2 Subsystems that are implemented but not wired 🔴

Seven subsystems are fully implemented, unit-tested and (in most cases) exposed in the
settings console, but **no call site invokes them**. They are the "fake toggles" the
project promised not to ship.

The startup log now names every one of them (see A-13 / D-4), and the console shows
them as *not wired* / *not available* instead of pretending they work.

| Subsystem | Class | What is missing | Plan |
| :--- | :--- | :--- | :--- |
| Chunk-section culling | `SectionCullingEngine` | `shouldRenderSection(...)` is never called — no chunk-section render hook | A-6 |
| Particle sheet batching | `ParticleBatchEngine` | never called — no particle render submission hook | A-8 |
| GPU entity instancing | `EntityInstancer` | never called — no entity render submission hook | A-7 |
| Async light propagation | `AsyncLightEngine` | the daemon worker starts but never receives work | A-9 |
| GL state filter | `GlStateTracker` | `shouldBindTexture` / `shouldUseProgram` are never consulted | A-10 |
| Immediate 2D batching | `ThoriumImmediateBatcher` | the flush runs every frame, but `shouldBatchDraw` is never consulted | A-11 |
| Input pacing | `ThoriumInputPacer` | `recordPacedTurn()` has no call site | A-12 |

**Additionally unsupported on some versions:**

| Subsystem | 1.21.1 | 1.21.11 | 26.1 | 26.2 | 26.3 |
| :--- | :---: | :---: | :---: | :---: | :---: |
| Chunk upload pacer | ✅ | ✅ | ⬜ | ⬜ | ⬜ |

The 26.x render pipeline uploads chunk meshes through a different path that Thorium
does not hook yet (PLAN A-4). The console shows the switch as *not available* on
those versions.

---
## 5. Risk register

| ID | Risk | Severity | Likelihood | Mitigation |
| :--- | :--- | :--- | :--- | :--- |
| R-1 | Mixin injection breaks on a future Minecraft update | High | **Low now** — the build-time audit fails the build | `require = 0` + era routing + `MixinTargetAuditTest` (G-12) |
| R-2 | `sun.misc.Unsafe.invokeCleaner` removed in a future JDK | Medium | Low | `OffHeap.available()` degrades to normal GC |
| R-3 | Memory regression from an unnoticed allocation | Medium | Medium | Enforce NFR-1; add allocation counters (G-7) |
| R-4 | Occlusion culling causes visible pop-in | Medium | Medium | Conservative thresholds; proximity guards (12-block BVH guard) |
| R-5 | Stonecutter/Loom toolchain breaks on upgrade | Medium | Low | Configuration cache already disabled; pin plugin versions |
| R-6 | Extreme FPS mode breaks "vanilla visuals" claims | Low | Medium | Mode is explicit and off by default |
| R-7 | Multiplayer server-side detection bans the client | Low | Very Low | 100 % client-side, no packet tampering |
| R-8 | Silent feature loss goes unnoticed (dead code paths, stale logs) | High | **Low now** | the build-time audit fails on a dead target; the startup log reports the real state of every subsystem |

---

## 6. Backlog (prioritized)

> **Full roadmap:** [`PLAN.md`](./PLAN.md) — the v0.2 work breakdown, per-item
> acceptance criteria, requirement-coverage matrix and task index. This section is
> the short form; `PLAN.md` is authoritative for anything added after 0.1.1.

### P0 — before v0.2

1. **A-6 / A-7 / A-8 / A-9 / A-10 / A-11** — give the seven unwired subsystems
   (§4.2) a real call site, or remove them from the console entirely.
2. **A-4** — chunk upload pacer on the 26.x render pipeline.
3. **G-7** — build the benchmark/capture harness; the README table stays empty until
   the numbers are reproducible.
4. **G-11** — tag `0.1.1`.
5. **G-8** — implement real input pacing, or rename the class to telemetry-only.

### P1 — quality & honesty

6. **D-6** — remove the remaining per-call `AABB` allocation in `FrustumCache`.
7. **G-4** — a primitive frustum test instead of the allocating `AABB` overload.
8. **D-3** — a render-thread stall watchdog that turns a silent freeze into a
   diagnosable event.
9. Obtain the `hs_err_pid22597.log` from the Android reporter to confirm the native
   crash attribution in §4.1.

### P2 — feature depth

10. **A-12** — input pacing (or rename it).
11. **G-4 / G-5 (PLAN)** — `LegacyVignetteMixin` exists only on 1.21.1: port or drop.
12. **G-9 (PLAN)** — `ThoriumFabricHooks` parity for the other four nodes.
13. Add a release CI workflow that runs `buildAll`, `test` and the mixin audit on all
    five nodes.

### Completed in this cycle

| Item | Status |
| :--- | :--- |
| **G-2** — particle ticking injection | ✅ Removed; the limit-accounting bug it caused is gone, particle culling stays live on all five nodes |
| **G-3** — particle simulator naming/pool | ✅ `ThoriumParticleSimulator` deleted, replaced by the honest `ThoriumParticleBudget` |
| **G-4** — `FrustumCache` allocation | 🟡 Distance test moved first; the remaining allocation needs an accessor mixin |
| **G-6** — documentation drift | ✅ Corrected; the unverifiable benchmark table was removed and replaced with a per-version feature matrix |
| **G-10** — `DefaultEntityBridge` no-op | ✅ Delegates to the real culling engines |
| **G-12** — build-time mixin audit | ✅ `MixinTargetAuditTest` — a dead target fails the build |
| **A-1** — frame pacer | ✅ `paceFrame()` is wired to `Minecraft#runTick`, with a persisted target-FPS cap |
| **A-2** — adaptive render distance | ✅ Wired to the game's own render-distance option, with a 60 s user-override yield |
| **A-3** — frame-context architecture | ✅ One publisher per frame on all five nodes; the per-entity publication is gone |
| **A-3b** — `LegacyFogMixin` on 1.21.11 | ✅ Ported to the new fog-environment API |
| **A-13 / D-4** — subsystem report | ✅ The startup log prints the real state of every subsystem |
| **D-1** — Sodium coexistence | ✅ Competing renderers are detected; Thorium stands down |
| **D-2** — fast-math safety | ✅ Off by default, disabled under a shader pack, no more double→float truncation |
| New — 26.2 frame pacing | ✅ `Window#updateVsync` never existed on 26.2; re-targeted to `GpuSurface#configure` |
| Android launch crash | ✅ Mitigated — safe-mode kill switch + 3 native-call guards |
## 7. Changelog

| Commit | Date | Change |
| :--- | :--- | :--- |
| *(uncommitted)* | 2026-10-21 | v0.2 groundwork: mixin target audit, frame context, frame pacer, adaptive distance, particle budget, compatibility probe, honest subsystem report, documentation pass |
| `948ec68` | 2026-10-20 | Restart & launch crash solved: safe mode, atomic settings persistence, hotkey parity |
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
| 2026-10-21 | **G-12 fixed** — new `MixinTargetAuditTest` reads the expanded `thorium.mixins.json` from the classpath, parses every mixin with ASM and asserts that each injection target exists in that node's Minecraft jar (walking the inheritance chain). A dead target now **fails the build**. The Minecraft jar is handed to the test through the new `thorium.compileClasspath` system property, because Loom keeps it off the test runtime classpath. |
| 2026-10-21 | The audit immediately found a defect that was in **no** gap list: `Window#updateVsync` does not exist on 26.2, so the frame-pacing override was inert there. 26.2's `ModernFramePacingMixin` now targets `com.mojang.blaze3d.systems.GpuSurface#configure` instead. |
| 2026-10-21 | **G-2 / G-3 fixed** — the dead `tickParticleList` injection was removed. Reading vanilla's tick loop showed that Thorium's re-implementation also skipped the particle-limit decrement, so on 1.21.1 (the only node where it ran) the engine stopped creating particles permanently once enough of them had died. `ThoriumParticleSimulator` was deleted; `ThoriumParticleBudget` (a live creation budget) replaced it. |
| 2026-10-21 | **A-3 fixed** — `ThoriumFrameContext` is now the single authoritative per-frame publisher. The modern entity-culling hook used to publish the camera context **once per entity**, which flushed the BVH cache, stepped the dynamic distance scale and fed the adaptive-distance EMA hundreds of times per frame. New per-node level-renderer hooks (`LegacyLevelRendererFrameMixin` on 1.21.11, `ModernLevelRendererFrameMixin` on 26.1/26.2/26.3) publish it once per frame. |
| 2026-10-21 | **A-1 fixed** — `ThoriumFramePacer.paceFrame()` was dead code; it is now driven by a core `Minecraft#runTick` hook (the only hook that fires exactly once per frame on every version). The pacer gained an absolute, drift-free deadline with bounded catch-up and a persisted target-FPS cap (`frame.pacer.targetFps`) exposed in the console. |
| 2026-10-21 | **A-2 fixed** — `AdaptiveDistanceController` is now bound to the game's own render-distance option and is driven by a real frame delta. It yields for 60 s when the player changes the distance by hand. |
| 2026-10-21 | **A-13 / D-4 fixed** — the startup log printed a hardcoded list of subsystem states. It now prints a live report (`ThoriumSubsystemReport`) that distinguishes ACTIVE / IDLE / DISABLED / UNWIRED / UNSUPPORTED, and names the seven subsystems that still have no call site. |
| 2026-10-21 | **D-1 / D-2 fixed** — `ThoriumCompatibility` detects competing renderers (Sodium family) and shader packs (Iris/Oculus/OptiFine). With a competing renderer, Thorium stands down the overlapping culling; with a shader pack, the fast-math overrides are disabled. The fast-math overrides are now off by default, and the double-returning `Mth.atan2` / `Mth.invSqrt` are no longer overridden at all. |
| 2026-10-21 | **G-6 fixed** — `README.md` / `MODRINTH.md` corrected: the unverifiable benchmark table was removed and replaced with a per-version feature matrix and a "measure it yourself" methodology. |
| 2026-10-21 | **G-10 fixed** — `DefaultEntityBridge` now delegates to the real culling engines instead of always returning `false`. |
| 2026-10-21 | **A-3b fixed** — `LegacyFogMixin` ported to 1.21.11 (the old `FogRenderer#setupFog` signature is gone; the hook now sits on `AtmosphericFogEnvironment#setupFog`). |
| 2026-10-21 | Test suite grew from 97 to **117 tests** across 26 classes. `buildAll` green on all five nodes, five jars produced. |
