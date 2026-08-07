import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Turns an equirectangular panorama (a 2:1 image, the format nearly every free HDRI and sky
 * photo comes in) into a Skyloom / OptiFine skybox sheet: three columns by two rows,
 * top row bottom/top/east, bottom row south/west/north.
 * <p>
 * Needs nothing but a JDK:
 * <pre>
 *   java PanoramaToSkybox.java panorama.jpg sky.png
 *   java PanoramaToSkybox.java panorama.jpg sky.png 2048     face size in pixels
 *   java PanoramaToSkybox.java panorama.jpg sky.png 2048 90  extra yaw in degrees
 * </pre>
 * The face size is per cube side, so 1536 writes a 4608x3072 sheet. Use the yaw when the
 * panorama's centre does not line up with where you want north to be.
 */
public final class PanoramaToSkybox {
    /** Same order and placement as Skyloom's SkyFace. */
    private static final Face[] FACES = {
            new Face("bottom", 0, 0), new Face("top", 1, 0), new Face("east", 2, 0),
            new Face("south", 0, 1), new Face("west", 1, 1), new Face("north", 2, 1)
    };

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: java PanoramaToSkybox.java <panorama> <output.png> [faceSize] [yawDegrees]");
            System.exit(2);
        }
        int faceSize = args.length > 2 ? Integer.parseInt(args[2]) : 1536;
        double yaw = args.length > 3 ? Math.toRadians(Double.parseDouble(args[3])) : 0.0;

        BufferedImage panorama = ImageIO.read(new File(args[0]));
        if (panorama == null) throw new IllegalArgumentException("could not read " + args[0]);
        int width = panorama.getWidth();
        int height = panorama.getHeight();
        if (Math.abs(width - height * 2) > height * 0.02) {
            System.out.printf(Locale.ROOT, "warning: %dx%d is not 2:1, an equirectangular panorama was expected%n",
                    width, height);
        }

        int[] source = panorama.getRGB(0, 0, width, height, null, 0, width);
        BufferedImage sheet = new BufferedImage(faceSize * 3, faceSize * 2, BufferedImage.TYPE_INT_RGB);

        for (Face face : FACES) {
            for (int y = 0; y < faceSize; y++) {
                double t = (y + 0.5) / faceSize;
                for (int x = 0; x < faceSize; x++) {
                    double s = (x + 0.5) / faceSize;
                    // the quad every face is built from: y = -1, x and z running -1..1
                    double[] direction = face.rotate(2.0 * s - 1.0, -1.0, 2.0 * t - 1.0);
                    sheet.setRGB(face.column * faceSize + x, face.row * faceSize + y,
                            sample(source, width, height, direction, yaw));
                }
            }
            System.out.println("  " + face.name);
        }

        ImageIO.write(sheet, "png", new File(args[1]));
        System.out.printf(Locale.ROOT, "wrote %s (%dx%d)%n", args[1], sheet.getWidth(), sheet.getHeight());
    }

    /** Bilinear lookup, wrapping around the seam so the vertical join stays invisible. */
    private static int sample(int[] source, int width, int height, double[] direction, double yaw) {
        double length = Math.sqrt(direction[0] * direction[0] + direction[1] * direction[1] + direction[2] * direction[2]);
        double dx = direction[0] / length, dy = direction[1] / length, dz = direction[2] / length;

        double longitude = Math.atan2(dx, -dz) + yaw;
        double latitude = Math.asin(Math.max(-1.0, Math.min(1.0, dy)));

        double u = (longitude / (2.0 * Math.PI) + 0.5) * width - 0.5;
        double v = (0.5 - latitude / Math.PI) * height - 0.5;

        int x0 = (int) Math.floor(u), y0 = (int) Math.floor(v);
        double fx = u - x0, fy = v - y0;

        int a = pixel(source, width, height, x0, y0);
        int b = pixel(source, width, height, x0 + 1, y0);
        int c = pixel(source, width, height, x0, y0 + 1);
        int d = pixel(source, width, height, x0 + 1, y0 + 1);

        int red = blend(a >> 16 & 255, b >> 16 & 255, c >> 16 & 255, d >> 16 & 255, fx, fy);
        int green = blend(a >> 8 & 255, b >> 8 & 255, c >> 8 & 255, d >> 8 & 255, fx, fy);
        int blue = blend(a & 255, b & 255, c & 255, d & 255, fx, fy);
        return red << 16 | green << 8 | blue;
    }

    private static int blend(int a, int b, int c, int d, double fx, double fy) {
        double top = a + (b - a) * fx;
        double bottom = c + (d - c) * fx;
        return (int) Math.round(top + (bottom - top) * fy);
    }

    private static int pixel(int[] source, int width, int height, int x, int y) {
        int wrapped = ((x % width) + width) % width;
        int clamped = Math.max(0, Math.min(height - 1, y));
        return source[clamped * width + wrapped];
    }

    private record Face(String name, int column, int row) {
        double[] rotate(double x, double y, double z) {
            return switch (this.name) {
                case "bottom" -> new double[]{z, y, -x};
                case "top" -> new double[]{-z, -y, -x};
                case "east" -> new double[]{1.0, -z, x};
                case "south" -> new double[]{-x, -z, 1.0};
                case "west" -> new double[]{-1.0, -z, -x};
                default -> new double[]{x, -z, -1.0};
            };
        }
    }
}
