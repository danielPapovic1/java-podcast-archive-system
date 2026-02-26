package com.daniel.podcast.podcastarchive.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jaudiotagger.audio.AudioFile; // In-memory representation of an audio file + header/tag sections.
import org.jaudiotagger.audio.AudioFileIO; // Entry point to read MP3 files from disk via jaudiotagger.
import org.jaudiotagger.audio.exceptions.CannotReadException; // Thrown when file cannot be parsed/read as supported audio.
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException; // Thrown when audio frame structure is invalid/corrupt.
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException; // Thrown when jaudiotagger cannot access file in writable mode.
import org.jaudiotagger.tag.FieldKey; // Enum of standard tag keys (TITLE, ARTIST, ALBUM, etc.).
import org.jaudiotagger.tag.Tag; // Container object for metadata tag values read from the MP3.
import org.jaudiotagger.tag.TagException; // Thrown when tag data is malformed or cannot be processed.
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
// Service is like @component but communicates intent: this class holds business/service logic and should be injectable.

public class Mp3MetadataService {

    /*
     * High-level purpose:
     * - Take MP3 file paths that were already validated/listed elsewhere.
     * - Read ID3 metadata safely (title/artist/album/etc).
     * - Apply defaults so API output stays predictable even with missing tags.
     * - Never crash feed generation because of one bad file.
     */
    private static final Logger log = LoggerFactory.getLogger(Mp3MetadataService.class);
    private static final String UNKNOWN_ARTIST = "Unknown";
    private static final String DEFAULT_ALBUM = "Podcast Archive";

    /*
     * Description fallback chain:
     * Different tools save "description-like" text in different fields.
     * We check the most common semantic fields first, then raw tag ids later.
     */
    private static final List<FieldKey> DESCRIPTION_FIELDKEY_CHAIN = List.of(
            FieldKey.COMMENT,
            FieldKey.LYRICS,
            FieldKey.COMPOSER
    );
    private static final List<String> DESCRIPTION_RAW_KEYS = List.of(
            "COMM", "COMMENT", "DESCRIPTION", "DESC", "REMARK", "REMARKS"
    );

    /*
     * Year fallback chain:
     * Some files store year directly, others store full recording/release dates.
     * We collect all likely candidates, then extract one valid 4-digit year.
     */
    private static final List<FieldKey> YEAR_FIELDKEY_CHAIN = List.of(
            FieldKey.YEAR,
            FieldKey.ALBUM_YEAR,
            FieldKey.ORIGINAL_YEAR,
            FieldKey.RECORDINGDATE,
            FieldKey.ORIGINALRELEASEDATE,
            FieldKey.RECORDINGSTARTDATE
    );
    private static final List<String> YEAR_RAW_KEYS = List.of(
            "TDRC", "TYER", "DATE", "YEAR", "ORIGINALYEAR"
    );

    // Parse entrypoint for all files listed by FileResolver; unreadable files are skipped.
    public List<PodcastEpisode> readEpisodes(List<Path> mp3Files) {
        /*
         * Pipeline shape:
         * 1) Try to parse each file with readEpisode(...)
         * 2) readEpisode returns Optional.empty() on parse failure
         * 3) flatMap(Optional::stream) removes failed files cleanly
         * Result: one broken MP3 does not break the entire feed.
         */
        return mp3Files.stream()
                .map(this::readEpisode)
                .flatMap(Optional::stream)
                .toList();
    }

    public Optional<PodcastEpisode> readEpisode(Path mp3File) {
        try {
            // jaudiotagger reads audio header + tag frames from disk into memory objects.
            AudioFile audioFile = AudioFileIO.read(mp3File.toFile());
            Tag tag = audioFile.getTag();

            /*
             * We always compute a safe title fallback from filename, so files without TITLE
             * still show a meaningful name in feed output.
             */
            String filename = mp3File.getFileName().toString();
            String fallbackTitle = filenameWithoutExtension(filename);
            String title = readTagValue(tag, FieldKey.TITLE, fallbackTitle);
            String artist = readTagValue(tag, FieldKey.ARTIST, UNKNOWN_ARTIST);
            String album = readTagValue(tag, FieldKey.ALBUM, DEFAULT_ALBUM);
            String description = readDescription(tag);
            EpisodeDateParts publishedAt = readPublishedAt(tag);
            Integer year = publishedAt != null ? publishedAt.year() : readYear(tag);

            /*
             * Duration + file size are not text tags:
             * - duration comes from audio header
             * - size comes from filesystem
             */
            int durationSeconds = extractDurationSeconds(audioFile);
            String durationText = formatDuration(durationSeconds);
            long fileSizeBytes = Files.size(mp3File); // Streaming services look for all 3 in enclosure (url, length, type)

            // PodcastEpisode is the normalized internal model used before API/RSS mapping.
            return Optional.of(new PodcastEpisode(
                    filename,
                    title,
                    artist,
                    album,
                    description,
                    year,
                    publishedAt,
                    fileSizeBytes,
                    durationSeconds,
                    durationText));
        } catch (CannotReadException | IOException | TagException | ReadOnlyFileException | InvalidAudioFrameException ex) {
            // Corrupt/unreadable MP3 should not break /feed; we log and skip the file.
            log.warn("Skipping unreadable MP3 '{}': {}", mp3File.getFileName(), ex.getMessage());
            return Optional.empty();
        }
    }

    // Tag can be null and tag fields can be blank; fallback keeps feed output stable.
    private String readTagValue(Tag tag, FieldKey fieldKey, String fallback) {
        // If no tag block exists at all, we immediately use the provided fallback.
        if (tag == null) {
            return fallback;
        }
        String value = tag.getFirst(fieldKey);
        // Blank/whitespace values are treated as missing metadata.
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        // Trim to avoid leading/trailing spaces from dirty tags.
        return value.trim();
    }

    // Cross-tool description fallback: comment-style fields first, then other text-bearing fields.
    private String readDescription(Tag tag) {
        String value = readTagValueFromMany(tag, DESCRIPTION_FIELDKEY_CHAIN, DESCRIPTION_RAW_KEYS);
        // Keep non-null contract for description in output model.
        return value == null ? "" : value;
    }

    // Year fallback accepts different tag conventions and extracts the first valid 4-digit year.
    private Integer readYear(Tag tag) {
        Optional<EpisodeDateParts> parsed = readDatePartsFromMany(tag, YEAR_FIELDKEY_CHAIN, YEAR_RAW_KEYS);
        return parsed.map(EpisodeDateParts::year).orElse(null);
    }

    // Parses best available date precision from configured tag priority without merging across keys.
    private EpisodeDateParts readPublishedAt(Tag tag) {
        Optional<EpisodeDateParts> parsed = readDatePartsFromMany(tag, YEAR_FIELDKEY_CHAIN, YEAR_RAW_KEYS);
        return parsed.orElse(null);
    }

    private String readTagValueFromMany(Tag tag, List<FieldKey> fieldKeys, List<String> rawKeys) {
        /*
         * Two-stage lookup:
         * - First use jaudiotagger's typed FieldKey access (clean and preferred)
         * - Then try raw frame ids/keys for cross-tool compatibility
         */
        if (tag == null) {
            return null;
        }

        for (FieldKey fieldKey : fieldKeys) {
            String value = safeGetFirst(tag, fieldKey);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }

        for (String rawKey : rawKeys) {
            String value = safeGetFirst(tag, rawKey);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }

        return null;
    }

    private Optional<EpisodeDateParts> readDatePartsFromMany(Tag tag, List<FieldKey> fieldKeys, List<String> rawKeys) {
        if (tag == null) {
            return Optional.empty();
        }

        for (FieldKey fieldKey : fieldKeys) {
            String value = safeGetFirst(tag, fieldKey);
            Optional<EpisodeDateParts> parsed = EpisodeDateParts.parseFromText(value);
            if (parsed.isPresent()) {
                return parsed;
            }
        }

        for (String rawKey : rawKeys) {
            String value = safeGetFirst(tag, rawKey);
            Optional<EpisodeDateParts> parsed = EpisodeDateParts.parseFromText(value);
            if (parsed.isPresent()) {
                return parsed;
            }
        }

        return Optional.empty();
    }

    private String safeGetFirst(Tag tag, FieldKey fieldKey) {
        try {
            return tag.getFirst(fieldKey);
        } catch (RuntimeException ex) {
            // Some tag implementations can throw for unsupported/malformed fields.
            return null;
        }
    }

    private String safeGetFirst(Tag tag, String rawKey) {
        try {
            return tag.getFirst(rawKey);
        } catch (RuntimeException ex) {
            // Raw keys are best-effort; failures should not break parse flow.
            return null;
        }
    }

    private int extractDurationSeconds(AudioFile audioFile) {
        // Header can be missing on malformed files; default to 0 seconds.
        if (audioFile.getAudioHeader() == null) {
            return 0;
        }
        return Math.max(audioFile.getAudioHeader().getTrackLength(), 0);
    }

    // Duration string is normalized to HH:mm:ss for consistent API and upcoming RSS mapping.
    private String formatDuration(int totalSeconds) {
        if (totalSeconds <= 0) {
            return "00:00:00";
        }
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private String filenameWithoutExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex <= 0) {
            // If no extension exists, use full filename as fallback title.
            return filename;
        }
        return filename.substring(0, dotIndex);
    }
}
