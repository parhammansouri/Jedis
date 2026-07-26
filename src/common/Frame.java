package common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Frame {
    public static final int LOUNGE = 0x01;
    public static final int DIRECT = 0x02;
    public static final int ACK = 0x03;
    public static final int REGISTER = 0x04;
    public static final int ROOT = 0x05;
    public static final int NOTICE = 0x06;
    public static final int LOGIN = 0x07;

    public static final int ROOT_MUTE = 0xAE;
    public static final int ROOT_KICK = 0xDF;

    private static final int MAX_LENGTH = 1024 * 1024;

    public final int type;
    public final byte[] payload;

    public Frame(int type, byte[] payload) {
        this.type = type;
        this.payload = payload == null ? new byte[0] : payload;
    }

    public static Frame read(DataInputStream input) throws IOException {
        int length;
        try {
            length = input.readInt();
        } catch (EOFException eof) {
            return null;
        }
        if (length < 1 || length > MAX_LENGTH) {
            throw new IOException("invalid frame length");
        }
        int type = input.readUnsignedByte();
        byte[] payload = new byte[length - 1];
        input.readFully(payload);
        return new Frame(type, payload);
    }

    public void write(DataOutputStream output) throws IOException {
        output.writeInt(payload.length + 1);
        output.writeByte(type);
        output.write(payload);
        output.flush();
    }

    public String text() {
        return new String(payload, StandardCharsets.UTF_8);
    }

    public static Frame textFrame(int type, String text) {
        return new Frame(type, text.getBytes(StandardCharsets.UTF_8));
    }

    public static Frame ack(int id) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        data.writeInt(id);
        return new Frame(ACK, bytes.toByteArray());
    }

    public static int ackId(byte[] payload) throws IOException {
        return new DataInputStream(new ByteArrayInputStream(payload)).readInt();
    }

    public static Frame direct(int id, String username, String message) throws IOException {
        byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        if (usernameBytes.length > 255) {
            throw new IOException("username is too long");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        data.writeInt(id);
        data.writeByte(usernameBytes.length);
        data.write(usernameBytes);
        data.write(messageBytes);
        return new Frame(DIRECT, bytes.toByteArray());
    }

    public static DirectPayload directPayload(byte[] payload) throws IOException {
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(payload));
        int id = data.readInt();
        int usernameLength = data.readUnsignedByte();
        byte[] usernameBytes = new byte[usernameLength];
        data.readFully(usernameBytes);
        byte[] messageBytes = new byte[payload.length - 5 - usernameLength];
        data.readFully(messageBytes);
        return new DirectPayload(id, new String(usernameBytes, StandardCharsets.UTF_8),
                new String(messageBytes, StandardCharsets.UTF_8));
    }

    public static final class DirectPayload {
        public final int id;
        public final String username;
        public final String message;

        DirectPayload(int id, String username, String message) {
            this.id = id;
            this.username = username;
            this.message = message;
        }
    }
}
