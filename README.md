# Podcast Archive System

## What This Project Does
This project is a Spring Boot podcast archive service that:
- reads MP3 files from a local media folder,
- parses MP3 metadata (ID3 tags),
- exposes a feed endpoint (`/feed`) in JSON or RSS,
- serves audio files through a safe file endpoint (`/file/{filename}.mp3`).

It is designed to be easy to run locally and structured in stages so each layer (security, metadata, RSS compatibility) was added incrementally.

## Stage Progression (Step 1 -> Step 5.1)
- Step 1: Scaffolded Spring Boot app and added `GET /health`.
- Step 2: Added typed config + secure filesystem resolver + JSON feed + MP3 file serving.
- Step 3: Added MP3 metadata parsing (`title`, `artist`, `album`, `duration`) with safe fallbacks.
- Step 4 / 4.1: Added RSS generation and precision-aware date handling:
  - full datetime -> `pubDate`
  - partial date -> `dc:date`
- Step 5 / 5.1: Added podcast-client compatibility polish:
  - iTunes-friendly tags,
  - stable GUID strategy,
  - channel owner fields,
  - channel image support,
  - episode image matching by filename base (extension-agnostic).

## Current Feature Set
- Health check endpoint for quick service verification.
- Safe MP3 resolution with traversal protection.
- JSON feed output for easy debugging/inspection.
- RSS feed output for podcast clients.
- MP3 metadata extraction via `jaudiotagger`.
- RSS/iTunes XML generation via Rome + `rome-modules`.
- Stable GUIDs (filename-hash based, not base URL dependent).
- Channel-level and episode-level image handling.

## Tech Stack
- Java 17 (project target)
- Spring Boot 3.5.x
- Maven
- jaudiotagger (MP3/ID3 parsing)
- Rome + rome-modules (RSS + iTunes tags)
- JUnit + Spring test (`MockMvc`)

### Maven in This Project
- Maven handles dependency resolution, compilation target, tests, and run/package lifecycle.
- `mvn` uses the globally installed Maven on your machine.
- `mvnw` / `mvnw.cmd` uses the project wrapper-configured Maven version.
- Wrapper is currently configured to Maven `3.9.12` (from `.mvn/wrapper/maven-wrapper.properties`).
- Even if you run on JDK 21, project bytecode target remains Java 17 (per `pom.xml`).
- Without Java installed, there is no JVM/JDK to run Maven, compile the project or launch Spring Boot, the build and app cannot run at all.
 
- Mvnw manages Maven version, but Java must still be installed because Maven and Spring Boot run on the JVM.

```bash
mvn -v
mvnw -v   
Windows: mvnw.cmd -v
```

## Project Structure
```text
src/main/java/com/daniel/podcast/podcastarchive/
  config/           # typed application properties
  media/            # filesystem resolver + metadata parsing models/services
  feed/             # RSS assembly and image resolution
  web/              # REST controllers (/health, /feed, /file)

src/main/resources/
  application.properties
  static/images/    # channel/episode artwork files

podcasts/           # local MP3 source files
docs/               # development notes
```

## Prerequisites
- Java installed
- Maven installed

## Run Locally
1. Run tests:
```bash
mvn test
```
2. Start server:
```bash
mvn spring-boot:run
```
3. Open:
- `http://localhost:8080/health`
- `http://localhost:8080/feed`
- `http://localhost:8080/feed?format=rss`

4. Copy one file URL from feed output and open it:
- Example: `http://localhost:8080/file/episode-1.mp3`

## Configuration
Main config is in `src/main/resources/application.properties`.

| Key | Purpose |
|---|---|
| `podcast.mediaDir` | Local MP3 folder to scan and serve from (through resolver rules). |
| `podcast.baseUrl` | Base URL used to build absolute file/image URLs in feed output. |
| `podcast.channelTitle` | RSS channel title. |
| `podcast.channelLink` | RSS channel link. |
| `podcast.channelDescription` | RSS channel description. |
| `podcast.channelAuthor` | Channel-level iTunes author. |
| `podcast.explicit` | iTunes explicit-content flag (`yes/no`). |
| `podcast.channelImageUrl` | Optional channel artwork URL (`itunes:image` at channel level). |
| `podcast.channelOwnerName` | iTunes owner name. |
| `podcast.channelOwnerEmail` | iTunes owner email. |
| `podcast.imageBasePath` | Web path prefix for episode images (for example `/images`). |

## Endpoints and Expected Responses
- `GET /health`
  - Returns plain text: `OK`

- `GET /feed`
  - Can return JSON or RSS depending on request negotiation.
  - Browser requests often include XML in `Accept`, so browser may show RSS.

- `GET /feed?format=rss`
  - Forces RSS XML response.

- `GET /file/{filename}.mp3`
  - Streams audio bytes with `audio/mpeg`.
  - Invalid/missing/traversal attempts return `404`.

Content negotiation examples:
```bash
# Force JSON
curl -H "Accept: application/json" http://localhost:8080/feed

# Force RSS
curl -H "Accept: application/rss+xml" http://localhost:8080/feed

# Also force RSS via query
curl http://localhost:8080/feed?format=rss
```

GUID vs enclosure:
- `guid` is stable episode identity (dedupe/tracking key in podcast clients).
- `enclosure` is the actual media URL/type/length for playback/download.
- They do not need to match.

## Media + Image Rules
- MP3 source files live in `podcasts/`.
- Episode metadata comes from MP3 tags with fallbacks when fields are missing.
- `/file/{filename}.mp3` is the controlled media-serving path.

Image behavior:
- Channel image: from `podcast.channelImageUrl`.
- Item image: matched from `src/main/resources/static/images` by base filename, extension ignored.
  - Example: `episode-2.mp3` matches `episode-2.webp`.
- If no matching image exists, feed still builds (item image tag omitted).

Architecture flow:
- `FileResolver -> Mp3MetadataService -> PodcastEpisode -> RssFeedService -> /feed response`
- `/file` endpoint is separate and streams audio bytes directly.

## Testing
Tests are backend-focused (`MockMvc` + service tests).

They validate:
- endpoint availability and contracts,
- RSS structure/tags,
- GUID stability across base URL changes,
- date behavior (`pubDate` vs `dc:date`),
- enclosure correctness,
- owner/image RSS mappings.

Run all tests:
```bash
mvn test
```

## Git Push Checklist
- Ensure `target/` is not committed.
- Confirm `.gitignore` includes build/editor noise.
- Confirm desired assets are included:
  - `podcasts/` (if intentionally tracked)
  - `src/main/resources/static/images/`
- Run tests before push: `mvn test`.