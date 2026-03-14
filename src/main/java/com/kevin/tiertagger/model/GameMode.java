package com.kevin.tiertagger.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kevin.tiertagger.TierTagger;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public record GameMode(String id, String title) {
    public static final GameMode NONE = new GameMode("annoying_long_id_that_no_one_will_ever_use_just_to_make_sure", "§cNone§r");

    public static CompletableFuture<List<GameMode>> fetchGamemodes(HttpClient client) {
        String endpoint = TierTagger.getManager().getConfig().getApiUrl() + "/v2/mode/list";
        final HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Accept", "application/json")
                .header("User-Agent", "TierTagger/SealTiers")
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parseModes(response.statusCode(), response.body()));
    }

    private static List<GameMode> parseModes(int statusCode, String body) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("Unexpected mode list status code " + statusCode);
        }

        JsonElement parsed = TierTagger.GSON.fromJson(body, JsonElement.class);
        List<GameMode> modes = new ArrayList<>();

        if (parsed == null || parsed.isJsonNull()) {
            throw new IllegalStateException("Mode list payload was empty");
        }

        if (parsed.isJsonObject()) {
            JsonObject obj = parsed.getAsJsonObject();
            obj.entrySet().forEach(entry -> {
                String modeId = entry.getKey();
                JsonObject modeObj = entry.getValue().getAsJsonObject();
                String modeTitle = modeObj.has("title") ? modeObj.get("title").getAsString() : modeId;
                modes.add(new GameMode(modeId, modeTitle));
            });
        } else if (parsed.isJsonArray()) {
            JsonArray arr = parsed.getAsJsonArray();
            for (JsonElement element : arr) {
                if (!element.isJsonObject()) continue;
                JsonObject modeObj = element.getAsJsonObject();

                String modeId = modeObj.has("id") ? modeObj.get("id").getAsString() : null;
                if (modeId == null && modeObj.has("name")) modeId = modeObj.get("name").getAsString();
                if (modeId == null) continue;

                String modeTitle = modeObj.has("title") ? modeObj.get("title").getAsString() : modeId;
                modes.add(new GameMode(modeId, modeTitle));
            }
        }

        if (modes.isEmpty()) {
            throw new IllegalStateException("Mode list payload could not be parsed");
        }

        return modes;
    }

    public boolean isNone() {
        return this.id.equals(NONE.id);
    }

    private Pair<Character, TextColor> iconAndColor() {
        int color = switch (this.id.toLowerCase()) {
            case "melee" -> 0xff6a6e;
            case "endstone" -> 0xf6cf64;
            case "crystal_sumo", "crystal-sumo", "crystalsumo" -> 0x8cc7ff;
            case "sword", "uhc", "nodebuff", "debuff" -> 0xff6a6e;
            case "axe", "mace" -> 0x6aff6e;
            case "pot", "nethop", "neth_pot" -> 0xff9900;
            case "crystal", "dia_crystal", "smp", "vanilla" -> 0x8cc7ff;
            default -> 0xffffff;
        };

        return Pair.of('•', TextColor.fromRgb(color));
    }

    public Optional<Character> icon() {
        Pair<Character, TextColor> pair = this.iconAndColor();

        return pair.right().getValue() == 0xFFFFFF ? Optional.empty() : Optional.of(pair.left());
    }

    public Component asStyled(boolean withDefaultDot) {
        Pair<Character, TextColor> pair = this.iconAndColor();

        if (pair.right().getValue() == 0xFFFFFF && !withDefaultDot) {
            return Component.literal(this.title);
        } else {
            Component name = Component.literal(this.title).withStyle(s -> s.withColor(pair.right()));
            return Component.literal(pair.left() + " ").append(name);
        }
    }
}
