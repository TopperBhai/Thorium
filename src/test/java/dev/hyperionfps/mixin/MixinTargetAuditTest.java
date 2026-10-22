package dev.hyperionfps.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Build-time guard against <em>dead mixin targets</em>.
 *
 * <p>Every Thorium injection carries {@code require = 0} so that a broken hook degrades
 * instead of crashing the game (AGENTS.md golden rule 5). The price of that safety net is
 * that a target which no longer exists becomes a <em>silent</em> no-op: no crash, no
 * warning, and the optimisation simply stops running. That is exactly how the particle
 * ticking injection ended up dead on four of the five version nodes (state.md G-2).</p>
 *
 * <p>This test closes the loop. It runs once per version node (the shared test tree is
 * compiled into every node) and therefore checks every registered mixin against the
 * Minecraft build of the node it is running on:</p>
 *
 * <ol>
 *   <li>The expanded {@code thorium.mixins.json} is read from the classpath, so the
 *       audit covers exactly the mixins that will actually be loaded - including the
 *       per-node {@code mixin_legacy} / {@code mixin_modern} expansions.</li>
 *   <li>Each mixin class is parsed with ASM to recover its {@code @Mixin} target and its
 *       injection method selectors.</li>
 *   <li>Each target class is read from the classpath and walked up the inheritance chain,
 *       mirroring how Mixin resolves a target method.</li>
 *   <li>Every selector must match at least one method, otherwise the build fails.</li>
 * </ol>
 *
 * <p>A second test runs the audit in reverse: every mixin class compiled into the node
 * must be registered in the configuration, otherwise it is dead code that can never
 * apply.</p>
 */
class MixinTargetAuditTest {

    /** Resource path of the expanded mixin configuration. */
    private static final String CONFIG_RESOURCE = "thorium.mixins.json";

    /**
     * System property carrying the node's compile classpath.
     *
     * <p>Loom puts the Minecraft jar on the compile classpath but not on the test
     * runtime classpath, so the audit reads the Minecraft class files from here.</p>
     */
    private static final String CLASSPATH_PROPERTY = "thorium.compileClasspath";

    /** Root package of every mixin class. */
    private static final String MIXIN_PACKAGE = "dev.hyperionfps.mixin";

    /** Mixin families that may appear in the configuration. */
    private static final List<String> FAMILIES = List.of("core", "legacy", "modern");

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("\"package\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CLIENT_PATTERN =
            Pattern.compile("\"client\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern QUOTED_PATTERN = Pattern.compile("\"([^\"]+)\"");

    private static final String MIXIN_ANNOTATION = "Lorg/spongepowered/asm/mixin/Mixin;";

    /**
     * The accessor annotation whose {@code value} element names a Minecraft
     * <em>field</em> instead of a method.
     */
    private static final String ACCESSOR_ANNOTATION = "Lorg/spongepowered/asm/mixin/gen/Accessor;";

    /**
     * The injector annotations whose {@code method} / {@code value} element names a
     * Minecraft method. Deliberately an allow-list: {@code At} also lives under the
     * {@code injection} package but never names a target method.
     */
    private static final Set<String> INJECTOR_ANNOTATIONS = Set.of(
            "Lorg/spongepowered/asm/mixin/injection/Inject;",
            "Lorg/spongepowered/asm/mixin/injection/Redirect;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyReceiver;",
            "Lorg/spongepowered/asm/mixin/injection/WrapOperation;",
            "Lorg/spongepowered/asm/mixin/injection/WrapWithCondition;",
            "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;",
            "Lcom/llamalad7/mixinextras/injector/ModifyReceiver;",
            "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;"
    );

    @Test
    void everyRegisteredMixinTargetExistsOnThisVersion() throws IOException {
        ClassLoader loader = getClass().getClassLoader();
        ClassLoader minecraft = minecraftClassLoader();
        Assumptions.assumeTrue(minecraft != null,
                CLASSPATH_PROPERTY + " is not set - the Minecraft classes are unavailable");

        List<String> registered = readRegisteredMixins(loader);

        assertFalse(registered.isEmpty(),
                "No mixins were read from " + CONFIG_RESOURCE + " - the audit would be vacuous");

        List<String> problems = new ArrayList<>();
        for (String mixin : registered) {
            problems.addAll(auditMixin(loader, minecraft, mixin));
        }

        assertTrue(problems.isEmpty(), () -> "Dead mixin targets detected on this Minecraft version:\n  "
                + String.join("\n  ", problems));
    }

    @Test
    void everyCompiledMixinIsRegistered() throws IOException {
        ClassLoader loader = getClass().getClassLoader();
        Set<String> registered = new LinkedHashSet<>(readRegisteredMixins(loader));
        Set<String> compiled = findCompiledMixins(loader);

        assertFalse(compiled.isEmpty(),
                "No compiled mixin classes were found - the audit would be vacuous");

        List<String> problems = new ArrayList<>();
        for (String mixin : compiled) {
            if (!registered.contains(mixin)) {
                problems.add(mixin + " is compiled but not registered in " + CONFIG_RESOURCE
                        + " - it can never apply");
            }
        }

        assertTrue(problems.isEmpty(), () -> "Unregistered mixin classes detected:\n  "
                + String.join("\n  ", problems));
    }

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    /**
     * Reads the mixin names registered for this node.
     *
     * <p>The configuration is a Gradle template; the per-node expansion replaces
     * {@code ${mixin_legacy}} / {@code mixin_modern}}. An unexpanded placeholder means
     * the resource pipeline is broken, which would silently reduce the audit to the
     * core mixins - so it is treated as a hard failure.</p>
     */
    private static List<String> readRegisteredMixins(ClassLoader loader) throws IOException {
        String text = readResource(loader, CONFIG_RESOURCE);
        assertNotNull(text, CONFIG_RESOURCE + " is not on the test classpath");
        assertFalse(text.contains("${"),
                CONFIG_RESOURCE + " still contains Gradle placeholders - the per-node "
                        + "mixin expansion did not run, so the audit would be incomplete");

        Matcher packageMatcher = PACKAGE_PATTERN.matcher(text);
        assertTrue(packageMatcher.find(), CONFIG_RESOURCE + " has no \"package\" entry");
        String mixinPackage = packageMatcher.group(1);

        Matcher clientMatcher = CLIENT_PATTERN.matcher(text);
        assertTrue(clientMatcher.find(), CONFIG_RESOURCE + " has no \"client\" array");

        List<String> names = new ArrayList<>();
        Matcher quoted = QUOTED_PATTERN.matcher(clientMatcher.group(1));
        while (quoted.find()) {
            names.add(mixinPackage + "." + quoted.group(1));
        }
        return names;
    }

    // ------------------------------------------------------------------
    // Forward audit
    // ------------------------------------------------------------------

    private static List<String> auditMixin(ClassLoader loader, ClassLoader minecraft, String mixinClassName)
            throws IOException {
        List<String> problems = new ArrayList<>();
        byte[] bytes = readClassBytes(loader, mixinClassName.replace('.', '/') + ".class");
        if (bytes == null) {
            problems.add(mixinClassName + " is registered but not compiled into this node");
            return problems;
        }

        List<String> targets = new ArrayList<>();
        List<String> selectors = new ArrayList<>();
        List<String> fieldSelectors = new ArrayList<>();
        readMixinMetadata(bytes, targets, selectors, fieldSelectors);

        if (targets.isEmpty()) {
            problems.add(mixinClassName + " has no readable @Mixin target");
            return problems;
        }
        if (selectors.isEmpty() && fieldSelectors.isEmpty()) {
            problems.add(mixinClassName + " declares no injection target method");
            return problems;
        }

        for (String target : targets) {
            Set<String> methods = collectMethods(minecraft, target);
            if (methods == null) {
                problems.add(mixinClassName + " targets " + target
                        + ", which does not exist on this Minecraft version");
                continue;
            }
            for (String selector : selectors) {
                if (!matches(methods, selector)) {
                    problems.add(mixinClassName + " -> " + target + "#" + selector
                            + " does not exist on this Minecraft version");
                }
            }
            if (!fieldSelectors.isEmpty()) {
                Set<String> fields = collectFields(minecraft, target);
                if (fields == null) {
                    problems.add(mixinClassName + " accesses fields of " + target
                            + ", which does not exist on this Minecraft version");
                    continue;
                }
                for (String field : fieldSelectors) {
                    if (!fields.contains(field)) {
                        problems.add(mixinClassName + " -> " + target + "#" + field
                                + " does not exist on this Minecraft version");
                    }
                }
            }
        }
        return problems;
    }

    /**
     * Extracts the {@code @Mixin} target classes, the injection method selectors and
     * the accessor field selectors.
     */
    private static void readMixinMetadata(byte[] bytes, List<String> targets, List<String> selectors,
                                          List<String> fieldSelectors) {
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (MIXIN_ANNOTATION.equals(descriptor)) {
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitArray(String name) {
                            if ("value".equals(name)) {
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visit(String ignored, Object value) {
                                        if (value instanceof Type type) {
                                            targets.add(type.getInternalName());
                                        }
                                    }
                                };
                            }
                            if ("targets".equals(name)) {
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visit(String ignored, Object value) {
                                        if (value instanceof String dotted) {
                                            targets.add(dotted.replace('.', '/'));
                                        }
                                    }
                                };
                            }
                            return null;
                        }
                    };
                }
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                        if (ACCESSOR_ANNOTATION.equals(annotationDescriptor)) {
                            // An accessor names a field, not a method. Without an
                            // explicit name Mixin derives it from the method name.
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public void visit(String ignored, Object value) {
                                    if (value instanceof String field) {
                                        fieldSelectors.add(field);
                                    }
                                }
                            };
                        }
                        if (!INJECTOR_ANNOTATIONS.contains(annotationDescriptor)) {
                            return null;
                        }
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public AnnotationVisitor visitArray(String elementName) {
                                if (!"method".equals(elementName) && !"value".equals(elementName)) {
                                    return null;
                                }
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visit(String ignored, Object value) {
                                        if (value instanceof String selector) {
                                            selectors.add(selector);
                                        }
                                    }
                                };
                            }
                        };
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    /**
     * Collects every method of a class and its superclasses.
     *
     * <p>Mixin resolves an injection target against the whole inheritance chain, so
     * {@code Screen#repositionElements} is a valid target for a mixin declared on
     * {@code PauseScreen}. Returns {@code null} when the class itself is absent.</p>
     */
    private static Set<String> collectMethods(ClassLoader loader, String internalName) throws IOException {
        Set<String> methods = new LinkedHashSet<>();
        String current = internalName;
        int guard = 0;
        while (current != null && guard++ < 32) {
            byte[] bytes = readClassBytes(loader, current + ".class");
            if (bytes == null) {
                return methods.isEmpty() ? null : methods;
            }
            String[] superName = new String[1];
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String name, String signature,
                                  String superType, String[] interfaces) {
                    superName[0] = superType;
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    methods.add(name + descriptor);
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            current = superName[0];
        }
        return methods;
    }

    /**
     * Collects every field of a class and its superclasses.
     *
     * <p>The counterpart of {@link #collectMethods} for the accessors, which name a
     * field instead of a method. A renamed field would break the accessor just as
     * silently as a renamed method breaks an injection, so it is audited the same
     * way.</p>
     *
     * @return the field names, or {@code null} when the class itself is absent
     */
    private static Set<String> collectFields(ClassLoader loader, String internalName) throws IOException {
        Set<String> fields = new LinkedHashSet<>();
        String current = internalName;
        int guard = 0;
        while (current != null && guard++ < 32) {
            byte[] bytes = readClassBytes(loader, current + ".class");
            if (bytes == null) {
                return fields.isEmpty() ? null : fields;
            }
            String[] superName = new String[1];
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String name, String signature,
                                  String superType, String[] interfaces) {
                    superName[0] = superType;
                }

                @Override
                public FieldVisitor visitField(int access, String name, String descriptor,
                                               String signature, Object value) {
                    fields.add(name);
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            current = superName[0];
        }
        return fields;
    }

    /**
     * Matches a Mixin method selector against the collected methods.
     *
     * <p>Supported forms: {@code name}, {@code name(descriptor)} and {@code name(args)ret}
     * - the latter two share the same JVM descriptor. {@code <init>} selects a
     * constructor.</p>
     */
    private static boolean matches(Set<String> methods, String selector) {
        String name = selector;
        String descriptor = null;
        int parenthesis = selector.indexOf('(');
        if (parenthesis >= 0) {
            name = selector.substring(0, parenthesis);
            descriptor = selector.substring(parenthesis);
        }
        if ("<init>".equals(name)) {
            for (String method : methods) {
                if (!method.startsWith("<init>")) {
                    continue;
                }
                if (descriptor == null || method.equals("<init>" + descriptor)) {
                    return true;
                }
            }
            return false;
        }
        for (String method : methods) {
            int open = method.indexOf('(');
            String methodName = open < 0 ? method : method.substring(0, open);
            if (!methodName.equals(name)) {
                continue;
            }
            if (descriptor == null || method.substring(open).equals(descriptor)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Reverse audit
    // ------------------------------------------------------------------

    /**
     * Finds every compiled mixin class in the {@code core} / {@code legacy} / {@code modern}
     * families by walking the classpath directories.
     */
    private static Set<String> findCompiledMixins(ClassLoader loader) throws IOException {
        Set<String> found = new LinkedHashSet<>();
        Enumeration<URL> roots = loader.getResources(MIXIN_PACKAGE.replace('.', '/') + "/");
        while (roots.hasMoreElements()) {
            URL root = roots.nextElement();
            if (!"file".equals(root.getProtocol())) {
                continue;
            }
            java.nio.file.Path directory;
            try {
                directory = java.nio.file.Paths.get(root.toURI());
            } catch (Exception unreadable) {
                continue;
            }
            for (String family : FAMILIES) {
                java.nio.file.Path familyDirectory = directory.resolve(family);
                if (!java.nio.file.Files.isDirectory(familyDirectory)) {
                    continue;
                }
                try (java.util.stream.Stream<java.nio.file.Path> entries =
                             java.nio.file.Files.list(familyDirectory)) {
                    entries.forEach(entry -> {
                        String name = entry.getFileName().toString();
                        if (name.endsWith(".class") && name.indexOf('$') < 0) {
                            found.add(MIXIN_PACKAGE + "." + family + "."
                                    + name.substring(0, name.length() - ".class".length()));
                        }
                    });
                } catch (IOException unreadable) {
                    // A classpath entry we cannot list simply contributes nothing.
                }
            }
        }
        return found;
    }

    // ------------------------------------------------------------------
    // Resource helpers
    // ------------------------------------------------------------------

    /**
     * Builds a class loader over the node's compile classpath so that the Minecraft
     * class files can be inspected. Returns {@code null} when the build did not provide
     * the classpath (for example when the test is launched from an IDE).
     */
    private static ClassLoader minecraftClassLoader() {
        String classpath = System.getProperty(CLASSPATH_PROPERTY);
        if (classpath == null || classpath.isBlank()) {
            return null;
        }
        List<URL> entries = new ArrayList<>();
        for (String entry : classpath.split(File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            try {
                entries.add(java.nio.file.Path.of(entry).toUri().toURL());
            } catch (Exception unreadable) {
                // A malformed classpath entry simply contributes nothing.
            }
        }
        if (entries.isEmpty()) {
            return null;
        }
        return new URLClassLoader(entries.toArray(URL[]::new), null);
    }

    private static String readResource(ClassLoader loader, String resource) throws IOException {
        byte[] bytes = readClassBytes(loader, resource);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] readClassBytes(ClassLoader loader, String resource) throws IOException {
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            if (stream == null) {
                return null;
            }
            return stream.readAllBytes();
        }
    }
}
