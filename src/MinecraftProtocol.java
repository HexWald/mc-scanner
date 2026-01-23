import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONException;

public class MinecraftProtocol {
    private static final int CONNECT_TIMEOUT = 3000;
    private static final int READ_TIMEOUT = 5000;
    private static final int PROTOCOL_VERSION = 4;
    
    public static ServerInfo queryServer(String ip, int port) {
        try {
            return performHandshake(ip, port);
        } catch (Exception e) {
            return new ServerInfo(ip, port);
        }
    }
    
    private static ServerInfo performHandshake(String ip, int port) throws IOException {
        long startTime = System.currentTimeMillis();
        
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(READ_TIMEOUT);
            socket.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT);
            
            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {
                
                sendHandshake(out, ip, port);
                
                out.writeByte(1);
                out.writeByte(0);
                
                readVarInt(in);
                int packetId = readVarInt(in);
                
                if (packetId != 0) {
                    throw new IOException("Invalid packet ID: " + packetId);
                }
                
                int jsonLength = readVarInt(in);
                if (jsonLength <= 0 || jsonLength > 32767) {
                    throw new IOException("Invalid JSON length: " + jsonLength);
                }
                
                byte[] jsonBytes = new byte[jsonLength];
                in.readFully(jsonBytes);
                String json = new String(jsonBytes, StandardCharsets.UTF_8);
                
                long now = System.currentTimeMillis();
                out.writeByte(9);
                out.writeByte(1);
                out.writeLong(now);
                
                readVarInt(in);
                readVarInt(in);
                in.readLong();
                
                long ping = System.currentTimeMillis() - startTime;
                
                return parseServerInfo(ip, port, json, ping);
            }
        }
    }
    
    private static void sendHandshake(DataOutputStream out, String host, int port) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream handshake = new DataOutputStream(buffer);
        
        handshake.writeByte(0);
        writeVarInt(handshake, PROTOCOL_VERSION);
        writeString(handshake, host);
        handshake.writeShort(port);
        writeVarInt(handshake, 1);
        
        byte[] packet = buffer.toByteArray();
        writeVarInt(out, packet.length);
        out.write(packet);
    }
    
    private static ServerInfo parseServerInfo(String ip, int port, String json, long ping) {
        try {
            JSONObject obj = new JSONObject(json);
            
            String version = obj.optJSONObject("version") != null
                ? obj.getJSONObject("version").optString("name", "Unknown")
                : "Unknown";
            
            JSONObject players = obj.optJSONObject("players");
            int online = players != null ? players.optInt("online", 0) : 0;
            int max = players != null ? players.optInt("max", 0) : 0;
            
            String motd = "";
            if (obj.has("description")) {
                Object desc = obj.get("description");
                if (desc instanceof String) {
                    motd = (String) desc;
                } else if (desc instanceof JSONObject) {
                    motd = ((JSONObject) desc).optString("text", "");
                }
            }
            
            boolean hasWhitelist = checkWhitelistWithTimeout(ip, port);
            
            return new ServerInfo(ip, port, true, version, online, max, motd, hasWhitelist, ping);
            
        } catch (JSONException e) {
            return new ServerInfo(ip, port, true, "Parse Error", 0, 0, "", false, ping);
        }
    }
    
    private static boolean checkWhitelistWithTimeout(String ip, int port) {
        final boolean[] result = {false};
        Thread thread = new Thread(() -> result[0] = checkWhitelist(ip, port));
        thread.setDaemon(true);
        thread.start();
        
        try {
            thread.join(2000);
        } catch (InterruptedException e) {
            thread.interrupt();
        }
        
        return result[0];
    }
    
    private static boolean checkWhitelist(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(1500);
            socket.connect(new InetSocketAddress(ip, port), 1500);
            
            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {
                
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                DataOutputStream handshake = new DataOutputStream(buffer);
                handshake.writeByte(0);
                writeVarInt(handshake, PROTOCOL_VERSION);
                writeString(handshake, ip);
                handshake.writeShort(port);
                writeVarInt(handshake, 2);
                
                byte[] packet = buffer.toByteArray();
                writeVarInt(out, packet.length);
                out.write(packet);
                
                buffer = new ByteArrayOutputStream();
                DataOutputStream login = new DataOutputStream(buffer);
                login.writeByte(0);
                writeString(login, "WLTest_" + System.currentTimeMillis());
                
                packet = buffer.toByteArray();
                writeVarInt(out, packet.length);
                out.write(packet);
                
                int length = readVarInt(in);
                int packetId = readVarInt(in);
                
                if (packetId == 0x00) {
                    int msgLength = readVarInt(in);
                    if (msgLength > 0 && msgLength < 10000) {
                        byte[] msgBytes = new byte[msgLength];
                        in.readFully(msgBytes);
                        String message = new String(msgBytes, StandardCharsets.UTF_8).toLowerCase();
                        
                        return message.contains("whitelist") || 
                               message.contains("white-list") ||
                               message.contains("not allowed") ||
                               message.contains("not whitelisted");
                    }
                }
            }
        } catch (Exception ignored) {}
        
        return false;
    }
    
    private static void writeString(DataOutputStream out, String string) throws IOException {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }
    
    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value & 0x7F);
    }
    
    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int position = 0;
        byte currentByte;
        
        do {
            currentByte = in.readByte();
            value |= (currentByte & 0x7F) << position;
            position += 7;
            
            if (position >= 32) {
                throw new IOException("VarInt too big");
            }
        } while ((currentByte & 0x80) != 0);
        
        return value;
    }
}