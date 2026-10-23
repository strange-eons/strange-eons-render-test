package org.strangeeons.gradle.rendertest;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;

/**
 * Builds the Strange Eons application from a checkout by running the
 * checkout's own Gradle wrapper ({@code :strange-eons-app:installDist}).
 * Always runs; the nested build decides what is up to date.
 */
public abstract class BuildStrangeEons extends DefaultTask {

    @Internal
    public abstract DirectoryProperty getCheckout();

    /** The installed distribution: {@code strange-eons-app/build/install/strange-eons-app}. */
    @OutputDirectory
    public abstract DirectoryProperty getInstallDir();

    @Nested
    public abstract Property<JavaLauncher> getLauncher();

    @Inject
    protected abstract ExecOperations getExec();

    public BuildStrangeEons() {
        getOutputs().upToDateWhen(t -> false);
    }

    @TaskAction
    void build() {
        File dir = getCheckout().get().getAsFile();
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        File wrapper = new File(dir, windows ? "gradlew.bat" : "gradlew");
        if (!wrapper.isFile()) {
            throw new GradleException("No Gradle wrapper in Strange Eons checkout " + dir
                    + ". Set strangeEonsRenderTest.strangeEonsDir or -PstrangeEonsDir.");
        }
        String javaHome = getLauncher().get().getMetadata().getInstallationPath().getAsFile().getAbsolutePath();
        List<String> cmd = new ArrayList<>();
        cmd.add(wrapper.getAbsolutePath());
        cmd.add("--quiet");
        cmd.add(":strange-eons-app:installDist");
        getExec().exec(spec -> {
            spec.setWorkingDir(dir);
            spec.setCommandLine(cmd);
            spec.environment("JAVA_HOME", javaHome);
        });
    }
}
