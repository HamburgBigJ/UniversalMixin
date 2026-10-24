package info.cho.universalMixin.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ModJarLoaderTest {
    @TempDir
    Path directory;

    private final List<JarFile> appended = new ArrayList<>();
    private final Instrumentation instrumentation = (Instrumentation) Proxy.newProxyInstance(
            Instrumentation.class.getClassLoader(), new Class<?>[]{Instrumentation.class},
            (proxy, method, arguments) -> {
                if (!method.getName().equals("appendToSystemClassLoaderSearch")) {
                    throw new AssertionError("Unexpected instrumentation call: " + method);
                }
                appended.add((JarFile) arguments[0]);
                return null;
            });

    @AfterEach
    void closeTestArchives() throws IOException {
        // This fake instrumentation does not retain archives as a real JVM does.
        for (JarFile jar : appended) {
            jar.close();
        }
    }

    @Test
    void noArgumentsLeavesClasspathAlone() throws IOException {
        assertEquals(List.of(), ModJarLoader.append(null, instrumentation));
        assertEquals(List.of(), ModJarLoader.append("", instrumentation));
        assertTrue(appended.isEmpty());
    }

    @Test
    void addsMultipleArchivesOnceAndKeepsThemReadable() throws IOException {
        Path first = archive("first mod.jar");
        Path second = archive("second.jar");
        String arguments = String.join(File.pathSeparator, first.toString(), second.toString(), first.toString());
        assertEquals(List.of(first.toRealPath(), second.toRealPath()), ModJarLoader.append(arguments, instrumentation));
        assertEquals(2, appended.size());
        for (JarFile jar : appended) {
            assertNotNull(jar.getJarEntry("mod.txt"));
        }
    }

    @Test
    void rejectsMissingAndInvalidArchivesBeforeAppendingAny() throws IOException {
        Path valid = archive("valid.jar");
        Path invalid = Files.writeString(directory.resolve("invalid.jar"), "not an archive");
        assertThrows(IOException.class, () -> ModJarLoader.append(
                valid + File.pathSeparator + invalid, instrumentation));
        assertThrows(IOException.class, () -> ModJarLoader.append(
                directory.resolve("missing.jar").toString(), instrumentation));
        assertTrue(appended.isEmpty());
    }

    @Test
    void rejectsEmptyEntries() throws IOException {
        Path valid = archive("valid.jar");
        assertThrows(IllegalArgumentException.class,
                () -> ModJarLoader.append(valid + File.pathSeparator, instrumentation));
        assertTrue(appended.isEmpty());
    }

    private Path archive(String name) throws IOException {
        Path path = directory.resolve(name);
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path))) {
            jar.putNextEntry(new JarEntry("mod.txt"));
            jar.write(1);
            jar.closeEntry();
        }
        return path;
    }
}
