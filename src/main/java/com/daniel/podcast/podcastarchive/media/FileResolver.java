package com.daniel.podcast.podcastarchive.media;

import java.io.IOException; // Checked exception type used for filesystem read/list failures.
import java.nio.file.Files; // NIO filesystem utility methods (exists/type checks, directory listing).
import java.nio.file.Path; // NIO path abstraction for safe path resolve/normalize operations.
import java.util.Comparator; // Comparator used to keep feed file listing order deterministic.
import java.util.List; // Collection type used for returned MP3 file lists.
import java.util.Locale; // Locale-safe lowercasing for extension checks.
import java.util.Optional; // Explicitly represents "resolved path present or not present".
import java.util.stream.Stream; // Stream API used when iterating directory entries.

import org.springframework.stereotype.Component; 
import org.springframework.util.StringUtils; // Utility used for safe blank/null string checks.

import com.daniel.podcast.podcastarchive.config.PodcastProperties;

@Component
// @Component tells Spring to auto-discover this class, create a managed bean instance, and allow dependency injection.

// A bean is an object created/managed by Spring; instead of calling new FileResolver(),
// Spring creates it, stores it in the container, and injects it where needed.


public class FileResolver {

    private final Path mediaRoot;
    // To load the podcasts path

    public FileResolver(PodcastProperties properties) {
        this.mediaRoot = properties.mediaRoot();
    }

    public Optional<Path> resolveMp3(String filename) {
        if (!StringUtils.hasText(filename)) {
            return Optional.empty();
        }

        String normalizedName = filename.trim();
        if (!normalizedName.toLowerCase(Locale.ROOT).endsWith(".mp3")) {
            return Optional.empty();
        }

        // Resolve + normalize first, then enforce that result stays inside mediaRoot.
        Path resolved = mediaRoot.resolve(normalizedName).normalize();
        if (!resolved.startsWith(mediaRoot)) {
            return Optional.empty();

            // Returns Optional.empty() for invalid/missing files; controller converts that to HTTP 404 via orElseThrow(...).

        }
        if (!Files.isRegularFile(resolved)) {
            return Optional.empty();
        }

        return Optional.of(resolved);
    }

    public List<Path> listMp3Files() {
        if (!Files.isDirectory(mediaRoot)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(mediaRoot)) {
            // Stable sort keeps /feed output deterministic between runs.
            // Step 3: this list is now passed into Mp3MetadataService for tag parsing before response mapping.
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }
}


// Validates request as an MP3 path, enforce it stays under mediaRoot, and require Files.isRegularFile(...) so only existing safe files are served.
