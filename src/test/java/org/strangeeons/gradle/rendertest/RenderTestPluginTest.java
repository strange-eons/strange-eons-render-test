package org.strangeeons.gradle.rendertest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RenderTestPluginTest {

    @TempDir
    Path tmp;

    private Project project() {
        Project p = ProjectBuilder.builder().withProjectDir(tmp.toFile()).build();
        p.getPluginManager().apply("org.strangeeons.render-test");
        return p;
    }

    @Test
    void rendersOnlyTheConfiguredBundles() {
        Project p = project();
        p.getExtensions().getByType(RenderTestExtension.class).getBundles().from("a.seext", "lib.selibrary");
        RenderComponents render = (RenderComponents) p.getTasks().getByName("renderComponents");
        assertEquals(Set.of("a.seext", "lib.selibrary"),
                render.getBundles().getFiles().stream().map(java.io.File::getName).collect(Collectors.toSet()));
        assertNull(p.getTasks().findByName("packagePluginFolder"));
    }

    @Test
    void failsWithoutBundlesBeforeClearingOutput() throws IOException {
        Project p = project();
        RenderComponents render = (RenderComponents) p.getTasks().getByName("renderComponents");
        Path old = render.getOutputDir().get().getAsFile().toPath().resolve("old.png");
        Files.createDirectories(old.getParent());
        Files.writeString(old, "x");
        GradleException e = assertThrows(GradleException.class, render::render);
        assertTrue(e.getMessage().contains("strangeEonsRenderTest.bundles"), e.getMessage());
        assertTrue(Files.exists(old));
    }
}
