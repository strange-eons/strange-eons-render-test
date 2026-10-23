package org.strangeeons.gradle.rendertest;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * The platform name that selects a masters folder, and the description of
 * the environment a set of masters was rendered in.
 */
final class RenderEnvironment {

    /** File written next to the masters of each platform. */
    static final String FILE_NAME = "environment.properties";

    private RenderEnvironment() {
    }

    /** Platform folder name for an {@code os.name} value: linux, macos, windows, or a sanitized name. */
    static String platformFor(String osName) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            return "windows";
        }
        if (os.startsWith("mac") || os.startsWith("darwin")) {
            return "macos";
        }
        if (os.startsWith("linux")) {
            return "linux";
        }
        String s = os.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return s.isEmpty() ? "unknown" : s;
    }

    static String currentPlatform() {
        return platformFor(System.getProperty("os.name"));
    }

    /** Checks a user-supplied platform name is a single, safe folder name. */
    static String validatePlatform(String p) {
        if (p == null || !p.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("Render-test platform must be a simple folder name "
                    + "(letters, digits, '.', '_', '-'): " + p);
        }
        return p;
    }

    /** Describes the rendering environment; keys are stable for comparison. */
    static Map<String, String> describe(String platform, String javaVendor, String javaRuntimeVersion) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("platform", platform);
        m.put("os.name", System.getProperty("os.name"));
        m.put("os.arch", System.getProperty("os.arch"));
        m.put("java.vendor", javaVendor);
        m.put("java.runtime.version", javaRuntimeVersion);
        return m;
    }

    static void write(Path file, Map<String, String> env) throws IOException {
        // Written by hand rather than Properties.store so the file carries no
        // timestamp and only changes when the environment does.
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> e : env.entrySet()) {
                w.write(e.getKey() + "=" + e.getValue().replace("\\", "\\\\") + "\n");
            }
        }
    }

    static Map<String, String> read(Path file) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        Map<String, String> m = new LinkedHashMap<>();
        for (String k : p.stringPropertyNames()) {
            m.put(k, p.getProperty(k));
        }
        return m;
    }

    /** Human-readable differences between a recorded and a current environment. */
    static List<String> differences(Map<String, String> recorded, Map<String, String> current) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : current.entrySet()) {
            String was = recorded.get(e.getKey());
            if (was != null && !was.equals(e.getValue())) {
                out.add(e.getKey() + ": masters " + was + ", this run " + e.getValue());
            }
        }
        return out;
    }
}
