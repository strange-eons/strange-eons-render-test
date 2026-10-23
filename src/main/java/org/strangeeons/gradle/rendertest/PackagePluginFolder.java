package org.strangeeons.gradle.rendertest;

import java.io.IOException;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Packages an SE plug-in project folder into a bundle; see {@link BundlePackager}. */
@CacheableTask
public abstract class PackagePluginFolder extends DefaultTask {

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getFolder();

    @OutputFile
    public abstract RegularFileProperty getBundle();

    @TaskAction
    void pack() throws IOException {
        BundlePackager.pack(getFolder().get().getAsFile().toPath(), getBundle().get().getAsFile().toPath());
    }
}
