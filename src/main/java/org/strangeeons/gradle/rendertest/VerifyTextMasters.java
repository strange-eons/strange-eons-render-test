package org.strangeeons.gradle.rendertest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Compares the text files a script wrote with masters. Fails if a file
 * differs, has no master, or was not written, listing the lines removed
 * ({@code -}) and added ({@code +}).
 */
@DisableCachingByDefault(because = "verification task")
public abstract class VerifyTextMasters extends DefaultTask {

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getActual();

    /** May not exist yet. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getMasters();

    /** Whether files match when they hold the same lines in any order. */
    @Input
    public abstract Property<Boolean> getIgnoreLineOrder();

    @TaskAction
    void verify() throws IOException {
        Path actual = getActual().get().getAsFile().toPath();
        Path masters = getMasters().get().getAsFile().toPath();
        List<String> failures = compare(actual, masters, getIgnoreLineOrder().get());
        if (!failures.isEmpty()) {
            throw new GradleException(failures.size() + " file(s) differ from the masters in " + masters + ":\n"
                    + String.join("\n", failures));
        }
        getLogger().lifecycle("{} file(s) match", files(actual).size());
    }

    /** One message per file that differs, in path order. */
    static List<String> compare(Path actual, Path masters, boolean ignoreLineOrder) throws IOException {
        Set<String> names = new TreeSet<>(files(actual));
        names.addAll(files(masters));
        List<String> failures = new ArrayList<>();
        for (String name : names) {
            Path a = actual.resolve(name);
            Path m = masters.resolve(name);
            if (!Files.exists(m)) {
                failures.add(name + ": no master");
            } else if (!Files.exists(a)) {
                failures.add(name + ": not written");
            } else {
                List<String> al = Files.readAllLines(a, StandardCharsets.UTF_8);
                List<String> ml = Files.readAllLines(m, StandardCharsets.UTF_8);
                if (ignoreLineOrder ? new LinkedHashSet<>(al).equals(new LinkedHashSet<>(ml)) : al.equals(ml)) {
                    continue;
                }
                List<String> diff = new ArrayList<>();
                ml.stream().filter(l -> !al.contains(l)).forEach(l -> diff.add("- " + l));
                al.stream().filter(l -> !ml.contains(l)).forEach(l -> diff.add("+ " + l));
                if (diff.isEmpty()) {
                    diff.add("same lines in another order or count");
                }
                failures.add(name + ":\n  " + String.join("\n  ", diff));
            }
        }
        return failures;
    }

    private static List<String> files(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isRegularFile).map(p -> root.relativize(p).toString().replace('\\', '/')).toList();
        }
    }
}
