package com.itheima.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;

/**
 * 独立角色库页面：按职业筛选角色，并展示立绘、定位、属性和战斗档案。
 *
 * <p>角色名采用中国古代与西方神话/历史意象的再创作形式，页面明确标注灵感来源，
 * 不把真实历史人物的生平直接改写成游戏剧情。卡面使用成熟华丽的二次元剧场风格，
 * 保留角色剪影和服装层次，但不使用露骨内容。</p>
 */
@SuppressWarnings("serial")
final class CharacterLibraryDialog extends GameDialog {
    private enum Filter {
        ALL("全部角色", "ROSTER"), ASSASSIN("刺客", "ASSASSIN"), MAGE("法师", "MAGE"),
        WARRIOR("战士", "WARRIOR"), HEALER("治疗", "HEALER");
        final String title;
        final String code;
        Filter(String title, String code) { this.title = title; this.code = code; }
    }

    private record Entry(int style, Filter filter, String name, String epithet, String origin,
                         String story, String stats, String skill, Color accent) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry(4, Filter.ASSASSIN, "荆轲·夜宴", "绯幕潜行者", "中国 · 战国刺客意象",
                    "她把长剑藏进宴席的灯影，只在命运翻页时现身。每一次突袭都像谢幕前的最后一束红光。",
                    "生命 140   攻击 42   防御 1", "绝技：残灯一闪 · 首次命中追加流血", new Color(226, 86, 105)),
            new Entry(1, Filter.ASSASSIN, "卡西乌斯·影誓", "罗马暗刃", "西方 · 罗马共和时代意象",
                    "他以银币和誓言换取情报，在城墙的阴影中寻找最短的胜利路径。",
                    "生命 150   攻击 39   防御 2", "绝技：双相裁决 · 连续两次轻击", new Color(195, 67, 96)),
            new Entry(5, Filter.MAGE, "张衡·星仪", "东都观星者", "中国 · 东汉天文学意象",
                    "铜仪指向夜空时，星轨便成为她的法阵。她相信每一颗星都记录着尚未发生的战斗。",
                    "生命 160   攻击 36   防御 3", "绝技：浑天落印 · 召唤星辉冲击", new Color(112, 172, 224)),
            new Entry(2, Filter.MAGE, "梅林·灰塔", "终局魔导师", "西方 · 亚瑟王传说意象",
                    "灰塔里的旧魔法从不喧哗。她只需抬手，舞台上便会出现一条通往终局的蓝色火线。",
                    "生命 155   攻击 40   防御 2", "绝技：灰塔星火 · 对低血目标增伤", new Color(136, 132, 221)),
            new Entry(0, Filter.WARRIOR, "岳飞·赤胆", "鸣金守关者", "中国 · 南宋忠勇将领意象",
                    "他把守护写在枪尖，把退路留给身后的同伴。战鼓响起时，整座剧场都像一面盾。",
                    "生命 220   攻击 28   防御 8", "绝技：满江壁垒 · 进入坚守并反击", new Color(105, 190, 171)),
            new Entry(3, Filter.WARRIOR, "阿基里斯·银矛", "不落的前锋", "西方 · 希腊史诗意象",
                    "银矛指向敌阵的瞬间，他的身影像一道被月光拉长的旗帜。勇气是他的铠甲。",
                    "生命 205   攻击 34   防御 6", "绝技：奔星突阵 · 破甲并推进回合", new Color(154, 190, 204)),
            new Entry(2, Filter.HEALER, "华佗·青囊", "幕后台前医者", "中国 · 东汉医者意象",
                    "她携一卷青囊穿过战场，不问伤者来自哪一方。药香升起时，破碎的灯幕重新亮起。",
                    "生命 180   攻击 22   防御 4", "绝技：青囊回春 · 治疗并清除负面效果", new Color(103, 205, 178)),
            new Entry(5, Filter.HEALER, "希波克拉底·晨星", "白庭誓约者", "西方 · 古希腊医者意象",
                    "她以晨星为灯，遵守一条古老誓约：每个走下战场的人，都应重新拥有选择明天的机会。",
                    "生命 175   攻击 24   防御 5", "绝技：晨星礼赞 · 群体恢复与护盾", new Color(231, 192, 116))
    );

    private final JPanel cards = new JPanel(new GridLayout(0, 2, 12, 12));
    private final JLabel count = label("8 / 8", 12, GameTheme.MUTED);
    private final CharacterPreview artwork = new CharacterPreview(false, true);
    private final JLabel name = label("荆轲·夜宴", 30, GameTheme.GOLD);
    private final JLabel epithet = label("绯幕潜行者", 16, GameTheme.ICE);
    private final JLabel role = label("ASSASSIN  /  中国 · 战国刺客意象", 11, GameTheme.MUTED);
    private final JLabel stats = label("生命 140   攻击 42   防御 1", 13, GameTheme.TEXT);
    private final JLabel skill = label("绝技：残灯一闪 · 首次命中追加流血", 13, GameTheme.GOLD);
    private final JTextArea story = textArea();
    private Filter filter = Filter.ALL;
    private int selected = 0;

    CharacterLibraryDialog(Window owner) {
        super(owner, "角色库 · ORIGIN CODEX");
        JPanel content = new JPanel(new BorderLayout(16, 0));
        content.setBackground(new Color(20, 30, 43));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        content.add(filters(), BorderLayout.NORTH);
        content.add(cardScroll(), BorderLayout.CENTER);
        content.add(details(), BorderLayout.EAST);
        setContentPane(content);
        setSize(new Dimension(1210, 760));
        setMinimumSize(new Dimension(1040, 680));
        artwork.setPreferredSize(new Dimension(360, 360));
        setLocationRelativeTo(owner);
        select(0);
    }

    private JPanel filters() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        buttons.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        for (Filter option : Filter.values()) {
            JToggleButton button = new JToggleButton(option.title);
            button.setSelected(option == Filter.ALL);
            button.setFont(GameTheme.font(Font.PLAIN, 13));
            button.setForeground(GameTheme.TEXT);
            button.setBackground(GameTheme.SURFACE);
            button.setFocusPainted(false);
            button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(GameTheme.BORDER),
                    BorderFactory.createEmptyBorder(7, 13, 7, 13)));
            button.addActionListener(event -> { filter = option; refreshCards(); });
            group.add(button); buttons.add(button);
        }
        JLabel title = label("职业角色库", 20, GameTheme.GOLD);
        title.setFont(GameTheme.display(20));
        bar.add(title, BorderLayout.WEST);
        bar.add(buttons, BorderLayout.CENTER);
        JPanel meta = new JPanel(new GridLayout(2, 1, 0, 1)); meta.setOpaque(false);
        meta.add(label("ORIGIN CODEX", 10, GameTheme.ICE)); meta.add(count);
        bar.add(meta, BorderLayout.EAST);
        return bar;
    }

    private JScrollPane cardScroll() {
        cards.setOpaque(false);
        cards.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 2));
        JScrollPane scroll = new JScrollPane(cards);
        scroll.setBorder(BorderFactory.createLineBorder(GameTheme.BORDER));
        scroll.getViewport().setBackground(new Color(25, 39, 54));
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    private JPanel details() {
        JPanel right = new JPanel(new BorderLayout(0, 10));
        right.setOpaque(false); right.setPreferredSize(new Dimension(370, 0));
        right.add(artwork, BorderLayout.NORTH);
        JPanel heading = new JPanel(new GridLayout(0, 1, 0, 4)); heading.setOpaque(false);
        heading.add(name); heading.add(epithet); heading.add(role); heading.add(stats); heading.add(skill);
        right.add(heading, BorderLayout.CENTER);
        JPanel lower = new JPanel(new BorderLayout(0, 6)); lower.setOpaque(false); lower.setPreferredSize(new Dimension(370, 180));
        lower.add(label("角色档案 / HISTORICAL IMAGERY", 10, GameTheme.MUTED), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(story); scroll.setBorder(BorderFactory.createLineBorder(GameTheme.BORDER));
        lower.add(scroll, BorderLayout.CENTER); right.add(lower, BorderLayout.SOUTH);
        return right;
    }

    private void refreshCards() {
        cards.removeAll();
        List<Integer> visible = IntStream.range(0, ENTRIES.size()).boxed()
                .filter(i -> filter == Filter.ALL || ENTRIES.get(i).filter() == filter).collect(Collectors.toList());
        for (int index : visible) cards.add(new LibraryCard(index));
        count.setText(visible.size() + " / " + ENTRIES.size());
        cards.revalidate(); cards.repaint();
    }

    private void select(int index) {
        selected = index;
        Entry entry = ENTRIES.get(index);
        artwork.setStyle(entry.style()); name.setText(entry.name()); epithet.setText(entry.epithet());
        role.setText(entry.filter().code + "  /  " + entry.origin()); stats.setText(entry.stats()); skill.setText(entry.skill());
        story.setText(entry.story()); story.setCaretPosition(0); refreshCards();
    }

    private final class LibraryCard extends JButton {
        private final int index;
        LibraryCard(int index) {
            this.index = index;
            setFocusable(true); setFocusPainted(false); setContentAreaFilled(false); setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder()); setMargin(new Insets(0, 0, 0, 0));
            getAccessibleContext().setAccessibleName("角色 " + ENTRIES.get(index).name());
            addActionListener(event -> select(index));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { repaint(); }
                @Override public void mouseExited(MouseEvent event) { repaint(); }
            });
        }
        @Override public Dimension getPreferredSize() { return new Dimension(238, 292); }
        @Override protected void paintComponent(Graphics graphics) {
            Entry entry = ENTRIES.get(index);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight(); boolean active = selected == index;
            Color edge = active ? entry.accent() : GameTheme.BORDER;
            g.setPaint(new GradientPaint(0, 0, new Color(54, 32, 49), w, h, new Color(22, 31, 48)));
            g.fillRoundRect(1, 1, w - 2, h - 2, 8, 8);
            g.setColor(new Color(entry.accent().getRed(), entry.accent().getGreen(), entry.accent().getBlue(), active ? 42 : 20));
            g.fillOval(w - 130, -42, 180, 180);
            BufferedImage image = GameArt.actorImage(entry.style());
            g.drawImage(image, (w - 136) / 2, 17, 136, 204, null);
            g.setColor(new Color(18, 22, 35, 224)); g.fillRect(12, h - 76, w - 24, 62);
            g.setColor(edge); g.setStroke(new BasicStroke(active ? 2.4f : 1.1f)); g.drawRoundRect(1, 1, w - 3, h - 3, 8, 8);
            g.setColor(entry.accent()); g.fillRect(20, h - 63, 4, 38);
            g.setColor(GameTheme.TEXT); g.setFont(GameTheme.display(19)); g.drawString(entry.name(), 34, h - 43);
            g.setColor(GameTheme.MUTED); g.setFont(GameTheme.font(Font.PLAIN, 11)); g.drawString(entry.filter().title + "  ·  " + entry.epithet(), 34, h - 23);
            if (getModel().isRollover()) { g.setColor(new Color(255, 255, 255, 22)); g.fillRoundRect(2, 2, w - 4, h - 4, 8, 8); }
            g.dispose();
        }
    }

    private static JTextArea textArea() {
        JTextArea area = new JTextArea(); area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true);
        area.setFont(GameTheme.font(Font.PLAIN, 14)); area.setForeground(GameTheme.TEXT); area.setBackground(new Color(25, 39, 54));
        area.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12)); return area;
    }

    private static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text); label.setFont(GameTheme.font(Font.PLAIN, size)); label.setForeground(color); return label;
    }
}
