package org.strangeeons.gradle.rendertest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RenderComponentsTest {

    @Test
    void linuxWithoutDisplayNeedsVirtualDisplay() {
        assertTrue(RenderComponents.needsVirtualDisplay(false, "Linux", null));
        assertTrue(RenderComponents.needsVirtualDisplay(false, "Linux", ""));
    }

    @Test
    void linuxWithDisplayDoesNot() {
        assertFalse(RenderComponents.needsVirtualDisplay(false, "Linux", ":0"));
    }

    @Test
    void macAndWindowsDoNot() {
        assertFalse(RenderComponents.needsVirtualDisplay(false, "Mac OS X", null));
        assertFalse(RenderComponents.needsVirtualDisplay(false, "Windows 11", null));
    }

    @Test
    void headlessNeverDoes() {
        assertFalse(RenderComponents.needsVirtualDisplay(true, "Linux", null));
    }
}
