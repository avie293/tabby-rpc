package cc.avie.tabby_rpc.discord;

import cc.avie.tabby_rpc.TabbyRPC;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;

/**
 * Connection to the local Discord client over its IPC socket.
 *
 * <p>A frame consists of an opcode and the payload length as little endian integers, followed by a JSON payload.
 * Instances are not thread safe. Commands block until Discord has answered, events that arrive in the meantime or are
 * found by {@link #poll()} are passed to the listener on the calling thread.
 */
final class DiscordIpc implements Closeable {
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;
    private static final int OP_PING = 3;
    private static final int OP_PONG = 4;

    private static final int HEADER_LENGTH = 8;
    private static final int MAX_PAYLOAD_LENGTH = 1 << 16;
    // Discord uses the first free slot, other clients such as Canary or PTB take the following ones.
    private static final int SOCKET_COUNT = 10;
    private static final String SOCKET_NAME = "discord-ipc-";
    private static final String[] UNIX_ENVIRONMENT = {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"};
    // Flatpak and Snap installations keep the socket in their own subdirectory.
    private static final String[] UNIX_SUBDIRECTORIES = {"", "app/com.discordapp.Discord", "snap.discord"};

    private final ByteChannel channel;
    // Only on Windows: reading from a pipe blocks, this tells whether there is something to read.
    private final @Nullable InputStream pipe;
    private final BiConsumer<String, JsonObject> listener;
    private final ByteBuffer header = ByteBuffer.allocate(HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
    private String payload = "";
    private int nonce;

    private DiscordIpc(ByteChannel channel, @Nullable InputStream pipe, BiConsumer<String, JsonObject> listener) {
        this.channel = channel;
        this.pipe = pipe;
        this.listener = listener;
    }

    /**
     * @param listener receives the name and the data of every event Discord sends
     * @throws ClosedException if Discord refused the application
     * @throws IOException if Discord is not running or the connection failed
     */
    static DiscordIpc connect(String applicationId, BiConsumer<String, JsonObject> listener) throws IOException {
        DiscordIpc ipc = System.getProperty("os.name").startsWith("Windows")
                ? openPipe(listener)
                : openSocket(listener);
        if (ipc == null) {
            throw new IOException("Discord is not running");
        }

        try {
            JsonObject handshake = new JsonObject();
            handshake.addProperty("v", 1);
            handshake.addProperty("client_id", applicationId);
            ipc.write(OP_HANDSHAKE, handshake.toString());
            // Discord answers with a ready event.
            ipc.header.clear();
            ipc.readFrame();
        } catch (IOException e) {
            ipc.close();
            throw e;
        }
        return ipc;
    }

    private static @Nullable DiscordIpc openPipe(BiConsumer<String, JsonObject> listener) throws IOException {
        for (int i = 0; i < SOCKET_COUNT; i++) {
            try {
                RandomAccessFile file = new RandomAccessFile("\\\\.\\pipe\\" + SOCKET_NAME + i, "rw");
                return new DiscordIpc(file.getChannel(), new FileInputStream(file.getFD()), listener);
            } catch (FileNotFoundException e) {
                // No client on this slot.
            }
        }
        return null;
    }

    private static @Nullable DiscordIpc openSocket(BiConsumer<String, JsonObject> listener) {
        Path directory = Path.of(socketDirectory());
        for (String subdirectory : UNIX_SUBDIRECTORIES) {
            for (int i = 0; i < SOCKET_COUNT; i++) {
                Path socket = directory.resolve(subdirectory).resolve(SOCKET_NAME + i);
                if (!Files.exists(socket)) {
                    continue;
                }

                try {
                    return new DiscordIpc(SocketChannel.open(UnixDomainSocketAddress.of(socket)), null, listener);
                } catch (IOException e) {
                    // Left behind by a client that is no longer running.
                }
            }
        }
        return null;
    }

    private static String socketDirectory() {
        for (String variable : UNIX_ENVIRONMENT) {
            String value = System.getenv(variable);
            if (value != null) {
                return value;
            }
        }
        return "/tmp";
    }

    void setActivity(JsonObject activity) throws IOException {
        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        args.add("activity", activity);
        this.send("SET_ACTIVITY", null, args);
    }

    void subscribe(String event) throws IOException {
        this.send("SUBSCRIBE", event, null);
    }

    /**
     * Answers a join request: accepting sends the user an invite, declining only dismisses the request.
     */
    void answerJoinRequest(String userId, boolean accept) throws IOException {
        JsonObject args = new JsonObject();
        args.addProperty("user_id", userId);
        this.send(accept ? "SEND_ACTIVITY_JOIN_INVITE" : "CLOSE_ACTIVITY_JOIN_REQUEST", null, args);
    }

    private void send(String command, @Nullable String event, @Nullable JsonObject args) throws IOException {
        String nonce = Integer.toString(++this.nonce);
        JsonObject message = new JsonObject();
        message.addProperty("cmd", command);
        if (event != null) {
            message.addProperty("evt", event);
        }
        if (args != null) {
            message.add("args", args);
        }
        message.addProperty("nonce", nonce);
        this.write(OP_FRAME, message.toString());

        while (true) {
            this.header.clear();
            if (this.readFrame() != OP_FRAME) {
                continue;
            }

            JsonObject response = parse(this.payload);
            if (!nonce.equals(string(response, "nonce"))) {
                this.dispatch(response);
            } else if ("ERROR".equals(string(response, "evt"))) {
                TabbyRPC.LOGGER.warn("Discord rejected {}: {}", command, response.get("data"));
                return;
            } else {
                return;
            }
        }
    }

    /**
     * Checks that Discord is still there. Without this a closed client would only be noticed on the next command.
     */
    void ping() throws IOException {
        this.write(OP_PING, "{}");
        int opcode;
        do {
            this.header.clear();
            opcode = this.readFrame();
            if (opcode == OP_FRAME) {
                this.dispatch(parse(this.payload));
            }
        } while (opcode != OP_PONG);
    }

    /**
     * Passes the events that have arrived to the listener, without waiting for more.
     */
    void poll() throws IOException {
        while (this.hasFrame()) {
            if (this.readFrame() == OP_FRAME) {
                this.dispatch(parse(this.payload));
            }
        }
    }

    /**
     * Clears the header and reports whether a frame is waiting. On a socket this already reads into the header.
     */
    private boolean hasFrame() throws IOException {
        this.header.clear();
        if (this.pipe != null) {
            return this.pipe.available() > 0;
        }

        SocketChannel socket = (SocketChannel) this.channel;
        socket.configureBlocking(false);
        try {
            return socket.read(this.header) != 0;
        } finally {
            socket.configureBlocking(true);
        }
    }

    private void dispatch(JsonObject message) {
        String event = string(message, "evt");
        if ("DISPATCH".equals(string(message, "cmd")) && event != null
                && message.get("data") instanceof JsonObject data) {
            this.listener.accept(event, data);
        }
    }

    private void write(int opcode, String payload) throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        ByteBuffer frame = ByteBuffer.allocate(HEADER_LENGTH + bytes.length).order(ByteOrder.LITTLE_ENDIAN);
        frame.putInt(opcode).putInt(bytes.length).put(bytes).flip();
        while (frame.hasRemaining()) {
            this.channel.write(frame);
        }
    }

    /**
     * Reads the rest of the header and the payload of one frame, the payload is left in {@link #payload}.
     *
     * @return the opcode of the frame
     */
    private int readFrame() throws IOException {
        this.readFully(this.header);
        this.header.flip();
        int opcode = this.header.getInt();
        int length = this.header.getInt();
        if (length < 0 || length > MAX_PAYLOAD_LENGTH) {
            throw new IOException("Invalid frame length " + length);
        }

        ByteBuffer buffer = ByteBuffer.allocate(length);
        this.readFully(buffer);
        this.payload = new String(buffer.array(), StandardCharsets.UTF_8);
        if (opcode == OP_CLOSE) {
            throw new ClosedException(this.payload);
        } else if (opcode == OP_PING) {
            this.write(OP_PONG, this.payload);
        }
        return opcode;
    }

    private void readFully(ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (this.channel.read(buffer) < 0) {
                throw new EOFException("Discord closed the connection");
            }
        }
    }

    private static JsonObject parse(String payload) throws IOException {
        try {
            return JsonParser.parseString(payload).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("Malformed message from Discord", e);
        }
    }

    static @Nullable String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    @Override
    public void close() throws IOException {
        this.channel.close();
    }

    /**
     * Discord ended the connection on purpose, for example because the application ID is unknown. The message is the
     * reason Discord gave.
     */
    static final class ClosedException extends IOException {
        private ClosedException(String message) {
            super(message);
        }
    }
}
