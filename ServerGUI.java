import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * ServerGUI.java — Admin Dashboard Server
 * Dark-themed Swing GUI showing live active users, a real-time traffic log,
 * and an admin Kick button. Handles all client Object streams internally.
 */
public class ServerGUI extends JFrame {

    static final int PORT = 12345;

    // ── Palette ───────────────────────────────────────────────────────────────
    static final Color BG      = new Color(10, 14, 20);
    static final Color SURFACE = new Color(18, 24, 32);
    static final Color CARD    = new Color(28, 35, 46);
    static final Color BORDER  = new Color(42, 52, 65);
    static final Color ACCENT  = new Color(88, 101, 242);
    static final Color GREEN   = new Color(50, 210, 120);
    static final Color RED     = new Color(220, 70,  80);
    static final Color TEXT    = new Color(220, 225, 235);
    static final Color MUTED   = new Color(120, 132, 150);
    static final Color YELLOW  = new Color(240, 180,  50);

    // ── Server state ──────────────────────────────────────────────────────────
    private final List<ClientHandler>  clients  = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Message> msgStore = Collections.synchronizedMap(new LinkedHashMap<>());
    private ServerSocket serverSocket;
    private int msgCount = 0;

    // ── GUI components ────────────────────────────────────────────────────────
    private DefaultListModel<String> userListModel;
    private JTextArea                logArea;
    private JLabel                   statsLabel;

    // =========================================================================
    public ServerGUI() {
        super("ChatApp  \u00b7  Server Admin Dashboard");
        applyNimbusDark();
        buildUI();
        startServer();
    }

    // ── UI construction ───────────────────────────────────────────────────────
    private void buildUI() {
        setSize(900, 640); setMinimumSize(new Dimension(700, 500));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        add(buildHeader(),  BorderLayout.NORTH);
        add(buildLeft(),    BorderLayout.WEST);
        add(buildCenter(),  BorderLayout.CENTER);
        add(buildStatus(),  BorderLayout.SOUTH);
        setVisible(true);
    }

    /** Gradient header with server branding. */
    private JPanel buildHeader() {
        JPanel h = new JPanel(new BorderLayout()) {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0,0,new Color(20,15,55),getWidth(),0,new Color(10,30,60)));
                g2.fillRect(0,0,getWidth(),getHeight()); g2.dispose();
            }
        };
        h.setOpaque(false);
        h.setBorder(new CompoundBorder(new MatteBorder(0,0,1,0,BORDER), new EmptyBorder(16,22,16,22)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT,10,0)); left.setOpaque(false);
        lbl(left, "\u25c6", new Font("Dialog",Font.BOLD,18), ACCENT);
        lbl(left, "Admin Dashboard", new Font("Segoe UI",Font.BOLD,22), TEXT);
        lbl(left, "\u25cf  LIVE", new Font("Segoe UI",Font.BOLD,12), GREEN);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT,6,4)); right.setOpaque(false);
        lbl(right, "Port: " + PORT, new Font("Segoe UI",Font.PLAIN,12), MUTED);

        h.add(left, BorderLayout.WEST); h.add(right, BorderLayout.EAST);
        return h;
    }

    /** Left panel: active users list + kick button. */
    private JPanel buildLeft() {
        JPanel p = new JPanel(new BorderLayout()); p.setBackground(SURFACE);
        p.setPreferredSize(new Dimension(200, 0));
        p.setBorder(new CompoundBorder(new MatteBorder(0,0,0,1,BORDER), new EmptyBorder(0,0,0,0)));

        JLabel title = new JLabel("  \u25a3  Active Users");
        title.setFont(new Font("Segoe UI",Font.BOLD,13)); title.setForeground(TEXT);
        title.setBorder(new CompoundBorder(new MatteBorder(0,0,1,0,BORDER), new EmptyBorder(12,8,12,8)));
        title.setBackground(CARD); title.setOpaque(true);

        userListModel = new DefaultListModel<>();
        JList<String> userList = new JList<>(userListModel);
        userList.setBackground(SURFACE); userList.setForeground(TEXT);
        userList.setFont(new Font("Segoe UI",Font.PLAIN,13)); userList.setSelectionBackground(CARD);
        userList.setFixedCellHeight(36);
        userList.setCellRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                JLabel c = (JLabel) super.getListCellRendererComponent(l,v,i,sel,foc);
                c.setText("  \u25cf  " + v); c.setForeground(GREEN); c.setBackground(sel?CARD:SURFACE);
                c.setBorder(new MatteBorder(0,0,1,0,BORDER)); return c;
            }
        });

        JScrollPane sp = new JScrollPane(userList); sp.setBorder(null); sp.setBackground(SURFACE);
        sp.getViewport().setBackground(SURFACE); styleScrollBar(sp.getVerticalScrollBar());

        // Kick button
        GradBtn kickBtn = new GradBtn("\u26a1  Kick User", RED, new Color(180,40,50));
        kickBtn.setFont(new Font("Segoe UI",Font.BOLD,12)); kickBtn.setForeground(Color.WHITE);
        kickBtn.setBorder(new EmptyBorder(10,0,10,0));
        kickBtn.addActionListener(e -> {
            String sel = userList.getSelectedValue();
            if (sel == null) { log("Select a user to kick.","warn"); return; }
            kickUser(sel);
        });

        p.add(title, BorderLayout.NORTH);
        p.add(sp,    BorderLayout.CENTER);
        p.add(kickBtn, BorderLayout.SOUTH);
        return p;
    }

    /** Center panel: real-time traffic log. */
    private JPanel buildCenter() {
        JPanel p = new JPanel(new BorderLayout()); p.setBackground(BG);

        JLabel title = new JLabel("   \u25a3  Traffic Log");
        title.setFont(new Font("Segoe UI",Font.BOLD,13)); title.setForeground(TEXT);
        title.setBorder(new CompoundBorder(new MatteBorder(0,0,1,0,BORDER), new EmptyBorder(12,8,12,8)));
        title.setBackground(CARD); title.setOpaque(true);

        logArea = new JTextArea();
        logArea.setEditable(false); logArea.setBackground(BG); logArea.setForeground(TEXT);
        logArea.setFont(new Font("Consolas",Font.PLAIN,12)); logArea.setMargin(new Insets(10,14,10,14));
        logArea.setLineWrap(true); logArea.setWrapStyleWord(true);

        JScrollPane sp = new JScrollPane(logArea); sp.setBorder(null);
        sp.getViewport().setBackground(BG); styleScrollBar(sp.getVerticalScrollBar());

        p.add(title, BorderLayout.NORTH); p.add(sp, BorderLayout.CENTER);
        return p;
    }

    /** Status bar at the bottom. */
    private JPanel buildStatus() {
        JPanel p = new JPanel(new BorderLayout()); p.setBackground(CARD);
        p.setBorder(new CompoundBorder(new MatteBorder(1,0,0,0,BORDER), new EmptyBorder(8,18,8,18)));
        statsLabel = new JLabel("Clients: 0  |  Messages: 0");
        statsLabel.setFont(new Font("Segoe UI",Font.PLAIN,11)); statsLabel.setForeground(MUTED);
        JLabel port = new JLabel("Listening on port " + PORT);
        port.setFont(new Font("Segoe UI",Font.PLAIN,11)); port.setForeground(MUTED);
        p.add(statsLabel, BorderLayout.WEST); p.add(port, BorderLayout.EAST);
        return p;
    }

    // ── Server logic ──────────────────────────────────────────────────────────

    private void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                log("Server started on port " + PORT, "info");
                while (true) {
                    Socket s = serverSocket.accept();
                    ClientHandler h = new ClientHandler(s);
                    clients.add(h); new Thread(h).start();
                }
            } catch (IOException e) { log("Server stopped: " + e.getMessage(), "error"); }
        }).start();
    }

    void broadcast(Message m) {
        synchronized (clients) {
            for (ClientHandler c : clients) c.send(m);
        }
    }

    void broadcastExcept(Message m, ClientHandler skip) {
        synchronized (clients) {
            for (ClientHandler c : clients) { if (c != skip) c.send(m); }
        }
    }

    void sendToUser(String username, Message m) {
        synchronized (clients) {
            for (ClientHandler c : clients) { if (c.username.equals(username)) { c.send(m); return; } }
        }
    }

    String buildUsersCsv() {
        StringBuilder sb = new StringBuilder();
        synchronized (clients) { for (ClientHandler c : clients) sb.append(c.username).append(","); }
        return sb.length() > 0 ? sb.substring(0, sb.length()-1) : "";
    }

    void kickUser(String username) {
        synchronized (clients) {
            for (ClientHandler c : clients) {
                if (c.username.equals(username)) { c.send(Message.kick(username)); c.close(); break; }
            }
        }
        log("KICK  \u25ba " + username, "warn");
    }

    void log(String msg, String level) {
        String ts = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String prefix = level.equals("error")?"[ERROR]":level.equals("warn")?"[WARN] ":"[INFO] ";
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + ts + "] " + prefix + " " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    void updateStats() {
        SwingUtilities.invokeLater(() ->
            statsLabel.setText("Clients: " + clients.size() + "  |  Messages: " + msgCount));
    }

    // ── ClientHandler ─────────────────────────────────────────────────────────
    class ClientHandler implements Runnable {
        final Socket socket; String username = "?";
        ObjectOutputStream out;

        ClientHandler(Socket s) { this.socket = s; }

        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream()); out.flush();
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

                // First message = JOIN
                Message join = (Message) in.readObject();
                username = join.getSender();
                log("JOIN  \u25ba " + username, "info");

                // Send current user list to new client
                send(Message.users(buildUsersCsv()));

                // Replay history to new client
                synchronized (msgStore) { for (Message m : msgStore.values()) send(m); }

                // Add to sidebar
                SwingUtilities.invokeLater(() -> { if (!userListModel.contains(username)) userListModel.addElement(username); });

                // Announce join
                broadcast(Message.system(username + " joined the chat."));
                broadcast(Message.join(username));
                updateStats();

                // Message loop
                while (true) {
                    Message m = (Message) in.readObject();
                    handleMessage(m);
                }
            } catch (EOFException | SocketException e) {
                log("LEAVE \u25ba " + username, "info");
            } catch (Exception e) {
                log("Error [" + username + "]: " + e.getMessage(), "error");
            } finally { remove(); }
        }

        void handleMessage(Message m) {
            switch (m.getType()) {
                case TEXT:
                    msgStore.put(m.getId(), m); msgCount++;
                    log("MSG   \u25ba [" + m.getSender() + "]: " + m.getContent(), "info");
                    broadcast(m); break;
                case TYPING:
                    log("TYPE  \u25ba " + m.getSender() + " is typing...", "info");
                    broadcastExcept(m, this); break;
                case EDIT:
                    if (msgStore.containsKey(m.getId())) { msgStore.get(m.getId()).setContent(m.getContent()); }
                    log("EDIT  \u25ba [" + m.getSender() + "]: " + m.getContent(), "info");
                    broadcast(m); break;
                case DELETE:
                    msgStore.remove(m.getId());
                    log("DEL   \u25ba [" + m.getSender() + "] deleted msg", "info");
                    broadcast(m); break;
                case PRIVATE:
                    log("PRIV  \u25ba [" + m.getSender() + "] \u2192 [" + m.getRecipient() + "]: " + m.getContent(), "info");
                    sendToUser(m.getRecipient(), m);
                    send(m); break;  // echo to sender too
                default: break;
            }
            updateStats();
        }

        void send(Message m) {
            try { synchronized (out) { out.reset(); out.writeObject(m); out.flush(); } }
            catch (IOException e) { /* client gone */ }
        }

        void close() { try { socket.close(); } catch (IOException ignored) {} }

        void remove() {
            clients.remove(this);
            SwingUtilities.invokeLater(() -> userListModel.removeElement(username));
            broadcast(Message.system(username + " left the chat."));
            broadcast(Message.leave(username));
            close(); updateStats();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    static void lbl(JPanel p, String t, Font f, Color c) {
        JLabel l = new JLabel(t); l.setFont(f); l.setForeground(c); p.add(l);
    }

    static void styleScrollBar(JScrollBar b) {
        b.setPreferredSize(new Dimension(5,0));
        b.setUI(new BasicScrollBarUI() {
            protected void configureScrollBarColors(){thumbColor=new Color(88,101,242,120);trackColor=BG;}
            protected JButton createDecreaseButton(int o){JButton x=new JButton();x.setPreferredSize(new Dimension(0,0));return x;}
            protected JButton createIncreaseButton(int o){JButton x=new JButton();x.setPreferredSize(new Dimension(0,0));return x;}
        });
    }

    static void applyNimbusDark() {
        try { for (UIManager.LookAndFeelInfo i : UIManager.getInstalledLookAndFeels())
                if ("Nimbus".equals(i.getName())) { UIManager.setLookAndFeel(i.getClassName()); break; }
        } catch (Exception ignored) {}
        UIManager.put("nimbusBase",            new Color(10,14,20));
        UIManager.put("nimbusBlueGrey",        new Color(18,24,32));
        UIManager.put("control",               new Color(18,24,32));
        UIManager.put("text",                  new Color(220,225,235));
        UIManager.put("nimbusLightBackground", new Color(10,14,20));
        UIManager.put("nimbusSelectionBackground", ACCENT);
        UIManager.put("nimbusSelectedText",    Color.WHITE);
    }

    /** Gradient button with configurable colors. */
    static class GradBtn extends JButton {
        final Color c1, c2;
        GradBtn(String t, Color c1, Color c2) {
            super(t); this.c1=c1; this.c2=c2;
            setContentAreaFilled(false); setFocusPainted(false); setBorderPainted(false);
            setOpaque(false); setCursor(new Cursor(Cursor.HAND_CURSOR));
        }
        protected void paintComponent(Graphics g) {
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Color a=getModel().isPressed()?c1.darker():getModel().isRollover()?c1.brighter():c1;
            Color b=getModel().isPressed()?c2.darker():getModel().isRollover()?c2.brighter():c2;
            g2.setPaint(new GradientPaint(0,0,a,getWidth(),getHeight(),b));
            g2.fillRect(0,0,getWidth(),getHeight()); g2.dispose(); super.paintComponent(g);
        }
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings","on");
        System.setProperty("swing.aatext","true");
        SwingUtilities.invokeLater(ServerGUI::new);
    }
}
