package org.strangeeons.gradle.rendertest;

import org.gradle.api.Named;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/**
 * A script whose text output, written to {@code rt.out}, is compared with
 * masters. A check named {@code defaults} adds {@code runDefaults},
 * {@code verifyDefaults} (part of {@code check}) and {@code updateDefaults}.
 */
public abstract class TextCheck implements Named {

    public abstract RegularFileProperty getScript();

    /** Other files the script reads. */
    public abstract ConfigurableFileCollection getScriptInputs();

    public abstract MapProperty<String, String> getSystemProperties();

    /** Folder of expected files. Required. */
    public abstract DirectoryProperty getMasters();

    /** Whether files match when they hold the same lines in any order. Defaults to false. */
    public abstract Property<Boolean> getIgnoreLineOrder();
}
