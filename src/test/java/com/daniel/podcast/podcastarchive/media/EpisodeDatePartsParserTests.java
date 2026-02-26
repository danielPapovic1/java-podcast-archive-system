package com.daniel.podcast.podcastarchive.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

// Focused parser tests for EpisodeDateParts precision handling and fallback behavior.
class EpisodeDatePartsParserTests {

    // Year-only text should parse and stay year-only (no fabricated month/day/time).
    @Test
    void parsesYearOnly() {
        Optional<EpisodeDateParts> parsed = EpisodeDateParts.parseFromText("2020");
        assertTrue(parsed.isPresent());
        assertEquals("2020", parsed.get().toIsoPartial());
        assertFalse(parsed.get().hasFullDateTime());
    }

    // Year-month input should preserve two-part precision.
    @Test
    void parsesYearMonth() {
        Optional<EpisodeDateParts> parsed = EpisodeDateParts.parseFromText("2020-07");
        assertTrue(parsed.isPresent());
        assertEquals("2020-07", parsed.get().toIsoPartial());
    }

    // Full date without time should parse to YYYY-MM-DD precision.
    @Test
    void parsesDate() {
        Optional<EpisodeDateParts> parsed = EpisodeDateParts.parseFromText("2020-07-18");
        assertTrue(parsed.isPresent());
        assertEquals("2020-07-18", parsed.get().toIsoPartial());
    }

    // Date-time without seconds is valid and should count as full datetime for pubDate eligibility.
    @Test
    void parsesDateTimeWithoutSeconds() {
        Optional<EpisodeDateParts> parsed = EpisodeDateParts.parseFromText("2020-07-18T14:30");
        assertTrue(parsed.isPresent());
        assertTrue(parsed.get().hasFullDateTime());
        assertEquals("2020-07-18T14:30", parsed.get().toIsoPartial());
    }

    // Parser should extract supported date-time even when wrapped in extra text.
    @Test
    void parsesNoisyDateText() {
        Optional<EpisodeDateParts> parsed = EpisodeDateParts.parseFromText("Recorded on 2020-07-18 14:30");
        assertTrue(parsed.isPresent());
        assertEquals("2020-07-18T14:30", parsed.get().toIsoPartial());
    }

    // Invalid text should safely return Optional.empty() instead of throwing.
    @Test
    void returnsEmptyForInvalidText() {
        Optional<EpisodeDateParts> parsed = EpisodeDateParts.parseFromText("release someday");
        assertTrue(parsed.isEmpty());
    }
}
