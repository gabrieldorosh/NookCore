package net.nikosnook.core;

import java.util.*;

/** Limits mining chat frequency without delaying the first update or completion. */
final class QuestProgressNotices {
    private record Key(UUID player, String goal) {}
    private final Map<Key, Long> nextNotice = new HashMap<>();

    boolean immediate(UUID player, NookStore.QuestUpdate update, long now) {
        var key = new Key(player, update.goal().id());
        if (update.paid() || !update.goal().kind().equals("MINE")) {
            nextNotice.remove(key);
            return true;
        }
        var next = nextNotice.get(key);
        if (next != null && now < next) return false;
        nextNotice.put(key, now + 10_000);
        return true;
    }

    void forget(UUID player) { nextNotice.keySet().removeIf(key -> key.player().equals(player)); }
    void clear() { nextNotice.clear(); }
}
