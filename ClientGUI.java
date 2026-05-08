import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class ClientGUI {
    static final Color BG=new Color(10,14,20),SURFACE=new Color(18,24,32),CARD=new Color(28,35,46);
    static final Color BORDER=new Color(42,52,65),ACCENT=new Color(88,101,242),ACCENTL=new Color(120,135,255);
    static final Color SENT=new Color(55,65,210),RECV=new Color(30,38,50);
    static final Color GREEN=new Color(50,210,120),TEXT=new Color(220,225,235),MUTED=new Color(120,132,150);
    static final Color SYS=new Color(80,95,115),PRIV=new Color(150,80,200);
    static final Color[] AVC={new Color(88,101,242),new Color(50,210,120),new Color(240,180,50),
                               new Color(220,70,150),new Color(255,120,50),new Color(50,190,255)};
    static final String HOST="localhost"; static final int PORT=12345;

    private final String username;
    private Socket socket; private ObjectOutputStream out; private ObjectInputStream in;
    private JFrame frame; private JPanel msgBox; private JScrollPane chatScroll;
    private JTextField input; private JLabel typingLabel; private JLabel privLabel;
    private DefaultListModel<String> usersModel = new DefaultListModel<>();
    private JList<String> usersList;
    private final Map<String,BubbleRow> bubbles = new LinkedHashMap<>();
    private final Set<String> sentIds = Collections.synchronizedSet(new HashSet<>());
    private String privateTarget = null;
    private javax.swing.Timer typingTimer;
    private boolean isTyping = false;

    public ClientGUI(String u) {
        username=u; connect(); buildUI(); startReader();
        send(Message.join(username));
    }

    void connect() {
        try {
            socket=new Socket(HOST,PORT);
            out=new ObjectOutputStream(socket.getOutputStream()); out.flush();
            in=new ObjectInputStream(socket.getInputStream());
        } catch(IOException e) { JOptionPane.showMessageDialog(null,"Cannot connect:\n"+e.getMessage(),"Error",0); System.exit(1); }
    }

    void send(Message m) {
        try { synchronized(out){out.reset();out.writeObject(m);out.flush();} } catch(IOException e){e.printStackTrace();}
    }

    void startReader() {
        Thread t=new Thread(()->{
            try { while(true){Message m=(Message)in.readObject(); SwingUtilities.invokeLater(()->handle(m));} }
            catch(Exception e){ SwingUtilities.invokeLater(()->addOrUpdate(Message.system("Connection lost."))); }
        }); t.setDaemon(true); t.start();
    }

    void handle(Message m) {
        switch(m.getType()) {
            case TEXT: case EDIT: case DELETE:
                if(m.getType()==Message.Type.TEXT && m.getSender().equals(username) && sentIds.contains(m.getId())) return;
                addOrUpdate(m); break;
            case PRIVATE: 
                if(m.getSender().equals(username) && sentIds.contains(m.getId())) return;
                addOrUpdate(m); break;
            case TYPING:
                if(!m.getSender().equals(username)){
                    typingLabel.setText(m.getSender()+" is typing...");
                    if(typingTimer!=null) typingTimer.restart();
                } break;
            case SYSTEM: addOrUpdate(m); break;
            case JOIN: if(!m.getSender().equals(username)&&!usersModel.contains(m.getSender())) usersModel.addElement(m.getSender()); break;
            case LEAVE: usersModel.removeElement(m.getSender()); break;
            case KICK:
                if(m.getRecipient().equals(username)){
                    JOptionPane.showMessageDialog(frame,"You were kicked by the admin.","Kicked",JOptionPane.WARNING_MESSAGE);
                    System.exit(0);
                } break;
            case USERS:
                usersModel.clear();
                for(String u:m.getContent().split(",")) if(!u.isEmpty()&&!u.equals(username)) usersModel.addElement(u);
                break;
            default: break;
        }
    }

    void addOrUpdate(Message m) {
        if(m.getType()==Message.Type.EDIT||m.getType()==Message.Type.DELETE){
            BubbleRow b=bubbles.get(m.getId()); if(b!=null){b.update(m);return;}
        }
        if(bubbles.containsKey(m.getId())&&(m.getType()==Message.Type.TEXT||m.getType()==Message.Type.PRIVATE)) return;
        BubbleRow row=new BubbleRow(m); bubbles.put(m.getId(),row);
        msgBox.add(row); msgBox.revalidate(); msgBox.repaint();
        SwingUtilities.invokeLater(()->{JScrollBar v=chatScroll.getVerticalScrollBar();v.setValue(v.getMaximum());});
    }

    void buildUI() {
        ServerGUI.applyNimbusDark();
        frame=new JFrame("ChatApp  \u00b7  "+username);
        frame.setSize(820,680); frame.setMinimumSize(new Dimension(600,480));
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setLocationRelativeTo(null); frame.getContentPane().setBackground(BG);
        frame.setLayout(new BorderLayout());
        frame.add(header(),BorderLayout.NORTH);
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,buildSidebar(),buildChatPanel());
        split.setDividerLocation(180); split.setDividerSize(1); split.setBorder(null);
        split.setBackground(BG); frame.add(split,BorderLayout.CENTER);
        frame.addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){
            try{send(Message.leave(username));out.close();in.close();socket.close();}catch(Exception ignored){}
            System.exit(0);
        }});
        // Typing timer: hides indicator after 2.5s silence
        typingTimer=new javax.swing.Timer(2500,e->typingLabel.setText(""));
        typingTimer.setRepeats(false);
        frame.setVisible(true); input.requestFocusInWindow();
    }

    JPanel header() {
        JPanel h=new JPanel(new BorderLayout()){
            protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create();
                g2.setPaint(new GradientPaint(0,0,new Color(15,12,40),getWidth(),0,new Color(10,25,55)));
                g2.fillRect(0,0,getWidth(),getHeight()); g2.dispose();
            }
        };
        h.setOpaque(false); h.setBorder(new CompoundBorder(new MatteBorder(0,0,1,0,BORDER),new EmptyBorder(14,20,14,20)));
        JPanel L=new JPanel(new FlowLayout(FlowLayout.LEFT,10,0)); L.setOpaque(false);
        ServerGUI.lbl(L,"\u25c6",new Font("Dialog",Font.BOLD,18),ACCENTL);
        ServerGUI.lbl(L,"ChatRoom",new Font("Segoe UI",Font.BOLD,21),TEXT);
        ServerGUI.lbl(L,"\u25cf",new Font("Dialog",Font.PLAIN,11),GREEN);
        Color ac=avc(username);
        JLabel chip=new JLabel("  "+username+"  "){
            protected void paintComponent(Graphics g){
                Graphics2D g2=(Graphics2D)g.create(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0,0,ACCENT,getWidth(),0,ACCENTL));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),getHeight(),getHeight()); g2.dispose(); super.paintComponent(g);
            }
        };
        chip.setFont(new Font("Segoe UI",Font.BOLD,12)); chip.setForeground(Color.WHITE); chip.setOpaque(false); chip.setBorder(new EmptyBorder(5,14,5,14));
        JPanel R=new JPanel(new FlowLayout(FlowLayout.RIGHT,0,4)); R.setOpaque(false); R.add(chip);
        h.add(L,BorderLayout.WEST); h.add(R,BorderLayout.EAST); return h;
    }

    JPanel buildSidebar() {
        JPanel p=new JPanel(new BorderLayout()); p.setBackground(SURFACE);
        p.setBorder(new MatteBorder(0,0,0,1,BORDER));
        JLabel t=new JLabel("  \u25a3  Online");
        t.setFont(new Font("Segoe UI",Font.BOLD,12)); t.setForeground(TEXT);
        t.setBorder(new CompoundBorder(new MatteBorder(0,0,1,0,BORDER),new EmptyBorder(12,8,12,8)));
        t.setBackground(CARD); t.setOpaque(true);
        usersList=new JList<>(usersModel); usersList.setBackground(SURFACE); usersList.setForeground(TEXT);
        usersList.setFont(new Font("Segoe UI",Font.PLAIN,12)); usersList.setSelectionBackground(CARD);
        usersList.setFixedCellHeight(38);
        usersList.setCellRenderer(new DefaultListCellRenderer(){
            public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean sel,boolean foc){
                JLabel c=(JLabel)super.getListCellRendererComponent(l,v,i,sel,foc);
                c.setText("  \u25cf  "+v); c.setForeground(GREEN); c.setBackground(sel?CARD:SURFACE);
                c.setBorder(new MatteBorder(0,0,1,0,BORDER)); return c;
            }
        });
        usersList.addMouseListener(new MouseAdapter(){
            public void mouseClicked(MouseEvent e){
                String sel=usersList.getSelectedValue();
                if(sel!=null){ privateTarget=sel; privLabel.setText(" \uD83D\uDD12 Private \u2192 "+sel+"  [x]"); privLabel.setVisible(true);}
            }
        });
        JScrollPane sp=new JScrollPane(usersList); sp.setBorder(null); sp.getViewport().setBackground(SURFACE);
        ServerGUI.styleScrollBar(sp.getVerticalScrollBar());
        p.add(t,BorderLayout.NORTH); p.add(sp,BorderLayout.CENTER); return p;
    }

    JPanel buildChatPanel() {
        JPanel p=new JPanel(new BorderLayout()); p.setBackground(BG);
        msgBox=new JPanel(){
            protected void paintComponent(Graphics g){
                super.paintComponent(g);
                Graphics2D g2=(Graphics2D)g.create(); g2.setColor(new Color(255,255,255,5));
                for(int x=0;x<getWidth();x+=22)for(int y=0;y<getHeight();y+=22)g2.fillOval(x,y,2,2);
                g2.dispose();
            }
        };
        msgBox.setLayout(new BoxLayout(msgBox,BoxLayout.Y_AXIS));
        msgBox.setBackground(BG); msgBox.setBorder(new EmptyBorder(14,12,14,12));
        msgBox.add(Box.createVerticalGlue());
        chatScroll=new JScrollPane(msgBox); chatScroll.setBorder(null);
        chatScroll.setBackground(BG); chatScroll.getViewport().setBackground(BG);
        chatScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        ServerGUI.styleScrollBar(chatScroll.getVerticalScrollBar());
        p.add(chatScroll,BorderLayout.CENTER); p.add(buildInputPanel(),BorderLayout.SOUTH); return p;
    }

    JPanel buildInputPanel() {
        JPanel wrap=new JPanel(new BorderLayout()); wrap.setBackground(SURFACE);
        wrap.setBorder(new MatteBorder(1,0,0,0,BORDER));
        // Private label bar
        privLabel=new JLabel("",SwingConstants.LEFT);
        privLabel.setFont(new Font("Segoe UI",Font.ITALIC,11)); privLabel.setForeground(PRIV);
        privLabel.setBorder(new EmptyBorder(5,16,0,16)); privLabel.setVisible(false);
        privLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        privLabel.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){
            if(e.getPoint().x>privLabel.getWidth()-30){privateTarget=null;privLabel.setVisible(false);usersList.clearSelection();}
        }});
        // Typing label
        typingLabel=new JLabel(""); typingLabel.setFont(new Font("Segoe UI",Font.ITALIC,11));
        typingLabel.setForeground(MUTED); typingLabel.setBorder(new EmptyBorder(4,16,2,16));
        JPanel meta=new JPanel(new BorderLayout()); meta.setOpaque(false);
        meta.add(privLabel,BorderLayout.NORTH); meta.add(typingLabel,BorderLayout.SOUTH);
        JPanel bar=new JPanel(new BorderLayout(10,0)); bar.setBackground(SURFACE); bar.setBorder(new EmptyBorder(10,14,12,14));
        input=new PlaceholderField("Type a message… (Shift+Click user for private)",28);
        input.setFont(new Font("Segoe UI",Font.PLAIN,14)); input.setBackground(CARD);
        input.setForeground(TEXT); input.setCaretColor(ACCENTL);
        input.setBorder(new CompoundBorder(new RoundBorder(BORDER,22),new EmptyBorder(11,16,11,16)));
        input.addActionListener(e->doSend());
        // Typing detection
        input.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            javax.swing.Timer stop=new javax.swing.Timer(2000,e->isTyping=false);
            { stop.setRepeats(false); }
            void onType(){if(!isTyping){isTyping=true;send(Message.typing(username));}stop.restart();}
            public void insertUpdate(javax.swing.event.DocumentEvent e){onType();}
            public void removeUpdate(javax.swing.event.DocumentEvent e){onType();}
            public void changedUpdate(javax.swing.event.DocumentEvent e){}
        });
        ServerGUI.GradBtn btn=new ServerGUI.GradBtn("Send \u27a4",ACCENT,new Color(50,200,140));
        btn.setFont(new Font("Segoe UI",Font.BOLD,13)); btn.setForeground(Color.WHITE);
        btn.setPreferredSize(new Dimension(100,44)); btn.addActionListener(e->doSend());
        bar.add(input,BorderLayout.CENTER); bar.add(btn,BorderLayout.EAST);
        wrap.add(meta,BorderLayout.NORTH); wrap.add(bar,BorderLayout.CENTER); return wrap;
    }

    void doSend() {
        String t=input.getText().trim(); if(t.isEmpty())return;
        Message m = (privateTarget!=null) ? Message.priv(username,privateTarget,t) : Message.text(username,t);
        if(m.getType()==Message.Type.TEXT || m.getType()==Message.Type.PRIVATE) sentIds.add(m.getId());
        addOrUpdate(m); send(m);
        input.setText(""); privateTarget=null; privLabel.setVisible(false); usersList.clearSelection();
        input.requestFocusInWindow();
    }

    // ── BubbleRow ─────────────────────────────────────────────────────────────
    class BubbleRow extends JPanel {
        Message msg; JLabel bodyLbl,timeLbl;
        BubbleRow(Message m){ msg=m; setOpaque(false); 
            setBorder(new EmptyBorder(4,0,4,0)); setLayout(new BorderLayout()); refresh(); }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        void update(Message m){msg=m;refresh();}

        void refresh(){
            removeAll();
            if(msg.getType()==Message.Type.SYSTEM||msg.getType()==Message.Type.JOIN||msg.getType()==Message.Type.LEAVE){
                JLabel l=new JLabel(msg.getContent(),SwingConstants.CENTER);
                l.setFont(new Font("Segoe UI",Font.ITALIC,11)); l.setForeground(SYS);
                l.setBorder(new EmptyBorder(6,0,6,0)); add(l,BorderLayout.CENTER); revalidate(); repaint(); return;
            }
            boolean mine=msg.getSender().equals(username);
            boolean priv=msg.getType()==Message.Type.PRIVATE;
            boolean del=msg.getType()==Message.Type.DELETE;
            boolean edit=msg.getType()==Message.Type.EDIT;
            Color bg=priv?PRIV.darker():mine?SENT:RECV;
            JPanel bubble=new ShadowBubble(bg,mine);
            bubble.setLayout(new BoxLayout(bubble,BoxLayout.Y_AXIS));
            bubble.setBorder(new EmptyBorder(10,14,8,mine?16:14));
            bubble.setMaximumSize(new Dimension(340,Short.MAX_VALUE));
            if(!mine){
                JLabel nm=new JLabel(msg.getSender()+(priv?" \uD83D\uDD12":""));
                nm.setFont(new Font("Segoe UI",Font.BOLD,11)); nm.setForeground(avc(msg.getSender()));
                nm.setBorder(new EmptyBorder(0,0,3,0)); bubble.add(nm);
            } else if(priv){
                JLabel nm=new JLabel("\uD83D\uDD12 Private \u2192 "+msg.getRecipient());
                nm.setFont(new Font("Segoe UI",Font.BOLD,11)); nm.setForeground(new Color(200,150,255));
                nm.setBorder(new EmptyBorder(0,0,3,0)); bubble.add(nm);
            }
            if(del){ bodyLbl=new JLabel("<html><i>Message deleted.</i></html>"); bodyLbl.setForeground(MUTED); }
            else {
                String col=mine?"white":"rgb(210,218,228)";
                bodyLbl=new JLabel("<html><body style='width:230px;font-size:13px;color:"+col+";'>"+esc(msg.getContent())+"</body></html>");
            }
            bodyLbl.setFont(new Font("Segoe UI",Font.PLAIN,13));
            String timeStr=msg.getFormattedTime()+(edit?" \u270e edited":"");
            timeLbl=new JLabel(timeStr); timeLbl.setFont(new Font("Segoe UI",Font.PLAIN,10));
            timeLbl.setForeground(mine?new Color(160,200,255):MUTED); timeLbl.setBorder(new EmptyBorder(4,0,0,0));
            bubble.add(bodyLbl); bubble.add(timeLbl);
            if(mine&&!del){
                bubble.addMouseListener(new MouseAdapter(){
                    public void mousePressed(MouseEvent e){if(e.isPopupTrigger())showPop(e,bubble);}
                    public void mouseReleased(MouseEvent e){if(e.isPopupTrigger())showPop(e,bubble);}
                });
            }
            if(!mine){
                final Color ac=avc(msg.getSender()); 
                final String ini=msg.getSender().isEmpty() ? "?" : msg.getSender().substring(0,1).toUpperCase();
                JPanel av=new JPanel(){protected void paintComponent(Graphics g){
                    Graphics2D g2=(Graphics2D)g.create(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(ac); g2.fillOval(0,0,getWidth(),getHeight());
                    g2.setColor(Color.WHITE); g2.setFont(new Font("Segoe UI",Font.BOLD,14));
                    FontMetrics fm=g2.getFontMetrics(); g2.drawString(ini,(getWidth()-fm.stringWidth(ini))/2,(getHeight()+fm.getAscent()-fm.getDescent())/2);
                    g2.dispose();
                }};
                av.setPreferredSize(new Dimension(36,36)); av.setMaximumSize(new Dimension(36,36));
                av.setOpaque(false); av.setAlignmentY(0f); bubble.setAlignmentY(0f);
                JPanel w=new JPanel(); w.setLayout(new BoxLayout(w,BoxLayout.X_AXIS)); w.setOpaque(false);
                w.setMaximumSize(new Dimension(380,Short.MAX_VALUE));
                w.add(av); w.add(Box.createHorizontalStrut(8)); w.add(bubble);
                add(w,BorderLayout.WEST);
            } else add(bubble,BorderLayout.EAST);
            revalidate(); repaint();
        }

        void showPop(MouseEvent e,JPanel b){
            JPopupMenu pop=new JPopupMenu(); pop.setBackground(CARD); pop.setBorder(BorderFactory.createLineBorder(BORDER));
            JMenuItem ei=new JMenuItem("\u270e  Edit"); JMenuItem di=new JMenuItem("\u2715  Delete");
            for(JMenuItem i:new JMenuItem[]{ei,di}){i.setBackground(CARD);i.setForeground(TEXT);i.setFont(new Font("Segoe UI",Font.PLAIN,12));i.setBorder(new EmptyBorder(8,14,8,14));pop.add(i);}
            ei.addActionListener(ev->{ String neo=JOptionPane.showInputDialog(frame,"Edit message:",msg.getContent());
                if(neo!=null&&!neo.trim().isEmpty()){Message em=Message.edit(msg.getId(),username,neo.trim());update(em);send(em);} });
            di.addActionListener(ev->{ Message dm=Message.delete(msg.getId(),username); update(dm); send(dm); });
            pop.show(b,e.getX(),e.getY());
        }
    }

    // ── Components ────────────────────────────────────────────────────────────
    static class ShadowBubble extends JPanel {
        final Color bg; final boolean r;
        ShadowBubble(Color bg,boolean r){this.bg=bg;this.r=r;setOpaque(false);}
        protected void paintComponent(Graphics g){
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0,0,0,60)); g2.fillRoundRect(3,4,getWidth()-5,getHeight()-5,22,22);
            g2.setColor(bg); g2.fillRoundRect(0,0,getWidth()-3,getHeight()-3,22,22);
            int ty=16;
            if(r){int[]x={getWidth()-3,getWidth()+8,getWidth()-3};int[]y={ty,ty+7,ty+14};g2.fillPolygon(x,y,3);}
            else{int[]x={0,-9,0};int[]y={ty,ty+7,ty+14};g2.fillPolygon(x,y,3);}
            g2.dispose();
        }
    }
    static class RoundBorder extends AbstractBorder {
        final Color c; final int r;
        RoundBorder(Color c,int r){this.c=c;this.r=r;}
        public void paintBorder(Component cp,Graphics g,int x,int y,int w,int h){
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            if(cp.isFocusOwner()){g2.setColor(new Color(88,101,242,70));g2.setStroke(new BasicStroke(3));g2.drawRoundRect(x-1,y-1,w+1,h+1,r+2,r+2);}
            g2.setColor(c);g2.setStroke(new BasicStroke(1));g2.drawRoundRect(x,y,w-1,h-1,r,r);g2.dispose();
        }
        public Insets getBorderInsets(Component c){return new Insets(1,1,1,1);}
    }
    static class PlaceholderField extends JTextField {
        final String ph;
        PlaceholderField(String ph,int c){super(c);this.ph=ph;}
        protected void paintComponent(Graphics g){super.paintComponent(g);
            if(getText().isEmpty()&&!isFocusOwner()){Graphics2D g2=(Graphics2D)g.create();g2.setColor(new Color(90,105,125));
                g2.setFont(getFont().deriveFont(Font.ITALIC));Insets ins=getInsets();
                g2.drawString(ph,ins.left+2,getHeight()/2+g2.getFontMetrics().getAscent()/2-2);g2.dispose();}}
    }

    static String esc(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    static Color avc(String n){return AVC[Math.abs(n.hashCode())%AVC.length];}

    // ── Login ─────────────────────────────────────────────────────────────────
    static String login(){
        JDialog d=new JDialog((Frame)null,"ChatApp — Sign In",true);
        d.setSize(400,300); d.setResizable(false); d.setLocationRelativeTo(null);
        JPanel root=new JPanel(new BorderLayout()); root.setBackground(SURFACE); d.setContentPane(root);
        JPanel top=new JPanel(){protected void paintComponent(Graphics g){Graphics2D g2=(Graphics2D)g.create();
            g2.setPaint(new GradientPaint(0,0,ACCENT,getWidth(),0,ACCENTL));g2.fillRect(0,0,getWidth(),getHeight());g2.dispose();}};
        top.setPreferredSize(new Dimension(0,6)); root.add(top,BorderLayout.NORTH);
        JPanel body=new JPanel(); body.setOpaque(false); body.setLayout(new BoxLayout(body,BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(32,46,24,46));
        JLabel ico=new JLabel("\u25c6  ChatRoom"); ico.setFont(new Font("Segoe UI",Font.BOLD,24)); ico.setForeground(TEXT); ico.setAlignmentX(.5f);
        JLabel sub=new JLabel("Enter username to join"); sub.setFont(new Font("Segoe UI",Font.PLAIN,12)); sub.setForeground(MUTED); sub.setAlignmentX(.5f);
        PlaceholderField f=new PlaceholderField("Your name…",16);
        f.setFont(new Font("Segoe UI",Font.PLAIN,14)); f.setBackground(CARD); f.setForeground(TEXT); f.setCaretColor(ACCENTL);
        f.setBorder(new CompoundBorder(new RoundBorder(BORDER,22),new EmptyBorder(11,16,11,16)));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE,46)); f.setAlignmentX(.5f);
        ServerGUI.GradBtn btn=new ServerGUI.GradBtn("Join Chat",ACCENT,new Color(50,200,140));
        btn.setFont(new Font("Segoe UI",Font.BOLD,13)); btn.setForeground(Color.WHITE);
        btn.setAlignmentX(.5f); btn.setMaximumSize(new Dimension(Integer.MAX_VALUE,44));
        body.add(ico);body.add(Box.createVerticalStrut(6));body.add(sub);body.add(Box.createVerticalStrut(20));
        body.add(f);body.add(Box.createVerticalStrut(14));body.add(btn); root.add(body,BorderLayout.CENTER);
        final String[]res={null};
        ActionListener join=e->{String n=f.getText().trim();if(!n.isEmpty()){res[0]=n;d.dispose();}};
        btn.addActionListener(join); f.addActionListener(join);
        d.addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){d.dispose();}});
        d.setVisible(true); return res[0];
    }

    public static void main(String[] args){
        System.setProperty("awt.useSystemAAFontSettings","on");
        System.setProperty("swing.aatext","true");
        ServerGUI.applyNimbusDark();
        SwingUtilities.invokeLater(()->{
            String name=login(); if(name==null){System.exit(0);return;}
            new ClientGUI(name);
        });
    }
}
