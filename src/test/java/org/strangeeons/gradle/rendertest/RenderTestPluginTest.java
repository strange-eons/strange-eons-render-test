package org.strangeeons.gradle.rendertest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private RenderTestExtension ext(Project p) {
        return p.getExtensions().getByType(RenderTestExtension.class);
    }

    private static Set<String> checkDependencies(Project p) {
        return p.getTasks().getByName("check").getTaskDependencies().getDependencies(null).stream()
                .map(org.gradle.api.Task::getName).collect(Collectors.toSet());
    }

    @Test
    void renderSetAddsCheckedTasks() {
        Project p = project();
        ext(p).getUserSettings().put("a", "1");
        ext(p).getRenderSets().register("inkSaver", set -> {
            set.getComponents().set(tmp.resolve("flexible").toFile());
            set.getMasters().set(tmp.resolve("ink-masters").toFile());
            set.getUserSettings().put("render-as-prototype", "yes");
            set.sharedMasters(m -> {
                m.getRenderSuffix().set("-1.png");
                m.getMasterSuffix().set("-0.png");
                m.mask("components/x/*", 1, 2, 3, 4);
            });
        });
        RenderComponents render = (RenderComponents) p.getTasks().getByName("renderInkSaver");
        assertEquals(java.util.Map.of("a", "1", "render-as-prototype", "yes"), render.getUserSettings().get());
        assertTrue(render.getOutputDir().get().getAsFile().toPath().endsWith("render-test/inkSaver/actual"));

        VerifyRenders verify = (VerifyRenders) p.getTasks().getByName("verifyInkSaverRenders");
        assertTrue(verify.getPlatformMasters().get().getAsFile().toPath().getParent().endsWith("ink-masters"));
        assertEquals("-0.png", verify.getSharedMasters().plan().masterFor("components/x/a-1.png")
                .replace("components/x/a", ""));
        UpdateRenderMasters update = (UpdateRenderMasters) p.getTasks().getByName("updateInkSaverRenderMasters");
        assertTrue(update.getSharedMasters().plan().isShared("components/x/a-1.png"));
        assertTrue(checkDependencies(p).containsAll(Set.of("verifyRenders", "verifyInkSaverRenders")));

        // the main set shares nothing
        assertFalse(((VerifyRenders) p.getTasks().getByName("verifyRenders")).getSharedMasters().plan()
                .isShared("components/x/a-1.png"));
    }

    @Test
    void textCheckAddsRunVerifyAndUpdate() {
        Project p = project();
        ext(p).getBundles().from("a.seext");
        ext(p).getTextChecks().register("defaults", c -> {
            c.getScript().set(tmp.resolve("dump.js").toFile());
            c.getMasters().set(tmp.resolve("defaults").toFile());
            c.getSystemProperties().put("fx.mode", "defaults");
            c.getIgnoreLineOrder().set(true);
        });
        RunScript run = (RunScript) p.getTasks().getByName("runDefaults");
        assertEquals(java.util.Map.of("fx.mode", "defaults"), run.getSystemProperties().get());
        assertEquals(Set.of("a.seext"), run.getBundles().getFiles().stream().map(java.io.File::getName)
                .collect(Collectors.toSet()));
        VerifyTextMasters verify = (VerifyTextMasters) p.getTasks().getByName("verifyDefaults");
        assertEquals(run.getOutputDir().get(), verify.getActual().get());
        assertTrue(verify.getIgnoreLineOrder().get());
        org.gradle.api.tasks.Sync update = (org.gradle.api.tasks.Sync) p.getTasks().getByName("updateDefaults");
        assertEquals(tmp.resolve("defaults").toFile().getName(), update.getDestinationDir().getName());
        assertTrue(checkDependencies(p).contains("verifyDefaults"));
    }

    @Test
    void addedScriptTasksGetTheSharedSettings() {
        Project p = project();
        ext(p).getBundles().from("a.seext");
        ext(p).getGameLanguage().set("de");
        RunScript run = p.getTasks().register("generateFixtures", RunScript.class).get();
        assertEquals("de", run.getGameLanguage().get());
        assertEquals(Set.of("a.seext"), run.getBundles().getFiles().stream().map(java.io.File::getName)
                .collect(Collectors.toSet()));
        assertFalse(run.getOutputDir().isPresent());
        assertTrue(run.getWorkDir().get().getAsFile().toPath().endsWith("render-test/generateFixtures/work"));
    }
}
