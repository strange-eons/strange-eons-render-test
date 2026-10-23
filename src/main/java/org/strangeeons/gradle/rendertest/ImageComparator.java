package org.strangeeons.gradle.rendertest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * Pixel-exact comparison of rendered images against masters.
 */
final class ImageComparator {

    /** Outcome for one image path (relative to the compared folders). */
    record Result(String path, Kind kind, String detail) {
    }

    enum Kind {
        MATCH, DIFFERENT, SIZE_CHANGED, NO_MASTER, NOT_RENDERED
    }

    private ImageComparator() {
    }

    /**
     * Compares every PNG under {@code actual} with the same relative path
     * under {@code masters}. Writes a diff image for each mismatch to
     * {@code diffs}.
     */
    static List<Result> compare(Path actual, Path masters, Path diffs) throws IOException {
        TreeSet<String> names = new TreeSet<>();
        names.addAll(pngs(actual));
        names.addAll(pngs(masters));
        List<Result> out = new ArrayList<>();
        for (String n : names) {
            Path a = actual.resolve(n);
            Path m = masters.resolve(n);
            if (!Files.exists(m)) {
                out.add(new Result(n, Kind.NO_MASTER, "no master image"));
                continue;
            }
            if (!Files.exists(a)) {
                out.add(new Result(n, Kind.NOT_RENDERED, "master exists but nothing was rendered"));
                continue;
            }
            BufferedImage ai = read(a);
            BufferedImage mi = read(m);
            if (ai.getWidth() != mi.getWidth() || ai.getHeight() != mi.getHeight()) {
                out.add(new Result(n, Kind.SIZE_CHANGED, String.format("size %dx%d, master %dx%d",
                        ai.getWidth(), ai.getHeight(), mi.getWidth(), mi.getHeight())));
                continue;
            }
            BufferedImage diff = new BufferedImage(ai.getWidth(), ai.getHeight(), BufferedImage.TYPE_INT_ARGB);
            long differing = 0;
            for (int y = 0; y < ai.getHeight(); ++y) {
                for (int x = 0; x < ai.getWidth(); ++x) {
                    int ap = ai.getRGB(x, y);
                    int mp = mi.getRGB(x, y);
                    if (ap != mp) {
                        ++differing;
                        diff.setRGB(x, y, 0xffff00ff);
                    } else {
                        diff.setRGB(x, y, faded(ap));
                    }
                }
            }
            if (differing == 0) {
                out.add(new Result(n, Kind.MATCH, ""));
            } else {
                Path d = diffs.resolve(n);
                Files.createDirectories(d.getParent());
                ImageIO.write(diff, "png", d.toFile());
                double pct = 100.0 * differing / ((double) ai.getWidth() * ai.getHeight());
                out.add(new Result(n, Kind.DIFFERENT, String.format("%d pixels differ (%.3f%%)", differing, pct)));
            }
        }
        return out;
    }

    /** Greyed, translucent copy of a pixel so magenta differences stand out. */
    private static int faded(int argb) {
        int r = (argb >> 16) & 0xff, g = (argb >> 8) & 0xff, b = argb & 0xff;
        int grey = (r * 30 + g * 59 + b * 11) / 100;
        grey = 128 + grey / 2;
        return 0x60000000 | (grey << 16) | (grey << 8) | grey;
    }

    private static BufferedImage read(Path p) throws IOException {
        BufferedImage i = ImageIO.read(p.toFile());
        if (i == null) {
            throw new IOException("not a readable image: " + p);
        }
        return i;
    }

    private static List<String> pngs(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .toList();
        }
    }
}
