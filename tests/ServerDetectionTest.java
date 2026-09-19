import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

public class ServerDetectionTest {
    public static void main(String[] args) throws Exception {
        JSONObject forge = new JSONObject().put("forgeData", new JSONObject()
            .put("channels", new JSONArray().put(new JSONObject().put("required", true)))
            .put("mods", new JSONArray().put(new JSONObject()
                .put("modId", "create").put("modmarker", "0.5.1"))));
        ServerDetection.Result forgeResult = expect(forge, "1.19.2", 760,
            JoinStatus.UNKNOWN, ServerPlatform.FORGE, Boolean.TRUE);
        expectMod(forgeResult, "create", "0.5.1");

        JSONObject shortened = new JSONObject().put("forgeData", new JSONObject()
            .put("channels", new JSONArray()).put("truncated", true));
        ServerDetection.Result shortenedResult = expect(shortened, "1.20.1", 763,
            JoinStatus.UNKNOWN, ServerPlatform.FORGE, null);
        if (!shortenedResult.isModListTruncated()) {
            throw new AssertionError("Truncated Forge data was not marked as partial");
        }

        expect(new JSONObject(), "Paper 1.21.4", 768, JoinStatus.OPEN,
            ServerPlatform.PAPER, Boolean.FALSE);
        expect(new JSONObject(), "1.21.4", 768, JoinStatus.ONLINE_MODE,
            ServerPlatform.VANILLA_OR_HIDDEN, null);

        JSONObject legacy = new JSONObject().put("modinfo", new JSONObject()
            .put("type", "FML").put("modList", new JSONArray()));
        legacy.getJSONObject("modinfo").getJSONArray("modList")
            .put(new JSONObject().put("modid", "jei").put("version", "4.16"));
        ServerDetection.Result legacyResult = expect(legacy, "1.12.2", 340, JoinStatus.OPEN,
            ServerPlatform.FORGE, null);
        expectMod(legacyResult, "jei", "4.16");

        expect(new JSONObject(), "NeoForge 21.1", 767, JoinStatus.MODS_REQUIRED,
            ServerPlatform.NEOFORGE, Boolean.TRUE);

        JSONObject packed = new JSONObject().put("forgeData", new JSONObject()
            .put("channels", new JSONArray())
            .put("mods", new JSONArray())
            .put("d", packedForgeData()));
        ServerDetection.Result packedResult = expect(packed, "1.20.1", 763, JoinStatus.UNKNOWN,
            ServerPlatform.FORGE, Boolean.TRUE);
        expectMod(packedResult, "examplemod", "1.0");

        ServerInfo info = new ServerInfo("localhost", 25565, true, "1.20.1", 0, 20, "",
            10, 763, JoinStatus.UNKNOWN, "", ServerPlatform.FORGE, Boolean.TRUE,
            packedResult.getMods(), packedResult.isModListTruncated());
        if (!"Mods (1): examplemod".equals(info.getCompactModSummary())) {
            throw new AssertionError("Unexpected compact mod summary: " + info.getCompactModSummary());
        }

        System.out.println("Server detection checks passed");
    }

    private static ServerDetection.Result expect(JSONObject status, String version, int protocol,
                                                 JoinStatus joinStatus, ServerPlatform platform,
                                                 Boolean clientModsRequired) {
        ServerDetection.Result result = ServerDetection.detect(status, version, protocol, joinStatus, "");
        if (result.getPlatform() != platform) {
            throw new AssertionError(version + " -> " + result.getPlatform() + ", expected " + platform);
        }
        if (clientModsRequired == null ? result.getClientModsRequired() != null
                : !clientModsRequired.equals(result.getClientModsRequired())) {
            throw new AssertionError(version + " mods -> " + result.getClientModsRequired()
                + ", expected " + clientModsRequired);
        }
        return result;
    }

    private static void expectMod(ServerDetection.Result result, String id, String version) {
        DetectedMod expected = new DetectedMod(id, version);
        if (!result.getMods().contains(expected)) {
            throw new AssertionError("Missing mod " + expected + " in " + result.getMods());
        }
    }

    private static String packedForgeData() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeBoolean(false);
        output.writeShort(1);
        writeVarInt(output, 2);
        writeString(output, "examplemod");
        writeString(output, "1.0");
        writeString(output, "main");
        writeVarInt(output, 1);
        output.writeBoolean(true);
        writeVarInt(output, 0);
        return encodeUtf15(bytes.toByteArray());
    }

    private static void writeString(DataOutputStream output, String value) throws Exception {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(output, bytes.length);
        output.write(bytes);
    }

    private static void writeVarInt(DataOutputStream output, int value) throws Exception {
        while ((value & ~0x7F) != 0) {
            output.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        output.writeByte(value);
    }

    private static String encodeUtf15(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        text.append((char) (bytes.length & 0x7FFF));
        text.append((char) (bytes.length >>> 15));

        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer |= (value & 0xFF) << bits;
            bits += 8;
            if (bits >= 15) {
                text.append((char) (buffer & 0x7FFF));
                buffer >>>= 15;
                bits -= 15;
            }
        }
        if (bits > 0) {
            text.append((char) (buffer & 0x7FFF));
        }
        return text.toString();
    }
}
