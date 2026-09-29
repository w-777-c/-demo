package com.itheima.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/** 兼容 JOptionPane 返回值的统一剧场弹窗工厂。 */
public final class GameDialogs {
    private GameDialogs() {}
    public static int showConfirmDialog(Component parent, Object message, String title, int options, int... ignored) {
        java.awt.Window owner = parent instanceof java.awt.Window window ? window : parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        GameDialog dialog = new GameDialog(owner, title); int[] result = {JOptionPane.CLOSED_OPTION};
        JPanel content = new JPanel(new BorderLayout(0, 22)); content.setBackground(GameTheme.BACKGROUND); content.setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));
        Component display;
        if (message instanceof Component component) display = component;
        else {
            JTextArea text = new JTextArea(String.valueOf(message)); text.setFont(GameTheme.font(java.awt.Font.PLAIN, 16));
            text.setForeground(GameTheme.TEXT); text.setBackground(GameTheme.BACKGROUND); text.setEditable(false);
            text.setLineWrap(true); text.setWrapStyleWord(true); text.setFocusable(false); text.setSize(380, Short.MAX_VALUE);
            text.setPreferredSize(new Dimension(380, Math.max(54, text.getPreferredSize().height))); display = text;
        }
        content.add(display, BorderLayout.CENTER);
        JPanel commands = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)); commands.setOpaque(false);
        if (options != JOptionPane.DEFAULT_OPTION) {
            GameButton cancel = new GameButton("取消", GameTheme.SURFACE);
            cancel.addActionListener(event -> { result[0] = JOptionPane.CANCEL_OPTION; dialog.dispose(); }); commands.add(cancel);
        }
        GameButton accept = new GameButton(options == JOptionPane.YES_NO_OPTION ? "是" : "确定", GameTheme.GREEN);
        accept.addActionListener(event -> { result[0] = JOptionPane.OK_OPTION; dialog.dispose(); }); commands.add(accept);
        content.add(commands, BorderLayout.SOUTH); dialog.setContentPane(content); dialog.getRootPane().setDefaultButton(accept);
        dialog.pack(); dialog.setResizable(false); dialog.setLocationRelativeTo(parent); dialog.setVisible(true); return result[0];
    }
    public static void showMessageDialog(Component parent, Object message, String title, int ignored) { showConfirmDialog(parent, message, title, JOptionPane.DEFAULT_OPTION); }
}
