package com.pmsjl.utils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/** Application timestamps matching the precision of MySQL DATETIME columns. */
public final class PersistenceTime {
    private PersistenceTime() {
    }

    public static Date now() {
        return Date.from(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }
}
