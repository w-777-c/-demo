package com.itheima.ui;

import com.itheima.doman.HeroCharacter;
import com.itheima.doman.User;
import com.itheima.game.Battle;
import com.itheima.game.GameSession;
import com.itheima.storage.Passwords;
import com.itheima.storage.UserStore;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

@SuppressWarnings("serial")
public final class GameFrame extends JFrame {
    public static final Color BACKGROUND = GameTheme.BACKGROUND;
    public static final Color SURFACE = GameTheme.SURFACE;
    public static final Color TEXT = GameTheme.TEXT;
    public static final Color MUTED = GameTheme.MUTED;
    public static final Color GREEN = GameTheme.GREEN;
    public static final Color RED = GameTheme.RED;
    private final UserStore store;
    private final Random random;
    private final ArenaPanel arena = new ArenaPanel();
    private final JTextPane log = new JTextPane();
    private final ChallengeTrack track = new ChallengeTrack();
    private final CharacterPreview portrait = new CharacterPreview(true);
    private final JLabel account = label("游客", 13, MUTED);
    private final JLabel stage = label("竞技场 / 待出战", 14, TEXT);
    private final JLabel level = label("Lv. 01", 24, TEXT);
    private final JLabel[] stats = new JLabel[4];
    private final JLabel[] records = new JLabel[4];
    private final JLabel recordTitle = label("个人纪录", 12, MUTED);
    private final JLabel phase = label("待命", 12, GameTheme.GOLD);
    private final JLabel status = label("准备就绪", 12, MUTED);
    private final JButton primary = button("开始挑战", GREEN);
    private final JButton retire = button("撤退结算", SURFACE);
    private final JButton login = button("登录 / 注册", SURFACE);
    private final GameButton[] actions = new GameButton[5];
    private final GameButton ultimate = button("职业大招", SURFACE);
    private final JLabel vocation = label("职业 / 未选择", 13, GREEN);
    private final JLabel intention = label("十关远征 / 击败王座守关者", 12, MUTED);
    private final JLabel energy = label("能量 0 / 100", 12, GameTheme.GOLD);
    private final JLabel loot = label("本局强化 0", 12, MUTED);
    private User user;
    private GameSession session;
    private boolean recorded;
    private boolean dirty;

    public GameFrame(UserStore store, Random random) {
        super("铁境竞技场 · 绯幕剧场 | IRON ARENA");
        this.store = store;
        this.random = random;
        GameTheme.install();
        GameAudio.start();
        setIconImage(UiAssets.image("crown"));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 760));
        setSize(1220, 860);
        setLocationRelativeTo(null);
        JPanel root = panel(new BorderLayout(0, 0), BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(16, 22, 14, 22));
        setContentPane(root);

        JPanel header = panel(new BorderLayout(), BACKGROUND);
        JPanel brand = panel(new GridLayout(2, 1, 0, 3), BACKGROUND);
        JLabel title = label("铁境竞技场", 32, GameTheme.GOLD); title.setFont(GameTheme.display(32)); brand.add(title);
        brand.add(label("绯幕剧场   /   CRIMSON THEATRE", 11, GREEN));
        header.add(brand, BorderLayout.WEST);
        JPanel profile = panel(new FlowLayout(FlowLayout.RIGHT, 12, 5), BACKGROUND);
        GameButton leaderboard = button("战绩榜", SURFACE); leaderboard.setGlyph("trophy");
        leaderboard.addActionListener(event -> showLeaderboard());
        login.addActionListener(event -> accountAction());
        GameButton online = button("联机对战", GREEN); online.setGlyph("users");
        online.addActionListener(event -> { new OnlineDialog(this, user == null ? "挑战者" : user.getUsername()).setVisible(true); refresh(); });
        GameButton settings = button("", SURFACE); settings.setGlyph("settings-2"); settings.setToolTipText("音画设置");
        settings.getAccessibleContext().setAccessibleName("音画设置"); settings.addActionListener(event -> new SettingsDialog(this).setVisible(true));
        profile.add(online); profile.add(account); profile.add(leaderboard); profile.add(login);
        profile.add(settings);
        header.add(profile, BorderLayout.EAST);
        header.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, GameTheme.BORDER),
                BorderFactory.createEmptyBorder(0, 0, 16, 0)));
        root.add(header, BorderLayout.NORTH);

        JPanel body = panel(new BorderLayout(18, 0), BACKGROUND);
        body.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
        JPanel play = panel(new BorderLayout(0, 10), BACKGROUND);
        JPanel stageRow = panel(new BorderLayout(), BACKGROUND);
        stageRow.add(stage, BorderLayout.WEST);
        stageRow.add(phase, BorderLayout.EAST);
        JPanel stageHeader = panel(new BorderLayout(0, 12), BACKGROUND);
        stageHeader.add(stageRow, BorderLayout.NORTH);
        stageHeader.add(track, BorderLayout.SOUTH);
        stageHeader.add(intention, BorderLayout.CENTER);
        play.add(stageHeader, BorderLayout.NORTH);
        play.add(arena, BorderLayout.CENTER);
        JPanel controls = panel(new GridLayout(1, 5, 8, 0), BACKGROUND);
        controls.setPreferredSize(new Dimension(700, 90));
        String[] names = {"普通攻击", "强力一击", "生命汲取", "防御", "治疗药水"};
        String[] glyphs = {"sword", "swords", "heart-pulse", "shield", "flask-conical"};
        String[] tips = {"造成攻击力减防御力的伤害，最低1点。", "消耗10 HP，造成180%攻击力伤害。", "120%攻击力；回复实际伤害的50% (最低1点)；间隔2个有效回合。", "下次命中伤害减半，多段攻击仅抵挡第一段。", "恢复最多50 HP；每次出战消耗一瓶。"};
        for (int i = 0; i < actions.length; i++) {
            final Battle.Action action = Battle.Action.values()[i];
            actions[i] = button(names[i], SURFACE);
            actions[i].setGlyph(glyphs[i]);
            actions[i].setToolTipText(tips[i]);
            actions[i].addActionListener(event -> takeTurn(action));
            controls.add(actions[i]);
        }
        play.add(controls, BorderLayout.SOUTH);
        body.add(play, BorderLayout.CENTER);
        body.add(sidebar(), BorderLayout.WEST);
        root.add(body, BorderLayout.CENTER);

        JPanel bottom = panel(new BorderLayout(0, 8), BACKGROUND);
        bottom.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        JPanel logTitle = panel(new BorderLayout(), BACKGROUND);
        logTitle.add(label("战斗记录", 14, TEXT), BorderLayout.WEST);
        logTitle.add(status, BorderLayout.EAST);
        bottom.add(logTitle, BorderLayout.NORTH);
        log.setEditable(false);
        log.setFont(GameTheme.font(Font.PLAIN, 13));
        log.setBackground(SURFACE); log.setForeground(TEXT); log.setCaretColor(GREEN);
        log.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JScrollPane scroll = new JScrollPane(log);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(800, 96));
        bottom.add(scroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        primary.addActionListener(event -> advance());
        ((GameButton) primary).setGlyph("chevron-right"); ultimate.setGlyph("sparkles");
        retire.addActionListener(event -> retire());
        ultimate.addActionListener(event -> {
            if (session == null || session.getState() != GameSession.State.FIGHTING) return;
            int hp = session.getHero().getHP(), enemyHP = session.getEnemy().getHP();
            int round = session.getBattle().getRound();
            session.ultimate().forEach(this::append);
            if (session.getBattle().getRound() != round) {
                GameAudio.effect("magic");
                arena.animateUltimate(hp, enemyHP);
            }
            if (session.getState() == GameSession.State.FINISHED) finish();
            refresh();
        });
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { closeGame(); }
            @Override public void windowIconified(WindowEvent event) { GameAudio.pause(true); }
            @Override public void windowDeiconified(WindowEvent event) { GameAudio.pause(false); }
        });
        append("竞技场已开放。等待挑战者入场。", MUTED);
        refresh();
    }

    private JPanel sidebar() {
        JPanel side = panel(new BorderLayout(0, 12), BACKGROUND);
        side.setPreferredSize(new Dimension(200, 350));
        side.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, GameTheme.BORDER),
                BorderFactory.createEmptyBorder(0, 0, 0, 16)));
        JPanel info = panel(null, BACKGROUND);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(label("契约者档案", 12, GameTheme.GOLD));
        info.add(Box.createVerticalStrut(8));
        portrait.setAlignmentX(0); info.add(portrait);
        info.add(Box.createVerticalStrut(8));
        info.add(level);
        info.add(Box.createVerticalStrut(8));
        info.add(vocation);
        info.add(Box.createVerticalStrut(10));
        info.add(statRows(new String[]{"生命", "攻击", "防御", "本局胜场"}, stats, 23));
        info.add(Box.createVerticalStrut(16));
        info.add(recordTitle);
        info.add(Box.createVerticalStrut(8));
        info.add(statRows(new String[]{"最高连胜", "累计胜场", "挑战局数", "通关次数"}, records, 22));
        info.add(Box.createVerticalGlue());
        // Let the viewport own the width while preserving the full dossier height.
        info.setPreferredSize(new Dimension(0, info.getPreferredSize().height));
        JScrollPane infoScroll = new JScrollPane(info, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        infoScroll.setBorder(BorderFactory.createEmptyBorder());
        infoScroll.getVerticalScrollBar().setUnitIncrement(20);
        side.add(infoScroll, BorderLayout.CENTER);
        JPanel buttons = panel(new GridLayout(3, 1, 0, 8), BACKGROUND);
        buttons.setPreferredSize(new Dimension(200, 132));
        buttons.add(ultimate); buttons.add(primary); buttons.add(retire);
        JPanel commands = panel(new BorderLayout(0, 9), BACKGROUND);
        JPanel charge = panel(new GridLayout(2, 1, 0, 3), BACKGROUND); charge.add(energy); charge.add(loot);
        commands.add(charge, BorderLayout.NORTH); commands.add(buttons, BorderLayout.CENTER);
        side.add(commands, BorderLayout.SOUTH);
        return side;
    }

    private JPanel statRows(String[] names, JLabel[] values, int height) {
        JPanel rows = panel(new GridLayout(names.length, 1, 0, 0), BACKGROUND);
        rows.setAlignmentX(0);
        rows.setMaximumSize(new Dimension(Integer.MAX_VALUE, names.length * height));
        rows.setPreferredSize(new Dimension(190, names.length * height));
        for (int i = 0; i < names.length; i++) {
            JPanel row = panel(new BorderLayout(6, 0), BACKGROUND);
            row.add(label(names[i], 12, MUTED), BorderLayout.WEST);
            values[i] = label("--", 13, TEXT);
            values[i].setHorizontalAlignment(JLabel.RIGHT);
            row.add(values[i], BorderLayout.CENTER);
            rows.add(row);
        }
        return rows;
    }

    private void advance() {
        if (session != null && session.getState() == GameSession.State.RESTING) {
            if (!session.getRewards().isEmpty()) { chooseReward(); return; }
            session.nextBattle();
            arena.setSession(session);
            append("\n第 " + (session.getWins() + 1) + " 场：" + session.getEnemy().show());
            refresh();
            return;
        }
        JPanel form = new JPanel(new GridLayout(0, 2, 12, 12));
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        form.setPreferredSize(new Dimension(500, 264));
        JComboBox<String> mode = new JComboBox<>(new String[]{"十关挑战", "无尽试炼"});
        JComboBox<String> build = new JComboBox<>(new String[]{"铁卫 / 生存与防守", "狂刃 / 爆发输出", "灵术师 / 汲取与续航", "自定义 / 铁卫大招"});
        build.setSelectedIndex(2);
        CharacterPreview preview = new CharacterPreview(false);
        JPanel creation = panel(new BorderLayout(18, 0), BACKGROUND);
        creation.add(preview, BorderLayout.WEST); creation.add(form, BorderLayout.CENTER);
        JLabel special = new JLabel(HeroCharacter.Style.MYSTIC.ultimate);
        JSpinner health = new JSpinner(new SpinnerNumberModel(8, 0, 20, 1));
        JSpinner power = new JSpinner(new SpinnerNumberModel(10, 0, 20, 1));
        JLabel armor = new JLabel("2");
        health.setEnabled(false); power.setEnabled(false);
        Runnable allocation = () -> armor.setText(Integer.toString(20 - (int) health.getValue() - (int) power.getValue()));
        health.addChangeListener(event -> allocation.run());
        power.addChangeListener(event -> allocation.run());
        build.addActionListener(event -> {
            boolean custom = build.getSelectedIndex() == 3;
            health.setEnabled(custom); power.setEnabled(custom);
            HeroCharacter.Style style = HeroCharacter.Style.values()[custom ? 0 : build.getSelectedIndex()];
            preview.setStyle(style.ordinal());
            special.setText(style.ultimate);
            special.setToolTipText(style.description);
            if (!custom) { health.setValue(style.health); power.setValue(style.power); }
        });
        form.add(new JLabel("挑战模式")); form.add(mode);
        form.add(new JLabel("职业 / 共20属性点")); form.add(build);
        form.add(new JLabel("专属大招 / 100能量")); form.add(special);
        form.add(new JLabel("生命点数 / 每点 +10 HP")); form.add(health);
        form.add(new JLabel("攻击点数 / 每点 +2 ATK")); form.add(power);
        form.add(new JLabel("防御 / 剩余点数")); form.add(armor);
        while (GameDialogs.showConfirmDialog(this, creation, "创建挑战者", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try { health.commitEdit(); power.commitEdit(); }
            catch (java.text.ParseException exception) { message("请输入有效的属性点数。"); continue; }
            int hp = (int) health.getValue();
            int atk = (int) power.getValue();
            if (hp + atk > 20) { message("分配点数不能超过20点。"); continue; }
            String name = user == null ? "挑战者" : user.getUsername();
            HeroCharacter hero = build.getSelectedIndex() == 3 ? HeroCharacter.create(name, hp, atk, 20 - hp - atk)
                    : HeroCharacter.create(name, HeroCharacter.Style.values()[build.getSelectedIndex()]);
            session = new GameSession(hero, mode.getSelectedIndex() == 0, random, true);
            recorded = false;
            arena.setSession(session);
            log.setText("");
            append("挑战开始。" + session.getHero().show());
            append("第 1 场：" + session.getEnemy().show());
            refresh();
            break;
        }
    }

    private void chooseReward() {
        JDialog dialog = new GameDialog(this, "战后奖励");
        JPanel choices = panel(new GridLayout(3, 1, 0, 12), BACKGROUND);
        choices.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        for (GameSession.Reward reward : session.getRewards()) {
            GameButton option = button(reward.title, SURFACE);
            option.setGlyph("gem");
            option.setDetail(reward.description, GREEN);
            option.addActionListener(event -> { append(session.chooseReward(reward)); dialog.dispose(); refresh(); });
            choices.add(option);
        }
        dialog.setContentPane(choices); dialog.setSize(450, 420); dialog.setResizable(false);
        dialog.setLocationRelativeTo(this); dialog.setVisible(true);
    }

    private void takeTurn(Battle.Action action) {
        if (session == null || session.getState() != GameSession.State.FIGHTING) return;
        int previousHeroHP = session.getHero().getHP();
        int previousEnemyHP = session.getEnemy().getHP();
        int previousRound = session.getBattle().getRound();
        append("\n[第 " + session.getBattle().getRound() + " 回合]");
        session.play(action).forEach(this::append);
        if (session.getBattle().getRound() != previousRound) {
            GameAudio.effect(switch (action) { case DEFEND -> "guard"; case POTION -> "heal"; case DRAIN -> "magic"; default -> "attack"; });
            arena.animateTurn(action, previousHeroHP, previousEnemyHP);
        }
        if (session.getState() == GameSession.State.FINISHED) finish();
        refresh();
    }

    private void retire() {
        if (!active()) return;
        if (GameDialogs.showConfirmDialog(this, "结束本局并保存已获得的胜场？", "撤退结算", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        session.retire();
        append("\n本局已结算，共 " + session.getWins() + " 胜。");
        finish(); refresh();
    }

    private boolean active() { return session != null && session.getState() != GameSession.State.FINISHED; }

    private void finish() {
        if (!recorded) GameAudio.effect(session.getHero().isAlive() ? "victory" : "defeat");
        if (!recorded && user != null) {
            user.recordGame(session.getWins(), session.isCleared());
            dirty = true;
        }
        recorded = true;
        saveRecords();
    }

    private boolean saveRecords() {
        if (!dirty) return true;
        try {
            store.save(); dirty = false; status.setText("战绩已保存"); return true;
        } catch (IOException exception) {
            status.setText("战绩保存失败");
            message("战绩暂存于本次运行，保存失败：" + exception.getMessage());
            return false;
        }
    }

    private void refresh() {
        boolean fighting = session != null && session.getState() == GameSession.State.FIGHTING;
        GameAudio.scene(fighting);
        portrait.setStyle(session == null ? 2 : session.getHero().getStyle().ordinal());
        ultimate.setEnabled(fighting && session.getBattle().getEnergy() == 100);
        ultimate.setText(session == null ? "职业大招" : session.getHero().getStyle().ultimate);
        ultimate.setToolTipText(session == null ? "" : session.getHero().getStyle().description);
        energy.setText("能量 " + (session == null ? 0 : session.getBattle().getEnergy()) + " / 100");
        vocation.setText("职业 / " + (session == null ? "未选择" : session.getHero().getStyle().title));
        loot.setText("本局强化 " + (session == null ? 0 : session.getUpgrades().size()));
        loot.setToolTipText(session == null ? "" : String.join("、", session.getUpgrades()));
        intention.setText(session == null ? "十关远征 / 击败王座守关者" : fighting ? "敌方意图：" + session.getBattle().getIntent()
                : session.getState() == GameSession.State.RESTING ? "战后营地 / " + (session.getRewards().isEmpty() ? "准备启程" : "选择一项奖励") : "本次远征已结束");
        for (JButton action : actions) action.setEnabled(fighting);
        if (fighting) {
            HeroCharacter hero = session.getHero();
            actions[1].setEnabled(hero.getHP() > 10);
            actions[2].setEnabled(session.getBattle().getDrainCooldown() == 0);
            actions[4].setEnabled(hero.getPotions() > 0 && hero.getHP() < hero.getMaxHP());
        }
        actions[2].setText(session != null && session.getBattle().getDrainCooldown() > 0 ? "汲取 (" + session.getBattle().getDrainCooldown() + ")" : "生命汲取");
        actions[4].setText(session == null ? "治疗药水" : "药水 (" + session.getHero().getPotions() + ")");
        actions[0].setDetail("100% 攻击", GREEN);
        actions[1].setDetail("180% 攻击 / -10 HP", GameTheme.GOLD);
        actions[2].setDetail(session != null && session.getBattle().getDrainCooldown() > 0 ? "冷却中 / " + session.getBattle().getDrainCooldown() + " 回合" : "120% 攻击 / 吸血", GREEN);
        actions[3].setDetail("下次命中减伤 50%", new Color(136, 183, 215));
        actions[4].setDetail("恢复 50 HP", RED);
        primary.setEnabled(!fighting);
        primary.setText(fighting ? "战斗进行中" : session != null && session.getState() == GameSession.State.RESTING ? "下一场战斗" : session == null ? "开始挑战" : "再来一局");
        if (session != null && !session.getRewards().isEmpty() && session.getState() == GameSession.State.RESTING) primary.setText("选择战后奖励");
        primary.setBackground(fighting ? SURFACE : GREEN);
        retire.setEnabled(active());
        login.setEnabled(!active());
        login.setText(user == null ? "登录 / 注册" : "退出登录");
        account.setText(user == null ? "游客" : user.getUsername());
        track.setSession(session);
        if (session == null) {
            level.setText("Lv. 01");
            for (JLabel value : stats) value.setText("--");
        } else {
            HeroCharacter hero = session.getHero();
            int encounter = session.getState() == GameSession.State.FIGHTING || session.getEnemy().isAlive() ? session.getWins() + 1 : session.getWins();
            stage.setText((session.isChallenge() ? "十关挑战" : "无尽试炼") + " / " + session.getRegion() + " / 第 " + encounter + " 场");
            level.setText("Lv. " + String.format("%02d", hero.getLevel()));
            stats[0].setText(hero.getHP() + " / " + hero.getMaxHP());
            stats[1].setText(Integer.toString(hero.getAttack()));
            stats[2].setText(Integer.toString(hero.getDefense()));
            stats[3].setText(Integer.toString(session.getWins()));
            stats[0].setForeground(hero.getHP() * 3 < hero.getMaxHP() ? RED : TEXT);
        }
        recordTitle.setText(user == null ? "个人纪录 / 游客不保存" : "个人纪录");
        String[] totals = user == null ? new String[]{"--", "--", "--", "--"} : new String[]{Integer.toString(user.getBestWins()), Integer.toString(user.getTotalWins()), Integer.toString(user.getGames()), Integer.toString(user.getClears())};
        for (int i = 0; i < records.length; i++) records[i].setText(totals[i]);
        phase.setText(session == null ? "待命" : fighting ? "你的回合" : session.getState() == GameSession.State.RESTING ? "战间休整" : session.isCleared() ? "挑战通关" : "已结算");
        phase.setForeground(fighting ? GREEN : GameTheme.GOLD);
        if (!dirty) status.setText(session != null && session.getState() == GameSession.State.RESTING ? "战后恢复已完成" : active() ? "第 " + session.getBattle().getRound() + " 回合" : user == null ? "游客模式" : "账号与战绩已保存");
        arena.repaint();
    }

    private void accountAction() {
        if (user != null) {
            if (!saveRecords()) return;
            user = null; session = null; arena.setSession(null); stage.setText("竞技场 / 待出战"); refresh(); return;
        }
        JDialog dialog = new GameDialog(this, "账号");
        JPanel form = new JPanel(new GridLayout(0, 1, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JPanel modes = new JPanel(new GridLayout(1, 2, 0, 0));
        JToggleButton signIn = new JToggleButton("登录", true);
        JToggleButton signUp = new JToggleButton("注册");
        ButtonGroup group = new ButtonGroup(); group.add(signIn); group.add(signUp);
        signIn.setFocusPainted(false); signUp.setFocusPainted(false);
        modes.add(signIn); modes.add(signUp);
        JTextField name = new JTextField(24);
        JPasswordField password = new JPasswordField(24);
        JPasswordField confirm = new JPasswordField(24);
        JLabel confirmationLabel = new JLabel("确认密码");
        JLabel feedback = new JLabel(" "); feedback.setForeground(RED);
        JButton submit = button("登录", GREEN);
        form.add(modes); form.add(new JLabel("用户名：3-16位字母数字，至少一个字母")); form.add(name);
        form.add(new JLabel("密码：6-64位，包含字母和数字")); form.add(password);
        form.add(confirmationLabel); form.add(confirm); form.add(feedback); form.add(submit);
        confirm.setEnabled(false);
        java.awt.event.ActionListener changeMode = event -> {
            confirm.setEnabled(signUp.isSelected());
            confirmationLabel.setForeground(signUp.isSelected() ? TEXT : MUTED);
            submit.setText(signUp.isSelected() ? "注册并登录" : "登录");
            feedback.setText(" ");
        };
        signIn.addActionListener(changeMode); signUp.addActionListener(changeMode);
        submit.addActionListener(event -> {
            String username = name.getText().trim();
            char[] raw = password.getPassword();
            String secret = new String(raw);
            Arrays.fill(raw, '\0');
            boolean registering = signUp.isSelected();
            char[] repeated = confirm.getPassword();
            boolean matches = secret.equals(new String(repeated));
            Arrays.fill(repeated, '\0');
            if (!UserStore.validUsername(username) || !UserStore.validPassword(secret)) { feedback.setText("用户名或密码格式不正确。"); return; }
            if (registering && !matches) { feedback.setText("两次输入的密码不一致。"); return; }
            submit.setEnabled(false); signIn.setEnabled(false); signUp.setEnabled(false);
            name.setEnabled(false); password.setEnabled(false); confirm.setEnabled(false);
            feedback.setText("正在验证...");
            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            new SwingWorker<User, Void>() {
                @Override protected User doInBackground() throws Exception {
                    if (registering) return store.register(username, secret);
                    User found = store.find(username);
                    return found != null && Passwords.verify(secret, found.getPasswordHash()) ? found : null;
                }
                @Override protected void done() {
                    try {
                        User found = get();
                        if (found == null) feedback.setText("用户名或密码错误。");
                        else {
                            user = found; session = null; arena.setSession(null); stage.setText("竞技场 / 待出战");
                            append("已登录：" + user.getUsername()); refresh(); dialog.dispose();
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt(); feedback.setText("操作已中断。");
                    } catch (ExecutionException exception) {
                        feedback.setText(exception.getCause() instanceof IllegalArgumentException ? "用户名已存在。" : "账号保存失败，请检查数据目录权限。");
                    } finally {
                        submit.setEnabled(true); signIn.setEnabled(true); signUp.setEnabled(true);
                        name.setEnabled(true); password.setEnabled(true); confirm.setEnabled(signUp.isSelected());
                        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                    }
                }
            }.execute();
        });
        dialog.setContentPane(form); dialog.getRootPane().setDefaultButton(submit);
        dialog.pack(); dialog.setResizable(false); dialog.setLocationRelativeTo(this); dialog.setVisible(true);
    }

    private void showLeaderboard() {
        String[] columns = {"玩家", "最高连胜", "累计胜场", "挑战局数", "通关次数"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int column) { return column == 0 ? String.class : Integer.class; }
        };
        store.all().stream().sorted(Comparator.comparingInt(User::getBestWins).reversed().thenComparing(User::getUsername))
                .limit(20).forEach(player -> model.addRow(new Object[]{player.getUsername(), player.getBestWins(), player.getTotalWins(), player.getGames(), player.getClears()}));
        JTable table = new JTable(model) {
            @Override public java.awt.Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                java.awt.Component component = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row)) component.setBackground(row % 2 == 0 ? SURFACE : GameTheme.RAISED);
                if (component instanceof JLabel label) {
                    label.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
                    label.setHorizontalAlignment(column == 0 ? JLabel.LEFT : JLabel.RIGHT);
                }
                return component;
            }
        };
        table.setRowHeight(36); table.setFillsViewportHeight(true);
        table.setShowGrid(false); table.setIntercellSpacing(new Dimension(0, 0));
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(650, 36));
        JScrollPane scroll = new JScrollPane(table); scroll.setPreferredSize(new Dimension(650, 300));
        scroll.setBorder(BorderFactory.createLineBorder(GameTheme.BORDER));
        JPanel content = panel(new BorderLayout(0, 14), SURFACE);
        content.add(label(model.getRowCount() == 0 ? "暂无战绩" : "挑战者排名 / " + model.getRowCount() + " 位", 16, TEXT), BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        GameDialogs.showMessageDialog(this, content, "本地战绩榜", JOptionPane.PLAIN_MESSAGE);
    }

    private void closeGame() {
        if (active()) {
            if (GameDialogs.showConfirmDialog(this, "结算当前挑战并退出游戏？", "退出游戏", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            session.retire(); finish();
        }
        if (!saveRecords()) { refresh(); return; }
        dispose();
    }

    private void append(String message) {
        Color color = message.contains("被击败") || message.contains("消耗") || session != null && message.startsWith(session.getEnemy().getName()) ? RED
                : message.contains("恢复") || message.contains("击败") || message.contains("升级") || message.contains("通关") ? GREEN
                : message.contains("[第") ? GameTheme.GOLD : TEXT;
        append(message, color);
    }

    private void append(String message, Color color) {
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setForeground(style, color);
        StyleConstants.setSpaceAbove(style, 2);
        javax.swing.text.StyledDocument document = log.getStyledDocument();
        try {
            document.insertString(document.getLength(), message + "\n", style);
            if (document.getLength() > 30000) document.remove(0, 10000);
        } catch (javax.swing.text.BadLocationException exception) { throw new IllegalStateException(exception); }
        log.setCaretPosition(document.getLength());
    }

    private void message(String message) { GameDialogs.showMessageDialog(this, message, "铁境竞技场", JOptionPane.INFORMATION_MESSAGE); }

    @Override public void dispose() { super.dispose(); GameAudio.shutdown(); }

    private static JPanel panel(java.awt.LayoutManager layout, Color color) {
        JPanel panel = new JPanel(layout); panel.setBackground(color); return panel;
    }

    private static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text); label.setFont(GameTheme.font(Font.PLAIN, size)); label.setForeground(color); label.setAlignmentX(0); return label;
    }

    private static GameButton button(String text, Color background) { return new GameButton(text, background); }
}
