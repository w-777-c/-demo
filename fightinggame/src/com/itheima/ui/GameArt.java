package com.itheima.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** Original cel-shaded characters and theatrical scenery, independent of display resolution. */
final class GameArt {
    private static final Color INK = new Color(35, 25, 39), SKIN = new Color(245, 218, 199), SHADE = new Color(215, 167, 156);
    private static final Map<Integer, BufferedImage> ACTORS = new HashMap<>();
    private GameArt() {}
    static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }
    static void diamond(Graphics2D g, double x, double y, double size) { polygon(g, g.getColor(), x, y - size, x + size * 0.7, y, x, y + size, x - size * 0.7, y); }
    static void frame(Graphics2D g, int x, int y, int width, int height) {
        quality(g); g.setStroke(new BasicStroke(1)); g.setColor(GameTheme.BORDER); g.drawRect(x + 2, y + 2, width - 5, height - 5);
        g.setColor(new Color(228, 189, 115, 75)); g.drawRect(x + 6, y + 6, width - 13, height - 13);
        for (int side = 0; side < 4; side++) {
            Graphics2D corner = (Graphics2D) g.create();
            corner.translate(x + (side % 2 == 0 ? 8 : width - 8), y + (side < 2 ? 8 : height - 8));
            corner.scale(side % 2 == 0 ? 1 : -1, side < 2 ? 1 : -1);
            corner.setColor(GameTheme.GOLD); corner.drawLine(0, 15, 0, 0); corner.drawLine(0, 0, 28, 0);
            corner.drawArc(1, 1, 21, 21, 0, -90); diamond(corner, 9, 9, 3); corner.dispose();
        }
    }
    static void scenery(Graphics2D canvas, int width, int height, int region) {
        Graphics2D g = (Graphics2D) canvas.create(); quality(g); g.scale(width / 1000.0, height / 600.0);
        Color[] curtain = {new Color(105, 28, 43), new Color(65, 36, 69), new Color(32, 69, 67), new Color(117, 34, 47)};
        g.setColor(new Color(27, 20, 30)); g.fillRect(0, 0, 1000, 600);
        g.setColor(new Color(53, 44, 56));
        for (int i = 0; i < 5; i++) {
            int x = 130 + i * 150;
            g.fillRoundRect(x, 65, 125, 398, 120, 120);
            g.setColor(new Color(29, 45, 51)); g.fillRoundRect(x + 8, 74, 109, 380, 108, 108);
            g.setColor(new Color(94, 126, 127, 75));
            polygon(g, g.getColor(), x + 62, 88, x + 105, 171, x + 62, 254, x + 19, 171);
            polygon(g, new Color(126, 85, 89, 85), x + 62, 262, x + 105, 333, x + 62, 408, x + 19, 333);
            g.setColor(new Color(170, 148, 110, 95)); g.drawLine(x + 62, 85, x + 62, 447); g.drawLine(x + 14, 253, x + 110, 253);
            g.setColor(new Color(53, 44, 56));
        }
        for (int side = 0; side < 2; side++) {
            Graphics2D drape = (Graphics2D) g.create(); if (side == 1) { drape.translate(1000, 0); drape.scale(-1, 1); }
            form(drape, curtain[region % 4], 0, 0, 85, 0, 165, 0, 225, 0, 207, 155, 128, 242, 81, 270, 106, 385, 107, 477, 139, 500, 90, 495, 51, 480, 0, 502);
            for (int fold = 0; fold < 5; fold++) {
                drape.setColor(new Color(17, 7, 16, 65)); drape.setStroke(new BasicStroke(11));
                Path2D path = new Path2D.Double(); path.moveTo(25 + fold * 33, 0); path.curveTo(30 + fold * 25, 120, 37 + fold * 11, 211, 67, 270); drape.draw(path);
            }
            drape.setStroke(new BasicStroke(3)); drape.setColor(GameTheme.GOLD); drape.drawArc(-12, 244, 113, 50, 190, 145);
            drape.drawLine(82, 278, 97, 342); diamond(drape, 98, 351, 11); drape.dispose();
        }
        for (int x = -30; x < 1000; x += 185) {
            form(g, curtain[region % 4].darker(), x, 0, x + 15, 86, x + 151, 97, x + 188, 0);
            g.setColor(new Color(190, 143, 86, 150)); g.setStroke(new BasicStroke(1)); g.drawArc(x + 6, -35, 175, 113, 190, 160);
        }
        polygon(g, new Color(210, 235, 227, 8), 170, 82, 260, 82, 664, 500, 313, 500);
        polygon(g, new Color(210, 235, 227, 8), 760, 82, 830, 82, 704, 500, 455, 500);
        g.setColor(new Color(48, 34, 41)); g.fillRect(0, 493, 1000, 107);
        g.setColor(new Color(87, 61, 62)); g.drawLine(0, 493, 1000, 493);
        for (int i = -5; i <= 10; i++) { g.setColor(new Color(79, 57, 64)); g.drawLine(500 + i * 88, 493, 500 + i * 190, 600); }
        g.drawLine(0, 539, 1000, 539); g.drawLine(0, 587, 1000, 587);
        g.setColor(new Color(214, 183, 131, 30)); g.setStroke(new BasicStroke(1.5f)); g.drawOval(275, 483, 450, 86); g.drawOval(306, 490, 388, 69);
        g.dispose();
    }
    static BufferedImage actorImage(int style) { return ACTORS.computeIfAbsent(style, GameArt::renderActor); }
    static void actor(Graphics2D canvas, double x, double ground, double height, int style, boolean flip, boolean alive, double breathe) {
        Graphics2D g = (Graphics2D) canvas.create(); quality(g);
        g.setColor(new Color(4, 5, 8, 95)); g.fill(new Ellipse2D.Double(x - height * 0.19, ground - 5, height * 0.38, 14));
        g.translate(x, ground); if (flip) g.scale(-1, 1);
        if (!alive) { g.setComposite(AlphaComposite.SrcOver.derive(0.38f)); g.rotate(-0.13); }
        double scale = height / 700; g.scale(scale * (1 + breathe * 0.002), scale * (1 + breathe * 0.004));
        g.drawImage(actorImage(style), -240, -700, null); g.dispose();
    }
    private static BufferedImage renderActor(int style) {
        BufferedImage image = new BufferedImage(480, 720, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics(); quality(g); g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int role = style % 3;
        Color[] hairs = {new Color(231, 220, 183), new Color(188, 67, 72), new Color(213, 223, 235), new Color(139, 158, 170), new Color(212, 169, 144), new Color(203, 204, 223)};
        Color hair = hairs[style % hairs.length];
        Color accent = role == 0 ? new Color(119, 181, 163) : role == 1 ? new Color(212, 73, 94) : new Color(120, 194, 221);
        Color cloth = role == 0 ? new Color(38, 71, 69) : role == 1 ? new Color(83, 27, 47) : new Color(43, 47, 76);
        Color gold = new Color(219, 183, 117), white = new Color(232, 225, 216);
        // Hair and coat back layers establish each character's silhouette.
        form(g, hair.darker(), 187, 67, 148, 102, 161, 216, 145, 261, 142, 308, 171, 341, 154, 377, 202, 349, 206, 290, 213, 213,
                245, 291, 296, 342, 341, 324, 300, 258, 318, 185, 302, 105, 280, 47, 223, 37, 187, 67);
        form(g, cloth, 196, 204, 126, 211, 137, 373, 112, 444, 96, 496, 74, 535, 74, 570, 142, 558, 190, 519, 228, 436,
                283, 510, 319, 545, 397, 531, 366, 485, 341, 408, 324, 344, 305, 267, 290, 207, 265, 197);
        form(g, accent.darker(), 161, 262, 134, 347, 144, 449, 97, 540, 167, 504, 206, 414, 213, 344);
        form(g, cloth.darker(), 284, 247, 285, 398, 332, 470, 376, 511, 316, 488, 271, 404, 258, 343);
        // Opaque stockings, boots, and layered skirt.
        polygon(g, new Color(59, 52, 69), 202, 412, 242, 423, 233, 539, 214, 650, 181, 650, 190, 536);
        polygon(g, new Color(43, 41, 55), 243, 415, 278, 403, 288, 529, 284, 643, 253, 647, 247, 539);
        polygon(g, new Color(31, 32, 44), 185, 562, 223, 569, 213, 658, 193, 686, 153, 686, 153, 677, 179, 651);
        polygon(g, new Color(31, 32, 44), 249, 558, 290, 553, 290, 653, 316, 677, 313, 687, 267, 687, 252, 658);
        line(g, gold, 186, 571, 218, 578, 208, 650); line(g, gold, 257, 567, 282, 564, 281, 648);
        for (int i = 0; i < 4; i++) { line(g, new Color(117, 106, 109), 186, 590 + i * 13, 211, 603 + i * 13); line(g, new Color(117, 106, 109), 258, 583 + i * 13, 282, 596 + i * 13); }
        polygon(g, cloth.darker(), 193, 320, 273, 319, 307, 444, 287, 455, 260, 441, 236, 467, 204, 443, 172, 448);
        polygon(g, cloth, 201, 331, 238, 348, 232, 452, 201, 429, 178, 438);
        polygon(g, accent.darker(), 251, 331, 272, 329, 298, 435, 284, 443, 264, 430);
        line(g, gold, 178, 438, 202, 432, 234, 457, 260, 434, 287, 448, 302, 438);
        // Neck, fitted jacket, cravat, and embroidered trim.
        form(g, SKIN, 215, 156, 218, 180, 214, 198, 202, 210, 233, 239, 262, 222, 271, 204, 251, 186, 252, 172, 256, 158);
        polygon(g, SHADE, 217, 165, 253, 165, 245, 191, 225, 188);
        form(g, new Color(42, 36, 46), 199, 205, 177, 238, 184, 285, 201, 318, 225, 335, 253, 335, 272, 315, 282, 271, 286, 224, 268, 201);
        polygon(g, white, 202, 204, 218, 189, 233, 212, 251, 192, 269, 204, 248, 242, 216, 244);
        polygon(g, cloth, 196, 204, 222, 250, 210, 270, 226, 293, 203, 318, 189, 272, 181, 233);
        polygon(g, cloth, 271, 204, 282, 234, 273, 284, 258, 318, 240, 292, 257, 265, 246, 249);
        line(g, gold, 196, 210, 215, 248, 207, 267, 223, 291); line(g, gold, 265, 210, 253, 248, 261, 266, 246, 291);
        polygon(g, accent, 231, 214, 219, 234, 232, 230, 240, 249, 248, 232, 258, 235, 240, 214);
        g.setColor(gold); diamond(g, 236, 218, 9);
        for (int i = 0; i < 4; i++) { g.setColor(gold); g.fillOval(226, 262 + i * 13, 4, 4); g.fillOval(246, 262 + i * 13, 4, 4); }
        polygon(g, new Color(92, 57, 60), 194, 316, 272, 316, 273, 336, 194, 337); polygon(g, gold, 225, 318, 247, 318, 247, 334, 225, 334);
        polygon(g, cloth.darker(), 230, 322, 242, 322, 242, 330, 230, 330);
        // Sleeves and gloved hands.
        form(g, cloth, 190, 208, 163, 203, 143, 237, 137, 275, 130, 307, 121, 326, 107, 340, 111, 358, 129, 365, 143, 346, 169, 316, 177, 270, 194, 245);
        polygon(g, white, 105, 334, 135, 348, 127, 367, 99, 351);
        form(g, SKIN, 102, 351, 83, 366, 79, 382, 89, 389, 101, 392, 118, 376, 124, 363);
        form(g, cloth, 274, 209, 297, 205, 315, 236, 325, 259, 340, 279, 359, 264, 373, 252, 383, 268, 374, 285, 363, 297, 332, 321, 302, 294, 286, 275);
        polygon(g, white, 365, 250, 381, 251, 390, 269, 373, 281);
        form(g, SKIN, 377, 250, 378, 233, 391, 221, 399, 219, 404, 224, 395, 239, 391, 245, 409, 235, 418, 239, 414, 246, 405, 259, 398, 268, 388, 267);
        line(g, gold, 144, 274, 160, 277, 168, 244); line(g, gold, 307, 252, 326, 285, 349, 287);
        // Face, warm cell shadow and large inked anime eyes.
        form(g, SKIN, 185, 99, 179, 125, 194, 157, 213, 173, 222, 184, 241, 190, 252, 178, 279, 159, 286, 128, 278, 100, 263, 70, 202, 69, 185, 99);
        form(g, SHADE, 185, 103, 187, 136, 198, 160, 214, 173, 219, 178, 227, 184, 233, 181, 207, 159, 203, 128, 202, 106);
        eye(g, 199, 121, accent); eye(g, 242, 119, accent);
        line(g, hair.darker().darker(), 197, 113, 210, 108, 222, 112); line(g, hair.darker().darker(), 241, 109, 254, 105, 267, 110);
        line(g, new Color(181, 127, 121), 233, 139, 230, 151, 235, 152);
        form(g, new Color(193, 112, 119), 222, 165, 229, 161, 238, 162, 244, 163, 235, 168, 230, 170, 222, 165);
        // Sculpted bangs and reflected hair ribbons.
        form(g, hair, 182, 124, 167, 87, 181, 46, 215, 43, 250, 25, 287, 51, 292, 81, 306, 117, 286, 160, 268, 170, 275, 147, 277, 127, 271, 101,
                263, 120, 250, 130, 239, 133, 251, 112, 247, 86, 239, 75, 231, 111, 214, 123, 200, 129, 202, 106, 201, 86, 210, 72, 187, 90, 191, 109, 182, 124);
        line(g, hair.brighter(), 193, 81, 208, 58, 232, 53); line(g, hair.brighter(), 251, 52, 270, 66, 279, 88);
        line(g, hair.darker(), 220, 53, 207, 89, 207, 113); line(g, hair.darker(), 252, 68, 259, 102, 250, 119);
        g.setColor(gold); diamond(g, 188, 106, 7); diamond(g, 273, 154, 6);
        g.setColor(accent); diamond(g, 274, 163, 5);
        if (role == 0) {
            for (int i = 0; i < 7; i++) { form(g, i % 2 == 0 ? hair : hair.darker(), 181, 131 + i * 18, 163, 140 + i * 18, 172, 158 + i * 18, 183, 151 + i * 18, 195, 140 + i * 18, 190, 137 + i * 18, 181, 131 + i * 18); }
            polygon(g, gold, 57, 350, 126, 334, 178, 365, 163, 435, 120, 483, 66, 450, 40, 389);
            polygon(g, cloth, 61, 361, 124, 346, 165, 370, 153, 429, 118, 466, 76, 440, 51, 391);
            polygon(g, new Color(70, 104, 95), 61, 361, 118, 350, 118, 466, 76, 440, 51, 391);
            g.setColor(gold); diamond(g, 113, 403, 34); g.setColor(accent); diamond(g, 113, 403, 19);
            line(g, gold, 113, 366, 113, 442); line(g, gold, 83, 402, 143, 402);
            sword(g, 385, 228, -0.12, gold, false);
        } else if (role == 1) {
            sword(g, 90, 383, -2.4, gold, true); sword(g, 392, 244, 0.24, gold, false);
            polygon(g, accent, 178, 182, 151, 160, 132, 205, 167, 195, 154, 240, 186, 200);
            g.setColor(gold); diamond(g, 183, 192, 9);
        } else {
            Graphics2D hat = (Graphics2D) g.create(); hat.rotate(-0.10, 230, 69);
            form(hat, new Color(28, 29, 48), 145, 73, 175, 52, 282, 50, 324, 77, 329, 94, 294, 106, 244, 99, 176, 101, 135, 93, 145, 73);
            polygon(hat, new Color(39, 38, 59), 181, 67, 188, 14, 271, 14, 286, 68);
            polygon(hat, accent.darker(), 182, 51, 282, 51, 286, 67, 180, 67); hat.setColor(gold); diamond(hat, 258, 59, 9); hat.dispose();
            line(g, gold, 395, 229, 366, 602); line(g, new Color(86, 66, 72), 400, 229, 371, 602);
            polygon(g, gold, 379, 194, 399, 157, 419, 198, 399, 229); polygon(g, accent, 386, 195, 399, 173, 412, 197, 399, 218);
            ticket(g, 94, 351, -0.28, accent); ticket(g, 114, 312, 0.16, gold);
        }
        if (style == 5) { polygon(g, gold, 186, 63, 182, 24, 205, 45, 229, 11, 250, 43, 279, 21, 272, 65); g.setColor(new Color(185, 55, 75)); diamond(g, 230, 48, 11); }
        g.dispose(); return image;
    }
    private static void eye(Graphics2D g, int x, int y, Color color) {
        form(g, new Color(252, 247, 237), x, y, x + 8, y - 7, x + 22, y - 6, x + 28, y, x + 22, y + 13, x + 8, y + 13, x, y);
        g.setColor(color); g.fillOval(x + 10, y - 3, 11, 17); g.setColor(INK); g.fillOval(x + 14, y, 4, 12);
        g.setColor(new Color(244, 247, 238)); g.fillOval(x + 11, y - 2, 5, 5); g.fillOval(x + 17, y + 8, 3, 3);
        g.setStroke(new BasicStroke(2.8f)); line(g, INK, x - 3, y - 3, x + 7, y - 7, x + 22, y - 6, x + 29, y - 1); g.setStroke(new BasicStroke(2.2f));
    }
    private static void sword(Graphics2D canvas, int x, int y, double angle, Color gold, boolean shortBlade) {
        Graphics2D g = (Graphics2D) canvas.create(); g.translate(x, y); g.rotate(angle);
        int length = shortBlade ? 100 : 158;
        polygon(g, new Color(206, 227, 228), -6, 0, -8, -length, 0, -length - 24, 9, -length, 6, 0);
        polygon(g, new Color(138, 168, 183), 0, -length - 20, 9, -length, 6, 0, 0, 0);
        polygon(g, gold, -26, -4, -20, -13, -7, -8, 7, -8, 22, -13, 27, -4, 7, 4, -7, 4);
        polygon(g, new Color(60, 40, 51), -4, 3, 5, 3, 5, 31, -4, 31); g.setColor(gold); diamond(g, 0, 36, 7); g.dispose();
    }
    static void ticket(Graphics2D canvas, int x, int y, double angle, Color ink) {
        Graphics2D g = (Graphics2D) canvas.create(); g.translate(x, y); g.rotate(angle);
        g.setColor(new Color(236, 222, 195)); g.fillRoundRect(-17, -26, 34, 52, 3, 3);
        g.setColor(ink); g.setStroke(new BasicStroke(1)); g.drawRect(-13, -22, 26, 44); diamond(g, 0, 0, 12); g.dispose();
    }
    private static void form(Graphics2D g, Color fill, double x, double y, double... controls) {
        Path2D path = new Path2D.Double(); path.moveTo(x, y);
        for (int i = 0; i < controls.length; i += 6) path.curveTo(controls[i], controls[i + 1], controls[i + 2], controls[i + 3], controls[i + 4], controls[i + 5]);
        path.closePath(); g.setColor(fill); g.fill(path); g.setColor(INK); g.draw(path);
    }
    private static void polygon(Graphics2D g, Color fill, double... points) {
        Path2D path = new Path2D.Double(); path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
        path.closePath(); g.setColor(fill); g.fill(path);
    }
    private static void line(Graphics2D g, Color color, double... points) {
        Path2D path = new Path2D.Double(); path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
        g.setColor(color); g.draw(path);
    }
}
