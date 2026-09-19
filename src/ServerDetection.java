import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ServerDetection {
    private static final int MAX_FORGE_DATA_SIZE = 1024 * 1024;

    private ServerDetection() {
    }

    public static Result detect(JSONObject status, String version, int protocolVersion,
                                JoinStatus joinStatus, String kickReason) {
        ServerPlatform platform = platformFromText(version + " " + kickReason);
        Boolean clientModsRequired = joinStatus == JoinStatus.MODS_REQUIRED ? Boolean.TRUE : null;
        List<DetectedMod> mods = new ArrayList<>();
        boolean modListTruncated = false;

        JSONObject forgeData = status.optJSONObject("forgeData");
        if (forgeData != null) {
            if (platform != ServerPlatform.NEOFORGE) {
                platform = ServerPlatform.FORGE;
            }
            ForgeDetails details = readForgeData(forgeData, protocolVersion);
            if (details.clientModsRequired != null) {
                clientModsRequired = details.clientModsRequired;
            }
            mods.addAll(details.mods);
            modListTruncated = details.truncated;
        }

        JSONObject modInfo = status.optJSONObject("modinfo");
        if (modInfo != null) {
            String type = modInfo.optString("type", "").toLowerCase(Locale.ROOT);
            if (type.contains("forge") || type.contains("fml")) {
                if (platform != ServerPlatform.NEOFORGE) {
                    platform = ServerPlatform.FORGE;
                }
            } else if (type.contains("bukkit")) {
                platform = ServerPlatform.BUKKIT;
            }
            addMods(mods, modInfo.optJSONArray("modList"));
        }

        if (clientModsRequired == null && joinStatus == JoinStatus.OPEN && !platform.isModLoader()) {
            clientModsRequired = Boolean.FALSE;
        }

        return new Result(platform, clientModsRequired, uniqueMods(mods), modListTruncated);
    }

    private static ServerPlatform platformFromText(String value) {
        String text = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (text.contains("mohist")) return ServerPlatform.MOHIST;
        if (text.contains("magma")) return ServerPlatform.MAGMA;
        if (text.contains("arclight")) return ServerPlatform.ARCLIGHT;
        if (text.contains("neoforge") || text.contains("neo forge")) return ServerPlatform.NEOFORGE;
        if (text.contains("purpur")) return ServerPlatform.PURPUR;
        if (text.contains("paper")) return ServerPlatform.PAPER;
        if (text.contains("spigot")) return ServerPlatform.SPIGOT;
        if (text.contains("craftbukkit") || text.contains("bukkit")) return ServerPlatform.BUKKIT;
        if (text.contains("fabric")) return ServerPlatform.FABRIC;
        if (text.contains("quilt")) return ServerPlatform.QUILT;
        if (text.contains("forge") || text.contains("fml")) return ServerPlatform.FORGE;
        if (text.contains("velocity")) return ServerPlatform.VELOCITY;
        if (text.contains("waterfall")) return ServerPlatform.WATERFALL;
        if (text.contains("bungeecord") || text.contains("bungee cord")) return ServerPlatform.BUNGEECORD;
        return text.trim().isEmpty() || text.contains("unknown")
            ? ServerPlatform.UNKNOWN
            : ServerPlatform.VANILLA_OR_HIDDEN;
    }

    private static ForgeDetails readForgeData(JSONObject forgeData, int protocolVersion) {
        List<DetectedMod> mods = new ArrayList<>();
        addMods(mods, forgeData.optJSONArray("mods"));
        boolean truncated = forgeData.optBoolean("truncated", false);
        boolean packedFailed = false;

        String packed = forgeData.optString("d", "");
        if (!packed.isEmpty()) {
            try {
                return readPackedForgeData(packed, protocolVersion);
            } catch (IOException | RuntimeException ignored) {
                packedFailed = true;
            }
        }

        JSONArray channels = forgeData.optJSONArray("channels");
        if (channels != null) {
            for (int i = 0; i < channels.length(); i++) {
                JSONObject channel = channels.optJSONObject(i);
                if (channel != null && channel.optBoolean("required", false)) {
                    return new ForgeDetails(Boolean.TRUE, mods, truncated);
                }
            }
            Boolean required = truncated || packedFailed ? null : Boolean.FALSE;
            return new ForgeDetails(required, mods, truncated);
        }
        return new ForgeDetails(null, mods, truncated);
    }

    private static ForgeDetails readPackedForgeData(String packed, int protocolVersion) throws IOException {
        byte[] decoded = decodeUtf15(packed);
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(decoded))) {
            boolean truncated = input.readBoolean();
            boolean clientModsRequired = false;
            List<DetectedMod> mods = new ArrayList<>();
            int modCount = input.readUnsignedShort();
            for (int i = 0; i < modCount; i++) {
                int channelData = readVarInt(input);
                String id = readString(input);
                String version = "";
                if ((channelData & 1) == 0) {
                    version = readString(input);
                }
                mods.add(new DetectedMod(id, version));
                int channelCount = channelData >>> 1;
                for (int channel = 0; channel < channelCount; channel++) {
                    readString(input);
                    readChannelVersion(input, protocolVersion);
                    if (input.readBoolean()) {
                        clientModsRequired = true;
                    }
                }
            }

            int extraChannels = readVarInt(input);
            for (int i = 0; i < extraChannels; i++) {
                readString(input);
                readChannelVersion(input, protocolVersion);
                if (input.readBoolean()) {
                    clientModsRequired = true;
                }
            }
            Boolean required = clientModsRequired ? Boolean.TRUE : (truncated ? null : Boolean.FALSE);
            return new ForgeDetails(required, mods, truncated);
        }
    }

    private static void addMods(List<DetectedMod> mods, JSONArray values) {
        if (values == null) return;
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.optJSONObject(i);
            if (value == null) continue;

            String id = value.optString("modId", value.optString("modid", "")).trim();
            String version = value.optString("modmarker", value.optString("version", "")).trim();
            if (!id.isEmpty()) {
                mods.add(new DetectedMod(id, version));
            }
        }
    }

    private static List<DetectedMod> uniqueMods(List<DetectedMod> mods) {
        Map<String, DetectedMod> unique = new LinkedHashMap<>();
        for (DetectedMod mod : mods) {
            unique.put(mod.getId().toLowerCase(Locale.ROOT), mod);
        }
        return new ArrayList<>(unique.values());
    }

    private static void readChannelVersion(DataInputStream input, int protocolVersion) throws IOException {
        if (protocolVersion >= 763) {
            readVarInt(input);
        } else {
            readString(input);
        }
    }

    private static byte[] decodeUtf15(String value) throws IOException {
        if (value.length() < 2) {
            throw new EOFException("Missing Forge data length");
        }

        int size = value.charAt(0) | (value.charAt(1) << 15);
        if (size < 0 || size > MAX_FORGE_DATA_SIZE) {
            throw new IOException("Forge data is too large");
        }

        byte[] result = new byte[size];
        int output = 0;
        int buffer = 0;
        int bits = 0;
        for (int i = 2; i < value.length() && output < size; i++) {
            buffer |= value.charAt(i) << bits;
            bits += 15;
            while (bits >= 8 && output < size) {
                result[output++] = (byte) buffer;
                buffer >>>= 8;
                bits -= 8;
            }
        }
        if (output != size) {
            throw new EOFException("Incomplete Forge data");
        }
        return result;
    }

    private static int readVarInt(DataInputStream input) throws IOException {
        int value = 0;
        int position = 0;
        while (position < 35) {
            int current = input.readUnsignedByte();
            value |= (current & 0x7F) << position;
            if ((current & 0x80) == 0) {
                return value;
            }
            position += 7;
        }
        throw new IOException("VarInt is too long");
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = readVarInt(input);
        if (length < 0 || length > 32767) {
            throw new IOException("Invalid string length");
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static final class Result {
        private final ServerPlatform platform;
        private final Boolean clientModsRequired;
        private final List<DetectedMod> mods;
        private final boolean modListTruncated;

        Result(ServerPlatform platform, Boolean clientModsRequired,
               List<DetectedMod> mods, boolean modListTruncated) {
            this.platform = platform;
            this.clientModsRequired = clientModsRequired;
            this.mods = mods;
            this.modListTruncated = modListTruncated;
        }

        public ServerPlatform getPlatform() {
            return platform;
        }

        public Boolean getClientModsRequired() {
            return clientModsRequired;
        }

        public List<DetectedMod> getMods() {
            return new ArrayList<>(mods);
        }

        public boolean isModListTruncated() {
            return modListTruncated;
        }
    }

    private static final class ForgeDetails {
        private final Boolean clientModsRequired;
        private final List<DetectedMod> mods;
        private final boolean truncated;

        ForgeDetails(Boolean clientModsRequired, List<DetectedMod> mods, boolean truncated) {
            this.clientModsRequired = clientModsRequired;
            this.mods = mods;
            this.truncated = truncated;
        }
    }
}
