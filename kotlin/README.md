# Schematicraft Kotlin API Client

A minimal, dependency-free Kotlin client for the Schematicraft In-Game API.

## API Documentation

Full API docs (Swagger): https://schematicraft.com/api/ingame/v1/docs

## Quick Start

```bash
# Compile
kotlinc src/SchematiCraftAPI.kt -include-runtime -d SchematiCraftAPI.jar

# Run (with API key)
kotlin -classpath SchematiCraftAPI.jar SchematiCraftAPIKt sk_live_your_key_here

# Or use environment variable
export SCHEMATICRAFT_API_KEY=sk_live_your_key_here
kotlin -classpath SchematiCraftAPI.jar SchematiCraftAPIKt
```

## Local Development

```bash
export SCHEMATICRAFT_API_KEY=sk_test_your_key_here
export SCHEMATICRAFT_API_URL=http://localhost:3000
kotlin -classpath SchematiCraftAPI.jar SchematiCraftAPIKt
```

## Usage in Your Mod

```kotlin
val api = SchematiCraftAPI("sk_live_...")

// Search
val results = api.search("castle", page = 1, limit = 20)

// AI Search (consumes quota)
val aiResults = api.searchAI("a cozy cottage with a garden")

// Get user's schematic library (bundles + unbundled schematics)
val library = api.getLibrary()
// Returns: { bundles: [...], unbundled: [...] }

// Download with idempotency
val requestId = java.util.UUID.randomUUID().toString()
try {
    val dl = api.download("schematic-uuid", Path.of("output.schematic"), requestId = requestId)
    println("Downloaded: ${dl.file}")
} catch (e: SchematiCraftAPI.AnalysisPendingException) {
    // Schematic still being analyzed, retry with same requestId
    Thread.sleep(e.retryAfterSeconds * 1000L)
    // Retry...
} catch (e: SchematiCraftAPI.QuotaExceededException) {
    // Download quota exhausted
} catch (e: SchematiCraftAPI.RateLimitException) {
    // Too many requests, wait e.retryAfterSeconds
}

// Download with format conversion
val dl = api.download("uuid", Path.of("out.litematic"), format = "litematic", targetVersion = "1.20.4")

// Upload (streams file, no memory buffering)
val response = api.upload(Path.of("my_build.schematic"), minecraftVersion = "1.20.4", loader = "fabric")

// Upload as public (visible to everyone)
val response = api.upload(
    file = file,
    title = "My Build",
    description = "Description",
    minecraftVersion = "1.20.4",
    loader = "fabric",
    tags = "castle,medieval",
    isPublished = true
)

// Upload to a specific bundle
val response = api.upload(
    file = file,
    title = "My Build",
    minecraftVersion = "1.20.4",
    loader = "fabric",
    bundleId = "bundle-uuid"
)

// Feedback
api.submitFeedback(dl.downloadId!!, "worked")
// Outcomes: "worked", "had_issues", "didnt_work"
// Issue categories: "import_error_or_crash", "missing_or_wrong_blocks", "wrong_orientation", "file_seems_corrupt", "other"

// Status (quotas, rate limits, supported formats)
val status = api.getStatus()
```

## Upload Options

| Parameter          | Required | Description                                       |
| ------------------ | -------- | ------------------------------------------------- |
| `file`             | Yes      | Path to schematic file                            |
| `title`            | No       | Display title (defaults to filename)              |
| `description`      | No       | Description text                                  |
| `minecraftVersion` | Yes      | e.g., "1.20.4"                                    |
| `loader`           | Yes      | "fabric", "forge", "neoforge", "quilt", "vanilla" |
| `tags`             | No       | Comma-separated tags                              |
| `isPublished`      | No       | `true` = public, `false` = private (default)      |
| `bundleId`         | No       | UUID of bundle to add schematic to                |

## Error Handling

| Exception                  | HTTP    | Meaning                   | Action                         |
| -------------------------- | ------- | ------------------------- | ------------------------------ |
| `RateLimitException`       | 429     | Too many requests         | Wait `retryAfterSeconds`       |
| `QuotaExceededException`   | 402     | Quota exhausted           | Upgrade plan or wait for reset |
| `AnalysisPendingException` | 409     | Schematic still analyzing | Retry with same `requestId`    |
| `APIException`             | 4xx/5xx | Other errors              | Check `body` for details       |

## Key Features

- **Idempotent downloads**: Pass `requestId` to safely retry without double-consuming quota
- **Streaming uploads**: Large files are streamed directly, no memory buffering
- **sk\_\* validation**: Constructor validates API key format
- **No secret logging**: API key is never logged or exposed in exceptions
- **Library endpoint**: Get all your schematics organized by bundles
- **Kotlin idioms**: Named parameters, data classes, null safety
