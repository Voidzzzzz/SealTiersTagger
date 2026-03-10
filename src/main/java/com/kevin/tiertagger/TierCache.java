package com.kevin.tiertagger;

import com.kevin.tiertagger.model.GameMode;
import com.kevin.tiertagger.model.PlayerInfo;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class TierCache {
    private static final List<GameMode> GAMEMODES = new ArrayList<>();
    private static final Map<UUID, Optional<Map<String, PlayerInfo.Ranking>>> TIERS = new ConcurrentHashMap<>();
    private static final List<GameMode> FALLBACK_MODES = List.of(
            new GameMode("melee", "Melee"),
            new GameMode("endstone", "Endstone"),
            new GameMode("crystal_sumo", "Crystal Sumo")
    );

    public static void init() {
        try {
            GAMEMODES.clear();
            GAMEMODES.addAll(GameMode.fetchGamemodes(TierTagger.getClient()).get());

            if (GAMEMODES.isEmpty()) {
                useFallbackModes("Mode list was empty");
            } else {
                TierTagger.getLogger().info("Found {} modes: {}", GAMEMODES.size(), GAMEMODES.stream().map(GameMode::id).toList());
            }
        } catch (ExecutionException e) {
            useFallbackModes("Failed to load gamemodes", e.getCause() == null ? e : e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            useFallbackModes("Loading gamemodes was interrupted", e);
        }
    }

    private static void useFallbackModes(String reason) {
        useFallbackModes(reason, null);
    }

    private static void useFallbackModes(String reason, Throwable throwable) {
        GAMEMODES.clear();
        GAMEMODES.addAll(FALLBACK_MODES);

        if (throwable == null) {
            TierTagger.getLogger().warn("{}; using fallback modes: {}", reason, FALLBACK_MODES.stream().map(GameMode::id).toList());
        } else {
            TierTagger.getLogger().warn("{}; using fallback modes: {}", reason, FALLBACK_MODES.stream().map(GameMode::id).toList(), throwable);
        }
    }

    public static List<GameMode> getGamemodes() {
        if (GAMEMODES.isEmpty()) {
            return Collections.singletonList(GameMode.NONE);
        } else {
            return GAMEMODES;
        }
    }

    public static Optional<Map<String, PlayerInfo.Ranking>> getPlayerRankings(UUID uuid) {
        return TIERS.computeIfAbsent(uuid, u -> {
            if (uuid.version() == 4) {
                PlayerInfo.getRankings(TierTagger.getClient(), uuid).thenAccept(info -> TIERS.put(uuid, Optional.ofNullable(info)));
            }

            return Optional.empty();
        });
    }

    public static CompletableFuture<PlayerInfo> searchPlayer(String query) {
        return PlayerInfo.search(TierTagger.getClient(), query).thenApply(p -> {
            UUID uuid = parseUUID(p.uuid());
            TIERS.put(uuid, Optional.of(p.rankings()));
            return p;
        });
    }

    public static void clearCache() {
        TIERS.clear();
    }

    public static GameMode findNextMode(GameMode current) {
        if (GAMEMODES.isEmpty()) {
            return GameMode.NONE;
        } else {
            return GAMEMODES.get((GAMEMODES.indexOf(current) + 1) % GAMEMODES.size());
        }
    }

    public static Optional<GameMode> findMode(String id) {
        return GAMEMODES.stream().filter(m -> m.id().equalsIgnoreCase(id)).findFirst();
    }

    public static GameMode findModeOrUgly(String id) {
        return findMode(id).orElseGet(() -> new GameMode(id, id));
    }

    private static UUID parseUUID(String uuid) {
        try {
            return UUID.fromString(uuid);
        } catch (Exception e) {
            long mostSignificant = Long.parseUnsignedLong(uuid.substring(0, 16), 16);
            long leastSignificant = Long.parseUnsignedLong(uuid.substring(16), 16);
            return new UUID(mostSignificant, leastSignificant);
        }
    }

    private TierCache() {
    }
}
