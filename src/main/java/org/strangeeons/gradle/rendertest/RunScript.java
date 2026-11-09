package org.strangeeons.gradle.rendertest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Runs a script in Strange Eons with the bundles under test. The script gets
 * {@link #getSystemProperties()}, {@code rt.results} (see
 * {@link StrangeEonsTask}) and, if {@link #getOutputDir()} is set,
 * {@code rt.out}, a folder emptied before the run. Without an output folder
 * the task runs every time.
 */
@DisableCachingByDefault(because = "output depends on the Strange Eons build")
public abstract class RunScript extends StrangeEonsTask {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getScript();

    /** Other files the script reads, such as modules it loads. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getScriptInputs();

    @Input
    public abstract MapProperty<String, String> getSystemProperties();

    @OutputDirectory
    @Optional
    public abstract DirectoryProperty getOutputDir();

    @TaskAction
    void runScript() throws IOException {
        List<File> bundles = bundles();
        Path work = getWorkDir().get().getAsFile().toPath();
        deleteTree(work);
        Files.createDirectories(work);
        Map<String, String> properties = new LinkedHashMap<>(getSystemProperties().get());
        if (getOutputDir().isPresent()) {
            Path out = getOutputDir().get().getAsFile().toPath();
            deleteTree(out);
            Files.createDirectories(out);
            properties.put("rt.out", out.toAbsolutePath().toString());
        }
        List<Fork> forks = List.of(new Fork(work, null, true));
        Path script = getScript().get().getAsFile().toPath();
        run(forks, script, bundles, f -> properties);
        Summary s = summarize(forks);
        for (String skip : s.skipped()) {
            getLogger().warn("Skipped {}", skip);
        }
        getLogger().lifecycle("{}: {} item(s) ok", script.getFileName(), s.ok());
        if (!s.failed().isEmpty()) {
            throw new GradleException(s.failed().size() + " item(s) failed:\n  " + String.join("\n  ", s.failed()));
        }
    }
}
