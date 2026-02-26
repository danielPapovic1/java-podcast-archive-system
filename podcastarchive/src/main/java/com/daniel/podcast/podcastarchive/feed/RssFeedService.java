package com.daniel.podcast.podcastarchive.feed;

import java.nio.charset.StandardCharsets; // Charset for deterministic filename-to-bytes encoding.
import java.security.MessageDigest; // SHA-256 digest engine for stable GUID hashing.
import java.security.NoSuchAlgorithmException; // Checked exception when requested hash algorithm is unavailable.
import java.util.ArrayList; // Mutable list when appending RSS/iTunes modules safely.
import java.util.Comparator; // Deterministic episode ordering for feed output.
import java.util.Date; // RSS Channel/Item date type expected by Rome.
import java.util.HexFormat; // Converts SHA-256 bytes into hex GUID text.
import java.util.List; // Collection type for episodes/items/modules.
import java.util.Locale; // Locale-safe lowercase for GUID normalization.

import org.jdom2.Element; // XML element type used for extension tags (dc:date).
import org.jdom2.Namespace; // XML namespace declaration for extension modules.
import org.springframework.stereotype.Service; // Marks this class as a Spring-managed service bean.
import org.springframework.web.util.UriUtils; // Encodes path segments for safe URL construction.

import com.daniel.podcast.podcastarchive.config.PodcastProperties; // Typed app config source for feed values.
import com.daniel.podcast.podcastarchive.media.EpisodeDateParts; // Parsed date precision model for pubDate/dc:date rules.
import com.daniel.podcast.podcastarchive.media.PodcastEpisode; // Internal episode metadata model used to build RSS items.
import com.rometools.modules.itunes.EntryInformationImpl; // iTunes item-level module implementation.
import com.rometools.modules.itunes.FeedInformationImpl; // iTunes channel-level module implementation.
import com.rometools.modules.itunes.types.Duration; // iTunes duration wrapper type.
import com.rometools.rome.feed.module.Module; // Base Rome module interface for attaching extensions.
import com.rometools.rome.feed.rss.Channel; // RSS channel root object.
import com.rometools.rome.feed.rss.Description; // RSS description object with type/value.
import com.rometools.rome.feed.rss.Enclosure; // RSS enclosure object for media URL/type/length.
import com.rometools.rome.feed.rss.Guid; // RSS GUID object for stable episode identity.
import com.rometools.rome.feed.rss.Item; // RSS item object for each episode entry.
import com.rometools.rome.io.FeedException; // Exception thrown by Rome during XML serialization.
import com.rometools.rome.io.WireFeedOutput; // Serializes Rome objects into RSS XML text.

@Service

// Spring auto-detects it, creates one instance, and lets other classes inject/use it.
// Used in Mp3MetadataService.java
public class RssFeedService {

    /*
     * Converts parsed episode metadata into RSS XML.
     * This class is the final serialization layer for RSS output:
     * - builds channel metadata
     * - maps each episode into an RSS item
     * - adds compatibility modules (iTunes + Dublin Core date)
     */
    private static final Namespace DUBLIN_CORE_NS = Namespace.getNamespace("dc", "http://purl.org/dc/elements/1.1/");
    private static final String GUID_PREFIX = "urn:podcastarchive:"; 
    private final PodcastProperties podcastProperties;
    private final EpisodeImageResolver episodeImageResolver;

    public RssFeedService(PodcastProperties podcastProperties, EpisodeImageResolver episodeImageResolver) {
        this.podcastProperties = podcastProperties;
        this.episodeImageResolver = episodeImageResolver;
    }

    /*
     * Main entrypoint called by FeedController for RSS responses. 
     * We first assemble a Channel object, then let Rome serialize it to XML.
     */
    public String buildFeedXml(List<PodcastEpisode> episodes) {
        Channel channel = new Channel("rss_2.0");
        channel.setTitle(podcastProperties.effectiveChannelTitle());
        channel.setLink(podcastProperties.normalizedChannelLink());
        channel.setDescription(podcastProperties.effectiveChannelDescription());
        channel.setLastBuildDate(new Date());
        addITunesChannelModule(channel);
        channel.setItems(buildItems(episodes));

        try {
            String xml = new WireFeedOutput().outputString(channel);
            return stripEmptyItunesKeywords(xml);
        } catch (FeedException ex) {
            throw new IllegalStateException("Failed to build RSS XML", ex);
        }
    }

    /*
     * Sort episodes for deterministic feed order so clients do not see random reordering.
     * Primary sort: newer year first.
     * Secondary sort: filename to break ties consistently.
     */
    private List<Item> buildItems(List<PodcastEpisode> episodes) {
        String baseUrl = podcastProperties.normalizedBaseUrl();

        return episodes.stream()
                // Newer-year episodes first, with filename tie-break for deterministic output.
                .sorted(Comparator
                        .comparing(PodcastEpisode::year, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PodcastEpisode::filename, String.CASE_INSENSITIVE_ORDER))
                .map(episode -> toItem(episode, baseUrl))
                .toList();
    }

    /*
     * Maps one internal PodcastEpisode model into one RSS Item.
     * This is where standard RSS fields + iTunes module fields are combined.
     */
    private Item toItem(PodcastEpisode episode, String baseUrl) {
        String fileUrl = buildFileUrl(baseUrl, episode.filename());
        String itemImageUrl = episodeImageResolver.resolveImageFileNameForEpisode(episode.filename())
                // Match by base filename and keep extension flexible (.jpg/.webp/.png/etc.).
                .map(imageFilename -> buildImageUrl(baseUrl, imageFilename))
                .orElse(null);

        Item item = new Item();
        item.setTitle(episode.title());
        item.setDescription(toDescription(episode.description()));
        // Standard RSS author field for broad client compatibility.
        item.setAuthor(nullToEmpty(episode.artist()));

        // Stable GUID should not depend on host/base URL.
        Guid guid = new Guid();
        guid.setPermaLink(false); // false: GUID is just a unique identifier string (not necessarily a URL).
        guid.setValue(buildStableGuid(episode.filename()));
        item.setGuid(guid);

        Enclosure enclosure = new Enclosure();
        enclosure.setUrl(fileUrl);
        enclosure.setType("audio/mpeg");
        enclosure.setLength(Math.max(episode.fileSizeBytes(), 0L));
        item.setEnclosures(List.of(enclosure));

        // Date mapping: strict pubDate only for full datetime; partial precision is preserved in dc:date.
        EpisodeDateParts parts = episode.publishedAt();
        if (parts != null && parts.hasFullDateTime()) {
            parts.toPubDateInstantUtcDefault().ifPresent(instant -> item.setPubDate(Date.from(instant)));
        }
        if (parts != null && parts.hasAny()) {
            addDcDate(item, parts.toIsoPartial());
        }

        addITunesItemModule(item, episode, itemImageUrl);
        return item;
    }

    // Builds the standard RSS <description> object with plain-text semantics.
    private Description toDescription(String value) {
        Description description = new Description();
        description.setType("text/plain");
        description.setValue(value == null ? "" : value);
        return description;
    }

    // Builds absolute file URL used by RSS enclosure for playback/download.
    private String buildFileUrl(String baseUrl, String filename) {
        String encodedFilename = UriUtils.encodePathSegment(filename, StandardCharsets.UTF_8);
        return baseUrl + "/file/" + encodedFilename;
    }

    private String buildImageUrl(String baseUrl, String imageFilename) {
        String encodedImageFilename = UriUtils.encodePathSegment(imageFilename, StandardCharsets.UTF_8);
        return baseUrl + podcastProperties.normalizedImageBasePath() + "/" + encodedImageFilename;
    }

    /*
     * Adds channel-level iTunes fields:
     * - author
     * - explicit
     * - summary
     * - optional image (when configured)
     */
    private void addITunesChannelModule(Channel channel) {
        FeedInformationImpl iTunes = new FeedInformationImpl();
        iTunes.setAuthor(podcastProperties.effectiveChannelAuthor());
        iTunes.setExplicit(podcastProperties.isExplicit());
        iTunes.setSummary(podcastProperties.effectiveChannelDescription());
        // Channel owner fields are explicitly set for better iTunes/podcast validator compatibility.
        iTunes.setOwnerName(podcastProperties.effectiveChannelOwnerName());
        iTunes.setOwnerEmailAddress(podcastProperties.effectiveChannelOwnerEmail());

        String imageUrl = podcastProperties.normalizedChannelImageUrlOrNull();
        if (imageUrl != null) {
            iTunes.setImageUri(imageUrl);
        }

        addModule(channel, iTunes);
    }

    /*
     * Adds item-level iTunes fields.
     * Current mapping decisions:
     * - artist -> itunes:author
     * - album  -> itunes:subtitle
     * - description -> itunes:summary
     * - duration seconds -> itunes:duration
     */
    private void addITunesItemModule(Item item, PodcastEpisode episode, String itemImageUrl) {
        EntryInformationImpl iTunes = new EntryInformationImpl();
        iTunes.setAuthor(nullToEmpty(episode.artist()));
        iTunes.setTitle(nullToEmpty(episode.title()));
        iTunes.setSubtitle(nullToEmpty(episode.album()));
        iTunes.setSummary(nullToEmpty(episode.description()));
        iTunes.setExplicit(podcastProperties.isExplicit());
        iTunes.setDuration(new Duration(Math.max((long) episode.durationSeconds(), 0L) * 1000L));
        // Item image is added only when a same-base-name file exists in static/images.
        if (itemImageUrl != null && !itemImageUrl.isBlank()) {
            iTunes.setImageUri(itemImageUrl);
        }
        addModule(item, iTunes);
    }

    // Utility for safely appending a module without losing existing channel modules.
    private void addModule(Channel channel, Module module) {
        List<Module> modules = channel.getModules();
        List<Module> updated = modules == null ? new ArrayList<>() : new ArrayList<>(modules);
        updated.add(module);
        channel.setModules(updated);
    }

    // Utility for safely appending a module without losing existing item modules.
    private void addModule(Item item, Module module) {
        List<Module> modules = item.getModules();
        List<Module> updated = modules == null ? new ArrayList<>() : new ArrayList<>(modules);
        updated.add(module);
        item.setModules(updated);
    }

    /*
     * Creates stable, URL-independent GUID value from filename.
     * Why hash:
     * - keeps identifier short and opaque
     * - avoids issues with spaces/special chars
     * - remains deterministic for the same filename
     */
    private String buildStableGuid(String filename) {
        String normalized = filename == null ? "" : filename.trim().toLowerCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); 
            String hash = HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
            return GUID_PREFIX + hash;
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);

            // Prevents duplicate/reposted episodes in clients.
            // GUID is now filename-hash based (not URL-based), changing baseUrl won’t make clients think old episodes are new.
            // Same filename input -> same SHA-256 hash output every time.
        }
    }

    // Normalizes null text to empty string for XML fields that should stay present.
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    // rome-modules emits empty itunes:keywords by default; remove it because this project does not use keywords.
    private String stripEmptyItunesKeywords(String xml) {
        return xml
                .replace("<itunes:keywords />", "")
                .replace("<itunes:keywords/>", "")
                .replace("<itunes:keywords></itunes:keywords>", "");
    } // If <itunes:keywords> contains real text (for example, "news,tech"), keep it in the RSS output.
      // Only empty keyword tags are removed.


    /*
     * Adds Dublin Core date extension tag (<dc:date>) for partial date precision.
     * This complements RSS pubDate rules:
     * - pubDate requires full date-timew
     * - dc:date can carry year or year-month safely
     */
    private void addDcDate(Item item, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        List<Element> foreignMarkup = item.getForeignMarkup();
        if (foreignMarkup == null) {
            foreignMarkup = new ArrayList<>();
            item.setForeignMarkup(foreignMarkup);
        }

        Element dcDate = new Element("date", DUBLIN_CORE_NS);
        dcDate.setText(value);
        foreignMarkup.add(dcDate);
        // dc:date is for flexible metadata date values.
    }

    // Called by FeedController when /feed is requested in RSS mode (Accept: application/rss+xml or ?format=rss).
    // This service formats RSS, while episode metadata values are produced by Mp3MetadataService and config by PodcastProperties.
    // pubDate is emitted only when full date+time exists; partial precision is carried in dc:date.
}
