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
import org.gradle.work.DisableCachingByDefault;

/**
 * Starts Strange Eons with the bundles under test, runs the render script,
 * and writes one PNG per sheet to {@link #getOutputDir()}. With more than one
 * {@linkplain #getForks() fork}, several processes split the components.
 *
 * <p>Unless {@link #getHeadless() headless}, SE builds its main window, even in
 * {@code --run} mode, so it needs a display. On Linux without {@code DISPLAY}
 * the process is wrapped in {@code xvfb-run}.
 */
@DisableCachingByDefault(because = "output depends on the Strange Eons build and platform fonts")
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

    /** Whether to run SE without opening any windows. */
    @Input
    public abstract Property<Boolean> getHeadless();

    /** Preferences written to the fresh SE user folder before the run. */
    @Input
    public abstract MapProperty<String, String> getUserSettings();

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

    @Nested
    public abstract Property<JavaLauncher> getLauncher();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    /** Scratch space: SE user folder, render script, results, log. */
    @Internal
    public abstract DirectoryProperty getWorkDir();

    @Inject
    protected abstract ExecOperations getExec();

    /** One Strange Eons process: its folder, component list and output files. */
    record Fork(Path dir, Path list, boolean classMap) {
        Path userFolder() {
            return dir.resolve("user");
        }

        Path results() {
            return dir.resolve("results.tsv");
        }

        Path log() {
            return dir.resolve("strange-eons.log");
        }
    }

    /** Totals merged from every fork's results file. */
    record Summary(int ok, List<String> failed, List<String> skipped) {
    }

    @TaskAction
    void render() throws IOException {
        Path out = getOutputDir().get().getAsFile().toPath();
        Path work = getWorkDir().get().getAsFile().toPath();
        deleteTree(out);
        deleteTree(work);
        Files.createDirectories(out);
        Path script = Files.createDirectories(work).resolve("render.js");
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
        for (Fork f : forks) {
            // SE reads preferences and installed plug-ins from its user folder,
            // which only STRANGE_EONS_USER_DIR relocates; a fresh one per run
            // keeps the developer's own preferences out of the renders.
            writePreferences(Files.createDirectories(f.userFolder()).resolve("preferences"));
        }

        run(forks, f -> command(f, out, script, bundles, hasComponents));
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

    private void run(List<Fork> forks, java.util.function.Function<Fork, List<String>> commands) {
        if (forks.size() == 1) {
            exec(forks.get(0), commands.apply(forks.get(0)));
            return;
        }
        var pool = java.util.concurrent.Executors.newFixedThreadPool(forks.size());
        try {
            List<java.util.concurrent.Future<?>> jobs = new ArrayList<>();
            for (Fork f : forks) {
                List<String> cmd = commands.apply(f);
                jobs.add(pool.submit(() -> exec(f, cmd)));
            }
            for (var job : jobs) {
                try {
                    job.get();
                } catch (java.util.concurrent.ExecutionException e) {
                    throw new GradleException("Could not run Strange Eons", e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new GradleException("Interrupted while rendering", e);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private void exec(Fork f, List<String> cmd) {
        try (var logOut = Files.newOutputStream(f.log())) {
            getExec().exec(spec -> {
                spec.setCommandLine(cmd);
                spec.environment("STRANGE_EONS_USER_DIR", f.userFolder().toAbsolutePath().toString());
                spec.setStandardOutput(logOut);
                spec.setErrorOutput(logOut);
                spec.setIgnoreExitValue(true);
            });
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private List<String> command(Fork f, Path out, Path script, List<File> bundles, boolean hasComponents) {
        List<String> cmd = new ArrayList<>();
        boolean headless = getHeadless().get();
        if (needsVirtualDisplay(headless, System.getProperty("os.name"), System.getenv("DISPLAY"))) {
            cmd.add("xvfb-run");
            cmd.add("--auto-servernum");
            cmd.add("--server-args=-screen 0 1920x1080x24");
        }
        cmd.add(getLauncher().get().getExecutablePath().getAsFile().getAbsolutePath());
        // Flags SE's own `run` task passes; see strange-eons-app/build.gradle.kts.
        cmd.add("--add-opens=java.desktop/javax.swing.table=ALL-UNNAMED");
        cmd.add("--enable-native-access=ALL-UNNAMED");
        if (headless) {
            cmd.add("-Djava.awt.headless=true");
        }
        cmd.addAll(getJvmArgs().get());
        cmd.add("-Drt.out=" + out.toAbsolutePath());
        cmd.add("-Drt.components=" + (hasComponents ? getComponents().get().getAsFile().getAbsolutePath() : ""));
        cmd.add("-Drt.games=" + String.join(",", getClassMapGames().get()));
        cmd.add("-Drt.dpi=" + getResolution().get());
        cmd.add("-Drt.backs=" + getRenderBacks().get());
        cmd.add("-Drt.results=" + f.results().toAbsolutePath());
        if (f.list() != null) {
            cmd.add("-Drt.list=" + f.list().toAbsolutePath());
        }
        cmd.add("-Drt.classmap=" + f.classMap());
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
        return cmd;
    }

    /**
     * Merges the forks' results. Fails if any fork did not finish; failed
     * components are returned, each followed by the log to read.
     */
    static Summary summarize(List<Fork> forks) throws IOException {
        int ok = 0;
        List<String> failed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Fork fork : forks) {
            Path results = fork.results();
            if (!Files.exists(results)) {
                throw new GradleException("Strange Eons did not run the render script. See " + fork.log());
            }
            List<String> lines = Files.readAllLines(results, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !lines.get(lines.size() - 1).equals("DONE")) {
                throw new GradleException("Render script stopped early. See " + fork.log() + " and " + results);
            }
            for (String l : lines) {
                String[] f = l.split("\t", 3);
                String detail = f.length > 2 ? f[2] : "";
                switch (f[0]) {
                    case "OK" -> ++ok;
                    case "FAIL" -> failed.add(f[1] + ": " + detail + " (see " + fork.log() + ")");
                    case "SKIP" -> skipped.add(f[1] + ": " + detail);
                    default -> {
                    }
                }
            }
        }
        return new Summary(ok, failed, skipped);
    }

    /** Saved components under {@code dir}, as sorted relative paths with {@code /}. */
    static List<String> listComponents(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".eon"))
                    .map(p -> dir.relativize(p).toString().replace(File.separatorChar, '/'))
                    .sorted()
                    .toList();
        }
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

    private void writePreferences(Path file) throws IOException {
        java.util.Properties p = new java.util.Properties();
        p.putAll(getUserSettings().get());
        try (var out = Files.newOutputStream(file)) {
            p.store(out, "written by renderComponents");
        }
    }

    static boolean needsVirtualDisplay(boolean headless, String osName, String display) {
        if (headless) {
            return false;
        }
        String os = osName.toLowerCase();
        boolean x11 = !os.startsWith("windows") && !os.startsWith("mac");
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
