package net.nikosnook.core;

import java.time.Duration;

/** Read-only view of Paper's activity clock; does not reset idle time or change kicks. */
final class AfkDisplay {
    static final String COLOUR="#AAAAAA";
    static boolean idle(boolean enabled,int seconds,Duration duration){
        return enabled && duration!=null && duration.compareTo(Duration.ofSeconds(Math.max(30,seconds)))>=0;
    }
}
