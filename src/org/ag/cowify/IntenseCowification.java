package org.ag.cowify;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/*
 Code _heavily_ based on https://gist.github.com/codebox/1233090
 */
public class IntenseCowification {
    private static final String TILES_DIR = "C:\\Users\\Andrew\\workspace\\elaine\\tiles";
    private static final String INPUT_IMG = "C:\\Users\\Andrew\\workspace\\elaine\\elaine.jpg";
    private static final String OUTPUT_IMG = "output.png";
    private static final int TILE_WIDTH = 300;
    private static final int TILE_HEIGHT = 300;
    private static final int TILE_SCALE = 50; // Basically make this as high as possible without getting an exception for best quality
    private static final boolean IS_BW = false;
    private static final int THREADS = 2;

    private static void log(String msg) {
        System.out.println(msg);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
//        log("Scaling images...");
//        scaleImages(new File(TILES_DIR));

        log("Reading tiles...");
        final Collection<Tile> tileImages = getImagesFromTiles(new File(TILES_DIR));

        log("Processing input image...");
        File inputImageFile = new File(INPUT_IMG);
        Collection<BufferedImagePart> inputImageParts = getImagesFromInput(inputImageFile);
        final Collection<BufferedImagePart> outputImageParts = Collections.synchronizedSet(new HashSet<>());

        ExecutorService newFixedThreadPool = Executors.newFixedThreadPool(THREADS);

        final AtomicInteger i = new AtomicInteger();
        final int partCount = inputImageParts.size();
        for (final BufferedImagePart inputImagePart : inputImageParts) {
            newFixedThreadPool.execute(() -> {
                Tile bestFitTile = getBestFitTile(inputImagePart.image, tileImages);
                log(String.format("Matching part %s of %s", i.incrementAndGet(), partCount));
                outputImageParts.add(new BufferedImagePart(bestFitTile.image, inputImagePart.x, inputImagePart.y));
            });
        }

        newFixedThreadPool.shutdown();
        newFixedThreadPool.awaitTermination(10000000, TimeUnit.SECONDS);

        log("Writing output image...");
        BufferedImage inputImage = ImageIO.read(inputImageFile);
        int width = inputImage.getWidth();
        int height = inputImage.getHeight();
        log("q");
        BufferedImage output = makeOutputImage(width, height, outputImageParts);
        log("r");
        ImageIO.write(output, "png", new File(OUTPUT_IMG));
        log("FINISHED");
    }

    private static BufferedImage makeOutputImage(int width, int height, Collection<BufferedImagePart> parts) {
        BufferedImage image = new BufferedImage(width * TILE_SCALE, height * TILE_SCALE, BufferedImage.TYPE_3BYTE_BGR);

        int count = 0;
        for (BufferedImagePart part : parts) {
            System.out.println((double) count / (double) parts.size() * (double) 100 + "% saved.");
            BufferedImage imagePart = image.getSubimage(part.x * TILE_SCALE, part.y * TILE_SCALE, TILE_WIDTH, TILE_HEIGHT);
            imagePart.setData(part.image.getData());
            count++;
        }

        return image;
    }

    private static Tile getBestFitTile(BufferedImage target, Collection<Tile> tiles) {
        Tile bestFit = null;
        int bestFitScore = -1;

        for (Tile tile : tiles) {
            int score = getScore(target, tile);
            if (score > bestFitScore) {
                bestFitScore = score;
                bestFit = tile;
            }
        }

        return bestFit;
    }

    private static int getScore(BufferedImage target, Tile tile) {
        assert target.getHeight() == Tile.SCALED_HEIGHT;
        assert target.getWidth() == Tile.SCALED_WIDTH;

        int total = 0;
        for (int x = 0; x < Tile.SCALED_WIDTH; x++) {
            for (int y = 0; y < Tile.SCALED_HEIGHT; y++) {
                int targetPixel = target.getRGB(x, y);
                Pixel candidatePixel = tile.pixels[x][y];
                int diff = getDiff(targetPixel, candidatePixel);
                int score;
                if (IS_BW) {
                    score = 255 - diff;
                } else {
                    score = 255 * 3 - diff;
                }

                total += score;
            }
        }

        return total;
    }

    private static int getDiff(int target, Pixel candidate) {
        if (IS_BW) {
            return Math.abs(getRed(target) - candidate.r);
        } else {
            return Math.abs(getRed(target) - candidate.r) +
                    Math.abs(getGreen(target) - candidate.g) +
                    Math.abs(getBlue(target) - candidate.b);
        }
    }

    private static int getRed(int pixel) {
        return (pixel >>> 16) & 0xff;
    }

    private static int getGreen(int pixel) {
        return (pixel >>> 8) & 0xff;
    }

    private static int getBlue(int pixel) {
        return pixel & 0xff;
    }

    private static void scaleImages(File tilesDir) throws IOException {
        double pixelResult = 300d;

        File[] files = tilesDir.listFiles();
        for (File file : files) {
            BufferedImage img = ImageIO.read(file);

            double xTransform = pixelResult / (double) img.getWidth();
            double yTransform = pixelResult / (double) img.getHeight();

            // Using BufferedImage.TYPE_INT_ARGB makes it look really cool (and wrong)
            img = Utils.scale(img, BufferedImage.TYPE_INT_RGB, (int)pixelResult, (int)pixelResult, xTransform, yTransform);

            ImageIO.write(img, ImageIO.getImageReaders(ImageIO.createImageInputStream(file)).next().getFormatName(), file);
        }
    }

    private static Collection<Tile> getImagesFromTiles(File tilesDir) throws IOException {
        Collection<Tile> tileImages = Collections.synchronizedSet(new HashSet<>());
        File[] files = tilesDir.listFiles();
        for (File file : files) {
            BufferedImage img = ImageIO.read(file);

            if (img != null) {
                tileImages.add(new Tile(img));
            } else {
                System.err.println("null image for file " + file.getName());
            }
        }
        return tileImages;
    }

    private static Collection<BufferedImagePart> getImagesFromInput(File inputImgFile) throws IOException {
        Collection<BufferedImagePart> parts = new HashSet<>();

        BufferedImage inputImage = ImageIO.read(inputImgFile);
        int totalHeight = inputImage.getHeight();
        int totalWidth = inputImage.getWidth();

        int x = 0, y = 0, w = Tile.SCALED_WIDTH, h = Tile.SCALED_HEIGHT;
        while (x + w <= totalWidth) {
            while (y + h <= totalHeight) {
                BufferedImage inputImagePart = inputImage.getSubimage(x, y, w, h);
                parts.add(new BufferedImagePart(inputImagePart, x, y));
                y += h;
            }
            y = 0;
            x += w;
        }

        return parts;
    }

    public static class Tile {
        public static int SCALED_WIDTH = TILE_WIDTH / TILE_SCALE;
        public static int SCALED_HEIGHT = TILE_HEIGHT / TILE_SCALE;
        public Pixel[][] pixels = new Pixel[SCALED_WIDTH][SCALED_HEIGHT];
        public BufferedImage image;

        public Tile(BufferedImage image) {
            this.image = image;
            calcPixels();
        }

        private void calcPixels() {
            for (int x = 0; x < SCALED_WIDTH; x++) {
                for (int y = 0; y < SCALED_HEIGHT; y++) {
                    pixels[x][y] = calcPixel(x * TILE_SCALE, y * TILE_SCALE, TILE_SCALE, TILE_SCALE);
                }
            }
        }

        private Pixel calcPixel(int x, int y, int w, int h) {
            int redTotal = 0, greenTotal = 0, blueTotal = 0;

            for (int i = 0; i < w; i++) {
                for (int j = 0; j < h; j++) {
                    int rgb = image.getRGB(x + i, y + j);
                    redTotal += getRed(rgb);
                    greenTotal += getGreen(rgb);
                    blueTotal += getBlue(rgb);
                }
            }
            int count = w * h;
            return new Pixel(redTotal / count, greenTotal / count, blueTotal / count);
        }
    }

    public static class BufferedImagePart {
        public BufferedImage image;
        public int x;
        public int y;
        public BufferedImagePart(BufferedImage image, int x, int y) {
            this.image = image;
            this.x = x;
            this.y = y;
        }
    }

    public static class Pixel {
        public int r, g, b;

        public Pixel(int r, int g, int b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }

        @Override
        public String toString() {
            return r + "." + g + "." + b;
        }
    }
}
