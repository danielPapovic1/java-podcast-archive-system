package com.daniel.podcast.podcastarchive.web;

import java.nio.charset.StandardCharsets; // UTF-8 constant for safe URL path encoding.
import java.util.List; // Collection type for episodes and feed items.
import java.util.Locale; // Locale-safe lowercase conversion for Accept header checks.

import org.springframework.http.MediaType; // Represents HTTP media types like application/rss+xml.
import org.springframework.http.ResponseEntity; // Wraps HTTP status/headers/body in controller responses.
import org.springframework.web.bind.annotation.GetMapping; // Maps GET requests to controller methods.
import org.springframework.web.bind.annotation.RequestHeader; // Binds an HTTP request header to a method argument.
import org.springframework.web.bind.annotation.RequestParam; // Binds a query parameter to a method argument.
import org.springframework.web.bind.annotation.RestController; // Marks this class as a REST endpoint controller.
import org.springframework.web.util.UriUtils; // Encodes filename segments so generated URLs are valid/safe.

import com.daniel.podcast.podcastarchive.config.PodcastProperties; // Typed podcast config values (base URL, channel fields).
import com.daniel.podcast.podcastarchive.feed.RssFeedService; // Builds final RSS XML output from parsed episode data.
import com.daniel.podcast.podcastarchive.media.FileResolver; // Lists MP3 files from the configured media directory safely.
import com.daniel.podcast.podcastarchive.media.Mp3MetadataService; // Reads ID3 metadata and maps files into PodcastEpisode objects.
import com.daniel.podcast.podcastarchive.media.PodcastEpisode; // Internal episode metadata model used for JSON/RSS mapping.

@RestController
// Feed endpoint can return either JSON metadata or RSS XML, while /file serves actual audio bytes.
public class FeedController {

    private static final MediaType RSS_MEDIA_TYPE = MediaType.parseMediaType("application/rss+xml;charset=UTF-8");

    private final FileResolver fileResolver;
    private final PodcastProperties podcastProperties;
    private final Mp3MetadataService mp3MetadataService;
    private final RssFeedService rssFeedService;

    // FileResolver provides safe MP3 discovery; this controller maps discovered episodes to JSON or RSS responses.
    public FeedController(
            FileResolver fileResolver,
            PodcastProperties podcastProperties,
            Mp3MetadataService mp3MetadataService,
            RssFeedService rssFeedService) {
        this.fileResolver = fileResolver;
        this.podcastProperties = podcastProperties;
        this.mp3MetadataService = mp3MetadataService;
        this.rssFeedService = rssFeedService;
    }

    @GetMapping("/feed")
    public ResponseEntity<?> feed(
            @RequestHeader(value = "Accept", required = false) String acceptHeader,
            @RequestParam(value = "format", required = false) String format) {
        List<PodcastEpisode> episodes = mp3MetadataService.readEpisodes(fileResolver.listMp3Files());

        // Query param lets clients explicitly force RSS mode with /feed?format=rss,
        // which is useful for browser/manual testing when Accept headers are generic.


        // Explicit RSS request returns XML for podcast clients.
        if (wantsRss(acceptHeader, format)) {
            String rssXml = rssFeedService.buildFeedXml(episodes);
            return ResponseEntity.ok()
                    .contentType(RSS_MEDIA_TYPE)
                    .body(rssXml);
        }


        // Content negotiation notes:
        // - /feed response type depends on the client request.
        // - Browsers often include application/xml in default Accept headers.
        // - API tools/clients may send application/json, */*, or other values.
        // - You can override explicitly, for example: Accept: application/rss+xml.
        //
        // FeedController.feed(...) does not return JSON and RSS together.
        // It chooses one branch per request:
        // - RSS branch -> build/send XML
        // - else branch -> build/send JSON
        // One request returns one format only.
        //
        // Else branch is hit when wantsRss(...) is false:
        // - no format=rss query param, and
        // - Accept does not include application/rss+xml or application/xml.
        //
        // Example that hits else (JSON):
        // - Accept: application/json
        // - or no XML-related Accept values



        // Client Accept headers decide format (browser often XML, APIs often JSON, explicit Accept can override), and each /feed request returns only one format.

        // If wantsRss(...) is false, /feed uses the default JSON response path.
        // Default response remains JSON metadata for local/debug compatibility.
        String baseUrl = podcastProperties.normalizedBaseUrl();
        List<FeedItem> feedItems = episodes.stream()
                .map(episode -> toFeedItem(episode, baseUrl))
                .toList();
        return ResponseEntity.ok(feedItems);
    }



    private FeedItem toFeedItem(PodcastEpisode episode, String baseUrl) {
        // Build a link to the file endpoint; actual file serving happens later when that URL is requested.
        String encodedFilename = UriUtils.encodePathSegment(episode.filename(), StandardCharsets.UTF_8);
        String fileUrl = baseUrl + "/file/" + encodedFilename;
        return new FeedItem(
                episode.filename(),
                fileUrl,
                episode.title(),
                episode.artist(),
                episode.album(),
                episode.durationText(),
                episode.description(),
                episode.year());
    }

    private boolean wantsRss(String acceptHeader, String format) { // wantsRss(...) returns true when either:
                                                                   // 1) query param format=rss, or
                                                                   // 2) Accept header contains application/rss+xml (or application/xml).
        if (format != null && "rss".equalsIgnoreCase(format.trim())) {
            return true;
        }
        if (acceptHeader == null) {
            return false;
        }
        String normalizedAccept = acceptHeader.toLowerCase(Locale.ROOT);
        return normalizedAccept.contains("application/rss+xml")
                || normalizedAccept.contains("application/xml");
    }

    public record FeedItem(
            String name,
            String url,
            String title,
            String artist,
            String album,
            String duration,
            String description,
            Integer year) {
    }
}

// FeedController orchestrates the flow:
// 1) FileResolver finds safe MP3 files.
// 2) Mp3MetadataService parses metadata from those files.
// 3) RssFeedService formats that parsed data into RSS XML.
// Keeping them separate keeps each class focused and easier to maintain.

// RssFeedService does not call Mp3MetadataService directly.
// FeedController first gets PodcastEpisode data from Mp3MetadataService,
// then passes that parsed metadata into RssFeedService to build RSS XML.
