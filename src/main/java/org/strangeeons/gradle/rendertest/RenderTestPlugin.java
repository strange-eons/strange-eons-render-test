package org.strangeeons.gradle.rendertest;

import javax.inject.Inject;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

/**
 * Adds render-and-compare testing of Strange Eons components:
 *
 * <ul>
 * <li>{@code buildStrangeEons}: builds SE from the configured checkout</li>
 * <li>{@code renderComponents}: renders components to PNG</li>
 * <li>{@code verifyRenders}: compares renders with masters (part of {@code check})</li>
 * <li>{@code updateRenderMasters}: replaces masters with the current renders</li>
 * <li>the same three for each {@linkplain RenderSet render set}, and run, verify
 * and update tasks for each {@linkplain TextCheck text check}</li>
 * </ul>
 *
 * Every {@link StrangeEonsTask}, including {@link RunScript} tasks a build
 * adds, runs the SE build with the extension's bundles and settings unless it
 * sets its own.
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
        ext.getRenderBacks().convention(true);
        ext.getRenderBleed().convention(false);
        ext.getHeadless().convention(project.getProviders().gradleProperty("renderTestHeadless")
                .map(Boolean::parseBoolean).orElse(false));
        ext.getForks().convention(project.getProviders().gradleProperty("renderTestForks")
                .map(Integer::parseInt).orElse(1));
        ext.getLauncher().convention(getToolchains().launcherFor(spec ->
                spec.getLanguageVersion().set(JavaLanguageVersion.of(25))));

        TaskContainer tasks = project.getTasks();
        DirectoryProperty buildDir = project.getLayout().getBuildDirectory();

        TaskProvider<BuildStrangeEons> buildSe = tasks.register("buildStrangeEons", BuildStrangeEons.class, t -> {
            t.setGroup(GROUP);
            t.setDescription("Builds the Strange Eons application from the configured checkout.");
            t.getCheckout().set(ext.getStrangeEonsDir());
            t.getInstallDir().set(ext.getStrangeEonsDir().dir("strange-eons-app/build/install/strange-eons-app"));
            t.getLauncher().set(ext.getLauncher());
        });

        tasks.withType(StrangeEonsTask.class).configureEach(t -> {
            t.getApplicationClasspath().from(buildSe.flatMap(b -> b.getInstallDir().dir("lib"))
                    .map(d -> d.getAsFileTree().matching(p -> p.include("*.jar"))));
            t.getApplicationClasspath().builtBy(buildSe);
            t.getBundles().from(ext.getBundles());
            t.getGameLanguage().convention(ext.getGameLanguage());
            t.getUiLanguage().convention(ext.getUiLanguage());
            t.getJvmArgs().convention(ext.getJvmArgs());
            t.getHeadless().convention(ext.getHeadless());
            t.getUserSettings().convention(ext.getUserSettings());
            t.getLauncher().convention(ext.getLauncher());
            t.getWorkDir().convention(buildDir.dir("render-test/" + t.getName() + "/work"));
        });
        tasks.withType(RenderComponents.class).configureEach(t -> {
            t.getResolution().convention(ext.getResolution());
            t.getRenderBacks().convention(ext.getRenderBacks());
            t.getRenderBleed().convention(ext.getRenderBleed());
            t.getForks().convention(ext.getForks());
            t.getClassMapGames().convention(ext.getClassMapGames());
        });

        Provider<String> platform = ext.getPlatform().map(RenderEnvironment::validatePlatform);
        Provider<String> javaVendor = ext.getLauncher().map(l -> l.getMetadata().getVendor());
        Provider<String> javaRuntime = ext.getLauncher().map(l -> l.getMetadata().getJavaRuntimeVersion());
        tasks.withType(VerifyRenders.class).configureEach(t -> {
            t.getPlatform().convention(platform);
            t.getJavaVendor().convention(javaVendor);
            t.getJavaRuntimeVersion().convention(javaRuntime);
        });
        tasks.withType(UpdateRenderMasters.class).configureEach(t -> {
            t.getPlatform().convention(platform);
            t.getJavaVendor().convention(javaVendor);
            t.getJavaRuntimeVersion().convention(javaRuntime);
        });

        TaskProvider<RenderComponents> render = tasks.register("renderComponents", RenderComponents.class, t -> {
            t.setGroup(GROUP);
            t.setDescription("Renders components with Strange Eons and writes one PNG per sheet.");
            t.getComponents().set(ext.getComponents());
            t.getOutputDir().set(buildDir.dir("render-test/actual"));
            t.getWorkDir().set(buildDir.dir("render-test/work"));
        });
        TaskProvider<VerifyRenders> verify = registerChecks(tasks, render, ext.getMasters(), platform,
                "verifyRenders", "updateRenderMasters", "", buildDir.dir("render-test/diffs"), null);
        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME, t -> t.dependsOn(verify));

        ext.getRenderSets().all(set -> {
            String name = capitalize(set.getName());
            set.getRenderBacks().convention(ext.getRenderBacks());
            set.getRenderBleed().convention(ext.getRenderBleed());
            String dir = "render-test/" + set.getName();
            TaskProvider<RenderComponents> setRender = tasks.register("render" + name, RenderComponents.class, t -> {
                t.setGroup(GROUP);
                t.setDescription("Renders the " + set.getName() + " components.");
                t.getComponents().set(set.getComponents());
                t.getClassMapGames().set(set.getClassMapGames());
                t.getRenderBacks().set(set.getRenderBacks());
                t.getRenderBleed().set(set.getRenderBleed());
                t.getUserSettings().set(ext.getUserSettings());
                t.getUserSettings().putAll(set.getUserSettings());
                t.getOutputDir().set(buildDir.dir(dir + "/actual"));
                t.getWorkDir().set(buildDir.dir(dir + "/work"));
            });
            TaskProvider<VerifyRenders> setVerify = registerChecks(tasks, setRender, set.getMasters(), platform,
                    "verify" + name + "Renders", "update" + name + "RenderMasters", " " + set.getName(),
                    buildDir.dir(dir + "/diffs"), set.getSharedMasters());
            tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME, t -> t.dependsOn(setVerify));
        });

        ext.getTextChecks().all(check -> {
            String name = capitalize(check.getName());
            check.getIgnoreLineOrder().convention(false);
            String dir = "render-test/" + check.getName();
            TaskProvider<RunScript> run = tasks.register("run" + name, RunScript.class, t -> {
                t.setGroup(GROUP);
                t.setDescription("Runs the " + check.getName() + " script.");
                t.getScript().set(check.getScript());
                t.getScriptInputs().from(check.getScriptInputs());
                t.getSystemProperties().set(check.getSystemProperties());
                t.getOutputDir().set(buildDir.dir(dir + "/actual"));
                t.getWorkDir().set(buildDir.dir(dir + "/work"));
            });
            TaskProvider<VerifyTextMasters> checkVerify = tasks.register("verify" + name, VerifyTextMasters.class,
                    t -> {
                        t.setGroup(GROUP);
                        t.setDescription("Fails if the " + check.getName() + " output differs from its masters.");
                        t.getActual().set(run.flatMap(RunScript::getOutputDir));
                        t.getMasters().set(check.getMasters());
                        t.getIgnoreLineOrder().set(check.getIgnoreLineOrder());
                    });
            tasks.register("update" + name, Sync.class, t -> {
                t.setGroup(GROUP);
                t.setDescription("Replaces the " + check.getName() + " masters with the current output.");
                t.from(run.flatMap(RunScript::getOutputDir));
                t.into(check.getMasters());
            });
            tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME, t -> t.dependsOn(checkVerify));
        });
    }

    // Registers the verify and update tasks for one render task.
    private static TaskProvider<VerifyRenders> registerChecks(TaskContainer tasks,
            TaskProvider<RenderComponents> render, DirectoryProperty masters, Provider<String> platform,
            String verifyName, String updateName, String what, Provider<Directory> diffs, SharedMasters shared) {
        Provider<Directory> platformMasters = masters.dir(platform);
        TaskProvider<VerifyRenders> verify = tasks.register(verifyName, VerifyRenders.class, t -> {
            t.setGroup(GROUP);
            t.setDescription("Fails if any" + what + " render differs from this platform's masters.");
            t.getActual().set(render.flatMap(RenderComponents::getOutputDir));
            t.getMastersRoot().set(masters);
            t.getPlatformMasters().set(platformMasters);
            t.getDiffs().set(diffs);
            share(shared, t.getSharedMasters());
        });
        tasks.register(updateName, UpdateRenderMasters.class, t -> {
            t.setGroup(GROUP);
            t.setDescription("Replaces this platform's" + what + " masters with the current renders.");
            t.getActual().set(render.flatMap(RenderComponents::getOutputDir));
            t.getPlatformMasters().set(platformMasters);
            share(shared, t.getSharedMasters());
        });
        return verify;
    }

    private static void share(SharedMasters from, SharedMasters to) {
        if (from == null) {
            return;
        }
        to.getRenderSuffix().set(from.getRenderSuffix());
        to.getMasterSuffix().set(from.getMasterSuffix());
        to.getExcludes().set(from.getExcludes());
        to.getMasks().set(from.getMasks());
    }

    static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
