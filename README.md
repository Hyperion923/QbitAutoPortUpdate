# QbitAutoPortUpdate

A small Spring Boot utility that watches a file for a forwarded port number and updates a running qBittorrent instance via its Web API. When the listen port changes, the app:
- Logs in to qBittorrent
- Updates the listen_port preference
- Temporarily stops currently running torrents
- Waits a short period
- Restarts those torrents

It also offers optional Spring Shell commands for manual login and port updates.


## Stack
- Language: Java 17
- Frameworks/Libraries: Spring Boot 3.5.x, Spring Shell, Jackson, Lombok
- Build tool: Gradle (via the included Gradle Wrapper)
- Container: Docker (Dockerfile) and Jib Gradle plugin for building/publishing container images
- Testing: JUnit 5, Spring Boot Test


## Requirements
- Java 17 (JDK) installed if running locally without Docker
- Docker, if you prefer to build/run in containers
- Internet access to download dependencies from Maven Central on first build

Optional (for publishing container images):
- Docker Hub account or another container registry (used by the Jib plugin)


## Configuration
Configuration is driven by Spring properties and environment variables. Defaults are defined in `src/main/resources/application.properties`.

Primary properties (with their environment variable equivalents and defaults):
- api.base-url (env: `API_BASE_URL`, default: `http://localhost:8080`)
- api.username (env: `API_USERNAME`, default: `admin`)
- api.password (env: `API_PASSWORD`, default: `adminadmin`)
- file.watch.path (env: `FILE_WATCH_PATH`, default: `C:/`)
- file.watch.filename (env: `FILE_WATCH_FILENAME`, default: `forwarded_port`)
- spring.shell.interactive.enabled (env: `SPRING_SHELL_INTERACTIVE_ENABLED`, default: `false`)
- watchdog.enabled (no default in properties; the code enables the watchdog by default via `@ConditionalOnProperty` — set to `false` to disable the file watcher)

Additional, commonly used env vars (from Dockerfile/build):
- SPRING_PROFILES_ACTIVE: Active Spring profile (default: `default`)
- JAVA_OPTS: Extra JVM arguments (e.g., `-Xms256m -Xmx512m`)

Note: The file watcher (DirectoryWatchdog) monitors `file.watch.path` for a file named by `file.watch.filename`. When the file changes or is created, it reads the first line, parses it as an integer port, and triggers the port update workflow.


## How to Run

### 1) Run locally with Gradle (recommended for development)
Windows:
- `gradlew.bat bootRun`

Linux/macOS:
- `./gradlew bootRun`

You can override configuration using environment variables, e.g.:
- Windows PowerShell: `setx API_BASE_URL "http://your-qbittorrent:8080"` (new shells) or `$env:API_BASE_URL="http://your-qbittorrent:8080"` (current shell)
- Linux/macOS: `export API_BASE_URL=http://your-qbittorrent:8080`

Alternatively, edit `src/main/resources/application.properties` (not recommended for secrets) or provide a custom `application-{profile}.properties` and start with `--spring.profiles.active=yourProfile`.

### 2) Run the built jar
- Build: `./gradlew build` (or `gradlew.bat build` on Windows)
- Run: `java -jar build/libs/qbitautoportupdate-<version>.jar`

Note: The Gradle build config sets the base name to `qbitautoportupdate`, so the jar will look like `build/libs/qbitautoportupdate-0.0.1.jar` by default.

### 3) Run with Docker
Using the provided Dockerfile:

- Build image: `docker build -t qbitautoportupdate:latest .`
- Run container (example):
  - `docker run --rm \
      -e API_BASE_URL=http://qbittorrent:8080 \
      -e API_USERNAME=admin \
      -e API_PASSWORD=adminadmin \
      -e FILE_WATCH_PATH=/watch \
      -e FILE_WATCH_FILENAME=forwarded_port \
      -v "${PWD}/watch:/watch" \
      qbitautoportupdate:latest`

The Docker image also recognizes:
- `SPRING_PROFILES_ACTIVE` (default `default`)
- `SPRING_SHELL_INTERACTIVE_ENABLED` (default `false`)
- `JAVA_OPTS` for JVM flags

### 4) Build and/or publish an image with Jib (no local Docker daemon required)
The project is configured with the Jib Gradle plugin.

- Build/push using environment-based defaults (Docker Hub):
  - Set `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` in your environment
  - Run: `./gradlew jib`

- Target a custom repository:
  - `./gradlew jib -PdockerRepo=ghcr.io/your-user-or-org`

By default, the image name is computed as:
- If `dockerRepo` is set: `<dockerRepo>/qbitautoportupdate:<version>`
- Else if `DOCKERHUB_USERNAME` is set: `docker.io/<username>/qbitautoportupdate:<version>`
- Else: `qbitautoportupdate:<version>`

The `latest` tag is also added.


## Shell Commands (optional)
This app includes Spring Shell commands. Interactive shell is disabled by default (`SPRING_SHELL_INTERACTIVE_ENABLED=false`). To use the commands, start the app with interactive shell enabled, e.g.:

- `SPRING_SHELL_INTERACTIVE_ENABLED=true ./gradlew bootRun`

Available commands:
- `login --username <user> --password <pass>` — authenticate against qBittorrent (uses configured defaults if omitted)
- `updatePort <port>` — update qBittorrent to use the given listen port and restart active torrents


## Development

### Project structure
- `src/main/java/.../QbitAutoPortUpdateApplication.java` — Spring Boot main class
- `src/main/java/.../controller/DirectoryWatchdog.java` — watches a directory for the forwarded port file
- `src/main/java/.../service/CommunicationService.java` — handles qBittorrent Web API interactions
- `src/main/java/.../debug/ConsoleCommands.java` — Spring Shell commands for manual actions
- `src/main/java/.../model/Preferences.java` — POJO mapping of qBittorrent preferences JSON
- `src/main/java/.../model/Torrent.java` — POJO for torrent info JSON
- `src/main/resources/application.properties` — default configuration
- `QbitTorren.http` and `http-client.env.json` — IDE HTTP client examples for the qBittorrent API
- `Dockerfile` — multi-stage Docker build for the app runtime image
- `build.gradle` — Gradle build configuration (Java 17, Spring Boot, Jib, etc.)

### Common Gradle tasks
- `./gradlew clean build` — compile and package the application
- `./gradlew test` — run tests
- `./gradlew bootRun` — run the application locally
- `./gradlew jib` — build/push container image via Jib


## Tests
Run the test suite with:
- `./gradlew test` (or `gradlew.bat test` on Windows)

Current tests include a context load test using the `test` profile.



## Notes and TODOs
- Licensing: No LICENSE file is present. TODO: Add an open-source license (e.g., MIT/Apache-2.0) or a proprietary license notice.


## License
TODO: Add a LICENSE file and update this section accordingly.
