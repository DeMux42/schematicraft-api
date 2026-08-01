<a id="readme-top"></a>

<div align="center">

# Schematicraft Java Client

Dependency-free Java client for the [Schematicraft](https://schematicraft.com) In-Game API.

[![License][license-shield]][license-url]
[![Java][java-shield]][java-url]
[![API][api-shield]][api-url]

[Client index](../README.md) &middot; [API reference][api-url] &middot; [Report a bug][issues-url]

</div>

<details>
  <summary>Contents</summary>

- [About](#about)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Try It](#try-it)
- [Usage](#usage)
  - [Search](#search)
  - [Library And Status](#library-and-status)
  - [Download](#download)
  - [Upload](#upload)
  - [Bundles](#bundles)
  - [Feedback](#feedback)
- [API Surface](#api-surface)
- [Error Handling](#error-handling)
- [Timeouts](#timeouts)
- [Testing](#testing)
- [License](#license)

</details>

## About

One source file, `src/com/schematicraft/api/SchematiCraftAPI.java`, in package `com.schematicraft.api`. Standard library only, so it adds nothing to your shaded jar.

This is the reference client. New API capabilities land here first, then get ported to the other languages.

Every method returns the raw JSON response as a `String`. Parse it with whatever JSON library your project already has; Minecraft ships Gson.

## Getting Started

### Prerequisites

- Java 21 or newer
- An API key from [schematicraft.com/profile#ingame-api-keys](https://schematicraft.com/profile#ingame-api-keys)

### Installation

**Vendor the source.** Copy the file into your project, or add `src` as a source directory:

```groovy
sourceSets.main.java.srcDir '../../api-clients/java/src'
```

**Or build a jar.** Requires Gradle on your PATH; no wrapper is committed yet.

```sh
gradle build
```

Output lands in `build/libs`. The `maven-publish` configuration publishes as `com.schematicraft:api-client`.

The Gradle build compiles the client and the integration test, but it does not run the test. The test needs a live server and a key, so it is invoked manually. See [Testing](#testing).

### Try It

The class has a `main` for smoke testing. It reads the key from `SCHEMATICRAFT_API_KEY` or the first argument.

```sh
javac -d out src/com/schematicraft/api/SchematiCraftAPI.java
java -cp out com.schematicraft.api.SchematiCraftAPI sk_live_your_key_here
```

```sh
export SCHEMATICRAFT_API_KEY=sk_test_your_key_here
export SCHEMATICRAFT_API_URL=http://localhost:3000
java -cp out com.schematicraft.api.SchematiCraftAPI
```

## Usage

```java
SchematiCraftAPI api = new SchematiCraftAPI(apiKey);
```

Three constructors are available. The key is validated for the `sk_` prefix and an invalid one throws `IllegalArgumentException` immediately, rather than failing later on a 401.

```java
new SchematiCraftAPI(apiKey);
new SchematiCraftAPI(apiKey, "http://localhost:3000");
new SchematiCraftAPI(apiKey, "https://schematicraft.com", "my-mod/1.0.0 (neoforge)");
```

The third argument sets the optional `X-Schematicraft-Client` header. Pass the host only; the client appends `/api/ingame/v1`.

### Search

```java
String results = api.search("castle", 1, 20);
String aiResults = api.searchAI("a cozy cottage with a garden");
```

`searchAI` consumes AI quota. `search` does not.

### Library And Status

```java
String status = api.getStatus();
String library = api.getLibrary();
```

`getStatus` returns the user, quotas, rate limits, and the formats this account can request. It is the cheapest way to validate a key after the user pastes one.

`getLibrary` returns `{ bundles: [...], unbundled: [...] }`, where each bundle carries its schematics.

### Download

```java
DownloadResult dl = api.download(id, Path.of("build.schem"));
```

With conversion, and with a request id so a retry does not consume quota twice:

```java
String requestId = UUID.randomUUID().toString();

try {
    DownloadResult dl = api.download(id, Path.of("out.litematic"), "litematic", null, "1.21.1", requestId);
    System.out.println("Saved " + dl.file + " as download " + dl.downloadId);
} catch (AnalysisPendingException e) {
    Thread.sleep(e.retryAfterSeconds * 1000L);
    // retry with the same requestId
} catch (QuotaExceededException e) {
    // out of downloads, show the reset date
} catch (RateLimitException e) {
    // back off for e.retryAfterSeconds
}
```

The six-argument form adds `targetEditor`, which disambiguates formats shared by several tools:

```java
api.download(id, Path.of("out.nbt"), "nbt", "create", "1.21.1", requestId);
```

`DownloadResult` carries `downloadId`, `file`, and `requestId`. Keep `downloadId` if you intend to submit feedback.

### Upload

Files stream in 8 KB chunks, so a large schematic is never held in memory.

```java
api.upload(Path.of("my_build.schem"), "My Build", "1.21.1", "neoforge");
```

Full options, including screenshots:

```java
api.upload(
    Path.of("my_build.schem"),
    "My Build",
    "A description",
    "1.21.1",
    "neoforge",
    "castle,medieval",
    true,                                    // publish immediately
    bundleId,                                // or null
    List.of(Path.of("shot1.png"), Path.of("shot2.png"))
);
```

| Parameter          | Required | Description                                       |
| ------------------ | -------- | ------------------------------------------------- |
| `file`             | Yes      | Path to the schematic                             |
| `minecraftVersion` | Yes      | For example `1.21.1`                              |
| `loader`           | Yes      | `fabric`, `forge`, `neoforge`, `quilt`, `vanilla` |
| `title`            | No       | Defaults to the filename                          |
| `description`      | No       | Free text                                         |
| `tags`             | No       | Comma separated                                   |
| `isPublished`      | No       | `true` is public, default is private              |
| `bundleId`         | No       | Bundle to add to, must be yours                   |
| `images`           | No       | PNG or JPEG screenshots                           |

Server-side limits apply: 50 MB per schematic, 10 MB per image, at most 5 images, 100 MB total per request. Exceeding them returns 413.

If the server rejects a request part way through the upload, the client reads the actual response and throws `APIException` with the real status and body, rather than surfacing a bare broken-pipe `IOException`. That distinction matters: a 413 looks like a network failure otherwise.

### Bundles

```java
String bundle = api.createBundle("Castle Builds", "Everything medieval");
```

Returns `id`, `name`, and `description`. Use that `id` as `bundleId` when uploading.

### Feedback

```java
api.submitFeedback(dl.downloadId, "worked", null, null);
```

With detail, which is worth sending when something went wrong:

```java
api.submitFeedback(
    dl.downloadId,
    "had_issues",
    "missing_or_wrong_blocks",
    "Stairs came in facing the wrong way",
    "1.21.1",
    "neoforge",
    "ATM11"
);
```

Outcomes are `worked`, `had_issues`, and `didnt_work`. Anything other than `worked` should carry an issue category: `import_error_or_crash`, `missing_or_wrong_blocks`, `wrong_orientation`, `file_seems_corrupt`, or `other`.

Feedback is what tells us a conversion path is broken, so send it when a user reports trouble.

## API Surface

| Method                                                                                     | Endpoint                       |
| ------------------------------------------------------------------------------------------ | ------------------------------ |
| `getStatus()`                                                                              | `GET /me/status`               |
| `getLibrary()`                                                                             | `GET /me/library`              |
| `search(query, page, limit)`                                                               | `GET /search`                  |
| `searchAI(query)`                                                                          | `POST /search/ai`              |
| `download(id, out)`                                                                        | `GET /schematics/:id/download` |
| `download(id, out, format, targetVersion, requestId)`                                      | same, with conversion          |
| `download(id, out, format, targetEditor, targetVersion, requestId)`                        | same, with editor              |
| `upload(file, title, mcVersion, loader)`                                                   | `POST /schematics`             |
| `upload(file, title, description, mcVersion, loader, tags)`                                | same                           |
| `upload(file, title, description, mcVersion, loader, tags, isPublished, bundleId)`         | same                           |
| `upload(file, title, description, mcVersion, loader, tags, isPublished, bundleId, images)` | same, with screenshots         |
| `createBundle(name, description)`                                                          | `POST /bundles`                |
| `submitFeedback(downloadId, outcome, issueCategory, notes)`                                | `POST /downloads/:id/feedback` |
| `submitFeedback(downloadId, outcome, issueCategory, notes, mcVersion, modLoader, modpack)` | same, with context             |

## Error Handling

| Exception                  | HTTP    | Meaning                       | What to do                      |
| -------------------------- | ------- | ----------------------------- | ------------------------------- |
| `AnalysisPendingException` | 409     | Schematic is still being read | Wait, retry same `requestId`    |
| `QuotaExceededException`   | 402     | Quota exhausted               | Show the reset date             |
| `RateLimitException`       | 429     | Too many requests             | Wait `retryAfterSeconds`        |
| `APIException`             | 4xx/5xx | Everything else               | Inspect `body` and `statusCode` |

All four extend `APIException`, so catch that if you do not need to distinguish. `AnalysisPendingException` and `RateLimitException` expose `retryAfterSeconds`, parsed from `Retry-After` and defaulting to 10.

The API key never appears in an exception message.

## Timeouts

| Phase         | Timeout |
| ------------- | ------- |
| Connect       | 30 s    |
| Read          | 60 s    |
| Read, uploads | 180 s   |

Uploads get the longer read timeout because a large schematic with screenshots is processed server side before the response arrives.

## Testing

`test/com/schematicraft/api/SchematiCraftAPITest.java` covers every wrapped endpoint against a live server. No test framework, just a `main` with a small assertion harness.

```sh
export SCHEMATICRAFT_API_KEY=sk_test_your_key_here
export SCHEMATICRAFT_API_URL=http://localhost:3000
javac -d test/out src/com/schematicraft/api/SchematiCraftAPI.java test/com/schematicraft/api/SchematiCraftAPITest.java
java -cp test/out com.schematicraft.api.SchematiCraftAPITest
```

It uploads and downloads real data and consumes real quota. Run it against a local backend with an `sk_test_` key, never a production key.

## License

Distributed under the MIT License. See [`LICENSE`](../LICENSE) for details.

<p align="right"><a href="#readme-top">Back to top</a></p>

[license-shield]: https://img.shields.io/badge/license-MIT-blue.svg
[license-url]: ../LICENSE
[java-shield]: https://img.shields.io/badge/Java-21-red.svg
[java-url]: https://adoptium.net/
[api-shield]: https://img.shields.io/badge/API-v1-brightgreen.svg
[api-url]: https://schematicraft.com/api/ingame/v1/docs
[issues-url]: https://github.com/DeMux42/schematicraft-api/issues
