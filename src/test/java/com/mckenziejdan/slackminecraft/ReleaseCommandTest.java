package com.mckenziejdan.slackminecraft;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseCommandTest {
    @TempDir Path directory;
    private static final String SHA = "0123456789012345678901234567890123456789";
    private final Path script = Path.of("scripts/release.sh").toAbsolutePath();

    @BeforeEach void commands() throws Exception {
        Files.createDirectory(directory.resolve("bin"));
        stub("git", """
                case "$1" in
                  branch) echo "${BRANCH:-dev}" ;;
                  status)
                    if [ "${DIRTY:-}" = yes ] || { [ "${CHANGE_DURING_BUILD:-}" = yes ] && [ -f built ]; }; then echo ' M pom.xml'; fi ;;
                  remote) echo https://github.com/Staticpast/SlackMinecraft.git ;;
                  rev-parse) echo "$HEAD_SHA" ;;
                  ls-remote)
                    if [ "$2" = --exit-code ]; then
                      printf '%s\\trefs/heads/dev\\n' "${REMOTE_SHA:-$HEAD_SHA}"
                    elif [ "${EXISTING_TAG:-}" = yes ]; then
                      printf '%s\\trefs/tags/v2.0.0\\n' "$HEAD_SHA"
                    fi ;;
                esac
                """);
        stub("mvn", """
                [ "${BUILD_FAIL:-}" != yes ] || exit 1
                version=''
                for arg in "$@"; do
                  case "$arg" in -Drevision=*) version=${arg#-Drevision=} ;; esac
                done
                mkdir -p target
                printf 'tested JAR' > "target/SlackMinecraft-$version.jar"
                touch built
                """);
        stub("gh", """
                [ "${GH_FAIL:-}" != yes ] || exit 1
                if [ "$1" = release ]; then
                  [ "$2" = create ] || exit 1
                  [ -s "$4" ] || exit 1
                fi
                """);
    }

    @Test void dryRunBuildsWithoutGitHubOrCleanTreeChecks() throws Exception {
        Result result = run(Map.of("DIRTY", "yes"), "2.0.0", "--dry-run");
        assertEquals(0, result.code(), result.output());
        assertTrue(result.trace().contains("-Drevision=2.0.0"));
        assertTrue(result.trace().contains("clean|verify|"));
        assertFalse(result.trace().contains("gh|"));
        assertFalse(result.trace().contains("git|"));
    }

    @Test void publishesTestedJarWithExactCommitAndCustomNotes() throws Exception {
        Files.writeString(directory.resolve("notes with spaces.md"), "## New\n\n- Test release.\n");
        Result result = run(Map.of(), "2.0.0", "--notes-file", "notes with spaces.md");
        assertEquals(0, result.code(), result.output());
        assertTrue(result.trace().contains("gh|release|create|v2.0.0|"));
        assertTrue(result.trace().contains("--target|" + SHA + "|--title|Build 2.0.0|"));
        assertTrue(result.trace().contains("--notes-file|notes with spaces.md|"));
        assertFalse(result.trace().contains("--draft|"));
    }

    @Test void draftsUseVersionNotesWhenPresent() throws Exception {
        Files.createDirectories(directory.resolve("docs/releases"));
        Files.writeString(directory.resolve("docs/releases/2.0.0.md"), "## New\n");
        Result result = run(Map.of(), "2.0.0", "--draft");
        assertEquals(0, result.code(), result.output());
        assertTrue(result.trace().contains("--notes-file|docs/releases/2.0.0.md|--draft|"));
    }

    @Test void generatesNotesWhenNoVersionNotesExist() throws Exception {
        Result result = run(Map.of(), "2.0.0");
        assertEquals(0, result.code(), result.output());
        assertTrue(result.trace().contains("--generate-notes|"));
    }

    @Test void invalidVersionNeverBuildsOrPublishes() throws Exception {
        Result result = run(Map.of(), "2.0.0-SNAPSHOT");
        assertNotEquals(0, result.code());
        assertEquals("", result.trace());
    }

    @Test void dirtyTreeNeverBuildsOrPublishes() throws Exception {
        Result result = run(Map.of("DIRTY", "yes"), "2.0.0");
        assertNotEquals(0, result.code());
        assertFalse(result.trace().contains("mvn|"));
        assertFalse(result.trace().contains("gh|release|"));
    }

    @Test void unpushedCommitCannotBeReleased() throws Exception {
        Result result = run(Map.of("REMOTE_SHA", "different"), "2.0.0");
        assertNotEquals(0, result.code());
        assertFalse(result.trace().contains("mvn|"));
        assertFalse(result.trace().contains("gh|release|"));
    }

    @Test void existingTagCannotBeOverwritten() throws Exception {
        Result result = run(Map.of("EXISTING_TAG", "yes"), "2.0.0");
        assertNotEquals(0, result.code());
        assertFalse(result.trace().contains("mvn|"));
        assertFalse(result.trace().contains("gh|release|"));
    }

    @Test void failedBuildCannotPublish() throws Exception {
        Result result = run(Map.of("BUILD_FAIL", "yes"), "2.0.0");
        assertNotEquals(0, result.code());
        assertFalse(result.trace().contains("gh|release|"));
    }

    @Test void editsDuringBuildCannotPublish() throws Exception {
        Result result = run(Map.of("CHANGE_DURING_BUILD", "yes"), "2.0.0");
        assertNotEquals(0, result.code());
        assertFalse(result.trace().contains("gh|release|"));
    }

    private record Result(int code, String output, String trace) {}

    private Result run(Map<String, String> variables, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("bash", script.toString()));
        command.addAll(List.of(args));
        var builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
        var environment = builder.environment();
        environment.put("PATH", directory.resolve("bin") + ":" + environment.get("PATH"));
        environment.put("TRACE", directory.resolve("trace").toString());
        environment.put("HEAD_SHA", SHA);
        environment.putAll(variables);
        var process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        int code = process.waitFor();
        Path trace = directory.resolve("trace");
        return new Result(code, output, Files.exists(trace) ? Files.readString(trace) : "");
    }

    private void stub(String name, String body) throws Exception {
        Path file = directory.resolve("bin").resolve(name);
        Files.writeString(file, "#!/bin/sh\nprintf '%s|' " + name + " \"$@\" >> \"$TRACE\"\nprintf '\\n' >> \"$TRACE\"\n" + body);
        assertTrue(file.toFile().setExecutable(true));
    }
}
