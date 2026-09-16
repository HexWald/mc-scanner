import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

public class JoinReasonClassifierTest {
    public static void main(String[] args) {
        expect(JoinStatus.WHITELIST,
            "{\"translate\":\"multiplayer.disconnect.not_whitelisted\"}");
        expect(JoinStatus.WHITELIST,
            "Вы не находитесь в белом списке этого сервера");
        expect(JoinStatus.MODS_REQUIRED,
            "{\"text\":\"This server has mods that require Forge to be installed on the client.\"}");
        expect(JoinStatus.BANNED,
            "{\"translate\":\"multiplayer.disconnect.banned.reason\",\"with\":[\"Testing\"]}");
        expect(JoinStatus.SERVER_FULL,
            "{\"translate\":\"multiplayer.disconnect.server_full\"}");
        expect(JoinStatus.VERSION_MISMATCH,
            "{\"translate\":\"multiplayer.disconnect.incompatible\",\"with\":[\"1.21.4\"]}");
        expect(JoinStatus.AUTH_FAILED, "Failed to verify username!");
        expect(JoinStatus.RATE_LIMITED, "Connection throttled! Please wait before reconnecting.");
        expect(JoinStatus.REJECTED, "Maintenance in progress");

        String readable = MinecraftProtocol.readableDisconnectReason(
            "{\"text\":\"Missing mods:\",\"extra\":[{\"text\":\" Create\"}]}"
        );
        if (!"Missing mods: Create".equals(readable)) {
            throw new AssertionError("Unexpected readable reason: " + readable);
        }

        expectLoginStartSize(758, 7); // Name only.
        expectLoginStartSize(759, 8); // Name and optional profile key.
        expectLoginStartSize(760, 9); // Name, optional profile key and optional UUID.
        expectLoginStartSize(761, 8); // Name and optional UUID.
        expectLoginStartSize(764, 23); // Name and required UUID.

        System.out.println("Join reason checks passed");
    }

    private static void expect(JoinStatus expected, String message) {
        JoinStatus actual = MinecraftProtocol.classifyDisconnectReason(message);
        if (actual != expected) {
            throw new AssertionError(message + " -> " + actual + ", expected " + expected);
        }
    }

    private static void expectLoginStartSize(int protocol, int expectedSize) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            MinecraftProtocol.sendLoginStart(new DataOutputStream(bytes), "Test", protocol);
            if (bytes.size() != expectedSize) {
                throw new AssertionError("Protocol " + protocol + " produced " + bytes.size()
                    + " bytes, expected " + expectedSize);
            }
        } catch (Exception e) {
            throw new AssertionError("Could not encode Login Start for protocol " + protocol, e);
        }
    }
}
