package org.strangeeons.gradle.rendertest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundlePackagerTest {

    @TempDir
    Path tmp;

    private Path write(String rel, String text) throws IOException {
        Path p = tmp.resolve("plugin").resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
        return p;
    }

    @Test
    void packsLikeMakeBundle() throws IOException {
        write("eons-plugin", "id = CATALOGUEID{x}\nres://game/Game.js\n");
        write("resources/game/Game.js", "useLibrary('extension');");
        write("seproject", "type = TASK");
        write(".DS_Store", "x");
        write("resources/game/scratch.ts.js", "x");
        write("resources/.git/config", "x");
        write("Thumbs.db", "x");

        Path bundle = tmp.resolve("out/Game.seext");
        BundlePackager.pack(tmp.resolve("plugin"), bundle);

        byte[] bytes = Files.readAllBytes(bundle);
        assertArrayEquals(BundlePackager.WEB_SAFE_HEADER, Arrays.copyOf(bytes, 4));
        List<String> names = new ArrayList<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(bytes, 4, bytes.length - 4))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null;) {
                names.add(e.getName());
            }
        }
        assertEquals(List.of("eons-plugin", "resources/", "resources/game/", "resources/game/Game.js"), names);
    }

    @Test
    void identicalInputsGiveIdenticalBundles() throws IOException {
        write("eons-plugin", "res://game/Game.js\n");
        write("resources/game/Game.js", "x");
        BundlePackager.pack(tmp.resolve("plugin"), tmp.resolve("a.seext"));
        BundlePackager.pack(tmp.resolve("plugin"), tmp.resolve("b.seext"));
        assertArrayEquals(Files.readAllBytes(tmp.resolve("a.seext")), Files.readAllBytes(tmp.resolve("b.seext")));
    }

    @Test
    void guessesExtensionFromScript() throws IOException {
        write("eons-plugin", "#\nid = CATALOGUEID{x}\ncatalog-description = long \\\n  continued: text\n\nres://game/Game.js\n");
        write("resources/game/Game.js", "useLibrary( 'extension' );");
        assertEquals(".seext", BundlePackager.guessExtension(tmp.resolve("plugin")));
    }

    @Test
    void guessesPluginForOtherScripts() throws IOException {
        write("eons-plugin", "res://game/Tool.js\n");
        write("resources/game/Tool.js", "function run() {}");
        assertEquals(".seplugin", BundlePackager.guessExtension(tmp.resolve("plugin")));
    }

    @Test
    void guessesLibraryWithoutRootOrPlugins() throws IOException {
        Files.createDirectories(tmp.resolve("lib"));
        assertEquals(".selibrary", BundlePackager.guessExtension(tmp.resolve("lib")));
        write("eons-plugin", "id = CATALOGUEID{x}\n");
        assertEquals(".selibrary", BundlePackager.guessExtension(tmp.resolve("plugin")));
    }

    @Test
    void firstPluginIdentifierSkipsKeysAndContinuations() {
        assertNull(BundlePackager.firstPluginIdentifier(List.of("# c", "a = b \\", "  res://not/this.js")));
        assertEquals("ca.example.Plugin", BundlePackager.firstPluginIdentifier(List.of("name = x", "ca.example.Plugin")));
    }
}
