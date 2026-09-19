import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.json.JSONObject;
import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONTokener;
import java.util.HashMap;
import java.util.Map;

public class MinecraftProtocol {
    private static final int CONNECT_TIMEOUT = 3000;
    private static final int READ_TIMEOUT = 5000;
    private static final String DEFAULT_CHECK_USERNAME = "MCScanner";
    
    private static final int[] PROTOCOL_VERSIONS = {
        774, 773, 772, 770, 769, 768, 767, 766, 765, 764, 763, 762, 761, 760, 759,
        758, 757, 756, 755, 754, 753, 751, 736, 735, 578, 575, 498, 477, 404, 393,
        340, 335, 316, 315, 210, 110, 109, 108, 107, 47, 5
    };
    
    // Mapping version names to protocol numbers
    private static final Map<String, Integer> VERSION_MAP = new HashMap<>();
    private static final Map<Integer, String> CLIENT_VERSION_MAP = new HashMap<>();
    static {
        VERSION_MAP.put("1.21.11", 774);
        VERSION_MAP.put("1.21.10", 773);
        VERSION_MAP.put("1.21.9", 773);
        VERSION_MAP.put("1.21.8", 772);
        VERSION_MAP.put("1.21.6", 770);
        VERSION_MAP.put("1.21.5", 769);
        VERSION_MAP.put("1.21.4", 768);
        VERSION_MAP.put("1.21.3", 768);
        VERSION_MAP.put("1.21.2", 768);
        VERSION_MAP.put("1.21.1", 767);
        VERSION_MAP.put("1.21", 767);
        VERSION_MAP.put("1.20.6", 766);
        VERSION_MAP.put("1.20.5", 766);
        VERSION_MAP.put("1.20.4", 765);
        VERSION_MAP.put("1.20.3", 765);
        VERSION_MAP.put("1.20.2", 764);
        VERSION_MAP.put("1.20.1", 763);
        VERSION_MAP.put("1.20", 763);
        VERSION_MAP.put("1.19.4", 762);
        VERSION_MAP.put("1.19.3", 761);
        VERSION_MAP.put("1.19.2", 760);
        VERSION_MAP.put("1.19.1", 760);
        VERSION_MAP.put("1.19", 759);
        VERSION_MAP.put("1.18.2", 758);
        VERSION_MAP.put("1.18.1", 757);
        VERSION_MAP.put("1.18", 757);
        VERSION_MAP.put("1.17.1", 756);
        VERSION_MAP.put("1.17", 755);
        VERSION_MAP.put("1.16.5", 754);
        VERSION_MAP.put("1.16.4", 754);
        VERSION_MAP.put("1.16.3", 753);
        VERSION_MAP.put("1.16.2", 751);
        VERSION_MAP.put("1.16.1", 736);
        VERSION_MAP.put("1.16", 735);
        VERSION_MAP.put("1.15.2", 578);
        VERSION_MAP.put("1.15", 575);
        VERSION_MAP.put("1.14.4", 498);
        VERSION_MAP.put("1.14", 477);
        VERSION_MAP.put("1.13.2", 404);
        VERSION_MAP.put("1.13", 393);
        VERSION_MAP.put("1.12.2", 340);
        VERSION_MAP.put("1.12", 335);
        VERSION_MAP.put("1.11.2", 316);
        VERSION_MAP.put("1.11.1", 316);
        VERSION_MAP.put("1.11", 315);
        VERSION_MAP.put("1.10", 210);
        VERSION_MAP.put("1.9.4", 110);
        VERSION_MAP.put("1.9.3", 110);
        VERSION_MAP.put("1.9.2", 109);
        VERSION_MAP.put("1.9.1", 108);
        VERSION_MAP.put("1.9", 107);
        VERSION_MAP.put("1.8", 47);
        VERSION_MAP.put("1.7.10", 5);

        CLIENT_VERSION_MAP.put(774, "1.21.11");
        CLIENT_VERSION_MAP.put(773, "1.21.9");
        CLIENT_VERSION_MAP.put(772, "1.21.8");
        CLIENT_VERSION_MAP.put(770, "1.21.6");
        CLIENT_VERSION_MAP.put(769, "1.21.5");
        CLIENT_VERSION_MAP.put(768, "1.21.4");
        CLIENT_VERSION_MAP.put(767, "1.21.1");
        CLIENT_VERSION_MAP.put(766, "1.20.6");
        CLIENT_VERSION_MAP.put(765, "1.20.4");
        CLIENT_VERSION_MAP.put(764, "1.20.2");
        CLIENT_VERSION_MAP.put(763, "1.20.1");
        CLIENT_VERSION_MAP.put(762, "1.19.4");
        CLIENT_VERSION_MAP.put(761, "1.19.3");
        CLIENT_VERSION_MAP.put(760, "1.19.2");
        CLIENT_VERSION_MAP.put(759, "1.19");
        CLIENT_VERSION_MAP.put(758, "1.18.2");
        CLIENT_VERSION_MAP.put(757, "1.18.1");
        CLIENT_VERSION_MAP.put(756, "1.17.1");
        CLIENT_VERSION_MAP.put(755, "1.17");
        CLIENT_VERSION_MAP.put(754, "1.16.5");
        CLIENT_VERSION_MAP.put(753, "1.16.3");
        CLIENT_VERSION_MAP.put(751, "1.16.2");
        CLIENT_VERSION_MAP.put(736, "1.16.1");
        CLIENT_VERSION_MAP.put(735, "1.16");
        CLIENT_VERSION_MAP.put(578, "1.15.2");
        CLIENT_VERSION_MAP.put(575, "1.15");
        CLIENT_VERSION_MAP.put(498, "1.14.4");
        CLIENT_VERSION_MAP.put(477, "1.14");
        CLIENT_VERSION_MAP.put(404, "1.13.2");
        CLIENT_VERSION_MAP.put(393, "1.13");
        CLIENT_VERSION_MAP.put(340, "1.12.2");
        CLIENT_VERSION_MAP.put(335, "1.12");
        CLIENT_VERSION_MAP.put(316, "1.11.2");
        CLIENT_VERSION_MAP.put(315, "1.11");
        CLIENT_VERSION_MAP.put(210, "1.10");
        CLIENT_VERSION_MAP.put(110, "1.9.4");
        CLIENT_VERSION_MAP.put(109, "1.9.2");
        CLIENT_VERSION_MAP.put(108, "1.9.1");
        CLIENT_VERSION_MAP.put(107, "1.9");
        CLIENT_VERSION_MAP.put(47, "1.8");
        CLIENT_VERSION_MAP.put(5, "1.7.10");
    }
    
    public static ServerInfo queryServer(String ip, int port) {
        return queryServer(ip, port, DEFAULT_CHECK_USERNAME);
    }

    public static ServerInfo queryServer(String ip, int port, String checkUsername) {
        try {
            return performHandshake(ip, port, normalizeCheckUsername(checkUsername));
        } catch (Exception e) {
            return new ServerInfo(ip, port);
        }
    }

    private static String normalizeCheckUsername(String checkUsername) {
        if (checkUsername == null || !checkUsername.matches("[A-Za-z0-9_]{3,16}")) {
            return DEFAULT_CHECK_USERNAME;
        }
        return checkUsername;
    }
    
    private static ServerInfo performHandshake(String ip, int port, String checkUsername) throws IOException {
        long startTime = System.currentTimeMillis();
        
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(READ_TIMEOUT);
            socket.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT);
            
            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {
                
                sendHandshake(out, ip, port, PROTOCOL_VERSIONS[0]);
                
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
                
                return parseServerInfo(ip, port, json, ping, checkUsername);
            }
        }
    }
    
    private static void sendHandshake(DataOutputStream out, String host, int port, int protocolVersion) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream handshake = new DataOutputStream(buffer);
        
        handshake.writeByte(0);
        writeVarInt(handshake, protocolVersion);
        writeString(handshake, host);
        handshake.writeShort(port);
        writeVarInt(handshake, 1);
        
        byte[] packet = buffer.toByteArray();
        writeVarInt(out, packet.length);
        out.write(packet);
    }
    
    private static ServerInfo parseServerInfo(String ip, int port, String json, long ping, String checkUsername) {
        try {
            JSONObject obj = new JSONObject(json);
            
            String version = obj.optJSONObject("version") != null
                ? obj.getJSONObject("version").optString("name", "Unknown")
                : "Unknown";
            
            int protocolVersion = obj.optJSONObject("version") != null
                ? obj.getJSONObject("version").optInt("protocol", -1)
                : -1;
            
            JSONObject players = obj.optJSONObject("players");
            int online = players != null ? players.optInt("online", 0) : 0;
            int max = players != null ? players.optInt("max", 0) : 0;
            
            String motd = "";
            if (obj.has("description")) {
                Object desc = obj.get("description");
                if (desc instanceof String) {
                    motd = (String) desc;
                } else if (desc instanceof JSONObject) {
                    motd = extractTextFromJson((JSONObject) desc);
                }
            }
            
            LoginCheckResult loginCheck = checkJoinSmart(ip, port, version, protocolVersion, checkUsername);
            ServerDetection.Result detection = ServerDetection.detect(
                obj, version, protocolVersion, loginCheck.joinStatus, loginCheck.reason);
            
            return new ServerInfo(ip, port, true, version, online, max, motd, ping, protocolVersion,
                loginCheck.joinStatus, loginCheck.reason, detection.getPlatform(),
                detection.getClientModsRequired(), detection.getMods(), detection.isModListTruncated());
            
        } catch (JSONException e) {
            return new ServerInfo(ip, port, true, "Parse Error", 0, 0, "", false, ping);
        }
    }
    
    private static String extractTextFromJson(JSONObject json) {
        StringBuilder text = new StringBuilder();
        
        if (json.has("text")) {
            text.append(json.optString("text", ""));
        }
        
        if (json.has("extra")) {
            try {
                JSONArray extra = json.getJSONArray("extra");
                for (int i = 0; i < extra.length(); i++) {
                    Object item = extra.get(i);
                    if (item instanceof String) {
                        text.append(item);
                    } else if (item instanceof JSONObject) {
                        text.append(extractTextFromJson((JSONObject) item));
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        
        return text.toString();
    }
    
    private static LoginCheckResult checkJoinSmart(String ip, int port, String version,
                                                   int reportedProtocol, String checkUsername) {
        Set<Integer> candidates = new LinkedHashSet<>();
        Integer detectedProtocol = getProtocolFromVersion(version);
        if (reportedProtocol > 0) {
            candidates.add(reportedProtocol);
        }
        if (detectedProtocol != null) {
            candidates.add(detectedProtocol);
        }
        if (candidates.isEmpty()) {
            int[] fallbackProtocols = {774, 772, 768, 767, 765, 763, 760, 758, 754, 340, 47, 5};
            for (int protocol : fallbackProtocols) {
                candidates.add(protocol);
            }
        }

        LoginCheckResult bestResult = LoginCheckResult.unknown("");
        for (int protocol : candidates) {
            LoginCheckResult result = checkLogin(ip, port, checkUsername, protocol);
            if (result.state == CheckState.MATCHED) {
                System.out.println("[Join Check] " + ip + ":" + port + " -> "
                    + result.joinStatus.getLabel()
                    + (result.reason.isEmpty() ? "" : " (" + result.reason + ")"));
                return result;
            }
            if (result.state == CheckState.PROTOCOL_MISMATCH || bestResult.reason.isEmpty()) {
                bestResult = result;
            }
        }

        System.out.println("[Join Check] " + ip + ":" + port + " -> Unknown");
        return bestResult;
    }

    public static String getClientVersionName(String reportedVersion, int protocolVersion) {
        String byProtocol = CLIENT_VERSION_MAP.get(protocolVersion);
        if (byProtocol != null) {
            return byProtocol;
        }

        Integer detectedProtocol = getProtocolFromVersion(reportedVersion);
        if (detectedProtocol == null) {
            return "";
        }

        String byReportedName = CLIENT_VERSION_MAP.get(detectedProtocol);
        return byReportedName != null ? byReportedName : "";
    }
    
    private static Integer getProtocolFromVersion(String version) {
        if (version == null || version.isEmpty()) return null;
        
        // Clean version string (remove "Minecraft ", "Paper ", "Spigot ", etc.)
        String cleanVersion = version
            .replaceAll("(?i)(minecraft|paper|spigot|purpur|fabric|forge|bungeecord|waterfall|velocity)\\s*", "")
            .trim();
        
        // Try exact match
        if (VERSION_MAP.containsKey(cleanVersion)) {
            return VERSION_MAP.get(cleanVersion);
        }
        
        // Try to extract version number (e.g., "1.20.1" from "Paper 1.20.1")
        String[] parts = cleanVersion.split("\\s+");
        for (String part : parts) {
            if (VERSION_MAP.containsKey(part)) {
                return VERSION_MAP.get(part);
            }
        }
        
        // Try partial match for versions like "1.20.x"
        for (Map.Entry<String, Integer> entry : VERSION_MAP.entrySet()) {
            if (cleanVersion.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    private enum CheckState {
        MATCHED,
        PROTOCOL_MISMATCH,
        ERROR
    }

    private static class LoginCheckResult {
        final CheckState state;
        final JoinStatus joinStatus;
        final String reason;

        LoginCheckResult(CheckState state, JoinStatus joinStatus, String reason) {
            this.state = state;
            this.joinStatus = joinStatus;
            this.reason = reason != null ? reason : "";
        }

        static LoginCheckResult matched(JoinStatus status, String reason) {
            return new LoginCheckResult(CheckState.MATCHED, status, reason);
        }

        static LoginCheckResult unknown(String reason) {
            return new LoginCheckResult(CheckState.ERROR, JoinStatus.UNKNOWN, reason);
        }
    }

    private static LoginCheckResult checkLogin(String ip, int port, String username, int protocolVersion) {
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(2500);
            socket.connect(new InetSocketAddress(ip, port), 3000);

            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {
                sendLoginHandshake(out, ip, port, protocolVersion);
                sendLoginStart(out, username, protocolVersion);

                byte[] response = readPacket(in);
                return readLoginResponse(response, in, false);
            }
        } catch (Exception e) {
            return LoginCheckResult.unknown(e.getMessage());
        }
    }

    private static void sendLoginHandshake(DataOutputStream out, String host, int port,
                                           int protocolVersion) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream handshake = new DataOutputStream(buffer);
        handshake.writeByte(0);
        writeVarInt(handshake, protocolVersion);
        writeString(handshake, host);
        handshake.writeShort(port);
        writeVarInt(handshake, 2);

        byte[] packet = buffer.toByteArray();
        writeVarInt(out, packet.length);
        out.write(packet);
        out.flush();
    }

    static void sendLoginStart(DataOutputStream out, String username,
                               int protocolVersion) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream login = new DataOutputStream(buffer);
        login.writeByte(0);
        writeString(login, username);

        if (protocolVersion == 759) {
            login.writeBoolean(false); // No signed profile key in 1.19.
        } else if (protocolVersion == 760) {
            login.writeBoolean(false); // No signed profile key.
            login.writeBoolean(false); // Let the server create the offline UUID.
        } else if (protocolVersion >= 761 && protocolVersion <= 763) {
            login.writeBoolean(false); // Optional UUID in 1.19.3 through 1.20.1.
        } else if (protocolVersion >= 764) {
            UUID offlineUuid = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
            login.writeLong(offlineUuid.getMostSignificantBits());
            login.writeLong(offlineUuid.getLeastSignificantBits());
        }

        byte[] packet = buffer.toByteArray();
        writeVarInt(out, packet.length);
        out.write(packet);
        out.flush();
    }

    private static LoginCheckResult readLoginResponse(byte[] packet, DataInputStream socketIn,
                                                      boolean compressionEnabled) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet));
        int packetId = readVarInt(in);

        if (packetId == 0x00) {
            String rawReason = readString(in);
            JoinStatus status = classifyDisconnectReason(rawReason);
            String reason = readableDisconnectReason(rawReason);
            CheckState state = status == JoinStatus.VERSION_MISMATCH
                ? CheckState.PROTOCOL_MISMATCH
                : CheckState.MATCHED;
            return new LoginCheckResult(state, status, reason);
        }
        if (packetId == 0x01) {
            return LoginCheckResult.matched(JoinStatus.ONLINE_MODE,
                "Server requires an authenticated Minecraft account");
        }
        if (packetId == 0x02) {
            return LoginCheckResult.matched(JoinStatus.OPEN, "");
        }
        if (packetId == 0x03 && !compressionEnabled) {
            readVarInt(in); // Compression threshold.
            try {
                return readLoginResponse(readCompressedPacket(socketIn), socketIn, true);
            } catch (IOException e) {
                return LoginCheckResult.matched(JoinStatus.UNKNOWN,
                    "Login continued after compression, but the final response was not received");
            }
        }
        if (packetId == 0x04) {
            readVarInt(in); // Login plugin request id.
            String channel = readString(in);
            JoinStatus status = isModdedLoginChannel(channel)
                ? JoinStatus.MODS_REQUIRED
                : JoinStatus.REJECTED;
            return LoginCheckResult.matched(status, "Custom login channel: " + channel);
        }

        return LoginCheckResult.matched(JoinStatus.UNKNOWN,
            "Unexpected login packet 0x" + Integer.toHexString(packetId).toUpperCase(Locale.ROOT));
    }

    private static byte[] readPacket(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length <= 0 || length > 1_048_576) {
            throw new IOException("Invalid packet length: " + length);
        }
        byte[] packet = new byte[length];
        in.readFully(packet);
        return packet;
    }

    private static byte[] readCompressedPacket(DataInputStream in) throws IOException {
        byte[] frame = readPacket(in);
        DataInputStream frameIn = new DataInputStream(new ByteArrayInputStream(frame));
        int uncompressedLength = readVarInt(frameIn);
        byte[] payload = new byte[frameIn.available()];
        frameIn.readFully(payload);
        if (uncompressedLength == 0) {
            return payload;
        }
        if (uncompressedLength < 0 || uncompressedLength > 1_048_576) {
            throw new IOException("Invalid uncompressed packet length: " + uncompressedLength);
        }

        Inflater inflater = new Inflater();
        inflater.setInput(payload);
        byte[] result = new byte[uncompressedLength];
        int offset = 0;
        try {
            while (!inflater.finished() && offset < result.length) {
                int count = inflater.inflate(result, offset, result.length - offset);
                if (count == 0) {
                    if (inflater.needsInput()) {
                        break;
                    }
                    throw new IOException("Compressed login packet could not be decoded");
                }
                offset += count;
            }
        } catch (DataFormatException e) {
            throw new IOException("Invalid compressed login packet", e);
        } finally {
            inflater.end();
        }
        if (offset != uncompressedLength) {
            throw new IOException("Incomplete compressed login packet");
        }
        return result;
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length < 0 || length > 262144 || length > in.available()) {
            throw new IOException("Invalid string length: " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static boolean isModdedLoginChannel(String channel) {
        String lower = channel.toLowerCase(Locale.ROOT);
        return lower.contains("forge") || lower.contains("fml") || lower.contains("neoforge")
            || lower.contains("fabric") || lower.contains("quilt");
    }

    static JoinStatus classifyDisconnectReason(String message) {
        String lower = (message + " " + readableDisconnectReason(message)).toLowerCase(Locale.ROOT);

        if (containsAny(lower,
                "multiplayer.disconnect.not_whitelisted", "not whitelisted", "not on the whitelist",
                "white-list", "white list", "allowlist", "вайтлист", "белом списке", "белый список")) {
            return JoinStatus.WHITELIST;
        }
        if (containsAny(lower,
                "mods that require forge", "requires forge", "require fml", "requires neoforge",
                "requires fabric", "incompatible mod", "mod mismatch", "mismatched mod",
                "missing required mod", "missing mods", "необходимы моды", "требуются моды")) {
            return JoinStatus.MODS_REQUIRED;
        }
        if (containsAny(lower,
                "multiplayer.disconnect.banned", "multiplayer.disconnect.ip_banned",
                "you are banned", "you have been banned", "ip banned", "забанен", "заблокирован")) {
            return JoinStatus.BANNED;
        }
        if (containsAny(lower,
                "multiplayer.disconnect.server_full", "server is full", "server full",
                "сервер заполнен", "сервер полон")) {
            return JoinStatus.SERVER_FULL;
        }
        if (containsAny(lower,
                "connection throttled", "too many connections", "rate limit",
                "слишком много подключений", "слишком част")) {
            return JoinStatus.RATE_LIMITED;
        }
        if (containsAny(lower,
                "multiplayer.disconnect.unverified_username", "failed to verify username",
                "unverified username", "invalid session", "not authenticated with minecraft.net",
                "authentication servers are down", "online mode", "не удалось проверить имя",
                "недействительная сессия", "ошибка авторизации")) {
            return JoinStatus.AUTH_FAILED;
        }
        if (isVersionMismatch(lower)) {
            return JoinStatus.VERSION_MISMATCH;
        }
        return JoinStatus.REJECTED;
    }

    private static boolean isVersionMismatch(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return containsAny(lower,
            "multiplayer.disconnect.outdated", "multiplayer.disconnect.incompatible",
            "outdated client", "outdated server", "version mismatch", "incompatible client",
            "несовпадение версий", "устаревший клиент", "устаревший сервер",
            "несовместимая версия", "несовместимый клиент");
    }

    static String readableDisconnectReason(String message) {
        String text = message == null ? "" : message.trim();
        try {
            Object component = new JSONTokener(text).nextValue();
            StringBuilder readable = new StringBuilder();
            appendChatComponent(component, readable);
            if (readable.length() > 0) {
                text = readable.toString();
            }
        } catch (Exception ignored) {
            // Some servers send plain text instead of a JSON chat component.
        }

        text = text.replace("multiplayer.disconnect.not_whitelisted", "Not whitelisted")
            .replace("multiplayer.disconnect.server_full", "Server full")
            .replace("multiplayer.disconnect.unverified_username", "Username verification failed")
            .replace("multiplayer.disconnect.ip_banned", "IP banned")
            .replace("multiplayer.disconnect.banned", "Banned")
            .replace("multiplayer.disconnect.outdated_client", "Outdated client")
            .replace("multiplayer.disconnect.outdated_server", "Outdated server")
            .replace("multiplayer.disconnect.incompatible", "Incompatible version");
        return text.replaceAll("(?i)\\u00A7[0-9A-FK-OR]", "")
            .replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private static void appendChatComponent(Object component, StringBuilder text) {
        if (component instanceof JSONObject) {
            JSONObject json = (JSONObject) component;
            appendPiece(text, json.optString("text", ""));
            appendPiece(text, json.optString("translate", ""));
            appendChatArray(json.optJSONArray("with"), text);
            appendChatArray(json.optJSONArray("extra"), text);
        } else if (component instanceof JSONArray) {
            appendChatArray((JSONArray) component, text);
        } else if (component != null && component != JSONObject.NULL) {
            appendPiece(text, String.valueOf(component));
        }
    }

    private static void appendChatArray(JSONArray array, StringBuilder text) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            appendChatComponent(array.opt(i), text);
        }
    }

    private static void appendPiece(StringBuilder text, String piece) {
        if (piece == null || piece.trim().isEmpty()) {
            return;
        }
        if (text.length() > 0) {
            text.append(' ');
        }
        text.append(piece.trim());
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }


    private static void writeString(DataOutputStream out, String string) throws IOException {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static String repeat(String text, int count) {
        StringBuilder builder = new StringBuilder(text.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(text);
        }
        return builder.toString();
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
