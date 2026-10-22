# AGENTS.md — Operating Manual for Thorium

**Repository:** Thorium (mod id `thorium`, internal codename `Th-90`)
**Java package root:** `dev.hyperionfps` (the `mod.group=dev.thorium` in
`gradle.properties` is *mod metadata only* — it is not the package root)
**Type:** Minecraft **client-side** performance/frame-pacing optimization mod (Fabric)

This file tells an AI agent (or new human) how to work in this repo *correctly*.
It is the operational contract. For *what* the product is supposed to do, read
[`spec.md`](./spec.md). For *where the project currently stands*, read
[`state.md`](./state.md). For *what to build next*, read [`PLAN.md`](./PLAN.md).

---

## 1. Golden rules (read these first)

1. **Never edit a file under `versions/*/build/` or `versions/*/logs/`.** Those are build
   artifacts. `versions/<mc>/src/` is source and *is* meant to be edited.
2. **Never hand-edit the generated mixin list in `src/main/resources/thorium.mixins.json`
   beyond the two template placeholders.** The file is a Gradle template; the literal
   mixin names are injected per version node. See §4.
3. **Never add a Minecraft-version-specific import to `src/main/java/`.** Shared
   source must compile against *every* version in the matrix. Version-specific code goes
   in `versions/<mc>/src/main/java/`. See §3.
4. **Never allocate on the render thread** in hot paths. This is the single most
   important invariant of the codebase. See §7.
5. **Never remove `require = 0` from a mixin injection** unless you have verified the
   target exists on *every* version in the matrix. The whole config runs with
   `defaultRequire: 0` on purpose: a mixin that fails to apply must degrade, not crash
   the game. See §5.
6. **Do not add a new dependency** without checking the legacy (pre-26.1) nodes still
   resolve it — `1.21.1` and `1.21.11` use a different Loom flavor and different
   mappings.
7. **Never write a vanilla option that the server also owns** — most importantly
   `Options#renderDistance`. See §10.11; it is the single most expensive mistake this
   codebase has made.
8. **Small atomic steps, mandatory markdown synchronization & hard turn stop.** Break large features
   into 5–10 small, verifiable steps (tracked in [`TODO.md`](./TODO.md)). Execute **strictly one step
   at a time**. After each step, run the test gate, update all core docs (`TODO.md`, `spec.md`,
   `state.md`, and `AGENTS.md`), and **STOP execution**. Never start the next step in the same turn
   without explicit user confirmation. A new AI session must be able to resume immediately
   without re-deriving context or guessing what is done.

---

## 2. Build & verify commands

All commands are run **from the repository root** and use the bundled Gradle wrapper.

| Goal | Command | Notes |
| :--- | :--- | :--- |
| Build every version | `./gradlew buildAll` | Aggregates `assemble` for all 5 nodes → `build/dist/*.jar`. Excludes `-sources.jar` / `-dev.jar`. |
| Build one version | `./gradlew :26.3:build` | Jar lands in `versions/26.3/build/libs/thorium-0.1.2+26.3.jar` |
| Run tests (one version) | `./gradlew :26.3:test` | **The gate.** 317 tests / 42 classes. |
| Run tests (all versions) | `./gradlew test` | Same shared test tree compiles into every node. |
| Run **one** test class | `./gradlew :26.3:test --tests "dev.hyperionfps.adaptive.AdaptiveDistanceTest"` | Fastest way to iterate on a single subsystem. |
| Compile only | `./gradlew :26.3:compileJava` | Fastest syntax/type check. |
| Launch dev client | `./gradlew :26.3:runClient` | Interactive; not usable in CI. |
| Clean | `./gradlew clean` | Also `versions/*/build` dirs are gitignored. |

Windows users use `gradlew.bat` in place of `./gradlew`.

### The mixin target audit is part of `test`

`src/test/java/dev/hyperionfps/mixin/MixinTargetAuditTest.java` runs once per version
node and **fails the build** when a registered mixin points at a method that does not
exist on that node's Minecraft version. Because every injection carries
`require = 0`, this test is the only thing standing between a renamed Minecraft method
and a silently dead optimisation.

It works by:

1. reading the **expanded** `thorium.mixins.json` from the classpath (so it covers
   exactly the mixins that will be loaded, per node),
2. parsing each mixin class with ASM (`org.objectweb.asm` — already on the classpath
   through Fabric Loader, no new dependency),
3. reading the target class from the node's Minecraft jar and walking the inheritance
   chain, exactly like Mixin resolves a target method.

The Minecraft jar is handed to the test through the `thorium.compileClasspath` system
property, because Loom puts the Minecraft jar on the **compile** classpath but not on
the test runtime classpath. Each node's `build.gradle.kts` sets it in the `test`
task's `doFirst` block.

**When you add a mixin, the audit is what tells you the target is wrong.** Run
`./gradlew :26.3:test` before you run the game.

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
* The 26.x nodes have **no `remapJar` task** (plain Loom). Use `assemble` / `build`.

### Definition of done (minimum bar before proposing a change)

```bash
./gradlew :26.3:test        # 0 failures
./gradlew test              # all five nodes
./gradlew buildAll          # all 5 nodes produce a jar
```

If a change touches shared source (`src/main/java/`), **all five** nodes must still
compile. `buildAll` is the only command that proves it.

---

## 2a. Looking up Minecraft internals (`javap`)

The `net/` folder in the repo root is a **stale partial decompile** (7 files) and is
gitignored. It is not a usable reference. The real source of truth is the Loom cache:

```powershell
# 26.x — the jar is already in Mojang names:
javap -p -c -classpath "$env:USERPROFILE\.gradle\caches\fabric-loom\26.3\minecraft-client.jar" net.minecraft.client.Options

# 1.21.x — the GLOBAL cache jar is in intermediary names; the PROJECT-local
# loom-cache jar is Mojang-named:
$jar = (Get-ChildItem .gradle/loom-cache/minecraftMaven -Recurse -Filter "*1.21.1-loom*.jar" | Select-Object -First 1).FullName
javap -p -c -classpath $jar net.minecraft.client.Options
```

`javap -c` (bytecode) is how you answer "what does vanilla actually do here" — it is
how the render-distance chain in §10.11 was proven. `javap -p` alone gives you the
member list, which is enough to answer "does this method exist on this version?".

---

## 3. Source layout

```
F:\Toperion Optimization\
├── settings.gradle.kts              # Stonecutter root; version matrix + mapBuilds
├── stonecutter.gradle.kts           # root build script; `buildAll` aggregate task
├── gradle.properties                # mod metadata + shared dep versions
├── src/main/java/dev/hyperionfps/   # SHARED source — compiles on all 5 versions (89 files)
├── src/main/resources/              # fabric.mod.json + thorium.mixins.json (Gradle templates)
├── src/test/java/dev/hyperionfps/   # SHARED tests — JUnit 5 (42 classes / 317 tests)
├── versions/<mc>/
│   ├── build.gradle.kts             # per-node plugin + template values
│   ├── gradle.properties            # deps.minecraft, mod.mc_range
│   └── src/main/java/dev/hyperionfps/   # VERSION-SPECIFIC source (20–22 files)
│       ├── mixin/legacy/*  or  mixin/modern/*
│       ├── gui/ThoriumVideoSettingsScreen.java
│       └── gui/ThoriumModernHudRenderer.java  (modern only)
└── net/                             # stale partial decompile (gitignored) — use javap, see §2a
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

### The shared source's Minecraft API surface is deliberately tiny

`src/main/java/` may only import these Minecraft types (verified by the current tree):
`Minecraft`, `Options`, `Frustum`, `SoundInstance`, `AbstractSoundInstance`,
`BlockPos`, `Component`, `SoundSource`, `Entity`, `Leashable`, `Player`,
`BlockEntity`, `BlockState`, `BlockGetter`, `AABB`, `Vec3`, `Direction`,
`MouseHandler`, `KeyboardHandler`, `ClientTelemetryManager`, `TelemetryEventSender`,
`LeavesBlock`, `BlockBehaviour`,
`ChestBlockEntity`, `TrappedChestBlockEntity`, `EnderChestBlockEntity`,
`ShulkerBoxBlockEntity`, `ItemFrame`, `GlowItemFrame`, `AbstractContainerMenu`, `Slot`,
`Particle`, `ParticleEngine`, `ParticleOptions`, `ParticleType`, `ParticleTypes`.

`MouseHandler` and `KeyboardHandler` are only used as **mixin targets** (the AFK
activity hooks). Their methods are referenced without a descriptor, because the
descriptor changed between the versions — see §10.18.

**Step 9** added one more type to that surface, `ClientLevel`:

| Member | Verified on all 5 nodes | Note |
| :--- | :--- | :--- |
| `ClientLevel#addParticle(ParticleOptions, double×6)` | ✅ | the method the weather uses to spawn the ground splash particles on **every** node; the shared splash hook sits here (`core/WeatherSplashMixin`) |

It is the only member of `ClientLevel` the shared source touches. `ClientLevel` also
carries a second `addParticle` overload with a different signature
(`ParticleOptions, boolean, boolean, double×7`), so the mixin selector must name the
descriptor explicitly — a name-only selector would match both.

**Step 8** added a second use of `MouseHandler`: it is now also an **accessor
target** (`core/MouseHandlerAccessor`), because the late input sampler has to know how
much cursor movement the game has collected but not yet applied. The two fields it
reads are verified on all five nodes with `javap`:

| Member | Verified on all 5 nodes | Note |
| :--- | :--- | :--- |
| `MouseHandler#accumulatedDX` | ✅ | the horizontal cursor movement waiting to be applied; reset to `0` by `handleAccumulatedMovement()` |
| `MouseHandler#accumulatedDY` | ✅ | the vertical counterpart |

`MouseHandler#handleAccumulatedMovement()` is the **late input sample point** — see
§8f.

The last eight were added by **Step 4** (`StorageCullingEngine`). They are not
version-specific: `javap` on the five client jars confirms the same FQN, the same
superclass and the same members on every node —

| Type | Verified on all 5 nodes | Note |
| :--- | :--- | :--- |
| `ChestBlockEntity` | ✅ | `TrappedChestBlockEntity` extends it on every node |
| `TrappedChestBlockEntity` | ✅ | listed explicitly so a future decoupling cannot silently drop it |
| `EnderChestBlockEntity` | ✅ | not a `Container` — its menu is backed by the player's ender inventory |
| `ShulkerBoxBlockEntity` | ✅ | |
| `ItemFrame` | ✅ | `GlowItemFrame` extends it on every node |
| `GlowItemFrame` | ✅ | Minecraft spells it `GlowItemFrame`, **not** `GlowingItemFrame` |
| `AbstractContainerMenu` | ✅ | `public final NonNullList<Slot> slots` on every node |
| `Slot` | ✅ | `public final Container container` on every node |

**Step 5** added two more to that list, both verified on all five nodes with
`javap`:

| Type | Verified on all 5 nodes | Note |
| :--- | :--- | :--- |
| `Leashable` | ✅ | `net.minecraft.world.entity.Leashable`; `isLeashed()` is a `default` method with the identical null-safe body on every node. Used by the mob farm exemption rule |
| `AbstractSoundInstance` | ✅ | `protected float volume` on every node — the field the acoustic dampening writes through `core/AbstractSoundInstanceAccessor` |

**Step 6** added the particle types, all verified on all five nodes with `javap`:

| Type | Verified on all 5 nodes | Note |
| :--- | :--- | :--- |
| `ParticleOptions` | ✅ | `net.minecraft.core.particles.ParticleOptions`; `getType()` returns `ParticleType<?>` on every node |
| `ParticleType` | ✅ | `net.minecraft.core.particles.ParticleType` |
| `ParticleTypes` | ✅ | `net.minecraft.core.particles.ParticleTypes`; the constants `EXPLOSION`, `EXPLOSION_EMITTER`, `FIREWORK`, `CAMPFIRE_COSY_SMOKE`, `CAMPFIRE_SIGNAL_SMOKE` and `LARGE_SMOKE` exist with the same names and the same types on every node |
| `ParticleEngine` | ✅ | `net.minecraft.client.particle.ParticleEngine`; `createParticle(ParticleOptions, double×6)` has the **identical descriptor** on all five nodes, which is why the particle hook is a single `core/` mixin |

**Step 7** added no new type to that surface, because the Elytra Flight Smoother
reads the player through the two members that were already allowed:

| Member | Verified on all 5 nodes | Note |
| :--- | :--- | :--- |
| `Minecraft#player` | ✅ | `public LocalPlayer player` — the field, not a getter, on every node |
| `Entity#getDeltaMovement()` | ✅ | `public Vec3 getDeltaMovement()` — same name, same return type on every node |

`PlayerVelocityPublisher` is the **only** class that touches them. It reads the
velocity once per world frame and publishes it into `ElytraFlightSmoother`, which
holds no Minecraft type at all. Keep it that way: the moment the player entity moves
into the policy engine, the whole decision table stops being unit-testable (§8e).

`ParticleTypes` is the one place where the constants could have moved between the
eras (the same thing happened to `EntityType` → `EntityTypes`). They did **not**:
the class keeps its name and its constants on every supported version, so the
explosion shield can name them from shared source. The comparison is by identity —
vanilla registers exactly one instance per particle type — so the classification
costs a handful of reference comparisons and no registry lookup.

Two era traps sit next to them and must not be used from shared code:

| API | 1.21.1 | 1.21.11 | 26.1 | 26.2 | 26.3 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Solid-render test | `isSolidRender(BlockGetter, BlockPos)` | `isSolidRender()` | `isSolidRender()` | `isSolidRender()` | `isSolidRender()` |
| Full-cube test | `isCollisionShapeFullBlock(BlockGetter, BlockPos)` | same | same | same | same |

`isSolidRender` changed its signature between the eras — use
`canOcclude()` **and** `isCollisionShapeFullBlock(BlockGetter, BlockPos)`, which are
identical on all five nodes, to decide whether a block hides what is behind it.

**Traps inside that surface** — these compile on one era and not the other:

| API | 1.21.1 | 1.21.11 | 26.1 | 26.2 | 26.3 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Player message | `displayClientMessage(C, boolean)` | `displayClientMessage(C, boolean)` | `sendOverlayMessage(C)` | `sendOverlayMessage(C)` | `sendOverlayMessage(C)` |
| Open a URL | `net.minecraft.Util` | `net.minecraft.util.Util` | `net.minecraft.util.Util` | `net.minecraft.util.Util` | `Blaze3D.openUri(URI)` |
| Current screen | `Minecraft#screen` (field) | `Minecraft#screen` (field) | `Minecraft#screen` (field) | `Minecraft#gui.screen()` | `Minecraft#gui.screen()` |
| Set the screen | `Minecraft#setScreen` | `Minecraft#setScreen` | `setScreen` + `setScreenAndShow` | `setScreenAndShow` | `setScreenAndShow` |
| Keyboard input | GLFW callbacks | GLFW callbacks | `KeyboardHandler#keyPress` | `KeyboardHandler#keyPress` | `KeyboardHandler#keyPress` |
| Swap interval | `GLFW.glfwSwapInterval` | `GLFW.glfwSwapInterval` | `SDL_GL_SetSwapInterval` | `SDL_GL_SetSwapInterval` | `SDL_GL_SetSwapInterval` |

So: **never send a player message, open a URL, or read the current screen from shared
code.** The method was renamed or moved between eras. Put it in a per-version class, or
go through the `VersionBridge`.

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
* **Accessors** (`@Mixin` + `@Accessor("field")`) are the only sanctioned way to read
  or write a Minecraft field that has no accessor. The first one is
  `core/AbstractSoundInstanceAccessor`, which writes the `volume` field of a sound
  instance for the mob farm acoustic dampening. The build-time audit verifies
  **field** selectors as well as method selectors, so a renamed field fails the
  build instead of breaking the accessor silently. An accessor mixin must be
  registered in `thorium.mixins.json` like any other, and the reverse audit fails
  if it is not.

---

## 5. Version abstraction — use the bridge, don't sniff versions

`dev.hyperionfps.version.VersionBridge` is the sanctioned way to talk to
version-dependent engine behaviour. It exposes four sub-bridges:

| Sub-bridge | Covers |
| :--- | :--- |
| `WindowContext` | GLFW/SDL swap interval, window handle, framebuffer size, raw FPS limit |
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

### 6a. The settings console has exactly one copy of every row ⚠️ read before adding a switch

The console exists in five per-version classes (`ThoriumVideoSettingsScreen`) because
the widget and rendering APIs differ between the legacy and the modern era. The
**rows** do not differ, so they live in shared code:

| Concern | Owner |
| :--- | :--- |
| A row's title, status, click handler, description, badge | `dev.hyperionfps.gui.ThoriumSettingRows` |
| A hardware preset's full profile | `dev.hyperionfps.config.ThoriumPresets` |
| A headline feature's persisted switch | `dev.hyperionfps.core.ThoriumFeatureToggles` |
| The widget, the scrolling and the drawing | the per-version screen |

**Therefore: never add a settings row to a `versions/<mc>/...ThoriumVideoSettingsScreen.java`
file.** Add it to `ThoriumSettingRows` and it appears on all five nodes at once. The
only things a per-version screen may still vary are the widget type, the drawing and
the two genuinely version-specific actions (the browser hand-off for the community
invite and the chunk-upload-pacer availability).

### 6b. Headline features: one switch, one owner

The eight "headline" features (REQ-HEAD-1 … REQ-HEAD-9) are delivered one step at a
time, so their **configuration** lives in `ThoriumFeatureToggles`:

* The switch itself lives in `ThoriumFeatureToggles.Feature` — never in the feature's
  subsystem class. A subsystem *reads* the registry; it does not keep a second flag.
* A feature calls `ThoriumFeatureToggles.Feature.X.markWired()` once it has a live
  call site. Until then the console shows `NOT AVAILABLE` and the startup report
  shows `UNWIRED` — a switch that cannot do anything must never look like one
  (REQ-UI-2).
* A new user-facing switch still needs all three places: the key constant, an entry in
  `captureFromSubsystems()` and an entry in `applyToSubsystems()` (§10.4).

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
| **No `String.format` on the render thread** | The HUD formats four floats per frame; `String.format` allocates a `Formatter` + `StringBuilder` + `String` each time. Format by hand. |

### Known telemetry counters (use these, don't add new ad-hoc ones)

Every subsystem exposes `get*Count()`, `get*Saved()`, `get*Skipped()` style metrics via
`AtomicLong`. These feed the HUD and the `ThoriumGuiManager.getTelemetrySummary()` line.
When you add a subsystem, add its counters to the summary string too.

The storage culler follows that convention:
`StorageCullingEngine.getContainersCulled()`, `getEnclosedContainersCulled()`,
`getItemFramesCulled()` and `getOpenContainersExempt()`.

The explosion shield follows it too:
`ExplosionShield.getAdmitted()`, `getCulledRedundant()`, `getCulledOverRate()` and
`getPriorityAdmitted()`, plus `ThoriumParticleBudget.getPriorityAdmitted()` for the
particles that were admitted outside the creation budget.

The late input sampler follows it as well:
`ThoriumReflexController.getLateSamples()` (frames that were given a late sample),
`getFreshInputFrames()` (frames in which the drain actually delivered movement) and
`getRecoveredPixels()` (the cursor movement that was applied to the camera one frame
earlier than vanilla would have applied it).

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

## 8a. The frame boundary is authoritative ⚠️ read this before adding a hook

There are exactly **two** per-frame entry points, and nothing else may claim to be one:

| Boundary | Hook | Fires | Owns |
| :--- | :--- | :--- | :--- |
| **Client frame** | `Minecraft#runTick(Z)V` — `core/MinecraftFrameBoundaryMixin` | once per iteration of Minecraft's main loop, in a world *and* in the menus | frametime sample, frame pacer |
| **World frame** | `LevelRenderer#renderLevel` / `#render` — `Legacy`/`ModernLevelRendererFrameMixin` | once per rendered world frame | camera context, dynamic distance scale, BVH cache flush, adaptive-distance EMA, entity LOD |

Both funnel into `dev.hyperionfps.frame.ThoriumFrameContext`.

**Why this matters.** Before the frame context existed, the camera context was published
from `AsyncOcclusionCuller#onFrameBegin`, which the modern entity-culling mixin called
**once per entity**. Everything downstream — the BVH cache flush, the dynamic distance
scale, the adaptive-distance EMA — therefore ran hundreds of times per frame with a
hundreds-of-times-too-small delta. The distance scale collapsed to its 0.35 floor
within a single frame.

**Rules.**

* Never call `ThoriumFrameContext.beginWorldFrame(...)` from anything but a
  level-renderer hook.
* Never publish camera state from a per-entity hook. A per-entity hook may only
  *read* the published state (`FrustumCache.update(frustum)` is fine — it is
  idempotent).
* `Minecraft#runTick(Z)V` has the identical descriptor on all five versions, which is
  why the client-frame boundary can live in `core/`.

---

## 8b. The culling entry points ⚠️ one copy of the decision

The block-entity and entity culling mixins have **one** decision function each, and
both live in shared source:

| Decision | Entry point | Handles |
| :--- | :--- | :--- |
| May this block entity be skipped? | `StorageCullingEngine.shouldCullBlockEntity(...)` | the storage containers itself, everything else through `AsyncOcclusionCuller` |
| May this entity be skipped? | `StorageCullingEngine.shouldCullEntity(...)` | the item frames itself, everything else through `AsyncOcclusionCuller` |

**Rules.**

* A culling mixin must call `StorageCullingEngine`, never `AsyncOcclusionCuller`
  directly. The engine is what keeps the storage rules and the generic rules in one
  place — the ten per-version mixins are one-line adapters.
* The engine **falls back** to `AsyncOcclusionCuller` for everything it does not
  handle itself, so a new storage rule can only ever *add* a cull, and switching the
  feature off restores the previous behaviour exactly.
* The policy lives in pure seams — `decideContainerCull`, `decideItemFrameCull`,
  `isBlockingNeighbour` — so the decision table is unit-testable without a Minecraft
  bootstrap. Add new rules there, not inline in the mixin.
* `StorageCullingEngine.install()` is called from `HyperionFPS.onInitializeClient()`.
  It flips the console row from `NOT AVAILABLE` to a working switch. A feature that
  has a call site but no `install()` is a fake toggle (REQ-UI-2).

---

## 8c. The animation entry point ⚠️ one copy of the decision

The entity animation hooks have **one** decision function, and it lives in shared
source:

| Decision | Entry point | Handles |
| :--- | :--- | :--- |
| May this mob's animation be skipped? | `MobFarmOptimizer.shouldSkipAnimation(...)` | the mob farm cluster rule, everything else through `ThoriumEntityLodThrottler` |

**Rules.**

* An animation hook must call `MobFarmOptimizer.shouldSkipAnimation(...)`, never
  `ThoriumEntityLodThrottler` directly. The optimizer applies the cluster rule and
  then falls back to the distance-based LOD, so switching the mob farm optimizer off
  restores the previous behaviour exactly.
* The **cluster map** is owned by the optimizer and is filled by
  `MobFarmOptimizer.observe(...)`. The exemption rule lives in
  `MobFarmOptimizer.isExemptEntity(...)` — the *facts* are gathered by the era
  hooks, the *rule* is in one place.
* The policy lives in pure seams — `decideThrottle`, `isDenseCluster`,
  `isExemptEntity`, `isAnimationFrame` — so the decision table is unit-testable
  without a Minecraft bootstrap.
* `MobFarmOptimizer.install()` is called from `HyperionFPS.onInitializeClient()`,
  and `MobFarmOptimizer.beginFrame()` from `ThoriumFrameContext.beginWorldFrame(...)`.

**The era difference that forces two shapes.** On 1.21.1 the renderer still works
with the `Entity`, so the animation hook sees the entity and can record it itself.
Since 1.21.11 the renderer works with an extracted `EntityRenderState` that carries
the species, the position and the name tag — but **not** the leash and the passenger
flags. The cluster map is therefore filled by a second hook on
`EntityRenderDispatcher#extractEntity` (`legacy.LegacyMobFarmEntityMixin`,
`modern.ModernMobFarmEntityMixin`), which is the last point at which the entity
itself is still available. Never assume the render state can answer a question
about the entity.

---

## 8d. The particle entry point ⚠️ one copy of the decision

The particle creation hook has **one** decision function, and it lives in shared
source:

| Decision | Entry point | Handles |
| :--- | :--- | :--- |
| May this particle be skipped? | `ExplosionParticleFilter.shouldCullParticle(options, x, y, z)` | the explosion / firework / smoke flood, everything else through `AsyncOcclusionCuller` |

**Rules.**

* The particle mixin must call `ExplosionParticleFilter`, never
  `AsyncOcclusionCuller` directly. The filter is what keeps the explosion rules
  and the generic rules in one place.
* The filter **falls back** to `AsyncOcclusionCuller.shouldCullParticle` for
  everything it does not handle itself, so the shield can only ever *add* a cull,
  and switching the feature off restores the previous behaviour exactly.
* The feature is split in two on purpose:
  * `dev.hyperionfps.particle.ExplosionShield` is a **pure policy engine** — it
    holds no Minecraft type at all, so the whole decision table, the token
    buckets and the per-cell bookkeeping are unit-testable without a running
    game and without booting the particle registry.
  * `dev.hyperionfps.particle.ExplosionParticleFilter` is the **Minecraft
    adapter** — it maps the vanilla particle types onto the categories and
    applies the decision to the engine.

  Never move the `ParticleTypes` comparison into `ExplosionShield`: doing so
  would drag `ParticleTypes` into the class that the unit tests load.

### The protected particles

The first particle of every category inside a burst is **protected**: it bypasses
the burst rate limit, the per-cell limit **and** the global creation budget
(`ThoriumParticleBudget.admitPriority()`). For an explosion those two protected
particles are the flash and the primary puff.

The budget bypass is not decoration. A client that is drowning in particles has
an **exhausted** creation budget, so without the priority path the budget would
swallow the explosion flash and the explosion would be invisible — exactly the
feedback the feature is required to preserve.

`AsyncOcclusionCuller` therefore has **two** particle entry points:

| Method | Checks |
| :--- | :--- |
| `shouldCullParticle(x, y, z)` | the creation budget **and** the view test |
| `isParticleOutOfView(x, y, z)` | the view test only (distance + occlusion) |

A protected particle uses the second one and takes the priority path in the
budget. It must still be distance- and occlusion-checked: a flash behind a wall
at the other end of the world is not worth drawing either way.

---

## 8e. The chunk section entry point ⚠️ one copy of the decision

The chunk-section hooks have **one** decision function, and it lives in shared
source:

| Decision | Entry point | Handles |
| :--- | :--- | :--- |
| May this section's build be postponed? | `ElytraFlightSmoother.shouldDefer(x, y, z)` | the sections behind the flight vector, everything else is built immediately |

**Rules.**

* A chunk hook must ask `ElytraFlightSmoother` / `ThoriumChunkUploadPacer`, never
  decide on its own. The policy lives in shared source, so the five era hooks are
  one-line adapters.
* The policy is split in two on purpose:
  * `dev.hyperionfps.chunk.ElytraFlightSmoother` is a **pure policy engine** — it
    holds no Minecraft type at all, so the classification (the 45° cone, the 15 m/s
    threshold, the distance guard) is unit-testable without a running game.
  * `dev.hyperionfps.chunk.PlayerVelocityPublisher` is the **Minecraft adapter** —
    it is the *only* class in the codebase that reads the player entity
    (`Minecraft#player.getDeltaMovement()`). Keep it that way: the moment a
    Minecraft type moves into the policy engine, the whole decision table stops
    being testable.
* `ThoriumChunkUploadPacer` owns the **deferred queue** and drains it exactly once
  per world frame, from `ThoriumFrameContext.beginWorldFrame(...)`. Never drain it
  from a mixin — the drain has to happen after the camera and the velocity are
  published, or the priority is computed against the previous frame.
* The release of a deferred section calls the vanilla scheduling method again, which
  re-enters the very injection that deferred it. The pacer's **replay guard**
  (`ThoriumChunkUploadPacer.isReplaying()` / `runUndeferred(...)`) is what stops
  that from becoming an infinite deferral. Every hook must check it first.

### Where the deferral is allowed to happen

The hook must intercept the point at which vanilla **schedules** a section build,
because that is the last point at which the build can still be postponed:

| Node | Method | Position |
| :--- | :--- | :--- |
| 1.21.1 | `RenderSection#rebuildSectionAsync(SectionRenderDispatcher, RenderRegionCache)` | `getOrigin()` |
| 1.21.11 | `RenderSection#rebuildSectionAsync(RenderRegionCache)` | `getRenderOrigin()` |
| 26.1 | `RenderSection#rebuildSectionAsync(RenderRegionCache)` | `getRenderOrigin()` |
| 26.2 / 26.3 | `RenderSection#compileAsync(RenderSectionRegion)` | `getRenderOrigin()` |

**Never** intercept the 26.x `RenderSection#addSectionBuffersToUberBuffer(...)`: the
compile task **spins** until it returns `true`, so cancelling it hangs the worker
thread forever.

### The three guards

The classification uses the **velocity** vector, not the view direction, so a section
that is behind the player can still be on the screen when the player looks back. The
guards are what make the feature acceptable:

1. a section closer than **16 blocks** is never deferred,
2. a section that has waited for more than **500 ms** is released regardless of its
   priority,
3. a section that can no longer be scheduled is dropped instead of crashing the
   frame.

---

## 8f. The input entry point ⚠️ one copy of the decision

The late input sampler (REQ-HEAD-7) has **one** decision function, and it lives in
shared source:

| Decision | Entry point | Handles |
| :--- | :--- | :--- |
| May the platform event queue be drained again this frame? | `ThoriumReflexController.shouldSample(windowFocused)` | the feature switch, the node's capability and the window focus |

**Rules.**

* The hook must call `ThoriumReflexController`, never the platform routine directly.
  The controller is what keeps the switch, the capability check and the accounting in
  one place.
* The policy lives in a pure seam — `ThoriumReflexController.decide(...)` — so the
  decision table is unit-testable without a Minecraft bootstrap.
* **Drain the queue, never copy it.** The sample calls the *game's own* drain, so the
  events are delivered exactly once — only earlier. A re-implementation of the input
  pipeline would deliver them twice, and a re-implementation of the camera rotation
  would fight the game's own smoothing.
* **Never touch the tick.** The movement packets the client sends must stay exactly
  the ones vanilla would have sent.

### The sample point

`MouseHandler#handleAccumulatedMovement()` is the sample point. The descriptor `()V`
is identical on all five versions, and on every node the game calls it **immediately
before it builds the camera matrix**:

| Node | Order inside `Minecraft#runTick` |
| :--- | :--- |
| 1.21.1 / 1.21.11 | tick → `handleAccumulatedMovement()` → render |
| 26.1 / 26.2 / 26.3 | tick → `handleAccumulatedMovement()` → `renderFrame()` |

Do **not** hook `Minecraft#runTick` for this: the tail of `runTick` is *after* the
render, which is too late — the camera of the current frame is already built.

### The platform drain is version-specific

The drain lives in the version-specific source tree under the **fixed** name
`dev.hyperionfps.input.ReflexEventPumpImpl`, and the shared controller finds it by
name (`Class.forName`). The shared source therefore never mentions GLFW or SDL.

| Node | The drain |
| :--- | :--- |
| 1.21.1 / 1.21.11 | `GLFW.glfwPollEvents()` — the game's own `RenderSystem#pollEvents()` is **private** there |
| 26.1 / 26.2 | `RenderSystem.pollEvents()` (public) |
| 26.3 | `RenderSystem.pollEvents(SDLEventHandler)` — SDL3 era; the handler is read through `modern/MinecraftSdlEventHandlerAccessor` |

A new node must ship a `ReflexEventPumpImpl`; `ThoriumReflexControllerTest` fails the
build if it does not.

---

## 8g. The weather and fog entry points ⚠️ one copy of the decision

The No-Lag Weather and the Clean Water / Fast Fog optimiser (roadmap step 9) have
**one** policy engine, and it lives in shared source:

| Decision | Entry point | Handles |
| :--- | :--- | :--- |
| May the precipitation layer be drawn? | `WeatherFogOptimizer.shouldRenderPrecipitation()` | the `OFF` mode; the Extreme FPS override stays in the mixin |
| May the ground splash particle be created? | `WeatherFogOptimizer.shouldRenderSplashParticles()` | the `MINIMAL` and `OFF` modes |
| What does the underwater fog look like? | `WeatherFogOptimizer.clampUnderwaterFog(start, end)` | the clean-water clamp |
| What does the distance fog look like? | `WeatherFogOptimizer.clampDistanceFog(start, end)` / `clampDistanceFogBand(band)` | the fast-fog band |

**Rules.**

* A weather or fog hook must call `WeatherFogOptimizer`, never decide on its own.
* The engine is a **pure policy engine** — it holds no Minecraft type at all, so the
  whole decision table is unit-testable without a game. The Minecraft half (cancelling
  the pass, dropping the particle, rewriting the fog) lives in the mixins.
* `WeatherFogOptimizer.install()` is called from `HyperionFPS.onInitializeClient()`,
  and it flips the console row from `NOT AVAILABLE` to a working switch.

### The splash particle is dropped at the particle call ⚠️

On **every** supported version the weather spawns the splash particles *and* plays the
rain sound from **one** method:

| Node | The method that spawns the splash **and** plays the rain sound |
| :--- | :--- |
| 1.21.1 | `LevelRenderer#tickRain(Camera)` |
| 1.21.11 | `WeatherEffectRenderer#tickRainParticles(ClientLevel, Camera, int, ParticleStatus, int)` |
| 26.1 | `WeatherEffectRenderer#tickRainParticles(ClientLevel, Camera, int, ParticleStatus, int)` |
| 26.2 / 26.3 | `ClientLevel#tickWeatherEffects()` |

Cancelling that method would silence the storm, which the spec forbids ("sound/world
weather intact"). So the splash is dropped at the **particle call** instead:

```java
ClientLevel#addParticle(ParticleOptions, double×6)
```

That method exists with the identical descriptor on all five nodes and is the entry
point the weather uses on all five — so the hook is a single **shared** mixin
(`core/WeatherSplashMixin`). The comparison is by identity against
`ParticleTypes.RAIN`; vanilla registers exactly one instance per particle type.

### The fog is written where the game computes it

The fog architecture differs between the eras, so the fog hooks are era-specific:

| Node | Clean water | Fast fog |
| :--- | :--- | :--- |
| 1.21.1 | `legacy/LegacyFogMixin` — reads and writes `RenderSystem`'s shader fog at the tail of `FogRenderer#setupFog` | the same hook, on the terrain fog |
| 1.21.11 | `legacy/LegacyWaterFogMixin` — `WaterFogEnvironment#setupFog` | `legacy/LegacyFogRendererMixin` — a `@Redirect` on the `Mth#clamp` that produces the band |
| 26.1 / 26.2 / 26.3 | `modern/ModernFogMixin` — `FogRenderer#setupFog` returns the `FogData` the renderer uploads | the same hook, on the `renderDistance*` range |

Two facts about the modern fog are worth remembering, because they decide where a hook
has to sit:

* the `FogData` carries **two** ranges — `environmentalStart/End` (the fog of the
  environment the camera is in) and `renderDistanceStart/End` (the fog at the edge of
  the render distance);
* the renderer fills the *environmental* range first (the fog environment runs) and
  then **overwrites** the *render-distance* range — so a hook on a fog environment can
  never change the render-distance fog, and a hook on `FogRenderer#setupFog` can
  change both.

`Camera#getFluidInCamera()` exists with the identical descriptor on all five nodes and
is how the hooks tell "underwater" from "above ground". `FogType` is only named in the
era-specific mixins, never in shared source.

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
* The shared singletons (`AdaptiveDistanceController`, `AsyncOcclusionCuller`, …) are
  process-wide. A test that mutates one must restore it, or the next test class in the
  same JVM inherits the state.

### Test seams that exist for this purpose

| Seam | Purpose |
| :--- | :--- |
| `FramePacing.inBootPhase(long uptimeMillis)` (package-private) | Test the boot window without sleeping |
| `IdleJanitor.sweepOnce(long budgetNanos)` | Test the sweep synchronously |
| `DirectBufferPool(name, minShift, maxShift, perClassLimit)` | Test small pools without allocating MiB |
| `GameVersionDetector` honours `-Dhyperionfps.gameVersion=` | Test version parsing/era routing |
| `AdaptiveDistanceController#checkAndAdjust(long now)` | Test the governor without a game — it takes the clock as a parameter |
| `MobFarmOptimizer.observe(Object species, double x, double z, boolean exempt)` | Fill the cluster map with a stand-in species — the species is compared by identity, so a plain `Object` works |
| `MobFarmOptimizer.beginFrame()` / `resetMetrics()` | Publish the map of the last frame and put the optimizer back to its initial state |
| `MobFarmOptimizer.decideThrottle` / `isDenseCluster` / `isExemptEntity` / `isAnimationFrame` (package-private) | The whole decision table without any game state |
| `ThoriumSoundGovernor.clusterAction(int)` / `exceedsCellLimit(int)` (package-private) | The sound policies as pure functions of the event count |
| `ExplosionShield.decide(boolean, boolean, boolean)` (package-private) | The explosion decision table without any game state |
| `ExplosionShield.evaluate(Category, x, y, z, nowMillis)` | The whole policy — buckets, per-cell bookkeeping, protection — with an injected clock |
| `ExplosionShield.Category` / `ExplosionShield.Decision` | The five watched categories and the four possible outcomes |
| `ElytraFlightSmoother.classify(dx, dy, dz, vx, vy, vz)` (package-private) | The whole 45°-cone classification without any published state |
| `ElytraFlightSmoother.publishCamera(...)` / `publishVelocity(...)` | Put the camera and the flight vector into the engine |
| `ElytraFlightSmoother.resetForTests()` | Clear the published state and the counters |
| `ThoriumChunkUploadPacer.deferSection(x, y, z, action)` / `releaseDeferredSections()` | The deferred queue and its release order, without a game |
| `ThoriumChunkUploadPacer.resetForTests()` | Empty the deferred queue and the counters |
| `ThoriumReflexController.decide(enabled, pumpInstalled, windowFocused)` (package-private) | The whole decision table without a game |
| `ThoriumReflexController.installPump(pump)` / `pumpEvents()` | Substitute a fake platform drain and count how often it is called |
| `ThoriumReflexController.recordLateSample(dx, dy)` / `resetForTests()` | The accounting and the reset, without a game |
| `WeatherFogOptimizer.shouldRenderPrecipitation()` / `shouldRenderSplashParticles()` | The whole weather decision table, without a game |
| `WeatherFogOptimizer.clampUnderwaterFog(start, end)` / `clampDistanceFog(start, end)` / `clampDistanceFogBand(band)` | Both fog clamps, including the guards against unusable ranges |
| `WeatherFogOptimizer.recordSplashSuppressed()` / `recordPrecipitationSkipped()` / `recordFogClamp()` / `resetForTests()` | The counters, without a game |

**When you add a subsystem, add a test class with it.** A new subsystem without tests
is an incomplete change.

---

## 10. Gotchas & traps

1. **`fabric.mod.json` and `thorium.mixins.json` are Gradle templates.** They contain
   `${...}` placeholders and are expanded by `processResources` via `expand(...)`.
   Raw `${` in those files will be interpreted by Gradle, not by the loader.
2. **`${mixin_legacy}` / `${mixin_modern}` are not optional.** Deleting them breaks the
   per-node mixin list silently — the mixin just never applies.
3. **The version matrix has 5 nodes.** `1.21.1`, `1.21.11`, `26.1`, `26.2` and `26.3`
   are all built and tested. `README.md` / `MODRINTH.md` list all five. A new node
   means a new `versions/<mc>/` directory **and** a new entry in every matrix in
   `README.md`, `MODRINTH.md`, `spec.md` and `state.md`.
4. **User settings live in `dev.hyperionfps.config.ThoriumConfig`.** Do not invent a
   second persistence mechanism. Read/write through `ThoriumConfig.get()`; it handles
   the atomic file write, the lenient parser and the default fallbacks. Adding a new
   user-facing toggle means adding a key constant, an entry in `captureFromSubsystems()`
   and an entry in `applyToSubsystems()` — all three, or the setting will not survive
   a restart. **A new user-facing toggle also needs a row in the shared
   `ThoriumSettingRows` catalogue** (§6a) — never in one of the five
   `ThoriumVideoSettingsScreen` files.
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
10. **The `net/` directory is a stale partial decompile and is gitignored.** Use
    `javap` on the Loom cache jars instead — see §2a.
11. **Never write `Options#renderDistance` (or `Options#serverRenderDistance`) at
    runtime.** This is the trap that shipped a world that reloaded itself every two
    seconds:
    * `Options#renderDistance` is **not** a client-local value. On a single-player
      world the integrated server polls it on **every server tick**
      (`IntegratedServer#tickServer` → `PlayerList#setViewDistance`).
    * `PlayerList#setViewDistance` broadcasts a `ClientboundSetChunkCacheRadiusPacket`
      and rebuilds the server chunk-tracking radius.
    * The client answers that packet with `ClientChunkCache#updateViewRadius`, which
      allocates a **new** `ClientChunkCache.Storage` — throwing away *every* cached
      chunk, not just the ones outside the new radius. The entire visible world is
      re-downloaded and re-meshed, which on 26.x (no chunk-upload pacer) is a multi-
      frame hitch.
    * `Options#serverRenderDistance` is **owned by the server**; it arrives from
      `ClientboundSetChunkCacheRadiusPacket`. A client that writes it desynchronises
      the fog distance and the far plane from what the server actually sends.
    * Consequence for design: the adaptive render distance may only ever *lower* the
      distance, at most once per settle window, and it is opt-in
      (`adaptive.distance.enabled` defaults to `false`).
12. **Never re-implement a vanilla game loop.** The particle ticking loop was
    re-implemented once and the re-implementation skipped vanilla's particle-limit
    bookkeeping, which permanently stopped the engine from creating particles. If you
    want to change *when* work happens, hook it — do not replace the loop.
13. **Do not promise what the code does not do.** Every console switch must change
    observable behaviour on the version it runs on. A switch that cannot work is
    shown as *not wired* / *not available*, and the startup log names it under
    `UNWIRED` (see `ThoriumSubsystemReport`).
14. **A dead mixin target fails SILENTLY.** Because of `defaultRequire: 0`, an injection
    whose target method no longer exists is a no-op at runtime — no crash, no warning
    on the 26.x nodes. This already shipped twice: `ParticleEngineMixin`'s
    `tickParticleList` target existed on **1.21.1 only** and was inert on 1.21.11 and
    all 26.x nodes (state.md G-2), and `ModernFramePacingMixin`'s `Window#updateVsync`
    target **never existed on 26.2**, so the frame-pacing override was inert there.
    **This is now caught by the build** — `MixinTargetAuditTest` fails the build when
    a registered mixin points at a method that does not exist on the running node's
    Minecraft version (see §2). The manual check is still useful while writing a mixin:
    ```bash
    javap -p <minecraft-client.jar> | grep <targetMethod>
    ```
    Loom's remapper prints `Cannot remap <method> because it does not exist` for the
    remap nodes only (1.21.1, 1.21.11) — treat that warning as a build failure, not
    noise. See `state.md` G-2 and G-12.
15. **`versions/*/logs/latest.log` contains JUnit output, not a game log.** A real
    client log lives in the Minecraft instance's own `logs/latest.log`. Do not read the
    version-node logs as evidence about in-game behaviour.
16. **`Minecraft#execute(Runnable)` runs the task INLINE on the game thread.** It is
    *not* a "do this next tick" helper: `BlockableEventLoop#scheduleExecutables()`
    returns `!isSameThread()`, so on the game thread `execute` falls through to
    `runnable.run()` immediately. Never use it to defer a screen switch — you would
    switch the screen from inside the screen's own constructor/init.
    **Switch screens from a `tick()` hook instead**: `TitleScreen#tick()` is called by
    the game loop (`Gui#tick()` on 26.x, `Minecraft#runTick` on 1.21.x) and is declared
    on `TitleScreen` itself, so the injection cannot leak into other screens.
17. **A config read is `synchronized`.** `ThoriumConfig#getBoolean` takes a lock, so
    never call it from a per-frame hook. Cache the decision in a field when the screen
    is built and read the field in the tick hook.
18. **A mixin selector may omit the descriptor — and the handler may omit the
    parameters.** This is the escape hatch for a method whose signature changed
    between the Minecraft versions:

    ```java
    @Inject(method = "onMove", at = @At("HEAD"), require = 0)
    private void hyperionfps$onMouseMove(CallbackInfo ci) { ... }
    ```

    The selector matches by **name** on every version, and the handler declares
    none of the target's parameters, so one core mixin covers all five nodes even
    though `MouseHandler#onMove` is `(JDD)V` on 1.21.1 … 26.2 and `(JDDDD)V` on
    26.3. The build-time audit accepts a name-only selector as long as *some*
    method with that name exists on the node.

    **The corollary:** a selector without a descriptor must match on *every* node
    the mixin is registered for. `onPress` only exists on 1.21.1 and `onButton`
    only on 1.21.11 / 26.x, so those two need their own era-specific mixin
    (`LegacyAfkClickMixin`, `ModernAfkClickMixin`) instead of one shared class.
19. **`BlockState#canOcclude()` does not mean "hides what is behind it".** It is a
    per-block *property*: it is `false` for glass and leaves, but **`true` for a
    slab, a stair and a chest** — none of which fill their whole cell. Deciding
    whether a block really hides what is behind it needs the full-cube test as well:
    `state.canOcclude() && state.isCollisionShapeFullBlock(level, pos)`
    (identical signature on all five nodes). The chest case is the one that bites:
    a chest is a full *collision* cube but only occupies 14/16 of its cell, so the
    other half of a double chest must never be treated as a wall.
20. **The modern render state is not the entity.** Since 1.21.11 the renderer extracts
    an `EntityRenderState` and renders *that*. The state carries the species
    (`entityType`), the position (`x`/`y`/`z`), the distance to the camera and the
    name tag — but **not** the leash flag, the passenger flag or the entity id. A
    hook that needs one of those has to run where the entity is still visible: on
    `EntityRenderDispatcher#extractEntity`, or on the tick. The state object is also
    **fresh every frame**, so it cannot carry a value from one frame to the next.
    See §8c.
21. **`EntityType` constants are era-specific.** 1.21.x has
    `EntityType.PLAYER`; 26.x moved the constants to `EntityTypes.PLAYER`. Never
    name an entity type constant in shared source — compare the *instance* instead
    (the render state stores the very same `EntityType` object the entity carries).
22. **A particle type is identified by identity, and the identity is stable.**
    `ParticleTypes.EXPLOSION` and its siblings are single registered instances, so
    `options.getType() == ParticleTypes.EXPLOSION` is the correct comparison — do
    not compare names or look the type up in a registry on the particle path.
23. **A rate limit that never refills is a cliff, not a rate.** The explosion
    shield's two token buckets refill *continuously* (`credit += elapsed × rate`),
    so a client that keeps producing particles keeps getting its share instead of
    being cut off until the next window. A per-cell **count** would have the
    opposite failure: a campfire that puffs smoke forever would accumulate the
    count and be silenced permanently.
24. **Never write a `ParticleType` comparison into a class the unit tests load.**
    The unit tests run without a Minecraft bootstrap, so a class that touches
    `ParticleTypes` at class-link time is a class the tests cannot load. Keep
    the vanilla comparison in the Minecraft adapter
    (`ExplosionParticleFilter`) and the policy in the pure engine
    (`ExplosionShield`).
25. **Never cancel `RenderSection#addSectionBuffersToUberBuffer(...)` on 26.x.**
    The 26.x compile task **spins** until that method returns `true`:
    `while (!added) { if (RenderSystem.isOnRenderThread()) continue; Thread.onSpinWait(); }`.
    Cancelling it hangs the worker thread forever. The deferral hook must intercept
    the point where vanilla *schedules* the build instead — see §8e.
26. **A deferred hook that re-enters itself is an infinite deferral.** When a
    deferred action calls the vanilla method again, the injection runs a second
    time. The pacer's replay guard (`ThoriumChunkUploadPacer.isReplaying()`) is
    what breaks the loop; a hook without it would defer the same section forever
    and the world would slowly fill with holes.
27. **The platform event queue is drained, not copied.** `glfwPollEvents` and
    `SDL_PollEvent` *remove* the events they deliver. Draining the queue a second
    time therefore delivers every event **exactly once** — just earlier. A hook
    that re-implements the input pipeline (reads the cursor position itself, or
    replays the delta) would deliver the movement twice and double the camera
    speed.
28. **The input pipeline belongs to the game.** The camera rotation is applied by
    `MouseHandler#handleAccumulatedMovement()`, with the game's own sensitivity and
    its own `SmoothDouble` smoothing. Thorium only makes sure the movement it
    applies is the freshest one available — it never writes the rotation itself.
29. **`RenderSystem#pollEvents()` is private on 1.21.x.** The legacy nodes cannot
    call the game's own drain, so they call `GLFW.glfwPollEvents()` — which is
    exactly what the private method does, minus the watchdog the game keeps around
    it. On 26.1 / 26.2 the method is public, and on 26.3 it takes the SDL event
    handler as an argument. See §8f.
30. **The rain sound lives in the same method as the splash particles.** On every
    supported version the weather spawns the ground splash particles *and* plays the
    rain sound from one method (`LevelRenderer#tickRain` on 1.21.1,
    `WeatherEffectRenderer#tickRainParticles` on 1.21.11 / 26.1,
    `ClientLevel#tickWeatherEffects` on 26.2 / 26.3). Cancelling that method
    silences the storm, which the spec forbids. Drop the splash at the **particle
    call** (`ClientLevel#addParticle(ParticleOptions, double×6)`) instead — see §8g.
31. **The modern fog carries two ranges, and the renderer overwrites one of them.**
    `FogData.environmentalStart/End` is the fog of the environment the camera is in;
    `FogData.renderDistanceStart/End` is the fog at the edge of the render distance.
    The fog *environment* fills the first one, and `FogRenderer#setupFog` then
    overwrites the second one — so a hook on a fog environment can never change the
    render-distance fog. The shader combines the two with `max`, not `min`, so
    clamping only the render-distance fog would change nothing at all.
32. **A narrower fog band is not a fill-rate saving.** The Minecraft fog shader
    computes `max(linear_fog(spherical), linear_fog(cylindrical))` for every fragment
    whether the band is 24 blocks wide or 64. Narrowing the band is a **visual**
    change — the fog reaches full strength sooner — and nothing else. Do not attach
    a frame-time claim to it; the measurable saving of the weather feature is the
    splash particle flood.
33. **The rain columns fade *in* with the distance.** The alpha of a rain column is
    `lerp(min(distSq/radiusSq, 1), 0.5, intensity)`, so the far columns are the
    *thickest* part of the curtain. Dropping them thins the rain where it is most
    visible — that is a visual change, not a saving. The rain sheets are cheap; the
    splash particles are not.

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

* Track **tasks, steps, and execution status** → update [`TODO.md`](./TODO.md).
* Change **behaviour** or **architecture** → update [`spec.md`](./spec.md).
* Change **status, add a subsystem, fix a gap** → update [`state.md`](./state.md).
* Change **how to build / where code lives / conventions** → update this file (`AGENTS.md`).

Keep all four in the repository root and update them after **every completed step**.

### The headline roadmap is closed

The ten-step roadmap in [`TODO.md`](./TODO.md) is **complete** (verified 2026-10-22:
317 tests / 42 classes green on all five nodes, five `0.1.2` jars built, the mixin
target audit green on every node). Do **not** look for a "next step" in `TODO.md` —
there is none.

The remaining v0.2 work is tracked in [`PLAN.md`](./PLAN.md), and the two places that
matter most are:

* the **seven subsystems that are implemented, unit-tested and exposed in the console
  but have no call site** (`state.md` §4.2) — they are reported as `UNWIRED` at
  startup and their console rows refuse the click, so nothing is lying, but the
  work is unfinished;
* the **benchmark harness** (G-7) — without it the README's performance section can
  only ever be a methodology, never a measurement.

A new feature still follows the same contract: one policy engine in shared source, one
decision function, one call site per version node, a unit test, and a console row that
reports `NOT AVAILABLE` until it is wired.
