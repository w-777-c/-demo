package com.itheima.ui;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/** 无系统标题栏的统一弹窗，提供金色边框、拖动标题栏和 Escape 关闭。 */
@SuppressWarnings({"serial", "this-escape"})
public class GameDialog extends JDialog {
    private final JPanel shell;
    private Container body;
    public GameDialog(Window owner, String title) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        GameTheme.install(); setUndecorated(true); setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        shell = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics); Graphics2D g = (Graphics2D) graphics.create(); GameArt.frame(g, 0, 0, getWidth(), getHeight()); g.dispose();
            }
        };
        shell.setBackground(GameTheme.BACKGROUND); shell.setBorder(BorderFactory.createEmptyBorder(9, 9, 9, 9));
        JPanel heading = new JPanel(new BorderLayout(14, 0)); heading.setBackground(GameTheme.SURFACE);
        heading.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, GameTheme.BORDER), BorderFactory.createEmptyBorder(12, 18, 12, 12)));
        JLabel name = new JLabel(title); name.setFont(GameTheme.display(28)); name.setForeground(GameTheme.GOLD); heading.add(name, BorderLayout.CENTER);
        GameButton close = new GameButton("", GameTheme.SURFACE); close.setGlyph("x"); close.setToolTipText("关闭"); close.getAccessibleContext().setAccessibleName("关闭");
        close.addActionListener(event -> requestClose()); heading.add(close, BorderLayout.EAST);
        MouseAdapter drag = new MouseAdapter() {
            private Point anchor;
            @Override public void mousePressed(MouseEvent event) { anchor = event.getPoint(); }
            @Override public void mouseDragged(MouseEvent event) { Point location = event.getLocationOnScreen(); setLocation(location.x - anchor.x, location.y - anchor.y); }
        };
        heading.addMouseListener(drag); heading.addMouseMotionListener(drag);
        shell.add(heading, BorderLayout.NORTH); super.setContentPane(shell);
        getRootPane().registerKeyboardAction(event -> requestClose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        GameAudio.effect("open");
    }
    private void requestClose() { dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)); }
    @Override public void setContentPane(Container content) {
        if (shell == null) { super.setContentPane(content); return; }
        if (body != null) shell.remove(body); body = content; shell.add(content, BorderLayout.CENTER);
    }
}
