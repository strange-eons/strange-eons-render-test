package org.strangeeons.gradle.rendertest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RenderEnvironmentTest {

    @Test
    void mapsOsNamesToPlatformFolders() {
        assertEquals("linux", RenderEnvironment.platformFor("Linux"));
        assertEquals("macos", RenderEnvironment.platformFor("Mac OS X"));
        assertEquals("windows", RenderEnvironment.platformFor("Windows 11"));
        assertEquals("freebsd", RenderEnvironment.platformFor("FreeBSD"));
        assertEquals("sunos", RenderEnvironment.platformFor("SunOS"));
    }

    @Test
    void rejectsPlatformNamesThatAreNotSimpleFolders() {
        assertEquals("linux-ci", RenderEnvironment.validatePlatform("linux-ci"));
        assertThrows(IllegalArgumentException.class, () -> RenderEnvironment.validatePlatform("../linux"));
        assertThrows(IllegalArgumentException.class, () -> RenderEnvironment.validatePlatform("a/b"));
        assertThrows(IllegalArgumentException.class, () -> RenderEnvironment.validatePlatform(""));
    }

    @Test
    void roundTripsAndReportsDifferences(@TempDir Path tmp) throws IOException {
        Map<String, String> recorded = RenderEnvironment.describe("linux", "Ubuntu", "25.0.4+1");
        Path f = tmp.resolve(RenderEnvironment.FILE_NAME);
        RenderEnvironment.write(f, recorded);
        assertEquals(recorded, RenderEnvironment.read(f));

        Map<String, String> now = RenderEnvironment.describe("linux", "Eclipse Adoptium", "25.0.4+1");
        assertEquals(List.of("java.vendor: masters Ubuntu, this run Eclipse Adoptium"),
                RenderEnvironment.differences(recorded, now));
        assertEquals(List.of(), RenderEnvironment.differences(recorded, recorded));
    }
}
