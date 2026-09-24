package com.itheima.ui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;

final class GameTheme {
    static final Color BACKGROUND = new Color(19, 23, 25);
    static final Color SURFACE = new Color(29, 35, 38);
    static final Color RAISED = new Color(37, 44, 47);
    static final Color BORDER = new Color(57, 66, 70);
    static final Color TEXT = new Color(235, 240, 239);
    static final Color MUTED = new Color(153, 168, 172);
    static final Color GREEN = new Color(100, 211, 176);
    static final Color RED = new Color(238, 130, 125);
    static final Color GOLD = new Color(225, 191, 121);

    private GameTheme() {}

    static Font font(int style, int size) { return new Font("Microsoft YaHei", style, size); }

    static void install() {
        MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme() {
            @Override protected ColorUIResource getPrimary1() { return new ColorUIResource(BORDER); }
            @Override protected ColorUIResource getPrimary2() { return new ColorUIResource(RAISED); }
            @Override protected ColorUIResource getPrimary3() { return new ColorUIResource(SURFACE); }
            @Override protected ColorUIResource getSecondary1() { return new ColorUIResource(BORDER); }
            @Override protected ColorUIResource getSecondary2() { return new ColorUIResource(RAISED); }
            @Override protected ColorUIResource getSecondary3() { return new ColorUIResource(SURFACE); }
            @Override public ColorUIResource getControlTextColor() { return new ColorUIResource(TEXT); }
            @Override public ColorUIResource getSystemTextColor() { return new ColorUIResource(TEXT); }
            @Override public ColorUIResource getUserTextColor() { return new ColorUIResource(TEXT); }
            @Override public ColorUIResource getWindowBackground() { return new ColorUIResource(BACKGROUND); }
        });
        try { UIManager.setLookAndFeel(new MetalLookAndFeel()); }
        catch (javax.swing.UnsupportedLookAndFeelException exception) { throw new IllegalStateException(exception); }
        Font font = font(Font.PLAIN, 13);
        for (String component : new String[]{"Label", "Button", "ToggleButton", "TextField", "PasswordField", "ComboBox", "Spinner", "Table", "TableHeader", "List", "ToolTip", "OptionPane"}) {
            UIManager.put(component + ".font", font);
            UIManager.put(component + ".background", SURFACE);
            UIManager.put(component + ".foreground", TEXT);
        }
        UIManager.put("Panel.background", SURFACE);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("OptionPane.messageFont", font);
        UIManager.put("OptionPane.buttonFont", font);
        UIManager.put("Button.gradient", null);
        UIManager.put("ToggleButton.gradient", null);
        UIManager.put("Button.disabledText", MUTED);
        UIManager.put("ToggleButton.select", new Color(47, 89, 77));
        UIManager.put("ComboBox.selectionBackground", new Color(47, 89, 77));
        UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("ComboBox.disabledBackground", SURFACE);
        UIManager.put("ComboBox.disabledForeground", MUTED);
        UIManager.put("TextField.inactiveForeground", MUTED);
        UIManager.put("TextField.inactiveBackground", BACKGROUND);
        UIManager.put("TextField.caretForeground", GREEN);
        UIManager.put("PasswordField.caretForeground", GREEN);
        UIManager.put("TextField.selectionBackground", new Color(47, 89, 77));
        UIManager.put("PasswordField.selectionBackground", new Color(47, 89, 77));
        UIManager.put("Table.selectionBackground", new Color(47, 89, 77));
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("ScrollPane.background", BACKGROUND);
        UIManager.put("Viewport.background", BACKGROUND);
        UIManager.put("ScrollBar.background", BACKGROUND);
        UIManager.put("ScrollBar.thumb", BORDER);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ToolTip.background", RAISED);
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(5, 8, 5, 8)));
    }
}
