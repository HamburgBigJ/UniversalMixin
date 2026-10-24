package info.cho.universalMixin.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AgentIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void packagedAgentDiscoversClassLiteralTargetBeforeItLoads() throws Exception {
        String output = runExample(true);
        assertTrue(output.contains("[Mixin HEAD] About to apply damage\n"
                + "[Player] Took 7 damage; health = 13\n"
                + "[Mixin RETURN] Damage finished\n"
                + "[App] Remaining health: 13\n"
                + "[App] Title: Modded player (health=13)\n[App] Bonus: 6"), output);
    }

    @Test
    void applicationWorksWithoutAgent() throws Exception {
        String output = runExample(false);
        assertFalse(output.contains("[Mixin"), output);
        assertTrue(output.contains("[App] Title: Player"), output);
        assertTrue(output.contains("[App] Bonus: 5"), output);
        assertTrue(output.contains("[App] Remaining health: 13"), output);
    }

    @Test
    void agentWithoutModsLeavesApplicationUnmodified() throws Exception {
        String output = runExample(true, false);
        assertFalse(output.contains("[Mixin"), output);
        assertTrue(output.contains("[App] Title: Player"), output);
        assertTrue(output.contains("[App] Bonus: 5"), output);
        assertTrue(output.contains("[App] Remaining health: 13"), output);
    }

    private String runExample(boolean withAgent) throws Exception {
        return runExample(withAgent, withAgent);
    }

    private String runExample(boolean withAgent, boolean withMods) throws Exception {
        String agent = System.getProperty("agent.jar");
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Xverify:all");
        Path application = Path.of(System.getProperty("example.jar"));
        byte[] originalJar = Files.readAllBytes(application);
        if (withAgent && withMods) {
            Path mods = Files.createDirectories(temporaryDirectory.resolve("mod jars"));
            Path mod = Files.copy(Path.of(System.getProperty("example.mod.jar")), mods.resolve("player mod.jar"));
            command.add("-javaagent:" + agent + "=" + mod);
        } else if (withAgent) {
            command.add("-javaagent:" + agent);
        }
        command.addAll(List.of("-jar", application.toString()));
        Path log = temporaryDirectory.resolve("example.log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Example JVM timed out");
            String output = Files.readString(log).replace("\r\n", "\n");
            assertEquals(0, process.exitValue(), output);
            assertArrayEquals(originalJar, Files.readAllBytes(application), "Application JAR was changed");
            return output;
        } finally {
            process.destroyForcibly();
        }
    }
}
