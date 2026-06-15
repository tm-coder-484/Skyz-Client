package net.sebbyo.combat.effect;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** JSON persistence for the reward/penalty ledger: {@code { "<uuid>": { "<effectId>": amp } }}. */
public final class EffectStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private EffectStore() {}

    public static void save(Path file, Map<UUID, EffectLedger> ledgers) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Map<String, Map<String, Integer>> out = new HashMap<>();
        for (Map.Entry<UUID, EffectLedger> e : ledgers.entrySet()) {
            if (!e.getValue().isEmpty()) out.put(e.getKey().toString(), e.getValue().view());
        }
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(out, w);
        }
    }

    public static void load(Path file, Map<UUID, EffectLedger> into) throws IOException {
        into.clear();
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file)) {
            Map<String, Map<String, Integer>> raw =
                    GSON.fromJson(r, new TypeToken<Map<String, Map<String, Integer>>>() {}.getType());
            if (raw == null) return;
            for (Map.Entry<String, Map<String, Integer>> e : raw.entrySet()) {
                EffectLedger l = new EffectLedger();
                for (Map.Entry<String, Integer> fx : e.getValue().entrySet()) {
                    l.putRaw(fx.getKey(), fx.getValue());
                }
                into.put(UUID.fromString(e.getKey()), l);
            }
        }
    }
}
