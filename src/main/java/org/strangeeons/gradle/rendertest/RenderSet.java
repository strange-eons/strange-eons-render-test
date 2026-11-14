package org.strangeeons.gradle.rendertest;

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

/**
 * Another set of renders with its own components, masters and settings,
 * checked like the main set. A set named {@code inkSaver} adds
 * {@code renderInkSaver}, {@code verifyInkSaverRenders} (part of
 * {@code check}) and {@code updateInkSaverRenderMasters}.
 */
public abstract class RenderSet implements Named {

    /** Saved components to render. */
    public abstract DirectoryProperty getComponents();

    /** Games whose class-map entries are rendered. Defaults to none. */
    public abstract ListProperty<String> getClassMapGames();

    /** Root of this set's masters, one subfolder per platform. Required. */
    public abstract DirectoryProperty getMasters();

    /** Preferences added to the extension's for this set. */
    public abstract MapProperty<String, String> getUserSettings();

    /** Defaults to the extension's {@code renderBacks}. */
    public abstract Property<Boolean> getRenderBacks();

    /** Defaults to the extension's {@code renderBleed}. */
    public abstract Property<Boolean> getRenderBleed();

    @Nested
    public abstract SharedMasters getSharedMasters();

    public void sharedMasters(Action<? super SharedMasters> action) {
        action.execute(getSharedMasters());
    }
}
