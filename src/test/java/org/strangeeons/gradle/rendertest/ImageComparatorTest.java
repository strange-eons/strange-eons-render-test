package org.strangeeons.gradle.rendertest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageComparatorTest {

    @TempDir
    Path tmp;

    private void png(String rel, int w, int h, int rgb) throws IOException {
        BufferedImage i = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; ++y) {
            for (int x = 0; x < w; ++x) {
                i.setRGB(x, y, rgb);
            }
        }
        Path p = tmp.resolve(rel);
        Files.createDirectories(p.getParent());
        ImageIO.write(i, "png", p.toFile());
    }

    @Test
    void classifiesEveryCase() throws IOException {
        png("actual/same.png", 4, 4, 0xff112233);
        png("masters/same.png", 4, 4, 0xff112233);
        png("actual/sub/changed.png", 4, 4, 0xff112233);
        png("masters/sub/changed.png", 4, 4, 0xff112234);
        png("actual/resized.png", 4, 5, 0xff000000);
        png("masters/resized.png", 4, 4, 0xff000000);
        png("actual/new.png", 2, 2, 0xff000000);
        png("masters/gone.png", 2, 2, 0xff000000);

        List<ImageComparator.Result> r = ImageComparator.compare(tmp.resolve("actual"), tmp.resolve("masters"), tmp.resolve("diffs"));
        Map<String, ImageComparator.Kind> kinds = r.stream().collect(Collectors.toMap(ImageComparator.Result::path, ImageComparator.Result::kind));

        assertEquals(Map.of(
                "same.png", ImageComparator.Kind.MATCH,
                "sub/changed.png", ImageComparator.Kind.DIFFERENT,
                "resized.png", ImageComparator.Kind.SIZE_CHANGED,
                "new.png", ImageComparator.Kind.NO_MASTER,
                "gone.png", ImageComparator.Kind.NOT_RENDERED), kinds);
        assertTrue(Files.exists(tmp.resolve("diffs/sub/changed.png")));
    }

    @Test
    void missingMastersFolderMeansNoMasters() throws IOException {
        png("actual/a.png", 1, 1, 0xff000000);
        List<ImageComparator.Result> r = ImageComparator.compare(tmp.resolve("actual"), tmp.resolve("nope"), tmp.resolve("diffs"));
        assertEquals(ImageComparator.Kind.NO_MASTER, r.get(0).kind());
    }
}
