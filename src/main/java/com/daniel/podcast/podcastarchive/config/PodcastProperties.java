package com.daniel.podcast.podcastarchive.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties; 
// Binds external config values (e.g., from application.properties) to fields in this class

@ConfigurationProperties(prefix = "podcast")
public class PodcastProperties {

    private String mediaDir = "podcasts";
    private String baseUrl = "http://localhost:8080";
    private String channelTitle = "Podcast Archive";
    private String channelLink = "";
    private String channelDescription = "Local podcast archive feed.";
    private String channelAuthor = "Podcast Archive";
    private boolean explicit = false;
    private String channelImageUrl = "";
    private String channelOwnerName = "Master";
    private String channelOwnerEmail = "masterbranch@email.com";
    private String imageBasePath = "/images";

    public String getMediaDir() {
        return mediaDir;
    }

    public void setMediaDir(String mediaDir) {
        this.mediaDir = mediaDir;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getChannelTitle() {
        return channelTitle;
    }

    public void setChannelTitle(String channelTitle) {
        this.channelTitle = channelTitle;
    }

    public String getChannelLink() {
        return channelLink;
    }

    public void setChannelLink(String channelLink) {
        this.channelLink = channelLink;
    }

    public String getChannelDescription() {
        return channelDescription;
    }

    public void setChannelDescription(String channelDescription) {
        this.channelDescription = channelDescription;
    }

    public String getChannelAuthor() {
        return channelAuthor;
    }

    public void setChannelAuthor(String channelAuthor) {
        this.channelAuthor = channelAuthor;
    }

    public boolean isExplicit() {
        return explicit;
    }

    public void setExplicit(boolean explicit) {
        this.explicit = explicit;
    }

    public String getChannelImageUrl() {
        return channelImageUrl;
    }

    public void setChannelImageUrl(String channelImageUrl) {
        this.channelImageUrl = channelImageUrl;
    }

    public String getChannelOwnerName() {
        return channelOwnerName;
    }

    public void setChannelOwnerName(String channelOwnerName) {
        this.channelOwnerName = channelOwnerName;
    }

    public String getChannelOwnerEmail() {
        return channelOwnerEmail;
    }

    public void setChannelOwnerEmail(String channelOwnerEmail) {
        this.channelOwnerEmail = channelOwnerEmail;
    }

    public String getImageBasePath() {
        return imageBasePath;
    }

    public void setImageBasePath(String imageBasePath) {
        this.imageBasePath = imageBasePath;
    }

    public Path mediaRoot() {
        // Convert configured mediaDir into a stable absolute root for safe path checks.
        return Path.of(mediaDir).toAbsolutePath().normalize();
    }

    public String normalizedBaseUrl() {
        if (baseUrl == null) {
            return "";
        }
        // Keep URL joining predictable by removing trailing slashes.
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    // Channel link falls back to baseUrl so RSS remains valid even if channelLink is not configured.
    public String normalizedChannelLink() {
        if (channelLink == null || channelLink.trim().isEmpty()) {
            return normalizedBaseUrl();
        }
        return trimTrailingSlash(channelLink);
    }

    // Channel description is required by RSS and should always have a stable, non-empty value.
    public String effectiveChannelDescription() {
        if (channelDescription == null || channelDescription.trim().isEmpty()) {
            return "Local podcast archive feed.";
        }
        return channelDescription.trim();
    }

    public String effectiveChannelTitle() {
        if (channelTitle == null || channelTitle.trim().isEmpty()) {
            return "Podcast Archive";
        }
        return channelTitle.trim();
    }

    public String effectiveChannelAuthor() {
        if (channelAuthor == null || channelAuthor.trim().isEmpty()) {
            return "Podcast Archive";
        }
        return channelAuthor.trim();
    }

    // Owner tags should never be blank so podcast clients always receive stable channel ownership metadata.
    public String effectiveChannelOwnerName() {
        if (channelOwnerName == null || channelOwnerName.trim().isEmpty()) {
            return "Podcast Archive";
        }
        return channelOwnerName.trim();
    }

    // Owner email is required by some podcast validators, so we enforce a non-empty fallback.
    public String effectiveChannelOwnerEmail() {
        if (channelOwnerEmail == null || channelOwnerEmail.trim().isEmpty()) {
            return "masterbranch@email.com";
        }
        return channelOwnerEmail.trim();
    }

    public String normalizedChannelImageUrlOrNull() {
        if (channelImageUrl == null) {
            return null;
        }
        String trimmed = channelImageUrl.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // Normalize to a URL path segment so baseUrl + imageBasePath always forms a predictable absolute image URL.
    public String normalizedImageBasePath() {
        if (imageBasePath == null || imageBasePath.trim().isEmpty()) {
            return "/images";
        }
        String normalized = imageBasePath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
