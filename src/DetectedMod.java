import java.util.Objects;

public final class DetectedMod {
    private final String id;
    private final String version;

    public DetectedMod(String id, String version) {
        this.id = id == null ? "" : id.trim();
        this.version = version == null ? "" : version.trim();
    }

    public String getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return version.isEmpty() ? id : id + "@" + version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DetectedMod)) return false;
        DetectedMod mod = (DetectedMod) other;
        return id.equals(mod.id) && version.equals(mod.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version);
    }
}
