package com.itheima.ui;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

public final class UiAssets {
    private static final Map<String, BufferedImage> IMAGES = new HashMap<>();
    private static final Font BODY = loadFont("/fonts/ZCOOLXiaoWei-Regular.ttf");
    private static final Font DISPLAY = loadFont("/fonts/MaShanZheng-Regular.ttf");
    private UiAssets() {}
    private static Font loadFont(String resource) {
        try (InputStream stream = UiAssets.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Missing font " + resource);
            return Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (IOException | java.awt.FontFormatException failure) {
            System.err.println("Font fallback: " + failure.getMessage());
            return new Font(Font.DIALOG, Font.PLAIN, 14);
        }
    }
    public static Font body(int style, int size) { return BODY.deriveFont(style, size); }
    public static Font display(int size) { return DISPLAY.deriveFont(Font.PLAIN, size); }
    public static synchronized BufferedImage image(String name) {
        return IMAGES.computeIfAbsent(name, key -> {
            try (InputStream input = UiAssets.class.getResourceAsStream("/icons/" + key + ".png")) {
                if (input == null) throw new IOException("Missing icon " + key);
                BufferedImage image = ImageIO.read(input);
                if (image == null) throw new IOException("Invalid icon " + key);
                return image;
            } catch (IOException failure) { throw new IllegalStateException(failure); }
        });
    }
    public static ImageIcon icon(String name, int size) {
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(image(name), 0, 0, size, size, null); g.dispose();
        return new ImageIcon(scaled);
    }
}
