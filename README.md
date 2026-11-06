<a id="readme-top"></a>

<!-- Structure adapted from othneildrew/Best-README-Template, credited in Acknowledgments. -->

<div align="center">

# Schematicraft API Clients

Official client libraries for the [Schematicraft](https://schematicraft.com) In-Game API.

Drop one file into your mod, plugin, or tool and you have search, download, upload, and conversion.

[![License][license-shield]][license-url]
[![Java][java-shield]][java-url]
[![API][api-shield]][api-url]
[![Issues][issues-shield]][issues-url]

[Website](https://schematicraft.com) &middot; [API reference][api-url] &middot; [Report a bug][issues-url]

</div>

<details>
  <summary>Contents</summary>

- [About The Project](#about-the-project)
  - [Available Clients](#available-clients)
  - [Versioning And The Parity Rule](#versioning-and-the-parity-rule)
  - [Design Goals](#design-goals)
  - [Built With](#built-with)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Get An API Key](#get-an-api-key)
  - [Installation](#installation)
- [Usage](#usage)
  - [Endpoints](#endpoints)
  - [Format Conversion](#format-conversion)
  - [Idempotent Downloads](#idempotent-downloads)
  - [Error Handling](#error-handling)
  - [Client Identification](#client-identification)
- [Local Development](#local-development)
- [Testing](#testing)
- [Security](#security)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)
- [Contact](#contact)
- [Acknowledgments](#acknowledgments)

</details>

## About The Project

Schematicraft stores Minecraft schematics in the cloud and converts between formats on the server. The In-Game API is how anything outside the website reaches that library: mods, server plugins, desktop sync clients, build scripts.

These clients exist so you do not have to hand-roll multipart uploads, retry semantics, and quota handling. Each one is a single dependency-free source file. Copy it into your project, or build it as a small library.

Reference consumer: [schematicraft-mod](https://github.com/DeMux42/schematicraft-mod), which uses the Java client as a source directory.

### Available Clients

| Language   | Version | Directory            | Notes                               |
| ---------- | ------- | -------------------- | ----------------------------------- |
| Java       | 1.1.0   | [java/](./java/)     | Packaged, Gradle build              |
| Kotlin     | 1.1.0   | [kotlin/](./kotlin/) | Single file, no package declaration |
| Python     | Planned | -                    |                                     |
| TypeScript | Planned | -                    |                                     |

### Versioning And The Parity Rule

**The clients ship in lockstep.** Java and Kotlin expose the same surface and carry the same version number. A change to one is a change to both, in the same release. There is no reference implementation that the others trail, because a client that silently lacks a capability is worse than no client at all.

The version lives in three places that have to agree. A single-file vendored library cannot derive them from each other, so this is a manual invariant:

- `SchematiCraftAPI.CLIENT_VERSION` in `java/src/com/schematicraft/api/SchematiCraftAPI.java`
- `SchematiCraftAPI.CLIENT_VERSION` in `kotlin/src/SchematiCraftAPI.kt`
- `version` in `java/build.gradle`

Semver has real teeth here, because callers compile against these:

| Bump  | Meaning                                               |
| ----- | ----------------------------------------------------- |
| PATCH | Bug fix, no signature change                          |
| MINOR | New methods or overloads, existing calls keep working |
| MAJOR | An existing signature or behavior changed             |

Adding an overload is a MINOR. Changing what an existing parameter means is a MAJOR even if it still compiles.

### Design Goals

**No dependencies.** Standard library only. `HttpURLConnection`, not a HTTP framework. A mod already ships enough jars.

**One file.** You can read the whole client in a sitting, and you can vendor it without a build system.

**Streaming uploads.** Schematics get large. Files stream in 8 KB chunks rather than being buffered into memory.

**Typed failure.** Quota exhaustion, rate limiting, and pending analysis are distinct exceptions, because each one calls for a different response.

**Keys stay quiet.** The API key is never logged and never included in an exception message.

### Built With

[![Java][java-shield]][java-url]
[![Kotlin][kotlin-shield]][kotlin-url]
[![Gradle][gradle-shield]][gradle-url]

- Java 21, set by the Gradle toolchain in `java/build.gradle`. The Java client imports only `java.io`, `java.net`, `java.nio` and `java.util`.
- Kotlin 1.4 or newer, on a JDK 11 or newer, for the Kotlin client, built with `kotlinc`. It imports only JDK packages.
- Gradle, with the `java-library` and `maven-publish` plugins, for the Java build. No wrapper is committed, so it uses the Gradle on your PATH.

<p align="right"><a href="#readme-top">Back to top</a></p>

## Getting Started

### Prerequisites

- Java 21 or newer, for the Java and Kotlin clients
- A free [schematicraft.com](https://schematicraft.com) account

### Get An API Key

1. Create an account at [schematicraft.com](https://schematicraft.com).
2. Open your profile and select the **In-Game API Keys** tab: [schematicraft.com/profile#ingame-api-keys](https://schematicraft.com/profile#ingame-api-keys).
3. Create a key. It is shown once, so store it then.

Keys are prefixed `sk_live_` for production and `sk_test_` for development. The client constructor rejects anything that does not start with `sk_`.

If you are shipping a mod, send users to that profile URL rather than asking them to paste anything else.

### Installation

Pick whichever fits how you build.

**Vendor the source file.** Copy `java/src/com/schematicraft/api/SchematiCraftAPI.java` into your project. This is what the reference mod does, via a Gradle source directory:

```groovy
sourceSets.main.java.srcDir '../../api-clients/java/src'
```

**Build it as a library.**

```sh
cd java
gradle build
```

The jar lands in `java/build/libs`. A `maven-publish` configuration is present, publishing as `com.schematicraft:api-client`.

There is no Gradle wrapper committed yet, so this needs Gradle on your PATH. Use the vendoring route if you would rather not install it.

<p align="right"><a href="#readme-top">Back to top</a></p>

## Usage

```java
SchematiCraftAPI api = new SchematiCraftAPI(apiKey);

String status = api.getStatus();
String library = api.getLibrary();
String results = api.search("castle", 1, 20);

DownloadResult dl = api.download(schematicId, Path.of("build.schem"));
api.submitFeedback(dl.downloadId, "worked", null, null);
```

Every method returns the raw JSON response as a `String`. There is no bundled JSON parser, deliberately: Minecraft ships Gson, servers ship Jackson, and a plugin has its own preference. Parse with whatever you already have.

Full per-language examples are in [java/README.md](./java/README.md) and [kotlin/README.md](./kotlin/README.md).

### Endpoints

Base path is `/api/ingame/v1`. The client prepends it, so you pass only the host.

Both clients cover all of it. An endpoint added to one must be added to the other in the same change.

| Method | Endpoint                   | Purpose                           |
| ------ | -------------------------- | --------------------------------- |
| GET    | `/me/status`               | User info, quotas, formats        |
| GET    | `/me/library`              | Bundles plus unbundled schematics |
| GET    | `/search`                  | Keyword search                    |
| POST   | `/search/ai`               | Natural language search           |
| GET    | `/schematics/:id/download` | Download, optionally converted    |
| POST   | `/schematics`              | Upload, with optional screenshots |
| POST   | `/bundles`                 | Create a bundle                   |
| POST   | `/downloads/:id/feedback`  | Report how a download went        |

`/me/status` is also the cheapest way to validate a key. Call it once after the user pastes one.

The palette endpoints are not wrapped by these clients yet. Palettes are authored on the website, and applying one at download time currently goes through the mod's own request path.

Authoritative schema, including request and response shapes: [the OpenAPI reference][api-url].

### Format Conversion

Conversion happens server side. Ask for the format you want and you get it, regardless of what was uploaded:

```java
api.download(id, Path.of("out.litematic"), "litematic", null, "1.21.1", requestId);
api.download(id, Path.of("out.nbt"), "nbt", "create", "1.21.1", requestId);
```

`targetEditor` matters when one extension serves several tools. A `.schematic` file means something slightly different to MCEdit, WorldEdit, and Schematica, and `.nbt` covers both Create and vanilla structure blocks. Naming the editor gets you a file that tool will actually open. Call `/me/status` for the formats your account can request.

### Idempotent Downloads

Downloads consume quota, so a retry must not double-charge you. Pass a `requestId` and reuse the same value on every retry of the same logical download:

```java
String requestId = UUID.randomUUID().toString();
DownloadResult dl = api.download(id, out, null, null, null, requestId);
```

This matters most with `AnalysisPendingException`. A freshly uploaded schematic has not been parsed yet, so the server answers 409 with a `Retry-After`. Sleep, then retry with the same `requestId`.

### Error Handling

| Exception                  | HTTP    | Meaning                       | What to do                      |
| -------------------------- | ------- | ----------------------------- | ------------------------------- |
| `AnalysisPendingException` | 409     | Schematic is still being read | Wait, retry same `requestId`    |
| `QuotaExceededException`   | 402     | Quota exhausted               | Surface the reset date          |
| `RateLimitException`       | 429     | Too many requests             | Wait `retryAfterSeconds`        |
| `APIException`             | 4xx/5xx | Everything else               | Inspect `body` and `statusCode` |

409 and 429 both carry `retryAfterSeconds`, parsed from `Retry-After`, defaulting to 10 seconds when the header is absent.

Quota exhaustion is a normal state, not a failure. Tell the user what reset and what their options are. Do not present it as an error.

### Client Identification

Optionally send an `X-Schematicraft-Client` header so we can tell which integrations are in use and debug reports against the right client:

```java
SchematiCraftAPI api = new SchematiCraftAPI(apiKey, "https://schematicraft.com", "my-mod/1.0.0 (neoforge)");
```

Format is `name/version (platform)`, for example `bg2-connector/1.4.2 (fabric)` or `litematic-bridge/0.9.0 (paper)`. Requests behave identically without it.

<p align="right"><a href="#readme-top">Back to top</a></p>

## Local Development

Point the client at a local backend with the base URL argument, or the `SCHEMATICRAFT_API_URL` environment variable when using the bundled `main` entry point:

```sh
export SCHEMATICRAFT_API_KEY=sk_test_your_key_here
export SCHEMATICRAFT_API_URL=http://localhost:3000
```

Use an `sk_test_` key against a local backend. Do not point a production key at a development host.

<p align="right"><a href="#readme-top">Back to top</a></p>

## Testing

Both clients ship an integration test that exercises every wrapped endpoint against a real server. There is no test framework; each is a plain `main` with a small assertion harness.

```sh
cd java
export SCHEMATICRAFT_API_KEY=sk_test_your_key_here
export SCHEMATICRAFT_API_URL=http://localhost:3000
javac -d test/out src/com/schematicraft/api/SchematiCraftAPI.java test/com/schematicraft/api/SchematiCraftAPITest.java
java -cp test/out com.schematicraft.api.SchematiCraftAPITest
```

These tests upload and download real data and consume real quota. Run them against a local backend with a test key.

<p align="right"><a href="#readme-top">Back to top</a></p>

## Security

Treat an API key as a password. It grants full access to the owner's library.

- Never commit a key, including in tests. Read it from the environment or from user configuration.
- Never log a key, or echo it back in an error message.
- Use `sk_test_` keys for development and rotate anything that has been printed to a terminal.
- Serve requests over HTTPS. Plain HTTP is for loopback only.

If a key is exposed, revoke it from the In-Game API Keys tab. Revocation is immediate.

<p align="right"><a href="#readme-top">Back to top</a></p>

## Roadmap

- [x] Java client, packaged with a Gradle build
- [x] Kotlin client
- [x] Streaming uploads with image attachments
- [x] Idempotent downloads with quota-safe retry
- [x] Bundle creation
- [x] Java and Kotlin at feature parity, released in lockstep
- [ ] Palette endpoints
- [ ] Gradle wrapper for the Java module
- [ ] Python client
- [ ] TypeScript client

See [open issues][issues-url] for the full list.

<p align="right"><a href="#readme-top">Back to top</a></p>

## Contributing

Issues and pull requests are welcome.

1. Fork the repository
2. Create a branch (`git checkout -b feature/thing`)
3. Commit your changes
4. Push and open a pull request

Two things to know before you start. Keep the clients dependency-free and keep each one to a single source file; the point is that they can be vendored. And never commit an API key, including in tests, even an expired one.

When adding a method, add it to the Java client first, then port it. Java is the reference, and the endpoint coverage table above should be updated in the same change.

<p align="right"><a href="#readme-top">Back to top</a></p>

## License

Distributed under the MIT License. See [`LICENSE`](LICENSE) for details.

<p align="right"><a href="#readme-top">Back to top</a></p>

## Contact

DeMux - [@DeMux42](https://github.com/DeMux42)

Project link: [https://github.com/DeMux42/schematicraft-api](https://github.com/DeMux42/schematicraft-api)

<p align="right"><a href="#readme-top">Back to top</a></p>

## Acknowledgments

- [Best-README-Template](https://github.com/othneildrew/Best-README-Template) by othneildrew, for this README's structure.
- [Shields.io](https://shields.io) for the badges.

<p align="right"><a href="#readme-top">Back to top</a></p>

[license-shield]: https://img.shields.io/badge/license-MIT-blue.svg
[license-url]: LICENSE
[java-shield]: https://img.shields.io/badge/Java-21-red.svg
[java-url]: https://adoptium.net/
[api-shield]: https://img.shields.io/badge/API-v1-brightgreen.svg
[api-url]: https://schematicraft.com/api/ingame/v1/docs
[issues-shield]: https://img.shields.io/github/issues/DeMux42/schematicraft-api.svg
[issues-url]: https://github.com/DeMux42/schematicraft-api/issues
[kotlin-shield]: https://img.shields.io/badge/Kotlin-1.4%2B-7F52FF.svg
[kotlin-url]: https://kotlinlang.org/
[gradle-shield]: https://img.shields.io/badge/Gradle-02303A.svg
[gradle-url]: https://gradle.org/
