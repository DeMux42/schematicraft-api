# Schematicraft Java API Client

A minimal, dependency-free Java client for the Schematicraft In-Game API.

## API Documentation

Full API docs (Swagger): https://schematicraft.com/api/ingame/v1/docs

## Quick Start

```bash
# Compile
javac SchematiCraftAPI.java

# Run (with API key)
java SchematiCraftAPI sk_live_your_key_here

# Or use environment variable
export SCHEMATICRAFT_API_KEY=sk_live_your_key_here
java SchematiCraftAPI
```

## Local Development

```bash
export SCHEMATICRAFT_API_KEY=sk_test_your_key_here
export SCHEMATICRAFT_API_URL=http://localhost:3000
java SchematiCraftAPI
```

## Usage in Your Mod

```java
SchematiCraftAPI api = new SchematiCraftAPI("sk_live_...");

// Search
String results = api.search("castle", 1, 20);

// AI Search (consumes quota)
String aiResults = api.searchAI("a cozy cottage with a garden");

// Get user's schematic library (bundles + unbundled schematics)
String library = api.getLibrary();
// Returns: { bundles: [...], unbundled: [...] }

// Download with idempotency
String requestId = UUID.randomUUID().toString();
try {
    DownloadResult dl = api.download("schematic-uuid", Path.of("output.schematic"), null, null, requestId);
    System.out.println("Downloaded: " + dl.file);
} catch (AnalysisPendingException e) {
    // Schematic still being analyzed, retry with same requestId
    Thread.sleep(e.retryAfterSeconds * 1000);
    // Retry...
} catch (QuotaExceededException e) {
    // Download quota exhausted
} catch (RateLimitException e) {
    // Too many requests, wait e.retryAfterSeconds
}

// Download with format conversion
DownloadResult dl = api.download("uuid", Path.of("out.litematic"), "litematic", "1.20.4", null);

// Upload (streams file, no memory buffering)
String response = api.upload(Path.of("my_build.schematic"), "My Build", "1.20.4", "fabric");

// Upload as public (visible to everyone)
String response = api.upload(file, "My Build", "Description", "1.20.4", "fabric", "castle,medieval", true, null);

// Upload to a specific bundle
String response = api.upload(file, "My Build", "Description", "1.20.4", "fabric", "castle", false, "bundle-uuid");

// Feedback
api.submitFeedback(dl.downloadId, "worked", null, null);
// Outcomes: "worked", "had_issues", "didnt_work"
// Issue categories: "import_error_or_crash", "missing_or_wrong_blocks", "wrong_orientation", "file_seems_corrupt", "other"

// Status (quotas, rate limits, supported formats)
String status = api.getStatus();
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
