package studio.pvf.preview;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** ACT1 constructor attachments absent from MOB files. Provenance is recorded beside each recipe. */
final class NativeEquipmentRecipes {
    private static final Map<Integer, List<Equipment>> RECIPES = read();
    private NativeEquipmentRecipes() {}
    static List<Equipment> forMonster(int id) { return RECIPES.getOrDefault(id, List.of()); }
    record Equipment(String path, List<Integer> variation) {}

    private static Map<Integer, List<Equipment>> read() {
        Map<Integer, List<Equipment>> recipes = new HashMap<>();
        try (var input = NativeEquipmentRecipes.class.getResourceAsStream("act1-equipment.tsv")) {
            if (input == null) throw new IOException("Missing ACT1 equipment recipes");
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null;) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] columns = line.split("\t"); List<Equipment> parts = new ArrayList<>();
                    for (String raw : columns[2].split(";")) {
                        String[] part = raw.split("@"); List<Integer> variation = part.length == 1 ? List.of()
                                : Arrays.stream(part[1].split(",")).map(Integer::valueOf).toList();
                        parts.add(new Equipment(part[0], variation));
                    }
                    for (String id : columns[0].split(",")) {
                        if (recipes.put(Integer.valueOf(id), List.copyOf(parts)) != null) throw new IOException("Duplicate ACT1 recipe " + id);
                    }
                }
            }
        } catch (IOException | RuntimeException failure) { throw new ExceptionInInitializerError(failure); }
        return Map.copyOf(recipes);
    }
}
