package com.daniel.podcast.podcastarchive.media;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

// EpisodeDateParts keeps date precision exactly as found in metadata (year, year-month, date, or datetime).
// This lets RSS set <pubDate> only when full datetime exists, while using <dc:date> for partial dates.
// It prevents fake/default month/day/time values from being invented.


/*
 * Holds only the date/time pieces we actually found in tags.
 * Missing pieces stay null instead of being defaulted.
 */
public record EpisodeDateParts(
        Integer year,
        Integer month,
        Integer day,
        Integer hour,
        Integer minute,
        Integer second,
        ZoneOffset offset
) {

    private static final Pattern DATETIME_PATTERN = Pattern.compile(
            "(?<!\\d)(19\\d{2}|20\\d{2}|2100)[-/]?(0[1-9]|1[0-2])[-/]?(0[1-9]|[12]\\d|3[01])[T\\s]+([01]\\d|2[0-3]):([0-5]\\d)(?::([0-5]\\d))?(?:\\s*(Z|[+-](?:[01]\\d|2[0-3]):?[0-5]\\d))?(?!\\d)"
    );
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(?<!\\d)(19\\d{2}|20\\d{2}|2100)[-/]?(0[1-9]|1[0-2])[-/]?(0[1-9]|[12]\\d|3[01])(?!\\d)"
    );
    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile(
            "(?<!\\d)(19\\d{2}|20\\d{2}|2100)[-/]?(0[1-9]|1[0-2])(?!\\d)"
    );
    private static final Pattern YEAR_PATTERN = Pattern.compile(
            "(?<!\\d)(19\\d{2}|20\\d{2}|2100)(?!\\d)"
    );

    public boolean hasAny() {
        return year != null;
    }

    public boolean hasFullDateTime() {
        return year != null
                && month != null
                && day != null
                && hour != null
                && minute != null;
    }

    // Keeps exact precision found in metadata (year, year-month, date, or datetime).
    public String toIsoPartial() {
        if (year == null) {
            return "";
        }
        if (month == null) {
            return String.format("%04d", year);
        }
        if (day == null) {
            return String.format("%04d-%02d", year, month);
        }
        if (hour == null || minute == null) {
            return String.format("%04d-%02d-%02d", year, month, day);
        }

        String base = second == null
                ? String.format("%04d-%02d-%02dT%02d:%02d", year, month, day, hour, minute)
                : String.format("%04d-%02d-%02dT%02d:%02d:%02d", year, month, day, hour, minute, second);

        if (offset == null) {
            return base;
        }
        return base + offset.getId();
    }

    // Converts to an Instant only when full date+time exists; no synthetic day/month/time is created.
    public Optional<Instant> toPubDateInstantUtcDefault() {
        if (!hasFullDateTime()) {
            return Optional.empty();
        }
        int requiredYear = Objects.requireNonNull(year, "year is required for pubDate");
        int requiredMonth = Objects.requireNonNull(month, "month is required for pubDate");
        int requiredDay = Objects.requireNonNull(day, "day is required for pubDate");
        int requiredHour = Objects.requireNonNull(hour, "hour is required for pubDate");
        int requiredMinute = Objects.requireNonNull(minute, "minute is required for pubDate");
        int safeSecond = Objects.requireNonNullElse(second, 0);
        ZoneOffset safeOffset = Objects.requireNonNullElse(offset, ZoneOffset.UTC);
        try {
            OffsetDateTime dateTime = OffsetDateTime.of(
                    requiredYear, requiredMonth, requiredDay, requiredHour, requiredMinute, safeSecond, 0, safeOffset);
            return Optional.of(dateTime.toInstant());
        } catch (DateTimeException ex) {
            return Optional.empty();
        }
    }

    public static Optional<EpisodeDateParts> parseFromText(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return Optional.empty();
        }
        String value = rawValue.trim();

        Optional<EpisodeDateParts> dateTime = parseDateTime(value);
        if (dateTime.isPresent()) {
            return dateTime;
        }

        Optional<EpisodeDateParts> date = parseDate(value);
        if (date.isPresent()) {
            return date;
        }

        Optional<EpisodeDateParts> yearMonth = parseYearMonth(value);
        if (yearMonth.isPresent()) {
            return yearMonth;
        }

        return parseYear(value);
    }

    private static Optional<EpisodeDateParts> parseDateTime(String value) {
        Matcher matcher = DATETIME_PATTERN.matcher(value);
        if (!matcher.find()) {
            return Optional.empty();
        }

        Integer year = parseInt(matcher.group(1));
        Integer month = parseInt(matcher.group(2));
        Integer day = parseInt(matcher.group(3));
        Integer hour = parseInt(matcher.group(4));
        Integer minute = parseInt(matcher.group(5));
        Integer second = parseInt(matcher.group(6));
        ZoneOffset offset = parseOffset(matcher.group(7));

        if (!isValidDate(year, month, day)) {
            return Optional.empty();
        }
        return Optional.of(new EpisodeDateParts(year, month, day, hour, minute, second, offset));
    }

    private static Optional<EpisodeDateParts> parseDate(String value) {
        Matcher matcher = DATE_PATTERN.matcher(value);
        if (!matcher.find()) {
            return Optional.empty();
        }

        Integer year = parseInt(matcher.group(1));
        Integer month = parseInt(matcher.group(2));
        Integer day = parseInt(matcher.group(3));

        if (!isValidDate(year, month, day)) {
            return Optional.empty();
        }
        return Optional.of(new EpisodeDateParts(year, month, day, null, null, null, null));
    }

    private static Optional<EpisodeDateParts> parseYearMonth(String value) {
        Matcher matcher = YEAR_MONTH_PATTERN.matcher(value);
        if (!matcher.find()) {
            return Optional.empty();
        }

        Integer year = parseInt(matcher.group(1));
        Integer month = parseInt(matcher.group(2));
        return Optional.of(new EpisodeDateParts(year, month, null, null, null, null, null));
    }

    private static Optional<EpisodeDateParts> parseYear(String value) {
        Matcher matcher = YEAR_PATTERN.matcher(value);
        if (!matcher.find()) {
            return Optional.empty();
        }
        Integer year = parseInt(matcher.group(1));
        return Optional.of(new EpisodeDateParts(year, null, null, null, null, null, null));
    }

    private static Integer parseInt(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean isValidDate(Integer year, Integer month, Integer day) {
        if (year == null || month == null || day == null) {
            return false;
        }
        try {
            LocalDate.of(year, month, day);
            return true;
        } catch (DateTimeException ex) {
            return false;
        }
    }

    private static ZoneOffset parseOffset(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if ("Z".equalsIgnoreCase(normalized)) {
            return ZoneOffset.UTC;
        }
        if (normalized.matches("[+-]\\d{4}")) {
            normalized = normalized.substring(0, 3) + ":" + normalized.substring(3);
        }
        try {
            return ZoneOffset.of(normalized);
        } catch (DateTimeException ex) {
            return null;
        }
    }
}
