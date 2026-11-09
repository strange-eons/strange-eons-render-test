package org.strangeeons.gradle.rendertest;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
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
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;

/**
 * Starts Strange Eons with the bundles under test and runs a script in it,
 * from a fresh user folder. The script reports on {@code rt.results}: one
 * {@code STATUS\tname\tdetail} line per item ({@code OK}, {@code FAIL} or
 * {@code SKIP}), then {@code DONE}. A missing {@code DONE}, a {@code FAIL}
 * or an uncaught script error that SE reports and recovers from fails the
 * task.
 *
 * <p>Unless {@link #getHeadless() headless}, SE builds its main window, even in
 * {@code --run} mode, so it needs a display. On Linux without {@code DISPLAY}
 * the process is wrapped in {@code xvfb-run}.
 */
@DisableCachingByDefault(because = "output depends on the Strange Eons build and platform fonts")
public abstract class StrangeEonsTask extends DefaultTask {

    private static final String MAIN_CLASS = "ca.cgjennings.apps.arkham.StrangeEons";

    /** How SE reports a script error it recovered from. */
    private static final String UNCAUGHT = "Uncaught ";

    /** Jars of the built SE application ({@code lib/*.jar} of the install). */
    @Classpath
    public abstract ConfigurableFileCollection getApplicationClasspath();

    /** The plug-in under test and every library it requires. */
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getBundles();

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

    @Nested
    public abstract Property<JavaLauncher> getLauncher();

    /** Scratch space: SE user folder, script, results, log. */
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

    /** The bundles to load; fails, before anything is cleared, if there are none. */
    protected List<File> bundles() {
        List<File> bundles = new ArrayList<>(getBundles().getFiles());
        if (bundles.isEmpty()) {
            throw new GradleException("No bundles to test. Add the plug-in's bundle to strangeEonsRenderTest.bundles.");
        }
        return bundles;
    }

    /**
     * Starts one SE process per fork, each running {@code script} with the
     * system properties {@code properties} gives it, and waits for all of
     * them. Each fork gets a fresh user folder with {@link #getUserSettings()}.
     */
    protected void run(List<Fork> forks, Path script, List<File> bundles,
            Function<Fork, Map<String, String>> properties) throws IOException {
        for (Fork f : forks) {
            // SE reads preferences and installed plug-ins from its user folder,
            // which only STRANGE_EONS_USER_DIR relocates; a fresh one per run
            // keeps the developer's own preferences out of the results.
            writePreferences(Files.createDirectories(f.userFolder()).resolve("preferences"));
        }
        if (forks.size() == 1) {
            Fork f = forks.get(0);
            exec(f, command(f, script, bundles, properties.apply(f)));
            return;
        }
        ExecutorService pool = Executors.newFixedThreadPool(forks.size());
        try {
            List<Future<?>> jobs = new ArrayList<>();
            for (Fork f : forks) {
                List<String> cmd = command(f, script, bundles, properties.apply(f));
                jobs.add(pool.submit(() -> exec(f, cmd)));
            }
            for (Future<?> job : jobs) {
                try {
                    job.get();
                } catch (ExecutionException e) {
                    throw new GradleException("Could not run Strange Eons", e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new GradleException("Interrupted while running Strange Eons", e);
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
            throw new UncheckedIOException(e);
        }
    }

    private List<String> command(Fork f, Path script, List<File> bundles, Map<String, String> properties) {
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
        properties.forEach((k, v) -> cmd.add("-D" + k + "=" + v));
        cmd.add("-Drt.results=" + f.results().toAbsolutePath());
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
     * Merges the forks' results. Fails if any fork did not finish or logged
     * an uncaught script error; failed items are returned, each followed by
     * the log to read.
     */
    static Summary summarize(List<Fork> forks) throws IOException {
        int ok = 0;
        List<String> failed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Fork fork : forks) {
            Path results = fork.results();
            if (!Files.exists(results)) {
                throw new GradleException("Strange Eons did not run the script. See " + fork.log());
            }
            List<String> lines = Files.readAllLines(results, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !lines.get(lines.size() - 1).equals("DONE")) {
                throw new GradleException("Script stopped early. See " + fork.log() + " and " + results);
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
            long uncaught = uncaughtErrors(fork.log());
            if (uncaught > 0) {
                failed.add(uncaught + " uncaught script error(s) (see " + fork.log() + ")");
            }
        }
        return new Summary(ok, failed, skipped);
    }

    /** Script errors SE reported in a log and recovered from. */
    static long uncaughtErrors(Path log) throws IOException {
        if (!Files.exists(log)) {
            return 0;
        }
        // ISO-8859-1 reads any bytes; the marker is ASCII
        try (Stream<String> lines = Files.lines(log, StandardCharsets.ISO_8859_1)) {
            return lines.filter(l -> l.startsWith(UNCAUGHT)).count();
        }
    }

    private void writePreferences(Path file) throws IOException {
        java.util.Properties p = new java.util.Properties();
        p.putAll(getUserSettings().get());
        try (var out = Files.newOutputStream(file)) {
            p.store(out, "written by " + getName());
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

    static void deleteTree(Path p) throws IOException {
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
