package org.strangeeons.gradle.rendertest;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * Packages an SE plug-in project folder into a bundle the way Strange Eons'
 * "Make Bundle" task does ({@code ca.cgjennings.apps.arkham.project.MakeBundle}):
 * a JAR stream preceded by SE's 4-byte web-safe header, containing every
 * project member except those SE hides or excludes.
 *
 * <p>Not reproduced: compiling {@code .java} sources (SE runs its "compile
 * all" step first) and touching the catalogue ID.
 */
final class BundlePackager {

    /** SE's web-safe bundle header ({@code ProjectUtilities.createPluginBundleArchive}). */
    static final byte[] WEB_SAFE_HEADER = {(byte) 0x07, (byte) 0x88, (byte) 0x53, (byte) 0x45};

    /** Default value of SE's {@code exclude-from-projects} setting. */
    private static final List<String> EXCLUDE_GLOBS = List.of("Thumbs.db", "_vti_cnf", ".hg*", ".svn*", ".git*");
    private static final List<Pattern> EXCLUDES = EXCLUDE_GLOBS.stream().map(BundlePackager::glob).toList();

    /** Fixed entry time so identical inputs produce identical bundles. */
    private static final long ENTRY_TIME = 315532800000L; // 1980-01-01T00:00:00Z

    private BundlePackager() {
    }

    static void pack(Path folder, Path bundle) throws IOException {
        Files.createDirectories(bundle.toAbsolutePath().getParent());
        try (OutputStream raw = new BufferedOutputStream(Files.newOutputStream(bundle))) {
            raw.write(WEB_SAFE_HEADER);
            try (JarOutputStream jar = new JarOutputStream(raw)) {
                for (Path p : members(folder)) {
                    String name = folder.relativize(p).toString().replace('\\', '/');
                    if (Files.isDirectory(p)) {
                        name += '/';
                    }
                    ZipEntry e = new ZipEntry(name);
                    e.setTime(ENTRY_TIME);
                    jar.putNextEntry(e);
                    if (!Files.isDirectory(p)) {
                        Files.copy(p, jar);
                    }
                    jar.closeEntry();
                }
            }
        }
    }

    /** All files and folders SE would pack, depth-first in sorted order. */
    static List<Path> members(Path folder) throws IOException {
        List<Path> out = new ArrayList<>();
        collect(folder, out);
        return out;
    }

    private static void collect(Path dir, List<Path> out) throws IOException {
        List<Path> kids;
        try (Stream<Path> s = Files.list(dir)) {
            kids = s.filter(BundlePackager::isMember).sorted().toList();
        }
        for (Path k : kids) {
            out.add(k);
            if (Files.isDirectory(k)) {
                collect(k, out);
            }
        }
    }

    /** Mirrors {@code Member.FILE_FILTER}. */
    static boolean isMember(Path p) {
        String name = p.getFileName().toString();
        try {
            if (Files.isHidden(p)) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        if (name.isEmpty() || name.charAt(0) == '.' || name.equals("seproject") || name.endsWith(".ts.js")) {
            return false;
        }
        for (Pattern x : EXCLUDES) {
            if (x.matcher(name).matches()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Guesses the bundle extension the way {@code MakeBundle.guessPluginType}
     * does: no root file means a library; a scripted plug-in whose script uses
     * the {@code extension} library (or returns {@code EXTENSION}) is an
     * extension; a class-based one is an extension unless it names a theme.
     */
    static String guessExtension(Path folder) throws IOException {
        Path root = folder.resolve("eons-plugin");
        if (!Files.isRegularFile(root)) {
            return ".selibrary";
        }
        String first = firstPluginIdentifier(Files.readAllLines(root, StandardCharsets.ISO_8859_1));
        if (first == null) {
            return ".selibrary";
        }
        String id = first;
        if (id.endsWith(".js") && !id.startsWith("class:") && !id.startsWith("script:")) {
            id = "script:" + id;
        }
        if (id.startsWith("res:")) {
            id = "script:" + id;
        }
        if (id.startsWith("script:")) {
            String path = id.substring("script:".length());
            if (path.startsWith("res://")) {
                path = path.substring("res://".length());
                path = path.startsWith("/") ? path.substring(1) : "resources/" + path;
            }
            Path script = folder.resolve(path);
            if (Files.isRegularFile(script)) {
                String text = Files.readString(script, StandardCharsets.UTF_8);
                if (Pattern.compile("return\\s+(\\p{Alnum}+\\.)*EXTENSION").matcher(text).find()
                        || Pattern.compile("use[lL]ibrary\\s*\\(\\s*['\"]extension['\"]").matcher(text).find()) {
                    return ".seext";
                }
            }
            return ".seplugin";
        }
        return id.toLowerCase(Locale.ROOT).contains("theme") ? ".setheme" : ".seext";
    }

    /**
     * Returns the first plug-in line of a root file: a non-blank,
     * non-comment line that is not a {@code key = value} pair or its
     * continuation.
     */
    static String firstPluginIdentifier(List<String> lines) {
        boolean continuation = false;
        for (String raw : lines) {
            String line = raw.strip();
            boolean wasContinuation = continuation;
            continuation = line.endsWith("\\");
            if (wasContinuation || line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            if (line.matches("[^:=\\s]+\\s*[=:].*") && !line.startsWith("res:") && !line.startsWith("script:")
                    && !line.startsWith("class:")) {
                continue;
            }
            return line;
        }
        return null;
    }

    private static Pattern glob(String g) {
        StringBuilder b = new StringBuilder();
        for (char c : g.toCharArray()) {
            switch (c) {
                case '*' -> b.append(".*");
                case '?' -> b.append('.');
                default -> b.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(b.toString(), Pattern.CASE_INSENSITIVE);
    }
}
