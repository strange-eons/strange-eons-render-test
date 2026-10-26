package org.strangeeons.gradle.rendertest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;

/**
 * Starts Strange Eons once with the bundles under test, runs the render
 * script, and writes one PNG per sheet to {@link #getOutputDir()}.
 *
 * <p>SE always builds its main window, even in {@code --run} mode, so it
 * needs a display. On Linux without {@code DISPLAY} the process is wrapped in
 * {@code xvfb-run}.
 */
public abstract class RenderComponents extends DefaultTask {

    private static final String MAIN_CLASS = "ca.cgjennings.apps.arkham.StrangeEons";

    /** Jars of the built SE application ({@code lib/*.jar} of the install). */
    @Classpath
    public abstract ConfigurableFileCollection getApplicationClasspath();

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getBundles();

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getComponents();

    @Input
    public abstract ListProperty<String> getClassMapGames();

    @Input
    public abstract Property<Integer> getResolution();

    @Input
    public abstract Property<String> getGameLanguage();

    @Input
    public abstract Property<String> getUiLanguage();

    @Input
    public abstract ListProperty<String> getJvmArgs();

    /** Preferences written to the fresh SE user folder before the run. */
    @Input
    public abstract MapProperty<String, String> getUserSettings();

    /** Whether DIY components' back faces are rendered. */
    @Input
    public abstract Property<Boolean> getRenderBacks();

    @Nested
    public abstract Property<JavaLauncher> getLauncher();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    /** Scratch space: SE user folder, render script, results, log. */
    @Internal
    public abstract DirectoryProperty getWorkDir();

    @Inject
    protected abstract ExecOperations getExec();

    @TaskAction
    void render() throws IOException {
        Path out = getOutputDir().get().getAsFile().toPath();
        Path work = getWorkDir().get().getAsFile().toPath();
        deleteTree(out);
        deleteTree(work);
        Files.createDirectories(out);
        // SE reads preferences and installed plug-ins from its user folder,
        // which only STRANGE_EONS_USER_DIR relocates; a fresh one per run keeps
        // the developer's own preferences out of the renders.
        Path userFolder = Files.createDirectories(work.resolve("user"));
        writePreferences(userFolder.resolve("preferences"));
        Path script = work.resolve("render.js");
        Path results = work.resolve("results.tsv");
        Path log = work.resolve("strange-eons.log");
        try (InputStream in = RenderComponents.class.getResourceAsStream("render.js")) {
            Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
        }

        List<File> bundles = new ArrayList<>(getBundles().getFiles());
        if (bundles.isEmpty()) {
            throw new GradleException("No bundles to test. Set strangeEonsRenderTest.pluginFolder or bundles.");
        }
        boolean hasComponents = getComponents().isPresent() && getComponents().get().getAsFile().isDirectory();
        if (!hasComponents && getClassMapGames().get().isEmpty()) {
            throw new GradleException("Nothing to render. Set strangeEonsRenderTest.components and/or classMapGames.");
        }

        List<String> cmd = new ArrayList<>();
        if (needsVirtualDisplay()) {
            cmd.add("xvfb-run");
            cmd.add("--auto-servernum");
            cmd.add("--server-args=-screen 0 1920x1080x24");
        }
        cmd.add(getLauncher().get().getExecutablePath().getAsFile().getAbsolutePath());
        // Flags SE's own `run` task passes; see strange-eons-app/build.gradle.kts.
        cmd.add("--add-opens=java.desktop/javax.swing.table=ALL-UNNAMED");
        cmd.add("--enable-native-access=ALL-UNNAMED");
        cmd.addAll(getJvmArgs().get());
        cmd.add("-Drt.out=" + out.toAbsolutePath());
        cmd.add("-Drt.components=" + (hasComponents ? getComponents().get().getAsFile().getAbsolutePath() : ""));
        cmd.add("-Drt.games=" + String.join(",", getClassMapGames().get()));
        cmd.add("-Drt.dpi=" + getResolution().get());
        cmd.add("-Drt.backs=" + getRenderBacks().get());
        cmd.add("-Drt.results=" + results.toAbsolutePath());
        cmd.add("-cp");
        cmd.add(getApplicationClasspath().getFiles().stream().map(File::getAbsolutePath)
                .collect(Collectors.joining(File.pathSeparator)));
        cmd.add(MAIN_CLASS);
        cmd.add("--xDisableJreCheck");
        cmd.add("--xDisablePluginLoading");
        cmd.add("--glang");
        cmd.add(getGameLanguage().get());
        cmd.add("--ulang");
        cmd.add(getUiLanguage().get());
        cmd.add("--plugintest");
        cmd.add(bundles.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator)));
        cmd.add("--run");
        cmd.add(script.toAbsolutePath().toString());

        try (var logOut = Files.newOutputStream(log)) {
            getExec().exec(spec -> {
                spec.setCommandLine(cmd);
                spec.environment("STRANGE_EONS_USER_DIR", userFolder.toAbsolutePath().toString());
                spec.setStandardOutput(logOut);
                spec.setErrorOutput(logOut);
                spec.setIgnoreExitValue(true);
            });
        }

        report(results, log);
    }

    private void report(Path results, Path log) throws IOException {
        if (!Files.exists(results)) {
            throw new GradleException("Strange Eons did not run the render script. See " + log);
        }
        List<String> lines = Files.readAllLines(results, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(lines.size() - 1).equals("DONE")) {
            throw new GradleException("Render script stopped early. See " + log + " and " + results);
        }
        int ok = 0;
        List<String> failed = new ArrayList<>();
        for (String l : lines) {
            String[] f = l.split("\t", 3);
            switch (f[0]) {
                case "OK" -> ++ok;
                case "FAIL" -> failed.add(f[1] + ": " + (f.length > 2 ? f[2] : ""));
                case "SKIP" -> getLogger().warn("Skipped {}: {}", f[1], f.length > 2 ? f[2] : "");
                default -> {
                }
            }
        }
        getLogger().lifecycle("Rendered {} component(s) to {}", ok, getOutputDir().get().getAsFile());
        if (!failed.isEmpty()) {
            throw new GradleException(failed.size() + " component(s) failed to render:\n  "
                    + String.join("\n  ", failed) + "\nSee " + log);
        }
    }

    private void writePreferences(Path file) throws IOException {
        java.util.Properties p = new java.util.Properties();
        p.putAll(getUserSettings().get());
        try (var out = Files.newOutputStream(file)) {
            p.store(out, "written by renderComponents");
        }
    }

    private static boolean needsVirtualDisplay() {
        String os = System.getProperty("os.name").toLowerCase();
        boolean x11 = !os.startsWith("windows") && !os.startsWith("mac");
        String display = System.getenv("DISPLAY");
        return x11 && (display == null || display.isEmpty());
    }

    private static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) {
            return;
        }
        try (Stream<Path> s = Files.walk(p)) {
            for (Path x : s.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(x);
            }
        }
    }
}
