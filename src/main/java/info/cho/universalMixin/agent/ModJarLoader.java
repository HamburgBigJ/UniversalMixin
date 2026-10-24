package info.cho.universalMixin.agent;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/** Makes external mod classes and resources visible to the application class loader. */
final class ModJarLoader {
    // The instrumentation API may read these archives for the lifetime of the JVM.
    private static final List<JarFile> OPEN_JARS = new ArrayList<>();

    private ModJarLoader() {}

    static List<Path> append(String arguments, Instrumentation instrumentation) throws IOException {
        if (arguments == null || arguments.isBlank()) {
            return List.of();
        }
        List<Path> paths = resolvePaths(arguments);

        // Open every archive before changing the class loader, so invalid input fails early.
        Deque<JarFile> pending = new ArrayDeque<>();
        try {
            for (Path path : paths) {
                pending.add(new JarFile(path.toFile()));
            }
            while (!pending.isEmpty()) {
                JarFile jar = pending.getFirst();
                instrumentation.appendToSystemClassLoaderSearch(jar);
                OPEN_JARS.add(jar);
                pending.removeFirst();
            }
        } finally {
            for (JarFile jar : pending) {
                jar.close();
            }
        }
        return paths;
    }

    private static List<Path> resolvePaths(String arguments) throws IOException {
        var paths = new LinkedHashSet<Path>();
        for (String entry : arguments.split(Pattern.quote(File.pathSeparator), -1)) {
            if (entry.isBlank()) {
                throw new IllegalArgumentException("Empty mod JAR path in agent arguments");
            }
            paths.add(Path.of(entry).toRealPath());
        }

        return List.copyOf(paths);
    }
}

