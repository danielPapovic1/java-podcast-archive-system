package com.daniel.podcast.podcastarchive.media;

// Metadata bridge model used by /feed now and RSS generation in the next step.
public record PodcastEpisode(
        String filename,
        String title,
        String artist,
        String album,
        String description,
        Integer year,
        EpisodeDateParts publishedAt,
        long fileSizeBytes,
        int durationSeconds, // Keep raw durationSeconds for future logic (sorting/math/analytics) while durationText is the display/feed format.

        String durationText
) {
}


// PodcastEpisode represents what the MP3 parser knows about a file,
// while FeedItem represents what the API returns to clients; keeping them separate lets us change
// response fields later without changing parsing logic.
