package org.strangeeons.gradle.rendertest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
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
     * Memory one comparison may need: two decoded sheets and a diff image.
     * Bounds how many run at once.
     */
    private static final long BYTES_PER_COMPARISON = 96L << 20;

    /**
     * Compares every PNG under {@code actual} with the same relative path
     * under {@code masters}, several at a time. Writes a diff image for each
     * mismatch to {@code diffs}. Results are in path order.
     */
    static List<Result> compare(Path actual, Path masters, Path diffs) throws IOException {
        TreeSet<String> names = new TreeSet<>();
        names.addAll(pngs(actual));
        names.addAll(pngs(masters));
        ForkJoinPool pool = new ForkJoinPool(parallelism(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory()));
        try {
            return pool.submit(() -> names.parallelStream().map(n -> {
                try {
                    return compare(n, actual, masters, diffs);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).toList()).get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof UncheckedIOException u) {
                throw u.getCause();
            }
            throw new IOException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } finally {
            pool.shutdown();
        }
    }

    /** Comparisons to run at once: one per processor, as memory allows. */
    static int parallelism(int processors, long maxMemory) {
        return (int) Math.max(1, Math.min(processors, maxMemory / BYTES_PER_COMPARISON));
    }

    private static Result compare(String n, Path actual, Path masters, Path diffs) throws IOException {
        Path a = actual.resolve(n);
        Path m = masters.resolve(n);
        if (!Files.exists(m)) {
            return new Result(n, Kind.NO_MASTER, "no master image");
        }
        if (!Files.exists(a)) {
            return new Result(n, Kind.NOT_RENDERED, "master exists but nothing was rendered");
        }
        BufferedImage ai = read(a);
        BufferedImage mi = read(m);
        int w = ai.getWidth();
        int h = ai.getHeight();
        if (w != mi.getWidth() || h != mi.getHeight()) {
            return new Result(n, Kind.SIZE_CHANGED, String.format("size %dx%d, master %dx%d",
                    w, h, mi.getWidth(), mi.getHeight()));
        }
        int[] ar = new int[w];
        int[] mr = new int[w];
        long differing = 0;
        for (int y = 0; y < h; ++y) {
            ai.getRGB(0, y, w, 1, ar, 0, w);
            mi.getRGB(0, y, w, 1, mr, 0, w);
            for (int x = 0; x < w; ++x) {
                if (ar[x] != mr[x]) {
                    ++differing;
                }
            }
        }
        if (differing == 0) {
            return new Result(n, Kind.MATCH, "");
        }
        BufferedImage diff = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; ++y) {
            ai.getRGB(0, y, w, 1, ar, 0, w);
            mi.getRGB(0, y, w, 1, mr, 0, w);
            for (int x = 0; x < w; ++x) {
                ar[x] = ar[x] != mr[x] ? 0xffff00ff : faded(ar[x]);
            }
            diff.setRGB(0, y, w, 1, ar, 0, w);
        }
        Path d = diffs.resolve(n);
        Files.createDirectories(d.getParent());
        ImageIO.write(diff, "png", d.toFile());
        double pct = 100.0 * differing / ((double) w * h);
        return new Result(n, Kind.DIFFERENT, String.format("%d pixels differ (%.3f%%)", differing, pct));
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
