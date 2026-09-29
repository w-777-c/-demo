package com.itheima.ui;

import com.itheima.doman.HeroCharacter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;

/** 可浏览的角色图鉴：左侧角色卡、中间立绘、右侧故事与技能页签。 */
@SuppressWarnings("serial")
final class CharacterArchiveDialog extends GameDialog {
    private static final String[] TITLES = {"铁卫", "狂刃", "灵术师", "银幕守卫", "绯刃使", "星辉术士"};
    private static final String[] SUBTITLES = {"不屈壁垒", "破阵斩", "灵魂潮汐", "古典守序者", "赤色追猎者", "终幕观星者"};
    private static final String[] STORIES = {
            "曾在剧场落幕前独自守住最后一道门。她相信，所谓勇气不是没有恐惧，而是在幕布落下前仍然向前一步。",
            "把每一场战斗都当作登台。双刃划开灯影，也划开命运写好的台词；她只相信自己的下一次出招。",
            "能够听见舞台深处的潮汐。她以星辉和纸牌编织法阵，把敌人的力量化为自己的续章。",
            "王座大厅留下的古老守卫，铠甲里封存着一段无人讲述的誓言。",
            "从侧幕穿行的追猎者，擅长在观众察觉之前结束一场演出。",
            "负责为终幕点亮星灯的术士，所有法术都像一段即兴乐章。"
    };
    private static final String[] SKILLS = {"普通攻击 · 铁壁反击\n强力一击 · 盾鸣\n生命汲取 · 不屈回响\n职业大招 · 不屈壁垒", "普通攻击 · 双刃\n强力一击 · 断幕\n生命汲取 · 血契\n职业大招 · 破阵斩", "普通攻击 · 星牌\n强力一击 · 奥术冲击\n生命汲取 · 潮汐回收\n职业大招 · 灵魂潮汐", "重击 · 150%攻击\n防御姿态 · 伤害减半", "快速攻击 · 连击两次\n敏捷 · 先手压制", "火球术 · 180%攻击\n星辉 · 远程爆发"};

    private final CharacterPreview artwork = new CharacterPreview(false, true);
    private final JLabel name = label("灵术师", 29, GameTheme.GOLD);
    private final JLabel subtitle = label("灵魂潮汐", 15, GameTheme.ICE);
    private final JLabel role = label("MYSTIC  /  CONTRACT 03", 10, GameTheme.MUTED);
    private final JLabel stats = label("生命 180   攻击 30   防御 2", 13, GameTheme.TEXT);
    private final JTextArea story = textArea();
    private final JTextArea skill = textArea();

    CharacterArchiveDialog(Window owner) {
        super(owner, "角色图鉴 · CONTRACT ARCHIVE");
        JPanel content = new JPanel(new BorderLayout(16, 0));
        content.setBackground(new Color(20, 30, 43)); content.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        content.add(characterList(), BorderLayout.WEST); content.add(artwork, BorderLayout.CENTER); content.add(details(), BorderLayout.EAST);
        setContentPane(content); setSize(new Dimension(1060, 700)); setMinimumSize(new Dimension(920, 620)); setLocationRelativeTo(owner);
        select(2);
    }
    private JPanel characterList() {
        JPanel list = new JPanel(new GridLayout(0, 1, 0, 8)); list.setOpaque(false); list.setPreferredSize(new Dimension(192, 0));
        JLabel heading = label("资料 / 故事", 17, GameTheme.GOLD); heading.setBorder(BorderFactory.createEmptyBorder(0, 8, 7, 0)); list.add(heading);
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < TITLES.length; i++) {
            final int index = i; GameButton button = new GameButton(TITLES[i], GameTheme.SURFACE); button.setGlyph(i < 3 ? "crown" : "swords");
            button.setHorizontalAlignment(javax.swing.SwingConstants.LEFT); button.setDetail(SUBTITLES[i], i < 3 ? GameTheme.ICE : GameTheme.MUTED);
            button.getAccessibleContext().setAccessibleName("角色 " + TITLES[i]); button.addActionListener(event -> select(index));
            group.add(button); list.add(button);
        }
        return list;
    }
    private JPanel details() {
        JPanel right = new JPanel(new BorderLayout(0, 12)); right.setOpaque(false); right.setPreferredSize(new Dimension(306, 0));
        JPanel heading = new JPanel(new GridLayout(0, 1, 0, 4)); heading.setOpaque(false);
        heading.add(name); heading.add(subtitle); heading.add(role); heading.add(stats); right.add(heading, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane(); tabs.setBackground(new Color(30, 49, 67)); tabs.setForeground(GameTheme.TEXT);
        tabs.addTab("角色故事", scroll(story)); tabs.addTab("技能档案", scroll(skill)); right.add(tabs, BorderLayout.CENTER);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); footer.setOpaque(false);
        footer.add(label("剧场收藏册", 11, GameTheme.MUTED)); right.add(footer, BorderLayout.SOUTH); return right;
    }
    private void select(int index) {
        artwork.setStyle(index); name.setText(TITLES[index]); subtitle.setText(SUBTITLES[index]);
        role.setText(index < 3 ? "CONTRACT " + String.format("%02d", index + 1) + "  /  PLAYABLE" : "ENCOUNTER " + String.format("%02d", index - 2) + "  /  OPPONENT");
        if (index < 3) {
            HeroCharacter.Style style = HeroCharacter.Style.values()[index]; stats.setText("生命 " + (100 + style.health * 10) + "   攻击 " + (10 + style.power * 2) + "   防御 " + style.armor);
        } else stats.setText("舞台敌影   /   由远征进度召唤");
        story.setText(STORIES[index]); skill.setText(SKILLS[index]); story.setCaretPosition(0); skill.setCaretPosition(0);
    }
    private static JScrollPane scroll(JTextArea area) { JScrollPane scroll = new JScrollPane(area); scroll.setBorder(BorderFactory.createLineBorder(GameTheme.BORDER)); return scroll; }
    private static JTextArea textArea() {
        JTextArea area = new JTextArea(); area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true); area.setFont(GameTheme.font(Font.PLAIN, 15));
        area.setForeground(GameTheme.TEXT); area.setBackground(new Color(25, 39, 54)); area.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14)); return area;
    }
    private static JLabel label(String text, int size, Color color) { JLabel label = new JLabel(text); label.setFont(GameTheme.font(Font.PLAIN, size)); label.setForeground(color); return label; }
}
