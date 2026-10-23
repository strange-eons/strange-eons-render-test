package org.strangeeons.gradle.rendertest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Compares renders with the masters for the current platform
 * ({@code <masters>/<platform>/}). Fails if any image differs by even one
 * pixel, if a render has no master, or if a master was not rendered. Writes
 * magenta-on-grey diff images for mismatches. Warns when the masters were
 * recorded with a different JDK or OS than this run.
 */
public abstract class VerifyRenders extends DefaultTask {

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getActual();

    /** Root holding one folder per platform. Used only to list alternatives. */
    @Internal
    public abstract DirectoryProperty getMastersRoot();

    /** This platform's masters; may not exist yet. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getPlatformMasters();

    @Input
    public abstract Property<String> getPlatform();

    @Input
    public abstract Property<String> getJavaVendor();

    @Input
    public abstract Property<String> getJavaRuntimeVersion();

    @OutputDirectory
    public abstract DirectoryProperty getDiffs();

    @TaskAction
    void verify() throws IOException {
        String platform = getPlatform().get();
        Path masters = getPlatformMasters().get().getAsFile().toPath();
        Path diffs = getDiffs().get().getAsFile().toPath();
        clear(diffs);

        if (!hasPngs(masters)) {
            List<String> others = otherPlatforms(getMastersRoot().get().getAsFile().toPath(), platform);
            throw new GradleException("No masters for platform '" + platform + "' in " + masters + ".\n"
                    + (others.isEmpty() ? "" : "Masters exist for: " + String.join(", ", others) + ".\n")
                    + "Run the updateRenderMasters task on this platform to record them, "
                    + "or pass -PrenderTestPlatform=<name> to select another set.");
        }

        List<String> envDiffs = List.of();
        Path envFile = masters.resolve(RenderEnvironment.FILE_NAME);
        if (Files.isRegularFile(envFile)) {
            Map<String, String> now = RenderEnvironment.describe(platform, getJavaVendor().get(), getJavaRuntimeVersion().get());
            envDiffs = RenderEnvironment.differences(RenderEnvironment.read(envFile), now);
            if (!envDiffs.isEmpty()) {
                getLogger().warn("Masters for '{}' were recorded in a different environment; pixel differences "
                        + "may come from that rather than the plug-in:\n  {}", platform, String.join("\n  ", envDiffs));
            }
        }

        List<ImageComparator.Result> results = ImageComparator.compare(getActual().get().getAsFile().toPath(), masters, diffs);
        List<ImageComparator.Result> bad = results.stream().filter(r -> r.kind() != ImageComparator.Kind.MATCH).toList();
        getLogger().lifecycle("{} of {} image(s) match the '{}' masters", results.size() - bad.size(), results.size(), platform);
        if (!bad.isEmpty()) {
            StringBuilder b = new StringBuilder();
            b.append(bad.size()).append(" image(s) do not match masters in ").append(masters).append(":\n");
            for (ImageComparator.Result r : bad) {
                b.append("  ").append(r.kind()).append("  ").append(r.path()).append("  ").append(r.detail()).append('\n');
            }
            if (!envDiffs.isEmpty()) {
                b.append("Environment differs from the masters':\n  ").append(String.join("\n  ", envDiffs)).append('\n');
            }
            b.append("Diff images: ").append(diffs).append('\n');
            b.append("If the changes are intended, run the updateRenderMasters task.");
            throw new GradleException(b.toString());
        }
    }

    private static boolean hasPngs(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> s = Files.walk(dir)) {
            return s.anyMatch(p -> p.getFileName().toString().toLowerCase().endsWith(".png"));
        }
    }

    private static List<String> otherPlatforms(Path root, String platform) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(root)) {
            return s.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().equals(platform))
                    .filter(p -> {
                        try {
                            return hasPngs(p);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private static void clear(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path x : s.sorted(Comparator.reverseOrder()).toList()) {
                if (!x.equals(dir)) {
                    Files.delete(x);
                }
            }
        }
    }
}
