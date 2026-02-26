package com.daniel.podcast.podcastarchive.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.Resource; // Spring abstraction for readable content returned in HTTP responses.
import org.springframework.core.io.UrlResource; // Resource implementation backed by a file/URL path for streaming.
import static org.springframework.http.HttpStatus.NOT_FOUND;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity; // Lets us set status, headers, and body explicitly.
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException; // Throws HTTP status errors (e.g., 404) from controller logic.

import com.daniel.podcast.podcastarchive.media.FileResolver;

@RestController
// This controller returns MP3 file bytes for /file requests instead of JSON/text payloads.
public class FileController {

    // Resolver gives a safe filesystem path to the requested MP3 file.
    private final FileResolver fileResolver;

    public FileController(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }


    // Route only matches .mp3 names so this endpoint is explicitly for audio file delivery.
    @GetMapping("/file/{filename:.+\\.mp3}")

    public ResponseEntity<Resource> getFile(@PathVariable String filename) {
        Path path = fileResolver.resolveMp3(filename)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));

        try {
            // UrlResource streams the actual file contents (bytes) as the HTTP response body.
            Resource resource = new UrlResource(path.toUri());

            // Instead of returning text/JSON like feed, we return a Resource (file content) so Spring streams the MP3 bytes to the client.

            return ResponseEntity.ok()
                    // audio/mpeg tells clients to treat the response as playable/downloadable MP3 audio.
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .contentLength(Files.size(path))
                    .body(resource);
        } catch (IOException ex) {
            throw new ResponseStatusException(NOT_FOUND);

            // If file I/O fails while building the response, convert it to a clean HTTP 404 instead of exposing internal exceptions.

        }
    }
}
