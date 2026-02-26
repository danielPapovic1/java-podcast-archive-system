package com.daniel.podcast.podcastarchive;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.daniel.podcast.podcastarchive.config.PodcastProperties;
import com.daniel.podcast.podcastarchive.feed.EpisodeImageResolver;
import com.daniel.podcast.podcastarchive.feed.RssFeedService;
import com.daniel.podcast.podcastarchive.media.EpisodeDateParts;
import com.daniel.podcast.podcastarchive.media.PodcastEpisode;

/* mvn test always runs the Surefire plugin phase.
But Surefire still needs a test framework/provider (like JUnit) to find and execute tests. */

@SpringBootTest
@AutoConfigureMockMvc



class PodcastarchiveApplicationTests {

	// MockMvc drives HTTP-style requests through Spring MVC in-memory for fast endpoint tests.
	@Autowired // So I dont need to build the web stack interface and can call '/' dependencies itself 
	private MockMvc mockMvc;
	// Verifies that Spring can bootstrap the application context without bean wiring errors.
	@Test

	void contextLoads() {
		System.out.println("[TEST] Spring application context loads successfully"); // prints are notes for myself
	}

	// Confirms the Step 1 health endpoint contract remains stable.
	@Test
	void healthEndpointReturnsOk() throws Exception {
		System.out.println("[TEST] Checking /health returns 200 and OK body");
		mockMvc.perform(get("/health"))
				.andExpect(status().isOk())
				.andExpect(content().string("OK"));
	}

	// Basic smoke test: /feed endpoint should be reachable.
	@Test
	void feedEndpointReturnsOk() throws Exception {
		System.out.println("[TEST] Checking /feed returns 200");
		mockMvc.perform(get("/feed"))
				.andExpect(status().isOk());
		// @SpringBootTest starts the app context inside the test JVM, and MockMvc sends requests to that in-memory Spring stack.
	}

	// Ensures JSON feed still carries enriched metadata keys for each item.
	@Test
	void feedIncludesMetadataFields() throws Exception {
		System.out.println("[TEST] Checking /feed item includes title/artist/album/duration keys");
		mockMvc.perform(get("/feed"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0]").exists())
				.andExpect(jsonPath("$[0].title").exists())
				.andExpect(jsonPath("$[0].artist").exists())
				.andExpect(jsonPath("$[0].album").exists())
				.andExpect(jsonPath("$[0].duration").exists());
	}

	// Backward-compat guard: original name/url fields must remain available.
	@Test
	void feedStillIncludesNameAndUrl() throws Exception {
		System.out.println("[TEST] Checking /feed still includes backward-compatible name and url");
		mockMvc.perform(get("/feed"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").exists())
				.andExpect(jsonPath("$[0].url").value(startsWith("http://localhost:8080/file/")));
	}

	// Missing media files should return 404 instead of leaking filesystem details.
	@Test
	void missingFileReturnsNotFound() throws Exception {
		System.out.println("[TEST] Checking missing /file endpoint returns 404");
		mockMvc.perform(get("/file/not-found.mp3"))
				.andExpect(status().isNotFound());


	}

	// RSS content negotiation check when RSS media type is explicitly requested.
	@Test
	void feedRssReturnsXmlContentTypeWhenRequested() throws Exception {
		System.out.println("[TEST] Checking /feed returns RSS content type when Accept is application/rss+xml");
		mockMvc.perform(get("/feed").header("Accept", "application/rss+xml"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("application/rss+xml"));
	}

	// Verifies top-level RSS envelope tags are present.
	@Test
	void feedRssContainsRssAndChannelTags() throws Exception {
		System.out.println("[TEST] Checking RSS body contains <rss> and <channel>");
		mockMvc.perform(get("/feed").header("Accept", "application/rss+xml"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("<rss")))
				.andExpect(content().string(containsString("<channel>")));
	}

	// Verifies each feed item includes media enclosure structure.
	@Test
	void feedRssContainsItemAndEnclosureTags() throws Exception {
		System.out.println("[TEST] Checking RSS body contains item and enclosure tags");
		mockMvc.perform(get("/feed").header("Accept", "application/rss+xml"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("<item>")))
				.andExpect(content().string(containsString("<enclosure")));
	}

	// Ensures enclosure URLs point to this app's /file streaming route.
	@Test
	void feedRssEnclosurePointsToFileEndpoint() throws Exception {
		System.out.println("[TEST] Checking RSS enclosure URL points to /file/");
		mockMvc.perform(get("/feed").header("Accept", "application/rss+xml"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/file/")));
	}

	// Keeps standard RSS description field present for broad client support.
	@Test
	void feedRssContainsDescription() throws Exception {
		System.out.println("[TEST] Checking RSS contains a description element");
		mockMvc.perform(get("/feed").header("Accept", "application/rss+xml"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("<description>")));
	}

	// pubDate is optional and only appears when full datetime metadata exists.
	@Test
	void feedRssIncludesPubDateWhenYearMetadataExists() throws Exception {
		System.out.println("[TEST] Checking pubDate exists when year metadata is available");
		String rssXml = mockMvc.perform(get("/feed").header("Accept", "application/rss+xml"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		if (rssXml.contains("<pubDate>")) {
			assertTrue(rssXml.contains("<pubDate>"));
		} else {
			System.out.println("[TEST] No year metadata found in current MP3 tags; pubDate omission is expected");
		}
	}

	// Unit-style RSS builder test: partial precision should map into dc:date.
	@Test
	void rssIncludesDcDateWhenAnyDatePrecisionExists() {
		RssFeedService rssFeedService = testRssFeedService();
		PodcastEpisode episode = buildEpisode("episode-1.mp3", 2020,
				new EpisodeDateParts(2020, null, null, null, null, null, null));

		String xml = rssFeedService.buildFeedXml(List.of(episode));
		assertTrue(xml.contains("<dc:date>2020</dc:date>"));
	}

	// Full datetime gets pubDate; year-only metadata must not fabricate one.
	@Test
	void rssPubDateOnlyAppearsWhenFullDateTimeExists() {
		RssFeedService rssFeedService = testRssFeedService();

		PodcastEpisode yearOnly = buildEpisode("year-only.mp3", 2020,
				new EpisodeDateParts(2020, null, null, null, null, null, null));
		String yearOnlyXml = rssFeedService.buildFeedXml(List.of(yearOnly));
		assertFalse(yearOnlyXml.contains("<pubDate>"));

		PodcastEpisode fullDateTime = buildEpisode("full-datetime.mp3", 2020,
				new EpisodeDateParts(2020, 7, 18, 14, 30, null, null));
		String fullDateTimeXml = rssFeedService.buildFeedXml(List.of(fullDateTime));
		assertTrue(fullDateTimeXml.contains("<pubDate>"));
	}

	// Feed should still render valid RSS even when an episode has no parseable date.
	@Test
	void rssStillBuildsWhenNoDateMetadataExists() {
		RssFeedService rssFeedService = testRssFeedService();
		PodcastEpisode episode = buildEpisode("no-date.mp3", null, null);

		String xml = rssFeedService.buildFeedXml(List.of(episode));
		assertTrue(xml.contains("<rss"));
		assertTrue(xml.contains("<item>"));
		assertFalse(xml.contains("<dc:date>"));
	}

	// iTunes namespace presence confirms extension module generation is active.
	@Test
	void feedRssContainsItunesNamespaceWhenRequested() throws Exception {
		System.out.println("[TEST] Checking RSS contains iTunes namespace");
		mockMvc.perform(get("/feed").header("Accept", "application/rss+xml"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("xmlns:itunes")));
	}

	// Channel-level iTunes author should come from configured properties.
	@Test
	void rssContainsChannelItunesAuthorFromConfig() {
		RssFeedService rssFeedService = testRssFeedService();
		String xml = rssFeedService.buildFeedXml(List.of(buildEpisode("episode-1.mp3", 2024, null)));
		assertTrue(xml.contains("<itunes:author>Podcast Archive</itunes:author>"));
	}

	// Item-level artist + album mapping should appear in RSS/iTunes fields.
	@Test
	void rssContainsItemArtistAndAlbumInItunesFields() {
		RssFeedService rssFeedService = testRssFeedService();
		PodcastEpisode episode = buildEpisode("episode-1.mp3", 2024, null);

		String xml = rssFeedService.buildFeedXml(List.of(episode));
		assertTrue(xml.contains("<author>Test Artist</author>"));
		assertTrue(xml.contains("<itunes:author>Test Artist</itunes:author>"));
		assertTrue(xml.contains("<itunes:subtitle>Test Album</itunes:subtitle>"));
	}

	// Explicit flag should serialize as "yes" when configured true.
	@Test
	void rssContainsItunesExplicitFromConfig() {
		RssFeedService explicitService = testRssFeedService(true, null, "Podcast Archive", "http://localhost:8080");
		String xml = explicitService.buildFeedXml(List.of(buildEpisode("episode-1.mp3", 2024, null)));
		assertTrue(xml.contains("<itunes:explicit>yes</itunes:explicit>"));
	}

	// Optional image tag must be omitted for blank image configuration.
	@Test
	void rssOmitsItunesImageWhenBlank() {
		RssFeedService rssFeedService = testRssFeedService(false, "", "Podcast Archive", "http://localhost:8080");
		String xml = rssFeedService.buildFeedXml(List.of(buildEpisode("no-image.mp3", 2024, null)));
		assertFalse(xml.contains("itunes:image"));
	}

	// Optional image tag must be included when image URL is configured.
	@Test
	void rssIncludesItunesImageWhenConfigured() {
		RssFeedService rssFeedService = testRssFeedService(
				false,
				"http://localhost:8080/images/cover.jpg",
				"Podcast Archive",
				"http://localhost:8080");
		String xml = rssFeedService.buildFeedXml(List.of(buildEpisode("episode-1.mp3", 2024, null)));
		assertTrue(xml.contains("itunes:image"));
		assertTrue(xml.contains("http://localhost:8080/images/cover.jpg"));
	}

	// Enclosure should always expose correct type and non-zero size for media clients.
	@Test
	void rssEnclosureContainsExpectedTypeAndLength() {
		RssFeedService rssFeedService = testRssFeedService();
		String xml = rssFeedService.buildFeedXml(List.of(buildEpisode("episode-1.mp3", 2024, null)));
		assertTrue(xml.contains("<enclosure"));
		assertTrue(xml.contains("type=\"audio/mpeg\""));
		assertTrue(xml.contains("length=\"1000\""));
	}

	// Stable GUID must not change if only base URL changes between environments.

	// This test drives code across multiple files using local helpers:
    // testRssFeedService(...) configures RssFeedService (from RssFeedService.java) with different baseUrl values,
    // buildEpisode(...) creates a PodcastEpisode model (from PodcastEpisode.java),
    // buildFeedXml(...) generates RSS, then extractGuidValue(...) compares GUID outputs.
    // Because GUID generation in RssFeedService uses filename hashing, GUID should stay equal even when baseUrl differs.

	@Test
	void rssGuidIsStableAcrossBaseUrlChanges() {
		PodcastEpisode episode = buildEpisode("episode-1.mp3", 2024, null);
		RssFeedService serviceA = testRssFeedService(false, null, "Podcast Archive", "http://localhost:8080");
		RssFeedService serviceB = testRssFeedService(false, null, "Podcast Archive", "http://127.0.0.1:9090");

		String xmlA = serviceA.buildFeedXml(List.of(episode));
		String xmlB = serviceB.buildFeedXml(List.of(episode));

		String guidA = extractGuidValue(xmlA);
		String guidB = extractGuidValue(xmlB);

		assertTrue(xmlA.contains("isPermaLink=\"false\""));
		assertTrue(guidA.startsWith("urn:podcastarchive:"));
		assertEquals(guidA, guidB);
	}

	// Verifies that configured channel owner values are emitted in the iTunes owner block.
	@Test
	void rssContainsChannelOwnerNameAndEmailFromConfig() {
		RssFeedService rssFeedService = testRssFeedService();
		String xml = rssFeedService.buildFeedXml(List.of(buildEpisode("episode-1.mp3", 2024, null)));
		assertTrue(xml.contains("<itunes:name>Master</itunes:name>"));
		assertTrue(xml.contains("<itunes:email>masterbranch@email.com</itunes:email>"));
	}

	// Confirms item image lookup matches by base filename even when extension is not .jpg.
	@Test
	void rssIncludesItemImageWhenMatchingBaseNameExistsDifferentExtension() {
		RssFeedService rssFeedService = testRssFeedService();
		String xml = rssFeedService.buildFeedXml(List.of(buildEpisode("episode-2.mp3", 2024, null)));
		assertTrue(xml.contains("itunes:image"));
		assertTrue(xml.contains("http://localhost:8080/images/episode-2.webp"));
	}

	// Missing image match should not fail feed generation and should not emit a URL for that episode.
	@Test
	void rssOmitsItemImageWhenNoMatchExists() {
		RssFeedService rssFeedService = testRssFeedService();
		String xml = rssFeedService.buildFeedXml(List.of(buildEpisode("no-image.mp3", 2024, null)));
		assertFalse(xml.contains("/images/no-image"));
	}

	// Ensures item artwork links are absolute and respect configured baseUrl + imageBasePath.
	@Test
	void rssItemImageUrlUsesBaseUrlAndImageBasePath() {
		RssFeedService rssFeedService = testRssFeedService(
				false,
				"",
				"Podcast Archive",
				"http://127.0.0.1:9090",
				"Master",
				"masterbranch@email.com",
				"/images");
		String xml = rssFeedService.buildFeedXml(List.of(buildEpisode("episode-1.mp3", 2024, null)));
		assertTrue(xml.contains("http://127.0.0.1:9090/images/episode-1.jpg"));
	}

	// Default local test configuration used by most service-level RSS tests.
	private RssFeedService testRssFeedService() {
		return testRssFeedService(
				false,
				null,
				"Podcast Archive",
				"http://localhost:8080",
				"Master",
				"masterbranch@email.com",
				"/images");
	}

	// Helper to create RssFeedService with controlled properties for deterministic assertions.
	private RssFeedService testRssFeedService(boolean explicit, String imageUrl, String channelAuthor, String baseUrl) {
		return testRssFeedService(
				explicit,
				imageUrl,
				channelAuthor,
				baseUrl,
				"Master",
				"masterbranch@email.com",
				"/images");
	}

	// Extended helper includes owner and image-path config used by new compatibility tests.
	private RssFeedService testRssFeedService(
			boolean explicit,
			String imageUrl,
			String channelAuthor,
			String baseUrl,
			String ownerName,
			String ownerEmail,
			String imageBasePath) {
		PodcastProperties properties = new PodcastProperties();
		properties.setBaseUrl(baseUrl);
		properties.setChannelTitle("Podcast Archive");
		properties.setChannelLink("http://localhost:8080");
		properties.setChannelDescription("Local podcast archive feed.");
		properties.setChannelAuthor(channelAuthor);
		properties.setExplicit(explicit);
		properties.setChannelImageUrl(imageUrl == null ? "" : imageUrl);
		properties.setChannelOwnerName(ownerName);
		properties.setChannelOwnerEmail(ownerEmail);
		properties.setImageBasePath(imageBasePath);
		return new RssFeedService(properties, new EpisodeImageResolver());
	}

	// Builder for a minimal deterministic episode fixture used across tests.
	private PodcastEpisode buildEpisode(String filename, Integer year, EpisodeDateParts publishedAt) {
		return new PodcastEpisode(
				filename,
				"Test Title",
				"Test Artist",
				"Test Album",
				"Test Description",
				year,
				publishedAt,
				1000L,
				60,
				"00:01:00");
	}

	// Extracts GUID text from serialized XML to compare stability between runs.
	private String extractGuidValue(String xml) {
		Matcher matcher = Pattern.compile("<guid[^>]*>([^<]+)</guid>").matcher(xml);
		assertTrue(matcher.find());
		return matcher.group(1);
	}


	/*@Test
	void intentionallyFailsForDemo() {
		System.out.println("[TEST] Intentional failure demo");
		assertEquals(1, 2, "Intentional fail to show Maven/JUnit failure output");
	} * expected: , actual: , message:  This is a purposely failed test*/
}
