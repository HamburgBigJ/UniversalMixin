package info.cho.universalMixin.discovery;

import info.cho.universalMixin.api.Mixin;
import info.cho.universalMixin.metadata.AccessRules;
import io.github.classgraph.AnnotationClassRef;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Publishes an immutable discovery result before the transformer starts. */
public final class MixinRegistry {
    private record State(Map<String, List<Class<?>>> mixins, Set<String> publicClasses) {}

    private static volatile State state = new State(Map.of(), Set.of());

    private MixinRegistry() {}

    public static void discover() {
        discover(List.of());
    }

    public static void discover(List<Path> modJars) {
        Map<String, List<Class<?>>> discovered = new HashMap<>();
        try (ScanResult scan = scanner(modJars).scan()) {
            for (ClassInfo info : scan.getClassesWithAnnotation(Mixin.class.getName())) {
                String target = targetName(info).replace('.', '/');
                discovered.computeIfAbsent(target, key -> new ArrayList<>()).add(info.loadClass());
            }
        }
        discovered.replaceAll((target, mixins) -> List.copyOf(mixins));
        state = new State(Map.copyOf(discovered), publicClassTargets(discovered));
    }

    public static List<Class<?>> getMixins(String internalClassName) {
        return state.mixins().getOrDefault(internalClassName, List.of());
    }

    /** Nested-class access flags also occur in enclosing and referencing classes. */
    public static Set<String> getPublicClassTargets() {
        return state.publicClasses();
    }

    private static ClassGraph scanner(List<Path> modJars) {
        ClassGraph scanner = new ClassGraph().enableClassInfo().enableAnnotationInfo();
        if (!modJars.isEmpty()) {
            // Appending to the system loader does not update java.class.path.
            List<Object> classpath = new ArrayList<>(scanner.getClasspathFiles());
            classpath.add(Mixin.class.getProtectionDomain().getCodeSource().getLocation());
            classpath.addAll(modJars);
            scanner.overrideClasspath(classpath);
        }
        return scanner;
    }

    private static String targetName(ClassInfo mixin) {
        // Reflecting on @Mixin.value() would load the target before the agent is ready.
        var parameters = mixin.getAnnotationInfo(Mixin.class.getName()).getParameterValues();
        String namedTarget = (String) parameters.getValue("target");
        AnnotationClassRef classTarget = (AnnotationClassRef) parameters.getValue("value");
        String target = namedTarget.isBlank() ? classTarget.getName() : namedTarget;
        if (target.equals("void") || target.isBlank()) {
            throw new IllegalArgumentException("@Mixin must specify a target: " + mixin.getName());
        }
        return target;
    }

    private static Set<String> publicClassTargets(Map<String, List<Class<?>>> mixins) {
        Set<String> targets = new HashSet<>();
        for (var entry : mixins.entrySet()) {
            if (entry.getValue().stream().anyMatch(mixin -> AccessRules.of(mixin).type())) {
                targets.add(entry.getKey());
            }
        }
        return Set.copyOf(targets);
    }
}
