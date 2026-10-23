package org.strangeeons.gradle.rendertest;

import java.io.IOException;
import java.io.UncheckedIOException;
import javax.inject.Inject;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

/**
 * Adds render-and-compare testing of Strange Eons components:
 *
 * <ul>
 * <li>{@code buildStrangeEons}: builds SE from the configured checkout</li>
 * <li>{@code packagePluginFolder}: bundles the SE plug-in project folder</li>
 * <li>{@code renderComponents}: renders components to PNG</li>
 * <li>{@code verifyRenders}: compares renders with masters (part of {@code check})</li>
 * <li>{@code updateRenderMasters}: replaces masters with the current renders</li>
 * </ul>
 */
public abstract class RenderTestPlugin implements Plugin<Project> {

    public static final String GROUP = "render test";

    @Inject
    protected abstract JavaToolchainService getToolchains();

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(BasePlugin.class);
        RenderTestExtension ext = project.getExtensions().create("strangeEonsRenderTest", RenderTestExtension.class);

        ext.getStrangeEonsDir().convention(project.getLayout().getProjectDirectory().dir(
                project.getProviders().gradleProperty("strangeEonsDir").orElse("../strange-eons")));
        ext.getMasters().convention(project.getLayout().getProjectDirectory().dir("src/renderTest/masters"));
        ext.getPlatform().convention(project.getProviders().gradleProperty("renderTestPlatform")
                .orElse(RenderEnvironment.currentPlatform()));
        ext.getResolution().convention(150);
        ext.getGameLanguage().convention("en");
        ext.getUiLanguage().convention("en");
        ext.getLauncher().convention(getToolchains().launcherFor(spec ->
                spec.getLanguageVersion().set(JavaLanguageVersion.of(25))));

        DirectoryProperty buildDir = project.getLayout().getBuildDirectory();

        TaskProvider<BuildStrangeEons> buildSe = project.getTasks().register("buildStrangeEons", BuildStrangeEons.class, t -> {
            t.setGroup(GROUP);
            t.setDescription("Builds the Strange Eons application from the configured checkout.");
            t.getCheckout().set(ext.getStrangeEonsDir());
            t.getInstallDir().set(ext.getStrangeEonsDir().dir("strange-eons-app/build/install/strange-eons-app"));
            t.getLauncher().set(ext.getLauncher());
        });

        TaskProvider<PackagePluginFolder> pack = project.getTasks().register("packagePluginFolder", PackagePluginFolder.class, t -> {
            t.setGroup(GROUP);
            t.setDescription("Packages the SE plug-in project folder into a bundle.");
            t.onlyIf("pluginFolder is set", x -> ext.getPluginFolder().isPresent());
            t.getFolder().set(ext.getPluginFolder());
            Provider<String> name = ext.getPluginBundleName().orElse(ext.getPluginFolder().map(f -> {
                try {
                    return f.getAsFile().getName() + BundlePackager.guessExtension(f.getAsFile().toPath());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }));
            t.getBundle().set(buildDir.dir("render-test/bundles").zip(name, Directory::file));
        });

        TaskProvider<RenderComponents> render = project.getTasks().register("renderComponents", RenderComponents.class, t -> {
            t.setGroup(GROUP);
            t.setDescription("Renders components with Strange Eons and writes one PNG per sheet.");
            t.getApplicationClasspath().from(buildSe.flatMap(b -> b.getInstallDir().dir("lib"))
                    .map(d -> d.getAsFileTree().matching(p -> p.include("*.jar"))));
            t.getApplicationClasspath().builtBy(buildSe);
            t.getBundles().from(ext.getPluginFolder().flatMap(f -> pack.flatMap(PackagePluginFolder::getBundle)));
            t.getBundles().builtBy(pack);
            t.getBundles().from(ext.getBundles());
            t.getComponents().set(ext.getComponents());
            t.getClassMapGames().set(ext.getClassMapGames());
            t.getResolution().set(ext.getResolution());
            t.getGameLanguage().set(ext.getGameLanguage());
            t.getUiLanguage().set(ext.getUiLanguage());
            t.getJvmArgs().set(ext.getJvmArgs());
            t.getUserSettings().set(ext.getUserSettings());
            t.getLauncher().set(ext.getLauncher());
            t.getOutputDir().set(buildDir.dir("render-test/actual"));
            t.getWorkDir().set(buildDir.dir("render-test/work"));
        });

        Provider<String> platform = ext.getPlatform().map(RenderEnvironment::validatePlatform);
        Provider<Directory> platformMasters = ext.getMasters().dir(platform);
        Provider<String> javaVendor = ext.getLauncher().map(l -> l.getMetadata().getVendor());
        Provider<String> javaRuntime = ext.getLauncher().map(l -> l.getMetadata().getJavaRuntimeVersion());

        TaskProvider<VerifyRenders> verify = project.getTasks().register("verifyRenders", VerifyRenders.class, t -> {
            t.setGroup(GROUP);
            t.setDescription("Fails if any render differs from this platform's masters.");
            t.getActual().set(render.flatMap(RenderComponents::getOutputDir));
            t.getMastersRoot().set(ext.getMasters());
            t.getPlatformMasters().set(platformMasters);
            t.getPlatform().set(platform);
            t.getJavaVendor().set(javaVendor);
            t.getJavaRuntimeVersion().set(javaRuntime);
            t.getDiffs().set(buildDir.dir("render-test/diffs"));
        });

        project.getTasks().register("updateRenderMasters", UpdateRenderMasters.class, t -> {
            t.setGroup(GROUP);
            t.setDescription("Replaces this platform's masters with the current renders.");
            t.getActual().set(render.flatMap(RenderComponents::getOutputDir));
            t.getPlatformMasters().set(platformMasters);
            t.getPlatform().set(platform);
            t.getJavaVendor().set(javaVendor);
            t.getJavaRuntimeVersion().set(javaRuntime);
        });

        project.getTasks().named(LifecycleBasePlugin.CHECK_TASK_NAME, t -> t.dependsOn(verify));
    }
}
