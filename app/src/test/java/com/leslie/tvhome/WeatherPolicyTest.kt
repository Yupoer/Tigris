package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class WeatherPolicyTest {
    @Test fun weatherMustBeExplicitlyEnabledWithACityAndVisibleClock() {
        assertFalse(WeatherPolicy.shouldShow(false, "北京", Prefs.CLOCK_CORNER))
        assertFalse(WeatherPolicy.shouldShow(true, "", Prefs.CLOCK_CORNER))
        assertFalse(WeatherPolicy.shouldShow(true, "北京", Prefs.CLOCK_OFF))
        assertTrue(WeatherPolicy.shouldShow(true, "北京", Prefs.CLOCK_CORNER))
        assertTrue(WeatherPolicy.shouldShow(true, "北京", Prefs.CLOCK_LARGE))
    }

    @Test fun refreshIsAtMostOncePer45Minutes() {
        assertTrue(WeatherPolicy.shouldRefresh(1000L, Long.MIN_VALUE))
        assertFalse(WeatherPolicy.shouldRefresh(44L * 60_000, 0))
        assertTrue(WeatherPolicy.shouldRefresh(45L * 60_000, 0))
    }

    @Test fun compactWeatherLabelIsStable() {
        assertEquals("23° · 晴", WeatherPolicy.shortText(23, 0))
        assertEquals("-2° · 雪", WeatherPolicy.shortText(-2, 75))
        assertEquals("17°", WeatherPolicy.shortText(17, 100))
    }
}
