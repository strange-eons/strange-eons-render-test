package org.strangeeons.gradle.rendertest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RenderComponentsTest {

    @Test
    void linuxWithoutDisplayNeedsVirtualDisplay() {
        assertTrue(RenderComponents.needsVirtualDisplay(false, "Linux", null));
        assertTrue(RenderComponents.needsVirtualDisplay(false, "Linux", ""));
    }

    @Test
    void linuxWithDisplayDoesNot() {
        assertFalse(RenderComponents.needsVirtualDisplay(false, "Linux", ":0"));
    }

    @Test
    void macAndWindowsDoNot() {
        assertFalse(RenderComponents.needsVirtualDisplay(false, "Mac OS X", null));
        assertFalse(RenderComponents.needsVirtualDisplay(false, "Windows 11", null));
    }

    @Test
    void headlessNeverDoes() {
        assertFalse(RenderComponents.needsVirtualDisplay(true, "Linux", null));
    }

    @Test
    void listsSavedComponentsSortedWithSlashes(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("b/c"));
        Files.writeString(dir.resolve("b/c/two.eon"), "");
        Files.writeString(dir.resolve("a.EON"), "");
        Files.writeString(dir.resolve("b/one.eon"), "");
        Files.writeString(dir.resolve("b/notes.txt"), "");
        Files.writeString(dir.resolve("b/text.eont"), "");
        Files.writeString(dir.resolve("b/upper.EONT"), "");
        Files.writeString(dir.resolve("b/other.toml"), "");
        Files.writeString(dir.resolve("b/text.eont.bak"), "");
        assertEquals(List.of("a.EON", "b/c/two.eon", "b/one.eon", "b/text.eont", "b/upper.EONT"),
                RenderComponents.listComponents(dir));
    }

    @Test
    void partitionDealsEveryItemOnce() {
        List<String> items = List.of("a", "b", "c", "d", "e", "f", "g");
        List<List<String>> parts = RenderComponents.partition(items, 3);
        assertEquals(List.of(List.of("a", "d", "g"), List.of("b", "e"), List.of("c", "f")), parts);
        Set<String> seen = new HashSet<>();
        parts.forEach(seen::addAll);
        assertEquals(Set.copyOf(items), seen);
        assertEquals(items.size(), parts.stream().mapToInt(List::size).sum());
    }

    @Test
    void forkCountStaysBetweenOneAndComponents() {
        assertEquals(4, RenderComponents.forkCount(4, 100));
        assertEquals(2, RenderComponents.forkCount(4, 2));
        assertEquals(1, RenderComponents.forkCount(4, 0));
        assertEquals(1, RenderComponents.forkCount(0, 10));
    }

    @Test
    void summaryMergesForks(@TempDir Path dir) throws IOException {
        RenderComponents.Fork a = fork(dir, "a", "OK\tcomponents/x\t2", "SKIP\tclassmap/G/y\tscripted", "DONE");
        RenderComponents.Fork b = fork(dir, "b", "OK\tcomponents/z\t1", "FAIL\tcomponents/w\tboom", "DONE");
        RenderComponents.Summary s = RenderComponents.summarize(List.of(a, b));
        assertEquals(2, s.ok());
        assertEquals(List.of("classmap/G/y: scripted"), s.skipped());
        assertEquals(1, s.failed().size());
        assertTrue(s.failed().get(0).startsWith("components/w: boom"));
        assertTrue(s.failed().get(0).contains(b.log().toString()));
    }

    @Test
    void summaryFailsWhenAForkStopsEarly(@TempDir Path dir) throws IOException {
        RenderComponents.Fork a = fork(dir, "a", "OK\tcomponents/x\t2", "DONE");
        RenderComponents.Fork b = fork(dir, "b", "OK\tcomponents/z\t1");
        GradleException e = assertThrows(GradleException.class, () -> RenderComponents.summarize(List.of(a, b)));
        assertTrue(e.getMessage().contains("stopped early"));
        assertTrue(e.getMessage().contains(b.log().toString()));
    }

    @Test
    void summaryFailsWhenAForkNeverRan(@TempDir Path dir) throws IOException {
        RenderComponents.Fork a = new RenderComponents.Fork(Files.createDirectories(dir.resolve("a")), null, true);
        GradleException e = assertThrows(GradleException.class, () -> RenderComponents.summarize(List.of(a)));
        assertTrue(e.getMessage().contains("did not run"));
    }

    @Test
    void summaryFailsOnUncaughtScriptErrors(@TempDir Path dir) throws IOException {
        RenderComponents.Fork a = fork(dir, "a", "OK\tcomponents/x\t2", "DONE");
        Files.writeString(a.log(), "INFO started\nUncaught TypeError: x is undefined\n  at line 3\n");
        RenderComponents.Summary s = RenderComponents.summarize(List.of(a));
        assertEquals(1, s.ok());
        assertEquals(1, s.failed().size());
        assertTrue(s.failed().get(0).startsWith("1 uncaught script error(s)"), s.failed().get(0));
    }

    private static RenderComponents.Fork fork(Path root, String name, String... lines) throws IOException {
        RenderComponents.Fork f = new RenderComponents.Fork(Files.createDirectories(root.resolve(name)), null, true);
        Files.write(f.results(), List.of(lines));
        return f;
    }
}
