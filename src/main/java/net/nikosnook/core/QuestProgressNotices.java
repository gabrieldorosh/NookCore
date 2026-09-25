package net.nikosnook.core;

import java.util.*;

/** Coalesces mining chat only; persistence and rewards remain immediate. */
final class QuestProgressNotices {
    record Notice(UUID player, NookStore.QuestUpdate update) {}
    private record Key(UUID player, String goal) {}
    private record Pending(NookStore.QuestUpdate update, long due) {}
    private final Map<Key, Pending> pending = new LinkedHashMap<>();

    boolean immediate(UUID player, NookStore.QuestUpdate update, long now) {
        var key = new Key(player, update.goal().id());
        if (update.paid() || !update.goal().kind().equals("MINE")) {
            pending.remove(key);
            return true;
        }
        var previous = pending.get(key);
        pending.put(key, new Pending(update, previous == null ? now + 10_000 : previous.due()));
        return false;
    }

    List<Notice> drain(long now) {
        var result = new ArrayList<Notice>();
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().due() <= now) {
                result.add(new Notice(entry.getKey().player(), entry.getValue().update()));
                iterator.remove();
            }
        }
        return result;
    }

    void forget(UUID player) { pending.keySet().removeIf(key -> key.player().equals(player)); }
    void clear() { pending.clear(); }
}
