package org.strangeeons.gradle.rendertest;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaLauncher;

/**
 * Configuration for rendering Strange Eons components and comparing them with
 * reference masters. Registered as {@code strangeEonsRenderTest}.
 *
 * <pre>
 * strangeEonsRenderTest {
 *     strangeEonsDir = file("../strange-eons")
 *     bundles.from(tasks.named("sePlugin"))    // the plug-in's bundle and its libraries
 *     classMapGames.add("MYGAME")              // render a default of each class-map entry
 *     components = file("src/renderTest/components")  // and/or saved .eon/.eont files
 * }
 * </pre>
 */
public abstract class RenderTestExtension {

    /**
     * The Strange Eons checkout to build and run. Defaults to the
     * {@code strangeEonsDir} Gradle property, or {@code ../strange-eons}.
     */
    public abstract DirectoryProperty getStrangeEonsDir();

    /**
     * Bundle files to load: the plug-in under test and every library it
     * requires, since SE does not resolve {@code catalog-requires} for bundles
     * passed on the command line. Required; this plug-in never builds them.
     * Each file's extension tells SE how to load it ({@code .seext},
     * {@code .seplugin}, {@code .selibrary}).
     */
    public abstract ConfigurableFileCollection getBundles();

    /**
     * Folder of saved {@code .eon} and {@code .eont} files to render (searched
     * recursively). Text saves need a Strange Eons build that reads them.
     * Optional.
     */
    public abstract DirectoryProperty getComponents();

    /**
     * Game codes whose class-map entries should each be rendered once with
     * default content, e.g. {@code AHLCG}. Only DIY and compiled entries are
     * supported; scripted entries are skipped. Empty by default.
     */
    public abstract ListProperty<String> getClassMapGames();

    /**
     * Root folder of reference images. Masters live in one subfolder per
     * {@linkplain #getPlatform() platform}, e.g. {@code masters/linux/}.
     * Defaults to {@code src/renderTest/masters}.
     */
    public abstract DirectoryProperty getMasters();

    /**
     * Which masters subfolder this run reads and writes. Defaults to the
     * {@code renderTestPlatform} Gradle property, else the current OS:
     * {@code linux}, {@code macos} or {@code windows}. Set it to keep
     * separate sets for environments on the same OS, e.g. {@code linux-ci}.
     */
    public abstract Property<String> getPlatform();

    /** Export resolution in pixels per inch. Defaults to 150. */
    public abstract Property<Integer> getResolution();

    /** Game language passed to SE as {@code --glang}. Defaults to {@code en}. */
    public abstract Property<String> getGameLanguage();

    /** Interface language passed to SE as {@code --ulang}. Defaults to {@code en}. */
    public abstract Property<String> getUiLanguage();

    /**
     * JVM used to build and run Strange Eons. Defaults to a Java 25 toolchain.
     * It must be a full JDK: the headless-only packages cannot open a display.
     */
    public abstract Property<JavaLauncher> getLauncher();

    /**
     * Whether to run Strange Eons without opening any windows. This requires a
     * Strange Eons build that supports headless {@code --run}. Defaults to
     * {@code -PrenderTestHeadless}, or else {@code false}.
     */
    public abstract Property<Boolean> getHeadless();

    /**
     * How many Strange Eons processes each render task starts. Saved
     * components are split between them. Defaults to
     * {@code -PrenderTestForks}, or else 1.
     */
    public abstract Property<Integer> getForks();

    /** Extra JVM arguments for the SE process. */
    public abstract ListProperty<String> getJvmArgs();

    /**
     * Whether to render the back faces of DIY components: the odd-numbered
     * sheets, since every DIY face style pairs each front with its back.
     * Turn off when backs repeat across components. Other components always
     * render every sheet. Defaults to {@code true}.
     */
    public abstract Property<Boolean> getRenderBacks();

    /**
     * SE preferences for the run, e.g. a plug-in option to render with.
     * Each run starts from an empty user folder, so nothing else from the
     * developer's own preferences applies.
     */
    public abstract MapProperty<String, String> getUserSettings();
}
