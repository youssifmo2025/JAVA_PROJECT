# ChatApp Project Documentation

## 1. Project Overview & Requirements Comparison

### 🎯 Core College Requirements
The original requirement was simple:
> *"Create a simple client-server chat application using sockets. The server can handle multiple clients, and clients can send messages to each other through the server."*

### 🏆 Our Implementation (What actually exists in the code)
Our system completely fulfills and **exceeds** these requirements by lightyears. 
- **Sockets Used?** Yes. We used standard `java.net.Socket` and `ServerSocket` for the network layer.
- **Client-Server Architecture?** Yes. A centralized server is deployed to handle multiple nodes securely.
- **Multiple Clients?** Yes. We implemented Java multithreading (`Runnable` threads for each `ClientHandler`) allowing the server to handle concurrent connections safely.
- **Message Exchange?** Yes. Clients send messages that the server receives and broadcasts correctly.

**How we exceeded the basic requirements (Advanced Features in our System):**
1. **Object Streams instead of simple Strings:** Instead of sending raw text via `PrintWriter`, we transmit complete, serialized `Message` objects using `ObjectInputStream` and `ObjectOutputStream`. This allows metadata (like UUIDs, Timestamps, Sender Name, and Type) to travel seamlessly.
2. **Real-Time CRUD Operations:** Users can Edit and Delete their messages in real-time, matching modern messaging standards.
3. **Private Whispers:** Users can select a specific user from an online sidebar to send private messages that no one else can read.
4. **Typing Indicators:** A real-time system broadcasts a "user is typing" status directly above the input box.
5. **Modern Swing GUI:** Highly advanced UI implementation using custom Painting, Drop Shadows, algorithmic Avatar Generation, Gradient buttons, and WhatsApp-style bubbles instead of boring legacy layouts.
6. **Server Admin Dashboard:** A fully graphical server interface that allows the admin to monitor active users, view all passing traffic, and forcibly "Kick" misbehaving clients.

---

## 2. System Architecture

The application is cleanly separated into three core components (Files):

### 📄 `Message.java` (The Data Model)
A `Serializable` object that acts as the network communication protocol. Every packet exchanged over the network is a `Message`. It uses an `enum Type` with 10 exact states:
- `TEXT`, `TYPING`, `EDIT`, `DELETE`, `PRIVATE` (Chat interactions)
- `SYSTEM`, `JOIN`, `LEAVE`, `KICK`, `USERS` (Network control and routing signals)

### 📄 `ServerGUI.java` (The Server Node)
- Starts a `ServerSocket` on a specified port (default: 12345).
- Maintains a thread-safe `List` of active `ClientHandler` network threads.
- Maintains a thread-safe `LinkedHashMap` of message history, enabling history replay for newly joined clients.
- Provides a Dark-Themed Admin UI containing an active user list, traffic monitor, and user kick controls.

### 📄 `ClientGUI.java` (The Client Node)
- Connects to the server via a standard `Socket`.
- Spawns a background Daemon thread to continuously read incoming messages without blocking the Swing Event Dispatch Thread (EDT).
- Renders messages dynamically on the screen using complex custom Swing components (`ShadowBubble`, `BubbleRow`).
- Implements contextual logic like an Idle Typing Timer, Right-Click Context Menus, and dynamic scrollbars.

---

## 3. How to Run the Project (Detailed Execution Guide)

Follow these exact steps to compile and run the project from scratch.

### Step 1: Clean and Compile
Open a **Terminal (PowerShell or CMD)** and navigate to the project directory. First, we delete old `.class` files to prevent caching issues, then compile the Java files:

```powershell
cd f:\JAVA_project\ChatApp
del *.class
javac Message.java ServerGUI.java ClientGUI.java
```
*(If the cursor returns to the next line with no errors, the compilation was perfectly successful).*

### Step 2: Start the Server (Terminal 1)
In the **same terminal**, launch the server application:
```powershell
java ServerGUI
```
> **Note:** A dark-themed Admin Dashboard window will open. Leave this window open in the background; it acts as the central brain of the chat.

### Step 3: Start Client 1 (Terminal 2)
Open a **completely new Terminal window**, navigate to the exact same folder, and run the client application:
```powershell
cd f:\JAVA_project\ChatApp
java ClientGUI
```
> **Note:** A login modal will appear. Enter a username (e.g., "Ahmed") and click "Join Chat".

### Step 4: Start Client 2 (Terminal 3)
Open **another new Terminal window**, navigate to the project folder, and run the client again:
```powershell
cd f:\JAVA_project\ChatApp
java ClientGUI
```
> **Note:** Enter a different username (e.g., "Khaled"). 

### Step 5: Test the Application
- **Public Chat:** Type a message and hit the Send button. Both clients will see it instantly.
- **Private Chat:** Click a username on the left "Online" sidebar, then send a message. You will notice a lock 🔒 icon.
- **Edit/Delete:** Right-click on one of your own blue messages to bring up the Edit/Delete context menu.
- **Admin Kick:** Go back to the Server GUI window, select a user from the left list, and click "Kick User". That client will be forcibly disconnected.
