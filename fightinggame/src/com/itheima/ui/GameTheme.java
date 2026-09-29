package com.itheima.ui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;

/** 全局视觉令牌：剧场配色、字体和 Swing Metal 控件默认样式。 */
final class GameTheme {
    static final Color BACKGROUND = new Color(20, 14, 20);
    static final Color SURFACE = new Color(35, 24, 31);
    static final Color RAISED = new Color(49, 33, 40);
    static final Color BORDER = new Color(94, 72, 65);
    static final Color TEXT = new Color(237, 225, 203);
    static final Color MUTED = new Color(174, 160, 153);
    static final Color GREEN = new Color(103, 190, 171);
    static final Color RED = new Color(226, 108, 117);
    static final Color GOLD = new Color(228, 189, 115);
    static final Color CRIMSON = new Color(156, 43, 59);
    static final Color ICE = new Color(115, 199, 221);
    private static boolean installed;
    private GameTheme() {}
    static Font font(int style, int size) { return UiAssets.body(style, size); }
    static Font display(int size) { return UiAssets.display(size); }
    static void install() {
        if (installed) return;
        installed = true;
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
            @Override public ColorUIResource getControlHighlight() { return new ColorUIResource(GOLD); }
            @Override public ColorUIResource getControlDarkShadow() { return new ColorUIResource(BORDER); }
            @Override public ColorUIResource getControlInfo() { return new ColorUIResource(TEXT); }
        });
        try { UIManager.setLookAndFeel(new MetalLookAndFeel()); }
        catch (javax.swing.UnsupportedLookAndFeelException exception) { throw new IllegalStateException(exception); }
        FontUIResource font = new FontUIResource(font(Font.PLAIN, 14));
        for (String component : new String[]{"Label", "Button", "ToggleButton", "TextField", "TextArea", "TextPane", "FormattedTextField", "PasswordField", "ComboBox", "Spinner", "Table", "TableHeader", "List", "ToolTip", "OptionPane", "CheckBox", "Slider", "MenuItem"}) {
            UIManager.put(component + ".font", font); UIManager.put(component + ".background", SURFACE); UIManager.put(component + ".foreground", TEXT);
        }
        UIManager.put("Panel.background", SURFACE); UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("OptionPane.messageFont", font); UIManager.put("OptionPane.buttonFont", font);
        UIManager.put("Button.gradient", null); UIManager.put("ToggleButton.gradient", null);
        UIManager.put("Button.disabledText", MUTED); UIManager.put("ToggleButton.select", CRIMSON);
        UIManager.put("ToggleButton.border", BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(8, 14, 8, 14)));
        UIManager.put("ComboBox.selectionBackground", CRIMSON); UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("ComboBox.disabledBackground", BACKGROUND); UIManager.put("ComboBox.disabledForeground", MUTED);
        UIManager.put("ComboBox.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("TextField.inactiveForeground", MUTED); UIManager.put("TextField.inactiveBackground", BACKGROUND);
        UIManager.put("FormattedTextField.inactiveForeground", MUTED); UIManager.put("FormattedTextField.inactiveBackground", BACKGROUND);
        for (String field : new String[]{"TextField", "PasswordField", "FormattedTextField"}) {
            UIManager.put(field + ".caretForeground", GOLD); UIManager.put(field + ".selectionBackground", CRIMSON); UIManager.put(field + ".selectionForeground", TEXT);
            UIManager.put(field + ".border", BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 1, 2, 1, BORDER), BorderFactory.createEmptyBorder(7, 9, 7, 9)));
        }
        UIManager.put("Table.selectionBackground", CRIMSON); UIManager.put("Table.selectionForeground", TEXT); UIManager.put("Table.gridColor", BORDER);
        UIManager.put("ScrollPane.background", BACKGROUND); UIManager.put("Viewport.background", BACKGROUND);
        UIManager.put("ScrollBar.background", BACKGROUND); UIManager.put("ScrollBar.thumb", BORDER); UIManager.put("ScrollBar.width", 10);
        UIManager.put("ToolTip.background", RAISED); UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(GOLD), BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        UIManager.put("Slider.trackWidth", 4); UIManager.put("Slider.majorTickLength", 6);
    }
}
