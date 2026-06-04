package com.allanvital.devzao.async;

import java.time.Duration;
import java.time.Instant;

/**
* @author Allan Vital (https://allanvital.com)
  */
public class ArtemisScheduling {

    public static final String SCHEDULED_DELIVERY = "_AMQ_SCHED_DELIVERY";

    private ArtemisScheduling() {
    }

    public static long toDeliveryTimeMillis(Instant when, Duration fallbackDelay) {
        if (when == null) {
            return Instant.now().plus(fallbackDelay).toEpochMilli();
        }

        Instant now = Instant.now();
        if (when.isBefore(now)) {
            return now.plus(fallbackDelay).toEpochMilli();
        }
        return when.toEpochMilli();
    }
}
