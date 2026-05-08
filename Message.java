import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * Message.java — Core data object (Serializable) shared by server and all clients.
 * Every packet exchanged over the network is a Message object.
 * The Type enum drives all server routing and client rendering decisions.
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 3L;

    /**
     * Message types:
     *  TEXT    — normal public chat message
     *  TYPING  — "user is typing" notification (no content stored)
     *  EDIT    — update content of an existing message (matched by id)
     *  DELETE  — mark an existing message as deleted  (matched by id)
     *  PRIVATE — whisper visible only to sender and recipient
     *  SYSTEM  — server notifications (join/leave/kick alerts)
     *  JOIN    — client has connected; payload = username
     *  LEAVE   — client has disconnected
     *  KICK    — server forcibly removes a client; recipient = target username
     *  USERS   — server sends comma-separated online user list on join
     */
    public enum Type { TEXT, TYPING, EDIT, DELETE, PRIVATE, SYSTEM, JOIN, LEAVE, KICK, USERS }

    private final String id;          // UUID — immutable, used to match EDIT/DELETE
    private final String sender;      // author username — immutable
    private final String recipient;   // target for PRIVATE / KICK (empty otherwise)
    private       String content;     // mutable for EDIT
    private final Type   type;
    private final long   timestamp;

    /** Full constructor — prefer static factories below. */
    public Message(String id, String sender, String recipient, String content, Type type, long timestamp) {
        this.id = id; this.sender = sender; this.recipient = recipient;
        this.content = content; this.type = type; this.timestamp = timestamp;
    }

    // ── Static factories ──────────────────────────────────────────────────────

    public static Message text(String sender, String content) {
        return new Message(uuid(), sender, "", content, Type.TEXT, now()); }

    public static Message typing(String sender) {
        return new Message(uuid(), sender, "", "", Type.TYPING, now()); }

    public static Message edit(String id, String sender, String newContent) {
        return new Message(id, sender, "", newContent, Type.EDIT, now()); }

    public static Message delete(String id, String sender) {
        return new Message(id, sender, "", "", Type.DELETE, now()); }

    public static Message priv(String sender, String recipient, String content) {
        return new Message(uuid(), sender, recipient, content, Type.PRIVATE, now()); }

    public static Message system(String content) {
        return new Message(uuid(), "Server", "", content, Type.SYSTEM, now()); }

    public static Message join(String username) {
        return new Message(uuid(), username, "", username, Type.JOIN, now()); }

    public static Message leave(String username) {
        return new Message(uuid(), username, "", username, Type.LEAVE, now()); }

    public static Message kick(String target) {
        return new Message(uuid(), "Server", target, target, Type.KICK, now()); }

    public static Message users(String csv) {
        return new Message(uuid(), "Server", "", csv, Type.USERS, now()); }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getId()        { return id; }
    public String getSender()    { return sender; }
    public String getRecipient() { return recipient; }
    public String getContent()   { return content; }
    public Type   getType()      { return type; }
    public long   getTimestamp() { return timestamp; }
    public void   setContent(String c) { content = c; }

    public String getFormattedTime() {
        return new SimpleDateFormat("HH:mm").format(new Date(timestamp));
    }

    private static String uuid() { return UUID.randomUUID().toString(); }
    private static long   now()  { return System.currentTimeMillis(); }
}
