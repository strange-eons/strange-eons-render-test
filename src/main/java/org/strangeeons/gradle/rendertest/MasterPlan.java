package org.strangeeons.gradle.rendertest;

import java.awt.Rectangle;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Map;

/**
 * Which master each render is checked against. By default a render's master
 * has the same path. A render ending in {@code renderSuffix}, unless
 * excluded, shares the master of the render ending in {@code masterSuffix}
 * instead, such as a back that repeats its front; the first matching mask
 * hides the region where they may differ, in both images.
 */
final class MasterPlan {

    static final MasterPlan SAME_PATH = new MasterPlan(null, null, List.of(), Map.of());

    private final String renderSuffix;
    private final String masterSuffix;
    private final List<PathMatcher> excludes;
    private final List<Map.Entry<PathMatcher, Rectangle>> masks;

    MasterPlan(String renderSuffix, String masterSuffix, List<String> excludes, Map<String, Rectangle> masks) {
        if ((renderSuffix == null) != (masterSuffix == null)) {
            throw new IllegalArgumentException("Set both renderSuffix and masterSuffix, or neither");
        }
        this.renderSuffix = renderSuffix;
        this.masterSuffix = masterSuffix;
        this.excludes = excludes.stream().map(MasterPlan::glob).toList();
        this.masks = masks.entrySet().stream().map(e -> Map.entry(glob(e.getKey()), e.getValue())).toList();
    }

    /** Whether the render shares another render's master. */
    boolean isShared(String render) {
        return renderSuffix != null && render.endsWith(renderSuffix)
                && excludes.stream().noneMatch(m -> m.matches(Path.of(render)));
    }

    /** Path of the master the render is checked against. */
    String masterFor(String render) {
        return isShared(render)
                ? render.substring(0, render.length() - renderSuffix.length()) + masterSuffix
                : render;
    }

    /** Region left out of the comparison, or null. */
    Rectangle maskFor(String render) {
        if (!isShared(render)) {
            return null;
        }
        return masks.stream().filter(e -> e.getKey().matches(Path.of(render))).map(Map.Entry::getValue)
                .findFirst().orElse(null);
    }

    private static PathMatcher glob(String pattern) {
        return FileSystems.getDefault().getPathMatcher("glob:" + pattern);
    }
}
