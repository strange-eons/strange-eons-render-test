package org.strangeeons.gradle.rendertest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Renders saved components and class-map defaults in Strange Eons and writes
 * one PNG per sheet to {@link #getOutputDir()}. With more than one
 * {@linkplain #getForks() fork}, several processes split the components.
 */
@DisableCachingByDefault(because = "output depends on the Strange Eons build and platform fonts")
public abstract class RenderComponents extends StrangeEonsTask {

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getComponents();

    @Input
    public abstract ListProperty<String> getClassMapGames();

    @Input
    public abstract Property<Integer> getResolution();

    /** Whether DIY components' back faces are rendered. */
    @Input
    public abstract Property<Boolean> getRenderBacks();

    /**
     * How many Strange Eons processes render at once. Saved components are
     * split between them; class-map entries all render in the first. Each
     * process needs its own memory, and the images are the same either way.
     */
    @Internal
    public abstract Property<Integer> getForks();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @TaskAction
    void render() throws IOException {
        List<File> bundles = bundles();
        Path out = getOutputDir().get().getAsFile().toPath();
        Path work = getWorkDir().get().getAsFile().toPath();
        deleteTree(out);
        deleteTree(work);
        Files.createDirectories(out);
        Path script = Files.createDirectories(work).resolve("render.js");
        try (InputStream in = RenderComponents.class.getResourceAsStream("render.js")) {
            Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
        }

        boolean hasComponents = getComponents().isPresent() && getComponents().get().getAsFile().isDirectory();
        if (!hasComponents && getClassMapGames().get().isEmpty()) {
            throw new GradleException("Nothing to render. Set strangeEonsRenderTest.components and/or classMapGames.");
        }

        List<String> files = hasComponents ? listComponents(getComponents().get().getAsFile().toPath()) : List.of();
        int count = forkCount(getForks().get(), files.size());
        List<Fork> forks = new ArrayList<>();
        if (count == 1) {
            forks.add(new Fork(work, null, true));
        } else {
            List<List<String>> parts = partition(files, count);
            for (int k = 0; k < count; ++k) {
                Path dir = Files.createDirectories(work.resolve("fork-" + k));
                Path list = Files.write(dir.resolve("components.txt"), parts.get(k), StandardCharsets.UTF_8);
                forks.add(new Fork(dir, list, k == 0));
            }
        }
        run(forks, script, bundles, f -> properties(f, out, hasComponents));
        Summary s = summarize(forks);
        for (String skip : s.skipped()) {
            getLogger().warn("Skipped {}", skip);
        }
        getLogger().lifecycle("Rendered {} component(s) to {}{}", s.ok(), getOutputDir().get().getAsFile(),
                forks.size() > 1 ? " with " + forks.size() + " processes" : "");
        if (!s.failed().isEmpty()) {
            throw new GradleException(s.failed().size() + " component(s) failed to render:\n  "
                    + String.join("\n  ", s.failed()));
        }
    }

    private Map<String, String> properties(Fork f, Path out, boolean hasComponents) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("rt.out", out.toAbsolutePath().toString());
        p.put("rt.components", hasComponents ? getComponents().get().getAsFile().getAbsolutePath() : "");
        p.put("rt.games", String.join(",", getClassMapGames().get()));
        p.put("rt.dpi", String.valueOf(getResolution().get()));
        p.put("rt.backs", String.valueOf(getRenderBacks().get()));
        if (f.list() != null) {
            p.put("rt.list", f.list().toAbsolutePath().toString());
        }
        p.put("rt.classmap", String.valueOf(f.classMap()));
        return p;
    }

    /**
     * Saved components ({@code .eon}, or {@code .eont} text saves) under
     * {@code dir}, as sorted relative paths with {@code /}.
     */
    static List<String> listComponents(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> isComponentFile(p.getFileName().toString()))
                    .map(p -> dir.relativize(p).toString().replace(File.separatorChar, '/'))
                    .sorted()
                    .toList();
        }
    }

    static boolean isComponentFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".eon") || lower.endsWith(".eont");
    }

    /** Deals {@code items} out in turn, so each part gets a mix of folders. */
    static List<List<String>> partition(List<String> items, int parts) {
        List<List<String>> out = new ArrayList<>();
        for (int k = 0; k < parts; ++k) {
            out.add(new ArrayList<>());
        }
        for (int i = 0; i < items.size(); ++i) {
            out.get(i % parts).add(items.get(i));
        }
        return out;
    }

    /** Processes to start: as requested, but at least one and no more than there are components. */
    static int forkCount(int requested, int components) {
        return Math.max(1, Math.min(requested, components));
    }
}
