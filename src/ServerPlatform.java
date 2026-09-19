public enum ServerPlatform {
    VANILLA_OR_HIDDEN("Vanilla / hidden", false),
    PAPER("Paper", false),
    PURPUR("Purpur", false),
    SPIGOT("Spigot", false),
    BUKKIT("Bukkit", false),
    FORGE("Forge", true),
    NEOFORGE("NeoForge", true),
    FABRIC("Fabric", true),
    QUILT("Quilt", true),
    VELOCITY("Velocity", false),
    WATERFALL("Waterfall", false),
    BUNGEECORD("BungeeCord", false),
    MOHIST("Mohist", true),
    MAGMA("Magma", true),
    ARCLIGHT("Arclight", true),
    UNKNOWN("Unknown", false);

    private final String label;
    private final boolean modLoader;

    ServerPlatform(String label, boolean modLoader) {
        this.label = label;
        this.modLoader = modLoader;
    }

    public String getLabel() {
        return label;
    }

    public boolean isModLoader() {
        return modLoader;
    }
}
