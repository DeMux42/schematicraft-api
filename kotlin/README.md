<a id="readme-top"></a>

<div align="center">

# Schematicraft Kotlin Client

Dependency-free Kotlin client for the [Schematicraft](https://schematicraft.com) In-Game API.

[![License][license-shield]][license-url]
[![Kotlin][kotlin-shield]][kotlin-url]
[![API][api-shield]][api-url]

[Client index](../README.md) &middot; [API reference][api-url] &middot; [Report a bug][issues-url]

</div>

<details>
  <summary>Contents</summary>

- [About](#about)
- [Parity With The Java Client](#parity-with-the-java-client)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Try It](#try-it)
- [Usage](#usage)
  - [Search](#search)
  - [Library And Status](#library-and-status)
  - [Download](#download)
  - [Upload](#upload)
  - [Feedback](#feedback)
- [API Surface](#api-surface)
- [Error Handling](#error-handling)
- [Timeouts](#timeouts)
- [Testing](#testing)
- [License](#license)

</details>

## About

One source file, `src/SchematiCraftAPI.kt`. Standard library only, no coroutines, no HTTP framework. It uses named parameters and default arguments, so most calls read as a single line.

Note that this file declares no package and lives at the root of `src`. If you vendor it into a project with a package structure, add a `package` line to match.

Every method returns the raw JSON response as a `String`. Parse it with whatever you already have.

## Parity With The Java Client

Java is the reference implementation and this port trails it. Currently missing here:

| Capability                                        | Java | Kotlin |
| ------------------------------------------------- | ---- | ------ |
| `targetEditor` on download                        | Yes  | No     |
| Screenshot images on upload                       | Yes  | No     |
| `createBundle`                                    | Yes  | No     |
| Feedback with MC version, loader, modpack         | Yes  | No     |
| Extended upload read timeout                      | Yes  | No     |
| Mid-stream upload rejection reported as HTTP code | Yes  | No     |

Two of these are worth knowing about before you rely on this client. Without `targetEditor` you cannot disambiguate formats that several tools share, such as `.nbt` for both Create and vanilla structure blocks. And without mid-stream error reporting, a server-side rejection during upload, a 413 for instance, surfaces as a generic `IOException` rather than the real status.

If you need any of these today, use the [Java client](../java/README.md). It is callable from Kotlin without ceremony.

Contributions closing these gaps are welcome. See [Contributing](../README.md#contributing).

## Getting Started

### Prerequisites

- Kotlin 1.9 or newer, and a JDK 21 or newer
- An API key from [schematicraft.com/profile#ingame-api-keys](https://schematicraft.com/profile#ingame-api-keys)

### Installation

Copy `src/SchematiCraftAPI.kt` into your project. There is no Gradle build in this directory; if you need a jar, compile it directly:

```sh
kotlinc src/SchematiCraftAPI.kt -include-runtime -d SchematiCraftAPI.jar
```

### Try It

The file has a `main` for smoke testing. It reads the key from `SCHEMATICRAFT_API_KEY` or the first argument.

```sh
kotlin -classpath SchematiCraftAPI.jar SchematiCraftAPIKt sk_live_your_key_here
```

```sh
export SCHEMATICRAFT_API_KEY=sk_test_your_key_here
export SCHEMATICRAFT_API_URL=http://localhost:3000
kotlin -classpath SchematiCraftAPI.jar SchematiCraftAPIKt
```

## Usage

```kotlin
val api = SchematiCraftAPI(apiKey)
```

`baseUrl` and `clientIdentifier` are optional named parameters. The key is checked for the `sk_` prefix in an `init` block, so an invalid one fails immediately rather than later on a 401.

```kotlin
val api = SchematiCraftAPI(
    apiKey,
    baseUrl = "http://localhost:3000",
    clientIdentifier = "my-mod/1.0.0 (fabric)"
)
```

Pass the host only; the client appends `/api/ingame/v1`.

### Search

```kotlin
val results = api.search("castle")                 // page and limit default to 1 and 20
val aiResults = api.searchAI("a cozy cottage with a garden")
```

`searchAI` consumes AI quota and accepts `page` and `limit` too. `search` does not consume quota.

### Library And Status

```kotlin
val status = api.getStatus()
val library = api.getLibrary()
```

`getStatus` returns the user, quotas, rate limits, and requestable formats. It is the cheapest way to validate a key.

`getLibrary` returns `{ bundles: [...], unbundled: [...] }`.

### Download

```kotlin
val dl = api.download(id, Path.of("build.schem"))
```

With conversion, and with a request id so a retry does not consume quota twice:

```kotlin
val requestId = UUID.randomUUID().toString()

try {
    val dl = api.download(id, Path.of("out.litematic"), format = "litematic", targetVersion = "1.21.1", requestId = requestId)
    println("Saved ${dl.file} as download ${dl.downloadId}")
} catch (e: SchematiCraftAPI.AnalysisPendingException) {
    Thread.sleep(e.retryAfterSeconds * 1000L)
    // retry with the same requestId
} catch (e: SchematiCraftAPI.QuotaExceededException) {
    // out of downloads, show the reset date
} catch (e: SchematiCraftAPI.RateLimitException) {
    // back off for e.retryAfterSeconds
}
```

`DownloadResult` is a data class carrying `downloadId`, `file`, and `requestId`. Keep `downloadId` if you intend to submit feedback.

### Upload

Files stream in 8 KB chunks, so a large schematic is never held in memory.

```kotlin
api.upload(Path.of("my_build.schem"), minecraftVersion = "1.21.1", loader = "neoforge")
```

Full options:

```kotlin
api.upload(
    file = Path.of("my_build.schem"),
    title = "My Build",
    description = "A description",
    minecraftVersion = "1.21.1",
    loader = "neoforge",
    tags = "castle,medieval",
    isPublished = true,
    bundleId = bundleId
)
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

Server-side limits apply: 50 MB per schematic and 100 MB total per request. Exceeding them returns 413, which this client currently surfaces as an `IOException` rather than an `APIException`.

### Feedback

```kotlin
api.submitFeedback(dl.downloadId!!, "worked")
api.submitFeedback(dl.downloadId!!, "had_issues", "missing_or_wrong_blocks", "Stairs faced the wrong way")
```

Outcomes are `worked`, `had_issues`, and `didnt_work`. Anything other than `worked` should carry an issue category: `import_error_or_crash`, `missing_or_wrong_blocks`, `wrong_orientation`, `file_seems_corrupt`, or `other`.

## API Surface

| Method                                                                                    | Endpoint                       |
| ----------------------------------------------------------------------------------------- | ------------------------------ |
| `getStatus()`                                                                             | `GET /me/status`               |
| `getLibrary()`                                                                            | `GET /me/library`              |
| `search(query, page, limit)`                                                              | `GET /search`                  |
| `searchAI(query, page, limit)`                                                            | `POST /search/ai`              |
| `download(schematicId, outputFile)`                                                       | `GET /schematics/:id/download` |
| `download(schematicId, outputFile, format, targetVersion, requestId)`                     | same, with conversion          |
| `upload(file, title, minecraftVersion, loader)`                                           | `POST /schematics`             |
| `upload(file, title, description, minecraftVersion, loader, tags)`                        | same                           |
| `upload(file, title, description, minecraftVersion, loader, tags, isPublished, bundleId)` | same                           |
| `submitFeedback(downloadId, outcome, issueCategory, notes)`                               | `POST /downloads/:id/feedback` |

## Error Handling

| Exception                  | HTTP    | Meaning                       | What to do                      |
| -------------------------- | ------- | ----------------------------- | ------------------------------- |
| `AnalysisPendingException` | 409     | Schematic is still being read | Wait, retry same `requestId`    |
| `QuotaExceededException`   | 402     | Quota exhausted               | Show the reset date             |
| `RateLimitException`       | 429     | Too many requests             | Wait `retryAfterSeconds`        |
| `APIException`             | 4xx/5xx | Everything else               | Inspect `body` and `statusCode` |

All are nested in `SchematiCraftAPI` and extend `APIException`, so catch that if you do not need to distinguish. `AnalysisPendingException` and `RateLimitException` expose `retryAfterSeconds`, parsed from `Retry-After` and defaulting to 10.

The API key never appears in an exception message.

## Timeouts

| Phase   | Timeout |
| ------- | ------- |
| Connect | 30 s    |
| Read    | 60 s    |

Uploads use the same 60 second read timeout as everything else, unlike the Java client. A large upload on a slow connection can time out here.

## Testing

`test/SchematiCraftAPITest.kt` covers the wrapped endpoints against a live server. No test framework, just a `main` with a small assertion harness.

```sh
export SCHEMATICRAFT_API_KEY=sk_test_your_key_here
export SCHEMATICRAFT_API_URL=http://localhost:3000
kotlinc -include-runtime -d test/out/test.jar src/SchematiCraftAPI.kt test/SchematiCraftAPITest.kt
kotlin -classpath test/out/test.jar SchematiCraftAPITestKt
```

It uploads and downloads real data and consumes real quota. Run it against a local backend with an `sk_test_` key, never a production key.

## License

Distributed under the MIT License. See [`LICENSE`](../LICENSE) for details.

<p align="right"><a href="#readme-top">Back to top</a></p>

[license-shield]: https://img.shields.io/badge/license-MIT-blue.svg
[license-url]: ../LICENSE
[kotlin-shield]: https://img.shields.io/badge/Kotlin-1.9-7F52FF.svg
[kotlin-url]: https://kotlinlang.org/
[api-shield]: https://img.shields.io/badge/API-v1-brightgreen.svg
[api-url]: https://schematicraft.com/api/ingame/v1/docs
[issues-url]: https://github.com/DeMux42/schematicraft-api/issues
