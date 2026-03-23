package rvd.render;

import javafx.scene.image.Image;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import rvd.RVDColor;
import xyz.marsavic.geometry.Box;
import xyz.marsavic.geometry.Transformation;
import xyz.marsavic.geometry.Vector;

import java.nio.IntBuffer;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

public class RasterDiagramRenderer {

    public record Classification(int index, int visibleCount, double angle) {}

    @FunctionalInterface
    public interface Classifier {
        Classification classify(Vector p);
    }

    @FunctionalInterface
    public interface Colorizer {
        RVDColor color(Classification classification);
    }

    @FunctionalInterface
    public interface Observer {
        void observe(Classification classification, Vector p);
    }

    private static final int N_BITS = 3;
    private static final int N_VALUES = 1 << N_BITS;

    private int[] pixels;
    private int sizeYp = 0;
    private int sizeXp = 0;
    private byte[][] bestI;
    private byte[][] bestDepth;
    private float[][] bestA;

    public Image render(
            Transformation tFromPixels,
            Box bImage,
            Classifier classifier,
            Colorizer colorizer,
            Observer observer,
            boolean trackMaxAngleObservation,
            IntPredicate eligibleColoredForMaxAngle,
            boolean snapMaxAngleToSkeleton
    ) {
        Vector diag = bImage.d().abs();
        int sizeX = diag.xInt();
        int sizeY = diag.yInt();

        if (isEmptyImage(sizeX, sizeY)) {
            return null;
        }

        ensureBuffers(sizeX, sizeY);
        computeNearestPass(tFromPixels, sizeX, sizeY, classifier);
        if (trackMaxAngleObservation) {
            emitDeterministicMaxAngleObservation(
                    tFromPixels,
                    sizeX,
                    sizeY,
                    observer,
                    eligibleColoredForMaxAngle,
                    snapMaxAngleToSkeleton
            );
        }
        computeAntialiasPass(tFromPixels, sizeX, sizeY, classifier, colorizer);
        return buildWritableImage(sizeX, sizeY);
    }

    private void ensureBuffers(int sizeX, int sizeY) {
        if ((sizeYp < sizeY) || (sizeXp < sizeX)) {
            sizeYp = sizeY;
            sizeXp = sizeX;
            bestI = new byte[sizeY][sizeX];
            bestDepth = new byte[sizeY][sizeX];
            bestA = new float[sizeY][sizeX];
            pixels = new int[sizeY * sizeX];
        }
    }

    private void computeNearestPass(
            Transformation tFromPixels,
            int sizeX,
            int sizeY,
            Classifier classifier
    ) {
        IntStream.range(0, sizeY).parallel().forEach(y -> {
            for (int x = 0; x < sizeX; x++) {
                Vector cPixel = Vector.xy(x + 0.5, y + 0.5);
                Vector p = tFromPixels.applyTo(cPixel);
                Classification classification = classifier.classify(p);
                bestI[y][x] = (byte) classification.index();
                bestDepth[y][x] = (byte) classification.visibleCount();
                bestA[y][x] = (float) classification.angle();
            }
        });
    }

    /**
     * Argmax angle over colored cells only; tie-break smallest y, then x. When {@code snapMaxAngleToSkeleton},
     * draws the marker at the skeleton pixel ({@code -1}) closest in world space to that colored optimum
     */
    private void emitDeterministicMaxAngleObservation(
            Transformation tFromPixels,
            int sizeX,
            int sizeY,
            Observer observer,
            IntPredicate eligibleColoredForMaxAngle,
            boolean snapMaxAngleToSkeleton
    ) {
        double bestAngle = Double.NEGATIVE_INFINITY;
        int bestX = -1;
        int bestY = -1;

        for (int y = 0; y < sizeY; y++) {
            for (int x = 0; x < sizeX; x++) {
                int i = bestI[y][x];
                if (!eligibleColoredForMaxAngle.test(i)) {
                    continue;
                }
                double a = bestA[y][x];
                if (a > bestAngle) {
                    bestAngle = a;
                    bestX = x;
                    bestY = y;
                }
            }
        }

        if (bestX < 0) {
            return;
        }

        Vector pColored = tFromPixels.applyTo(Vector.xy(bestX + 0.5, bestY + 0.5));

        int outX = bestX;
        int outY = bestY;
        if (snapMaxAngleToSkeleton) {
            double bestD2 = Double.POSITIVE_INFINITY;
            int skX = -1;
            int skY = -1;
            for (int y = 0; y < sizeY; y++) {
                for (int x = 0; x < sizeX; x++) {
                    if (bestI[y][x] != -1) {
                        continue;
                    }
                    Vector pSk = tFromPixels.applyTo(Vector.xy(x + 0.5, y + 0.5));
                    double d2 = pSk.sub(pColored).lengthSquared();
                    if (d2 < bestD2) {
                        bestD2 = d2;
                        skX = x;
                        skY = y;
                    }
                }
            }
            if (skX >= 0) {
                outX = skX;
                outY = skY;
            }
        }

        Vector p = tFromPixels.applyTo(Vector.xy(outX + 0.5, outY + 0.5));
        Classification classification = new Classification(
                bestI[bestY][bestX],
                bestDepth[bestY][bestX],
                bestA[bestY][bestX]
        );
        observer.observe(classification, p);
    }

    private void computeAntialiasPass(
            Transformation tFromPixels,
            int sizeX,
            int sizeY,
            Classifier classifier,
            Colorizer colorizer
    ) {
        IntStream.range(0, sizeY).parallel().forEach(y -> {
            for (int x = 0; x < sizeX; x++) {
                int bI = bestI[y][x];

                boolean shouldAntialias = false;
                if ((x > 0) && (bI != bestI[y][x - 1])) shouldAntialias = true;
                if ((x < sizeX - 1) && (bI != bestI[y][x + 1])) shouldAntialias = true;
                if ((y > 0) && (bI != bestI[y - 1][x])) shouldAntialias = true;
                if ((y < sizeY - 1) && (bI != bestI[y + 1][x])) shouldAntialias = true;

                int code;
                if (!shouldAntialias) {
                    Classification classification = new Classification(
                            bI,
                            bestDepth[y][x],
                            bestA[y][x]
                    );
                    code = colorizer.color(classification).code();
                } else {
                    RVDColor sum = new RVDColor();
                    for (int idx = 0; idx < N_VALUES; idx++) {
                        int idy = Integer.reverse(idx) >>> (32 - N_BITS); // preserved ordering from original logic

                        Vector cPixel = Vector.xy(x + (idx + 0.5) / N_VALUES, y + (idy + 0.5) / N_VALUES);
                        Vector p = tFromPixels.applyTo(cPixel);
                        Classification classification = classifier.classify(p);
                        sum = sum.add(colorizer.color(classification));
                    }
                    code = sum.mul(1.0 / N_VALUES).code();
                }

                pixels[y * sizeX + x] = code;
            }
        });
    }

    private boolean isEmptyImage(int sizeX, int sizeY) {
        return sizeX == 0 || sizeY == 0;
    }

    private Image buildWritableImage(int sizeX, int sizeY) {
        PixelFormat<IntBuffer> pixelFormat = PixelFormat.getIntArgbPreInstance();
        IntBuffer buffer = IntBuffer.wrap(pixels);
        PixelBuffer<IntBuffer> pixelBuffer = new PixelBuffer<>(sizeX, sizeY, buffer, pixelFormat);
        pixelBuffer.updateBuffer(pb -> null);
        return new WritableImage(pixelBuffer);
    }
}
