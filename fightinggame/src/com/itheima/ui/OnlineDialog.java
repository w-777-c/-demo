package com.itheima.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.itheima.doman.Character;
import com.itheima.game.Battle;
import com.itheima.net.DuelClient;
import com.itheima.net.LocalServer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;

@SuppressWarnings("serial")
public final class OnlineDialog extends GameDialog {
    private final JTextField address = new JTextField("127.0.0.1:8765", 21);
    private final JTextField name;
    private final JSpinner port = new JSpinner(new SpinnerNumberModel(8765, 1024, 65535, 1));
    private final GameButton host = button("创建房间"), connect = button("连接"), disconnect = button("断开");
    private final GameButton join = button("入席"), ready = button("准备"), rematch = button("再战"), resign = button("认输"), leave = button("离席");
    private final GameButton[] actions = new GameButton[5];
    private final JLabel status = label("未连接"), identity = label("观战席"), seats = label("等待玩家");
    private final ArenaPanel arena = new ArenaPanel();
    private final JTextArea log = new JTextArea();
    private DuelClient client;
    private LocalServer server;
    private boolean starting, disposed;
    private String previousPhase = "";

    public OnlineDialog(JFrame owner, String nickname) {
        super(owner, "联机竞技场 / 双人对决");
        GameAudio.start(); GameAudio.scene(false);
        name = new JTextField(nickname, 10);
        port.setEditor(new JSpinner.NumberEditor(port, "0"));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 730)); setSize(1140, 820); setLocationRelativeTo(owner);
        JPanel root = panel(new BorderLayout(0, 14));
        root.setBorder(BorderFactory.createEmptyBorder(14, 20, 16, 20)); setContentPane(root);
        JPanel heading = panel(new GridLayout(0, 1, 0, 10));
        host.setGlyph("users"); ready.setGlyph("swords"); rematch.setGlyph("sparkles");
        JPanel connection = panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        connection.add(label("地址")); connection.add(address); connection.add(connect); connection.add(disconnect);
        connection.add(label("端口")); connection.add(port); connection.add(host); heading.add(connection);
        JPanel profile = panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        profile.add(label("昵称")); profile.add(name); profile.add(join); profile.add(identity); profile.add(seats); heading.add(profile);
        heading.add(status); root.add(heading, BorderLayout.NORTH);
        JPanel middle = panel(new BorderLayout(14, 10)); middle.add(arena, BorderLayout.CENTER);
        JPanel commands = panel(new GridLayout(1, 5, 8, 0));
        String[] labels = {"普通攻击", "强力一击", "生命汲取", "防御", "治疗药水"};
        String[] glyphs = {"sword", "swords", "heart-pulse", "shield", "flask-conical"};
        String[] details = {"100%攻击", "180%攻击 / -10 HP", "120%攻击 / 吸血", "下次命中减伤50%", "恢复50 HP"};
        for (int i = 0; i < actions.length; i++) {
            String action = Battle.Action.values()[i].name();
            actions[i] = button(labels[i]); actions[i].setDetail(details[i], i == 1 ? GameTheme.GOLD : GameTheme.GREEN);
            actions[i].setGlyph(glyphs[i]);
            actions[i].addActionListener(event -> command("action", action)); commands.add(actions[i]);
        }
        commands.setPreferredSize(new Dimension(850, 90)); middle.add(commands, BorderLayout.SOUTH);
        root.add(middle, BorderLayout.CENTER);
        JPanel bottom = panel(new BorderLayout(0, 10));
        JPanel lobby = panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        for (JButton button : new JButton[]{ready, rematch, resign, leave}) lobby.add(button);
        bottom.add(lobby, BorderLayout.NORTH);
        log.setEditable(false); log.setLineWrap(true); log.setWrapStyleWord(true);
        log.setFont(GameTheme.font(Font.PLAIN, 12)); log.setBackground(GameTheme.SURFACE); log.setForeground(GameTheme.TEXT);
        log.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JScrollPane scroll = new JScrollPane(log); scroll.setPreferredSize(new Dimension(800, 82));
        scroll.setBorder(BorderFactory.createEmptyBorder()); bottom.add(scroll, BorderLayout.CENTER); root.add(bottom, BorderLayout.SOUTH);
        connect.addActionListener(event -> connect()); host.addActionListener(event -> host());
        disconnect.addActionListener(event -> { if (confirmLeave()) stop(); });
        join.addActionListener(event -> command("join", name.getText().strip()));
        ready.addActionListener(event -> command("ready", "")); rematch.addActionListener(event -> command("rematch", ""));
        resign.addActionListener(event -> { if (confirm("认输并结束本局？")) command("resign", ""); });
        leave.addActionListener(event -> { if (confirm("离开席位？进行中的对局将判负。")) command("leave", ""); });
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { if (confirmLeave()) dispose(); }
        });
        refreshOnline(null);
    }
    private boolean confirm(String text) { return GameDialogs.showConfirmDialog(this, text, "联机竞技场", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION; }
    private boolean confirmLeave() {
        return client == null && server == null || confirm(server != null ? "关闭房间并断开连接？房间内所有玩家都将断开。" : "断开连接并退出当前对局？");
    }
    private void connect() {
        try { client = new DuelClient(DuelClient.endpoint(address.getText()), this::refreshOnline); refreshOnline(client); }
        catch (IllegalArgumentException invalid) { status.setText(invalid.getMessage()); }
    }
    private void host() {
        try { port.commitEdit(); } catch (java.text.ParseException invalid) { status.setText("请输入有效端口。"); return; }
        int selected = (int) port.getValue();
        starting = true; server = new LocalServer(); LocalServer launching = server;
        refreshOnline(null); status.setText("房间启动中");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception { launching.start(selected); return localAddress(selected); }
            @Override protected void done() {
                if (disposed || server != launching) { launching.close(); return; }
                starting = false;
                try {
                    String local = get(); address.setText(local);
                    client = new DuelClient(DuelClient.endpoint("127.0.0.1:" + selected), OnlineDialog.this::refreshOnline);
                    refreshOnline(client);
                } catch (Exception failure) {
                    launching.close(); server = null; refreshOnline(null);
                    status.setText(failure.getCause() == null ? failure.getMessage() : failure.getCause().getMessage());
                }
            }
        }.execute();
    }
    private static String localAddress(int port) throws Exception {
        for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!network.isUp() || network.isLoopback() || network.isVirtual()) continue;
            for (var ip : Collections.list(network.getInetAddresses())) {
                if (ip instanceof Inet4Address && ip.isSiteLocalAddress()) return ip.getHostAddress() + ":" + port;
            }
        }
        return "127.0.0.1:" + port;
    }
    private void command(String type, String value) { if (client != null) client.command(type, value); }
    private void refreshOnline(DuelClient current) {
        if (disposed) return;
        boolean idle = current == null && !starting;
        host.setEnabled(idle); connect.setEnabled(idle); address.setEditable(idle); port.setEnabled(idle);
        disconnect.setEnabled(!idle); name.setEnabled(current == null || current.seat() < 0);
        boolean available = current != null && current.available();
        int seat = current == null ? -1 : current.seat();
        JsonNode state = current == null ? null : current.state();
        JsonNode me = fighter(state, seat);
        JsonNode p1 = fighter(state, 0), p2 = fighter(state, 1);
        String phase = state == null ? "" : state.path("phase").asText();
        GameAudio.scene(phase.equals("FIGHTING"));
        if (phase.equals("FINISHED") && !previousPhase.equals(phase)) GameAudio.effect(seat >= 0 && state.path("winner").asInt(-1) != seat ? "defeat" : "victory");
        previousPhase = phase;
        boolean bothOnline = p1 != null && p2 != null && p1.path("connected").asBoolean() && p2.path("connected").asBoolean();
        join.setEnabled(available && seat < 0 && (p1 == null || p2 == null));
        ready.setEnabled(available && me != null && phase.equals("WAITING") && !me.path("ready").asBoolean());
        ready.setText(me != null && me.path("ready").asBoolean() ? "已准备" : "准备");
        rematch.setEnabled(available && me != null && bothOnline && phase.equals("FINISHED") && !me.path("rematch").asBoolean());
        rematch.setText(me != null && me.path("rematch").asBoolean() ? "等待再战" : "再战");
        resign.setEnabled(available && me != null && (phase.equals("FIGHTING") || phase.equals("PAUSED")));
        leave.setEnabled(available && me != null);
        identity.setText(seat < 0 ? "观战席" : "你的席位 P" + (seat + 1));
        seats.setText("P1 " + presence(p1) + "   /   P2 " + presence(p2));
        status.setText(current == null ? starting ? "房间启动中" : "未连接" : current.status()
                + (server == null ? "" : " / 房主 " + address.getText()) + (current.error().isEmpty() ? "" : " / " + current.error()));
        status.setToolTipText(status.getText());
        for (int i = 0; i < actions.length; i++) {
            JsonNode skill = null;
            if (me != null) for (JsonNode candidate : me.path("skills")) if (candidate.path("action").asText().equals(Battle.Action.values()[i].name())) skill = candidate;
            actions[i].setEnabled(available && phase.equals("FIGHTING") && state.path("activeSeat").asInt() == seat
                    && skill != null && skill.path("available").asBoolean());
            actions[i].setToolTipText(skill == null ? "" : skill.path("reason").asText());
        }
        actions[2].setText(me == null || me.path("cooldown").asInt() == 0 ? "生命汲取" : "汲取 (" + me.path("cooldown").asInt() + ")");
        actions[4].setText(me == null ? "治疗药水" : "药水 (" + me.path("potions").asInt() + ")");
        String title = switch (phase) {
            case "FIGHTING" -> "P" + (state.path("activeSeat").asInt() + 1) + " 的回合";
            case "PAUSED" -> "等待重连";
            case "FINISHED" -> state.path("winner").asInt(-1) < 0 ? "平局" : "P" + (state.path("winner").asInt() + 1) + " 获胜";
            default -> "等待开战";
        };
        String subtitle = switch (phase) {
            case "FIGHTING" -> "第 " + state.path("turn").asInt() + " 回合";
            case "FINISHED" -> state.path("reason").asText();
            case "PAUSED" -> "席位保留 30 秒";
            default -> "双方准备后开战";
        };
        arena.setDuel(display(p1), display(p2), title, subtitle);
        StringBuilder text = new StringBuilder();
        if (state != null) for (JsonNode entry : state.path("log")) text.append(entry.path("text").asText()).append('\n');
        if (!log.getText().contentEquals(text)) { log.setText(text.toString()); log.setCaretPosition(log.getDocument().getLength()); }
    }
    private static JsonNode fighter(JsonNode state, int seat) {
        if (state != null) for (JsonNode fighter : state.path("fighters")) if (fighter.path("seat").asInt() == seat) return fighter;
        return null;
    }
    private static String presence(JsonNode fighter) {
        return fighter == null ? "空席" : !fighter.path("connected").asBoolean() ? "断线" : fighter.path("ready").asBoolean() ? "已准备" : "已入席";
    }
    private static Character display(JsonNode fighter) {
        if (fighter == null) return null;
        Character view = new Character(fighter.path("name").asText(), fighter.path("maxHp").asInt(), fighter.path("attack").asInt(), fighter.path("defense").asInt());
        view.takeDamage(view.getMaxHP() - fighter.path("hp").asInt());
        if (fighter.path("guarding").asBoolean()) view.defend();
        return view;
    }
    private void stop() {
        if (client != null) client.close(); client = null;
        if (server != null) server.close(); server = null;
        starting = false; refreshOnline(null);
    }
    @Override public void dispose() { disposed = true; stop(); super.dispose(); if (getOwner() == null) GameAudio.shutdown(); }
    private static JPanel panel(java.awt.LayoutManager layout) { JPanel panel = new JPanel(layout); panel.setBackground(GameTheme.BACKGROUND); return panel; }
    private static JLabel label(String text) { JLabel label = new JLabel(text); label.setForeground(GameTheme.TEXT); label.setFont(GameTheme.font(Font.PLAIN, 12)); return label; }
    private static GameButton button(String text) { return new GameButton(text, GameTheme.SURFACE); }
}
