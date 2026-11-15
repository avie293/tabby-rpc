package cc.avie.tabby_rpc.discord;

import cc.avie.tabby_rpc.TabbyRPC;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Connection to the local Discord client over its IPC socket.
 *
 * <p>A frame consists of an opcode and the payload length as little endian integers, followed by a JSON payload.
 * Instances are not thread safe and every call blocks until Discord has answered.
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
    private final ByteBuffer header = ByteBuffer.allocate(HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
    private int nonce;

    private DiscordIpc(ByteChannel channel) {
        this.channel = channel;
    }

    /**
     * @throws ClosedException if Discord refused the application
     * @throws IOException if Discord is not running or the connection failed
     */
    static DiscordIpc connect(String applicationId) throws IOException {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        ByteChannel channel = windows ? openPipe() : openSocket();
        if (channel == null) {
            throw new IOException("Discord is not running");
        }

        DiscordIpc ipc = new DiscordIpc(channel);
        try {
            JsonObject handshake = new JsonObject();
            handshake.addProperty("v", 1);
            handshake.addProperty("client_id", applicationId);
            ipc.write(OP_HANDSHAKE, handshake.toString());
            ipc.read(OP_FRAME);
        } catch (IOException e) {
            ipc.close();
            throw e;
        }
        return ipc;
    }

    private static @Nullable ByteChannel openPipe() {
        for (int i = 0; i < SOCKET_COUNT; i++) {
            try {
                return new RandomAccessFile("\\\\.\\pipe\\" + SOCKET_NAME + i, "rw").getChannel();
            } catch (FileNotFoundException e) {
                // No client on this slot.
            }
        }
        return null;
    }

    private static @Nullable ByteChannel openSocket() {
        Path directory = Path.of(socketDirectory());
        for (String subdirectory : UNIX_SUBDIRECTORIES) {
            for (int i = 0; i < SOCKET_COUNT; i++) {
                Path socket = directory.resolve(subdirectory).resolve(SOCKET_NAME + i);
                if (!Files.exists(socket)) {
                    continue;
                }

                try {
                    return SocketChannel.open(UnixDomainSocketAddress.of(socket));
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

        JsonObject command = new JsonObject();
        command.addProperty("cmd", "SET_ACTIVITY");
        command.add("args", args);
        command.addProperty("nonce", Integer.toString(++this.nonce));
        this.write(OP_FRAME, command.toString());

        JsonObject response = parse(this.read(OP_FRAME));
        JsonElement event = response.get("evt");
        if (event != null && event.isJsonPrimitive() && "ERROR".equals(event.getAsString())) {
            TabbyRPC.LOGGER.warn("Discord rejected the activity: {}", response.get("data"));
        }
    }

    /**
     * Checks that Discord is still there. Without this a closed client would only be noticed on the next activity
     * update.
     */
    void ping() throws IOException {
        this.write(OP_PING, "{}");
        this.read(OP_PONG);
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
     * Reads frames until one with the given opcode arrives and returns its payload.
     */
    private String read(int opcode) throws IOException {
        while (true) {
            this.header.clear();
            this.readFully(this.header);
            this.header.flip();
            int received = this.header.getInt();
            int length = this.header.getInt();
            if (length < 0 || length > MAX_PAYLOAD_LENGTH) {
                throw new IOException("Invalid frame length " + length);
            }

            ByteBuffer buffer = ByteBuffer.allocate(length);
            this.readFully(buffer);
            String payload = new String(buffer.array(), StandardCharsets.UTF_8);
            if (received == opcode) {
                return payload;
            } else if (received == OP_CLOSE) {
                throw new ClosedException(payload);
            } else if (received == OP_PING) {
                this.write(OP_PONG, payload);
            }
        }
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
            throw new IOException("Malformed response from Discord", e);
        }
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
