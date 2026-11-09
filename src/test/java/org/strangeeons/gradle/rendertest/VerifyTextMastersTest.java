package org.strangeeons.gradle.rendertest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerifyTextMastersTest {

    @TempDir
    Path tmp;

    private void write(String rel, String text) throws IOException {
        Path p = tmp.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }

    private List<String> compare(boolean ignoreLineOrder) throws IOException {
        return VerifyTextMasters.compare(tmp.resolve("actual"), tmp.resolve("masters"), ignoreLineOrder);
    }

    @Test
    void reportsEachKindOfDifference() throws IOException {
        write("actual/same.txt", "a\nb\n");
        write("masters/same.txt", "a\nb\n");
        write("actual/sub/changed.txt", "a\nc\n");
        write("masters/sub/changed.txt", "a\nb\n");
        write("actual/new.txt", "x\n");
        write("masters/gone.txt", "x\n");
        assertEquals(List.of("gone.txt: not written", "new.txt: no master", "sub/changed.txt:\n  - b\n  + c"),
                compare(false));
    }

    @Test
    void lineOrderMattersUnlessIgnored() throws IOException {
        write("actual/a.txt", "b\na\n");
        write("masters/a.txt", "a\nb\n");
        assertEquals(List.of("a.txt:\n  same lines in another order or count"), compare(false));
        assertEquals(List.of(), compare(true));
    }

    @Test
    void missingMastersFolderMeansNoMasters() throws IOException {
        write("actual/a.txt", "a\n");
        assertEquals(List.of("a.txt: no master"), compare(false));
    }
}
