package com.daniel.podcast.podcastarchive.feed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

@Service
// Resolves episode artwork by matching the episode base filename in static/images, ignoring image extension.
public class EpisodeImageResolver {

    private static final Path LOCAL_IMAGE_DIR = Path.of("src", "main", "resources", "static", "images");
    private static final String CLASSPATH_IMAGE_PATTERN = "classpath:/static/images/*";

    /*
     * Returns the matched image filename (including extension) for a given episode filename.
     * Example: episode-2.mp3 -> episode-2.webp (or any extension with the same base name).
     */
    public Optional<String> resolveImageFileNameForEpisode(String episodeFilename) {
        if (episodeFilename == null || episodeFilename.isBlank()) {
            return Optional.empty();
        }

        // Extract base name so matching is extension-agnostic for both mp3 and image files.
        String episodeBaseName = extractBaseName(episodeFilename);
        if (episodeBaseName.isBlank()) {
            return Optional.empty();
        }

        List<String> imageFileNames = loadImageFileNames();

        // Sort matches and pick first to keep duplicate-base-name selection deterministic.
        return imageFileNames.stream()
                .filter(imageName -> extractBaseName(imageName).equalsIgnoreCase(episodeBaseName))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .findFirst();
    }

    private List<String> loadImageFileNames() {
        Set<String> fileNames = new LinkedHashSet<>();

        if (Files.isDirectory(LOCAL_IMAGE_DIR)) {
            try (var stream = Files.list(LOCAL_IMAGE_DIR)) {
                stream.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .forEach(fileNames::add);
            } catch (IOException ignored) {
                // Local folder read failure should not break feed generation; classpath fallback can still work.
            }
        }

        if (fileNames.isEmpty()) {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            try {
                Resource[] resources = resolver.getResources(CLASSPATH_IMAGE_PATTERN);
                for (Resource resource : resources) {
                    String filename = resource.getFilename();
                    if (filename != null && !filename.isBlank()) {
                        fileNames.add(filename);
                    }
                }
            } catch (IOException ignored) {
                // No images found is acceptable; RSS should continue without item image tags.
            }
        }

        List<String> sorted = new ArrayList<>(fileNames);
        sorted.sort(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)));
        return sorted;
    }

    private String extractBaseName(String filename) {
        String trimmed = filename == null ? "" : filename.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        int dotIndex = trimmed.lastIndexOf('.');
        if (dotIndex <= 0) {
            return trimmed;
        }
        return trimmed.substring(0, dotIndex);
    }
}
