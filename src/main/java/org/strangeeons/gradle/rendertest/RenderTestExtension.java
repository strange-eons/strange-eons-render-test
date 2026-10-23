package org.strangeeons.gradle.rendertest;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaLauncher;

/**
 * Configuration for rendering Strange Eons components and comparing them with
 * reference masters. Registered as {@code strangeEonsRenderTest}.
 *
 * <pre>
 * strangeEonsRenderTest {
 *     strangeEonsDir = file("../strange-eons")
 *     pluginFolder = file("MyGame/MyGame")      // an SE plug-in project folder, or
 *     bundles.from(tasks.named("shadowJar"))   // bundles built some other way
 *     classMapGames.add("MYGAME")              // render a default of each class-map entry
 *     components = file("src/renderTest/components")  // and/or saved .eon files
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
     * An SE plug-in project folder (the folder holding {@code eons-plugin}
     * and {@code resources/}). When set, it is packaged into a bundle the way
     * SE's "Make Bundle" command does and added to the bundles under test.
     */
    public abstract DirectoryProperty getPluginFolder();

    /**
     * File name for the bundle packaged from {@link #getPluginFolder()},
     * including the extension that tells SE how to load it ({@code .seext},
     * {@code .seplugin}, {@code .selibrary}). Defaults to the folder name plus
     * the extension SE itself would guess.
     */
    public abstract Property<String> getPluginBundleName();

    /**
     * Bundle files to load, in addition to the one packaged from
     * {@link #getPluginFolder()}. Must include every library the plug-in
     * requires: SE does not resolve {@code catalog-requires} for bundles passed
     * on the command line.
     */
    public abstract ConfigurableFileCollection getBundles();

    /**
     * Folder of saved {@code .eon} files to render (searched recursively).
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

    /** Extra JVM arguments for the SE process. */
    public abstract ListProperty<String> getJvmArgs();
}
