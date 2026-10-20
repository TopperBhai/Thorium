# AGENTS.md — Operating Manual for Thorium

**Repository:** Thorium (mod id `thorium`, internal codename `Th-90`)
**Package root:** `dev.hyperionfps`
**Type:** Minecraft **client-side** performance/frame-pacing optimization mod (Fabric)

This file tells an AI agent (or new human) how to work in this repo *correctly*.
It is the operational contract. For *what* the product is supposed to do, read
[`spec.md`](./spec.md). For *where the project currently stands*, read
[`state.md`](./state.md).

---

## 1. Golden rules (read these first)

1. **Never edit a file under `versions/*/build/` or `versions/*/logs/`.** Those are build
   artifacts. `versions/<mc>/src/` is source and *is* meant to be edited.
2. **Never hand-edit the generated mixin list in `src/main/resources/thorium.mixins.json`
   beyond the two template placeholders.** The file is a Gradle template; the literal
   mixin names are injected per version node. See §4.
3. **Never add a Minecraft-version-specific import to `src/main/java/`.** Shared source
   must compile against *every* version in the matrix. Version-specific code goes in
   `versions/<mc>/src/main/java/`. See §3.
4. **Never allocate on the render thread** in hot paths. This is the single most
   important invariant of the codebase. See §7.
5. **Never remove `require = 0` from a mixin injection** unless you have verified the
   target exists on *every* version in the matrix. The whole config runs with
   `defaultRequire: 0` on purpose: a mixin that fails to apply must degrade, not crash
   the game. See §5.
6. **Do not add a new dependency** without checking the legacy (pre-26.1) nodes still
   resolve it — `1.21.1` and `1.21.11` use a different Loom flavor and different
   mappings.

---

## 2. Build & verify commands

All commands are run **from the repository root** and use the bundled Gradle wrapper.

| Goal | Command | Notes |
| :--- | :--- | :--- |
| Build every version | `./gradlew buildAll` | Aggregates `assemble` for all 5 nodes → `build/dist/*.jar`. Excludes `-sources.jar` / `-dev.jar`. |
| Build one version | `./gradlew :26.3:build` | Jar lands in `versions/26.3/build/libs/thorium-0.1.1+26.3.jar` |
| Run tests (one version) | `./gradlew :26.3:test` | **The gate.** 79 tests / 22 classes. |
| Run tests (all versions) | `./gradlew test` | Same shared test tree compiles into every node. |
| Compile only | `./gradlew :26.3:compileJava` | Fastest syntax/type check. |
| Launch dev client | `./gradlew :26.3:runClient` | Interactive; not usable in CI. |
| Clean | `./gradlew clean` | Also `versions/*/build` dirs are gitignored. |

Windows users can use `gradlew.bat` in place of `./gradlew`.

### Version matrix

| Node | Minecraft | Loom flavor | Java toolchain | Era |
| :--- | :--- | :--- | :--- | :--- |
| `:1.21.1` | 1.21.1 | `fabric-loom-remap` | 21 | legacy (obfuscated) |
| `:1.21.11` | 1.21.11 | `fabric-loom-remap` | 21 | legacy (obfuscated) |
| `:26.1` | 26.1 | `fabric-loom` | 25 | modern (unobfuscated) |
| `:26.2` | 26.2 | `fabric-loom` | 25 | modern (unobfuscated) |
| `:26.3` | 26.3 | `fabric-loom` | 25 | modern (unobfuscated) |

* `stonecutter active "26.3"` in `stonecutter.gradle.kts` — the node the IDE treats as
  "current". Marked `/* [SC] DO NOT EDIT */`; change it only deliberately.
* Compile `options.release` is **21 for every node**, even the Java-25 toolchain nodes.
  This keeps bytecode loadable by the Java 21 runtime required by 1.21.x.
* Gradle config cache is **disabled** (`gradle.properties`); Stonecutter + dual-Loom is
  not configuration-cache clean. Do not try to "fix" this.
* JDK 25 is auto-provisioned via the `foojay-resolver-convention` plugin — no manual
  JDK install needed.

### Definition of done (minimum bar before proposing a change)

```bash
./gradlew :26.3:test        # 0 failures
./gradlew buildAll          # all 5 nodes produce a jar
```

If a change touches shared source (`src/main/java/`), **all five** nodes must still
compile. `buildAll` is the only command that proves it.

---

## 3. Source layout

```
F:\Toperion Optimization\
├── settings.gradle.kts              # Stonecutter root; version matrix + mapBuilds
├── stonecutter.gradle.kts           # root build script; `buildAll` aggregate task
├── gradle.properties                # mod metadata + shared dep versions
├── src/main/java/dev/hyperionfps/   # SHARED source — compiles on all 5 versions (57 files)
├── src/main/resources/              # fabric.mod.json + thorium.mixins.json (Gradle templates)
├── src/test/java/dev/hyperionfps/   # SHARED tests — JUnit 5 (22 classes / 79 tests)
├── versions/<mc>/
│   ├── build.gradle.kts             # per-node plugin + template values
│   ├── gradle.properties            # deps.minecraft, mod.mc_range
│   └── src/main/java/dev/hyperionfps/   # VERSION-SPECIFIC source (18–21 files)
│       ├── mixin/legacy/*  or  mixin/modern/*
│       ├── gui/ThoriumVideoSettingsScreen.java
│       └── gui/ThoriumModernHudRenderer.java  (modern only)
└── net/                             # decompile reference (gitignored)
```

**The shared/version split is the single most important structural fact.**

* `src/main/java/` — engine-agnostic policy and logic. Must not reference Minecraft
  classes that changed between eras *unless* the reference is safe on all versions.
  Where a version difference is unavoidable, go through `dev.hyperionfps.version.VersionBridge`.
* `versions/<mc>/src/main/java/` — code that touches a specific Minecraft API surface.
  `1.21.1` and `1.21.11` carry `mixin/legacy/*`; `26.x` carry `mixin/modern/*`.

### Where to put new code

| You are adding… | Put it in |
| :--- | :--- |
| A pure policy/algorithm class (no MC API) | `src/main/java/dev/hyperionfps/<pkg>/` |
| A mixin that targets the same method on all versions | `src/main/java/dev/hyperionfps/mixin/core/` |
| A mixin targeting a version-specific method | `versions/<mc>/src/main/java/dev/hyperionfps/mixin/{legacy,modern}/` |
| A test for a shared class | `src/test/java/dev/hyperionfps/<pkg>/` |
| A new subsystem | New subpackage of `dev.hyperionfps` + a matching test package |

---

## 4. Adding a mixin (two-place registration)

A mixin only applies if it is registered in **both** places.

**Step 1 — the config template** (`src/main/resources/thorium.mixins.json`):

```json
{
  "client": [
    "core.MinecraftThreadCaptureMixin",
    ...
    "core.SmartLeavesCullingMixin"${mixin_legacy}${mixin_modern}
  ]
}
```

The `${mixin_legacy}` / `${mixin_modern}` placeholders are expanded by each node's
`processResources` task.

**Step 2 — the per-node template values** (`versions/<mc>/build.gradle.kts`):

```kotlin
"mixin_legacy" to ",\n    \"legacy.LegacyMthMixin\",\n    ...",
"mixin_modern" to ""
```

So a **legacy-only** mixin must be appended to `mixin_legacy` in
`versions/1.21.1/build.gradle.kts` **and** `versions/1.21.11/build.gradle.kts`.
A **modern-only** mixin must be appended to `mixin_modern` in all three
`versions/26.{1,2,3}/build.gradle.kts`.

**Step 3 — era routing.** `dev.hyperionfps.mixin.HyperionMixinPlugin` decides at launch
whether a mixin applies, based on the detected Minecraft version:

| Package prefix | Applies when |
| :--- | :--- |
| `core.*` | always |
| `legacy.*` | era == `LEGACY` (Minecraft < 26.1) |
| `modern.*` | era == `UNOBFUSCATED` (Minecraft >= 26.1) |
| anything else | always |

The plugin logs a routing summary (`eligible=`, `skipped=`, `applied=`) that
`HyperionFPS.onInitializeClient()` prints. **Check this log line first** when a mixin
"does nothing" — it is the fastest diagnosis.

### Mixin authoring rules

* Always annotate injections with `require = 0` unless the target is guaranteed on all
  versions. The config sets `defaultRequire: 0`.
* Use **Mojang official mappings** (`loom.officialMojangMappings()`) for every node —
  the unified source namespace. Do not use Yarn names.
* Legacy-era mixins use the **GLFW** keyboard/event API; modern-era mixins use the
  **InputConstants** key-event API. Match the existing `LegacyKeyboardMixin` /
  `ModernKeyboardMixin` pattern.
* Mixin classes are named `Legacy<Concern>Mixin` / `Modern<Concern>Mixin` to make the
  era family self-evident.
* Static `@Inject` handlers at `@At("HEAD")` are required where the handler must run
  before the constructor completes (see `MinecraftThreadCaptureMixin`).

---

## 5. Version abstraction — use the bridge, don't sniff versions

`dev.hyperionfps.version.VersionBridge` is the sanctioned way to talk to
version-dependent engine behaviour. It exposes four sub-bridges:

| Sub-bridge | Covers |
| :--- | :--- |
| `WindowContext` | GLFW swap interval, window handle, framebuffer size, raw FPS limit |
| `RenderBridge` | batching support, render distance, Extreme FPS mode |
| `EntityBridge` | entity/block-entity/particle culling queries, dynamic distance scale |
| `MathBridge` | fast `sin`/`cos`/`atan2`/`invSqrt`/`sqrt` |

Obtain it via `HyperionFPS.bridge()` (or `VersionBridgeRegistry.get()`).

**Do not** write `if (version >= 26.1)` scattered through subsystem code. The
`GameVersion` value object already supports comparisons
(`gameVersion().isAtLeast("26.1")`), and `Era` classifies the obfuscation boundary.
Engine API differences belong in a mixin (era-specific) or behind the bridge
(behavioural), never inline in a policy class.

---

## 6. Coding conventions

* **Java 21 language level** everywhere, even in Java-25 toolchain nodes.
* Package-private classes and members by default; `public` only for genuine API surface.
* Static utility classes: `private` constructor, all-`static` members.
* Toggles are `AtomicBoolean` (or `volatile boolean` for render-thread-only state).
  Every toggle follows the pattern `isEnabled()` / `setEnabled(boolean)`.
* Javadoc on every non-trivial class explaining *why*, not *what*. Existing files model
  the expected depth — match it.
* Logging via the `Logger` in `HyperionFPS` (`"Thorium"`) or a local `LoggerFactory`
  logger with a topic suffix (e.g. `"HyperionFPS/MixinRouter"`).
* Formatting: 4-space indent, no tabs, ~120 column soft limit.

---

## 7. Performance invariants (the reason this project exists)

Thorium's entire value proposition is *less work per frame*. A change that regresses
this is a regression in the product.

### Hard rules

| Rule | Detail |
| :--- | :--- |
| **No allocation in frame paths** | Use the pooled types: `ZeroAllocationEngine.pooledBlockPos()`, `pooledVec3()`, `pooledMatrix4f()`, `pooledQuaternion()`. |
| **Prefer primitive arrays** | Frametime history is `float[128]` + `boolean[128]`, not a `Deque<Float>`. |
| **Bound every loop** | Raycasts cap at 12 steps. Loops over world data must have a hard maximum. |
| **Budget time, not just count** | The chunk upload pacer uses *both* a nanosecond budget (2.5 ms) and a section count (8). |
| **Use daemon threads** | All background workers are daemon threads with a `try/catch` around the loop body and an `interrupt` check. |
| **Cache with bounded maps** | `ConcurrentHashMap` with a fixed initial capacity and an explicit eviction path when it grows. |
| **No `synchronized` on the render thread** | Prefer `volatile` snapshots or atomics. The render thread must never block. |

### Known telemetry counters (use these, don't add new ad-hoc ones)

Every subsystem exposes `get*Count()`, `get*Saved()`, `get*Skipped()` style metrics via
`AtomicLong`. These feed the HUD and the `ThoriumGuiManager.getTelemetrySummary()` line.
When you add a subsystem, add its counters to the summary string too.

---

## 8. Threading rules

| Thread | Owns | Rules |
| :--- | :--- | :--- |
| Render / client thread | `FramePacing`, `ThoriumFramePacer`, `ThoriumFrametimeTracker`, `GlStateTracker`, `HudBatcher`, `ThoriumImmediateBatcher`, `FrustumCache` | Never block, never allocate, never `synchronized`. |
| `Thorium-OcclusionCuller` (daemon) | `AsyncOcclusionCuller` cache maintenance | 50 ms tick. Uses `ConcurrentHashMap` + `BitSet`. |
| `Thorium-LightEngine` (daemon) | `AsyncLightEngine` | `ConcurrentLinkedQueue`, drains 256 tasks/batch, parks 10 ms idle. |
| `Hyperion-Janitor` (daemon) | `IdleJanitor` | 60 s sweep, 120 s idle budget. |
| `Hyperion-Governor` (daemon) | `HyperionPrelaunch` | 20 iterations × 500 ms. |

`RenderThreads.captureClientThread()` (invoked from `MinecraftThreadCaptureMixin` at
constructor HEAD) records the live client thread. `ThreadGovernor` raises the render and
worker thread priorities to `Thread.MAX_PRIORITY`, never lowering them.

---

## 9. Testing conventions

* **JUnit 5 (Jupiter)** — `junit-bom:6.1.3`, `useJUnitPlatform()`.
* One shared test tree: `src/test/java/dev/hyperionfps/<same-package-as-prod>/`.
* Test classes are **package-private** (`class FooTest`), methods are package-private
  `void`, annotated `@Test`. No `@DisplayName`, no `@ParameterizedTest`, no `@Nested`.
* Static-import `org.junit.jupiter.api.Assertions.*`.
* `@BeforeEach` resets global static toggles; `@AfterEach` shuts down spawned workers
  (see `AsyncLightEngineTest`).
* Tests are **plain JVM** — no Minecraft bootstrap. They cover policy layers, toggle
  semantics, null-safety, buffer accounting and version parsing. Anything that needs a
  live Minecraft class cannot be unit tested here; keep that logic behind the
  `VersionBridge` so it *can* be tested.

### Test seams that exist for this purpose

| Seam | Purpose |
| :--- | :--- |
| `FramePacing.inBootPhase(long uptimeMillis)` (package-private) | Test the boot window without sleeping |
| `IdleJanitor.sweepOnce(long budgetNanos)` | Test the sweep synchronously |
| `DirectBufferPool(name, minShift, maxShift, perClassLimit)` | Test small pools without allocating MiB |
| `GameVersionDetector` honours `-Dhyperionfps.gameVersion=` | Test version parsing/era routing |

**When you add a subsystem, add a test class with it.** A new subsystem without tests
is an incomplete change.

---

## 10. Gotchas & traps

1. **`fabric.mod.json` and `thorium.mixins.json` are Gradle templates.** They contain
   `${...}` placeholders and are expanded by `processResources` via `expand(...)`.
   Raw `${` in those files will be interpreted by Gradle, not by the loader.
2. **`${mixin_legacy}` / `${mixin_modern}` are not optional.** Deleting them breaks the
   per-node mixin list silently — the mixin just never applies.
3. **The version matrix has 5 nodes, but the docs say 4.** `1.21.11` exists and is
   built. `README.md` / `MODRINTH.md` are out of date on this point.
4. **User settings live in `dev.hyperionfps.config.ThoriumConfig`.** Do not invent a
   second persistence mechanism. Read/write through `ThoriumConfig.get()`; it handles
   the atomic file write, the lenient parser and the default fallbacks. Adding a new
   user-facing toggle means adding a key constant, an entry in `captureFromSubsystems()`
   and an entry in `applyToSubsystems()` — all three, or the setting will not survive
   a restart.
5. **`TelemetryGate.allowed()` always returns `false`.** Telemetry is hard-disabled by
   policy, not by config. Do not add a "user consent" path without an explicit request.
6. **`OffHeap` uses `sun.misc.Unsafe.invokeCleaner`** and prints a JDK deprecation
   warning at test time. That warning is expected, not a failure.
7. **`HyperionMixinPlugin` decides at launch.** A mixin added to the config but not to
   the per-node `templateValues` map never runs — and never errors.
8. **Never call a native GL/SDL function without a current context.**
   `glfwSwapInterval` and `SDL_GL_SetSwapInterval` are undefined behaviour without one
   and have been observed to abort the process with a native SIGSEGV on Android OpenGL
   translation layers (Pojav/RxLauncher with LTW). Always route them through the
   guarded helpers in the frame-pacing mixins.
9. **`sun.misc.Unsafe` / `ProcessHandle` / `LockSupport.parkNanos`** are all used. Keep
   them off the render thread's critical path, and never let them escape an unguarded
   `try`/`catch` in a startup path.
10. **The `net/` directory is a decompile reference and is gitignored.** Use it to look
    up Minecraft internals; never edit it.
11. **A dead mixin target fails SILENTLY.** Because of `defaultRequire: 0`, an injection
    whose target method no longer exists is a no-op at runtime — no crash, no warning
    on the 26.x nodes. This already shipped once: `ParticleEngineMixin`'s
    `tickParticleList` target exists on **1.21.1 only** and is inert on 1.21.11 and all
    26.x nodes. **Verify your target exists in every node you claim to support.**
    The check that catches it:
    ```bash
    javap -p <minecraft-client.jar> | grep <targetMethod>
    ```
    Loom's remapper prints `Cannot remap <method> because it does not exist` for the
    remap nodes only (1.21.1, 1.21.11) — treat that warning as a build failure, not
    noise. See `state.md` G-2 and G-12.

### Safe mode — the emergency escape hatch

If a change to Thorium breaks a user's game, they must be able to recover without
uninstalling the mod. Two independent triggers exist:

| Trigger | When it is read | Scope |
| :--- | :--- | :--- |
| `-Dhyperionfps.safeMode=true` | Mixin bootstrap, before any Minecraft class is touched | Every mixin is skipped |
| `safeMode` key in `thorium-config.json` | Client initialisation, before the window is created | All subsystems disabled; the frame-pacing hook becomes a no-op |

The property is checked first **on purpose**: a player whose game cannot start at all
cannot edit a config file. `ThoriumSafety.isSafeMode()` is the single query — use it in
any code path that touches native graphics or the render pipeline. Safe mode is also
exposed as a toggle in the settings console (Advanced tab).

---

## 11. Where to record work

* Change **behaviour** or **architecture** → update [`spec.md`](./spec.md).
* Change **status, add a subsystem, fix a gap** → update [`state.md`](./state.md).
* Change **how to build / where code lives / conventions** → update this file.

Keep all three in the repository root and commit them together with the change they
describe.
