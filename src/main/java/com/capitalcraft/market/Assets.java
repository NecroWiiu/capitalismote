package com.capitalcraft.market;

public final class Assets {
    private Assets() {}

    public static final String[] SECTORS = {"energy", "mining", "tech", "logistics", "finance", "defense", "commodities", "agro", "health", "construction"};

    public record Stock(String symbol, String name, int sector, double price, double vol, double drift, double yield, int color) {}

    public record Crypto(String symbol, String name, double price, double vol, double drift, int color,
                         boolean minable, double payout, boolean stable, boolean meme) {}

    public static final Stock[] STOCKS = {
            new Stock("CRPR", "Creeper Dynamics", 5, 84.20, 0.060, 0.002, 0.004, 0xFF5BC44B),
            new Stock("NOTC", "Notch & Sons Mining", 1, 120.50, 0.045, 0.002, 0.005, 0xFFB08850),
            new Stock("RSTN", "Redstone Systems", 2, 210.00, 0.070, 0.004, 0.002, 0xFFE0443A),
            new Stock("NTHR", "Nether Fuel & Energy", 0, 56.80, 0.055, 0.002, 0.004, 0xFFD9622B),
            new Stock("VBNK", "Villager Banking Group", 4, 142.30, 0.030, 0.002, 0.008, 0xFF9C6B4E),
            new Stock("ENDR", "Ender Teleport Inc.", 3, 305.00, 0.080, 0.004, 0.001, 0xFFB45CE6),
            new Stock("DMPK", "Diamond Pick Co.", 1, 98.70, 0.050, 0.002, 0.005, 0xFF4DD9E0),
            new Stock("GLMD", "Golem Defense Systems", 5, 175.40, 0.035, 0.002, 0.006, 0xFFC9C9C9),
            new Stock("PGLD", "Piglin Gold Trading", 6, 64.25, 0.060, 0.002, 0.004, 0xFFF2C94C),
            new Stock("STFM", "Steve's Farms Agro", 7, 38.90, 0.030, 0.002, 0.008, 0xFF7BD04F),
            new Stock("WTHR", "Wither Pharma", 8, 220.00, 0.065, 0.003, 0.003, 0xFF8A8FA8),
            new Stock("BDRK", "Bedrock Construction", 9, 77.60, 0.025, 0.002, 0.008, 0xFF7A7F8C)
    };

    public static final Crypto[] CRYPTOS = {
            new Crypto("BTB", "Bitblock", 42180.00, 0.090, 0.003, 0xFFF7931A, true, 0.80, false, false),
            new Crypto("ETR", "Ethercraft", 2650.00, 0.110, 0.003, 0xFF8C9EFF, true, 0.95, false, false),
            new Crypto("RDC", "RedCoin", 18.40, 0.140, 0.002, 0xFFE0443A, true, 1.05, false, false),
            new Crypto("DMC", "DiamondCoin", 310.00, 0.130, 0.003, 0xFF4DD9E0, true, 1.00, false, false),
            new Crypto("EMC", "EmeraldCash", 54.60, 0.150, 0.002, 0xFF3DDC84, true, 1.00, false, false),
            new Crypto("ENC", "EnderCoin", 9.75, 0.170, 0.002, 0xFFB45CE6, true, 1.10, false, false),
            new Crypto("NTC", "NetherToken", 3.20, 0.190, 0.002, 0xFFD9622B, true, 1.15, false, false),
            new Crypto("AMT", "AmethystChain", 128.00, 0.120, 0.002, 0xFFC084FC, true, 0.90, false, false),
            new Crypto("LPZ", "LapisLink", 6.40, 0.160, 0.002, 0xFF3B6FE0, true, 1.00, false, false),
            new Crypto("CRC", "CreeperCoin", 0.0420, 0.320, 0.000, 0xFF5BC44B, true, 1.40, false, true),
            new Crypto("WLF", "WolfCoin", 0.0085, 0.380, 0.000, 0xFFD0D0D0, true, 1.50, false, true),
            new Crypto("USDM", "StableMoney", 1.00, 0.001, 0.000, 0xFF9AA5B8, false, 0.0, true, false)
    };

    public static int cryptoIndex(String symbol) {
        for (int i = 0; i < CRYPTOS.length; i++) if (CRYPTOS[i].symbol().equals(symbol)) return i;
        return 0;
    }

    public static int firstMinable() {
        for (int i = 0; i < CRYPTOS.length; i++) if (CRYPTOS[i].minable()) return i;
        return 0;
    }
}
