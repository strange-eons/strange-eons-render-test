package org.strangeeons.gradle.rendertest;

import java.awt.Rectangle;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * Renders checked against another render's master instead of their own, such
 * as backs that repeat their fronts. Their own masters are not recorded.
 *
 * <pre>
 * sharedMasters {
 *     renderSuffix = "-1.png"                 // a back ...
 *     masterSuffix = "-0.png"                 // ... is checked against its front
 *     excludes.add("components/key/**")      // backs with masters of their own
 *     mask("components/enemy/*", 670, 1018, 50, 26)  // where back and front differ
 * }
 * </pre>
 */
public abstract class SharedMasters {

    @Input
    @Optional
    public abstract Property<String> getRenderSuffix();

    @Input
    @Optional
    public abstract Property<String> getMasterSuffix();

    /** Globs of render paths that keep their own masters. */
    @Input
    public abstract ListProperty<String> getExcludes();

    /**
     * Regions left out when a render is checked against a shared master, by
     * glob of render path; the first match, in the order added, applies.
     */
    @Input
    public abstract MapProperty<String, Rectangle> getMasks();

    /** Adds a mask for renders matching {@code glob}. */
    public void mask(String glob, int x, int y, int width, int height) {
        getMasks().put(glob, new Rectangle(x, y, width, height));
    }

    MasterPlan plan() {
        return new MasterPlan(getRenderSuffix().getOrNull(), getMasterSuffix().getOrNull(), getExcludes().get(),
                getMasks().get());
    }
}
