package org.strangeeons.gradle.rendertest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Replaces the masters of one platform with the current renders and records
 * the environment they came from. Masters of other platforms are untouched.
 */
public abstract class UpdateRenderMasters extends DefaultTask {

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getActual();

    /** The platform's masters folder, e.g. {@code src/renderTest/masters/linux}. */
    @Internal
    public abstract DirectoryProperty getPlatformMasters();

    @Input
    public abstract Property<String> getPlatform();

    @Input
    public abstract Property<String> getJavaVendor();

    @Input
    public abstract Property<String> getJavaRuntimeVersion();

    public UpdateRenderMasters() {
        getOutputs().upToDateWhen(t -> false);
    }

    @TaskAction
    void update() throws IOException {
        Path from = getActual().get().getAsFile().toPath();
        Path to = getPlatformMasters().get().getAsFile().toPath();
        if (Files.exists(to)) {
            try (Stream<Path> s = Files.walk(to)) {
                for (Path x : s.sorted(Comparator.reverseOrder()).toList()) {
                    if (!x.equals(to)) {
                        Files.delete(x);
                    }
                }
            }
        }
        Files.createDirectories(to);
        List<Path> files;
        try (Stream<Path> s = Files.walk(from)) {
            files = s.filter(Files::isRegularFile).toList();
        }
        for (Path f : files) {
            Path dest = to.resolve(from.relativize(f).toString());
            Files.createDirectories(dest.getParent());
            Files.copy(f, dest);
        }
        RenderEnvironment.write(to.resolve(RenderEnvironment.FILE_NAME), RenderEnvironment.describe(
                getPlatform().get(), getJavaVendor().get(), getJavaRuntimeVersion().get()));
        getLogger().lifecycle("Wrote {} master image(s) for platform '{}' to {}", files.size(), getPlatform().get(), to);
    }
}
