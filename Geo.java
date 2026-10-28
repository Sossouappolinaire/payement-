package com.sebtransfer.app;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Pays du monde (générés depuis Android), indicatifs, devises et réseaux crypto. */
final class Geo {
    private Geo() {}

    static final class Country {
        final String code;      // ISO 3166-1 alpha-2
        final String name;      // nom en français
        final String dial;      // indicatif (null si inconnu)
        final String currency;  // code devise (null si inconnu)

        Country(String code, String name, String dial, String currency) {
            this.code = code;
            this.name = name;
            this.dial = dial;
            this.currency = currency;
        }
    }

    static final class Crypto {
        final String id;
        final String label;
        final String kind; // evm | tron | other

        Crypto(String id, String label, String kind) {
            this.id = id;
            this.label = label;
            this.kind = kind;
        }

        /** Retourne un message d'erreur, ou null si l'adresse semble valide. */
        String validate(String addr) {
            String a = addr == null ? "" : addr.trim();
            if (a.isEmpty()) return "Saisissez l'adresse du portefeuille.";
            switch (kind) {
                case "evm":
                    if (!EVM.matcher(a).matches()) return "Adresse invalide : format attendu 0x suivi de 40 caractères.";
                    break;
                case "tron":
                    if (!TRON.matcher(a).matches()) return "Adresse Tron invalide : elle commence par T (34 caractères).";
                    break;
                default:
                    if (a.length() < 20 || a.length() > 120 || a.contains(" ")) return "Adresse de portefeuille invalide.";
            }
            return null;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final Pattern EVM = Pattern.compile("^0x[0-9a-fA-F]{40}$");
    private static final Pattern TRON = Pattern.compile("^T[1-9A-HJ-NP-Za-km-z]{33}$");

    /** Pour ajouter un réseau crypto : une ligne ici. */
    static final List<Crypto> CRYPTOS = Arrays.asList(
            new Crypto("BTC", "Bitcoin (BTC)", "other"),
            new Crypto("ETH", "Ethereum (ERC-20)", "evm"),
            new Crypto("BSC", "BNB Smart Chain (BEP-20)", "evm"),
            new Crypto("TRX", "Tron (TRC-20)", "tron"),
            new Crypto("SOL", "Solana", "other"),
            new Crypto("MATIC", "Polygon", "evm"),
            new Crypto("AVAXC", "Avalanche C-Chain", "evm"),
            new Crypto("ARB", "Arbitrum One", "evm"),
            new Crypto("OP", "Optimism", "evm"),
            new Crypto("BASE", "Base", "evm"),
            new Crypto("TON", "TON", "other"),
            new Crypto("XRP", "XRP Ledger", "other"),
            new Crypto("LTC", "Litecoin", "other"),
            new Crypto("DOGE", "Dogecoin", "other"),
            new Crypto("BCH", "Bitcoin Cash", "other"),
            new Crypto("ADA", "Cardano", "other"),
            new Crypto("XLM", "Stellar", "other"),
            new Crypto("DOT", "Polkadot", "other"),
            new Crypto("ATOM", "Cosmos", "other"),
            new Crypto("NEAR", "NEAR Protocol", "other"));

    /** Indicatifs des pays couverts par les opérateurs SebPay. */
    private static final Map<String, String> DIAL = new HashMap<>();

    static {
        String[] d = {
                "BF", "226", "CD", "243", "CG", "242", "CI", "225", "CM", "237",
                "GA", "241", "GN", "224", "GW", "245", "ML", "223", "NE", "227",
                "SN", "221", "TG", "228", "BJ", "229", "GM", "220", "TD", "235",
                "NG", "234", "GH", "233", "KE", "254", "UG", "256", "TZ", "255"};
        for (int i = 0; i + 1 < d.length; i += 2) DIAL.put(d[i], d[i + 1]);
    }

    /** Tous les pays du monde, triés par nom français. */
    static List<Country> countries() {
        List<Country> list = new ArrayList<>();
        for (String cc : Locale.getISOCountries()) {
            try {
                Locale l = new Locale.Builder().setRegion(cc).build();
                String name = l.getDisplayCountry(Locale.FRENCH);
                if (name == null || name.isEmpty() || name.equals(cc)) continue;
                String cur = null;
                try {
                    Currency c = Currency.getInstance(l);
                    if (c != null) cur = c.getCurrencyCode();
                } catch (Exception ignored) {
                    // pas de devise pour ce territoire
                }
                list.add(new Country(cc, name, DIAL.get(cc), cur));
            } catch (Exception ignored) {
                // code pays non reconnu : ignoré
            }
        }
        final Collator col = Collator.getInstance(Locale.FRENCH);
        Collections.sort(list, (a, b) -> col.compare(a.name, b.name));
        return list;
    }
}
