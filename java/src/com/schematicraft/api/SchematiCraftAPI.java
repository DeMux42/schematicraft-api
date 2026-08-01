package com.schematicraft.api;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * SchematiCraft In-Game API Client
 * 
 * A minimal, dependency-free Java client for the SchematiCraft API.
 * Copy this file into your mod and customize as needed.
 * 
 * API Documentation: https://schematicraft.com/api/ingame/v1/docs
 */
public class SchematiCraftAPI {
    
    private final String baseUrl;
    private final String apiKey;
    private final String clientIdentifier;
    
    public SchematiCraftAPI(String apiKey) {
        this(apiKey, "https://schematicraft.com", null);
    }
    
    public SchematiCraftAPI(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, null);
    }
    
    /**
     * Creates a new API client.
     * @param apiKey API key (must start with sk_live_ or sk_test_)
     * @param baseUrl Base URL for the API (default: https://schematicraft.com)
     * @param clientIdentifier Optional client identifier for telemetry. Format: name/version (platform)
     *                         Example: "my-mod/1.0.0 (fabric)"
     */
    public SchematiCraftAPI(String apiKey, String baseUrl, String clientIdentifier) {
        if (apiKey == null || !apiKey.startsWith("sk_")) {
            throw new IllegalArgumentException("API key must start with sk_live_ or sk_test_");
        }
        this.apiKey = apiKey;
        this.baseUrl = baseUrl + "/api/ingame/v1";
        this.clientIdentifier = clientIdentifier;
    }
    
    // ========== Search ==========
    
    public String search(String query, int page, int limit) throws IOException, APIException {
        String url = baseUrl + "/search?query=" + URLEncoder.encode(query, "UTF-8") 
                   + "&page=" + page + "&limit=" + limit;
        return get(url);
    }
    
    public String searchAI(String query) throws IOException, APIException {
        String json = "{\"query\":\"" + escapeJson(query) + "\"}";
        return post(baseUrl + "/search/ai", json);
    }
    
    // ========== Download ==========
    
    /** Downloads schematic in original format. */
    public DownloadResult download(String schematicId, Path outputFile) throws IOException, APIException {
        return download(schematicId, outputFile, null, null, null, null);
    }
    
    /** Downloads with optional format conversion. Generates X-Request-Id for idempotency. */
    public DownloadResult download(String schematicId, Path outputFile, String format, String targetVersion, String requestId) throws IOException, APIException {
        return download(schematicId, outputFile, format, null, targetVersion, requestId);
    }

    /** Downloads with optional format conversion and target editor. */
    public DownloadResult download(String schematicId, Path outputFile, String format, String targetEditor, String targetVersion, String requestId) throws IOException, APIException {
        StringBuilder url = new StringBuilder(baseUrl + "/schematics/" + schematicId + "/download");
        List<String> params = new ArrayList<>();
        if (format != null) params.add("format=" + format);
        if (targetEditor != null) params.add("targetEditor=" + URLEncoder.encode(targetEditor, "UTF-8"));
        if (targetVersion != null) params.add("targetVersion=" + targetVersion);
        params.add("force=true");
        if (!params.isEmpty()) url.append("?").append(String.join("&", params));
        
        HttpURLConnection conn = openConnection(url.toString());
        conn.setRequestMethod("GET");
        if (requestId != null) {
            conn.setRequestProperty("X-Request-Id", requestId);
        }
        
        int status = conn.getResponseCode();
        String responseRequestId = conn.getHeaderField("X-Request-Id");
        
        if (status == 409) {
            // Analysis pending - retry after delay
            int retryAfter = parseRetryAfter(conn);
            throw new AnalysisPendingException("Schematic analysis pending", retryAfter, responseRequestId);
        }
        if (status == 402) {
            throw new QuotaExceededException("Download quota exhausted", readError(conn));
        }
        if (status == 429) {
            int retryAfter = parseRetryAfter(conn);
            throw new RateLimitException("Rate limit exceeded", retryAfter);
        }
        if (status >= 400) {
            throw new APIException("Download failed: " + status, readError(conn), status);
        }
        
        String downloadId = conn.getHeaderField("X-Download-Id");
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, outputFile, StandardCopyOption.REPLACE_EXISTING);
        }
        
        return new DownloadResult(downloadId, outputFile, responseRequestId);
    }
    
    // ========== Upload ==========
    
    public String upload(Path file, String title, String minecraftVersion, String loader) throws IOException, APIException {
        return upload(file, title, null, minecraftVersion, loader, null, false, null);
    }
    
    /** Uploads schematic with streaming (no full file buffering). */
    public String upload(Path file, String title, String description, String minecraftVersion, String loader, String tags) throws IOException, APIException {
        return upload(file, title, description, minecraftVersion, loader, tags, false, null, null);
    }
    
    /** 
     * Uploads schematic with full options.
     * @param isPublished If true, schematic is immediately public. Default is private.
     * @param bundleId Optional bundle ID to add the schematic to (must own the bundle).
     */
    public String upload(Path file, String title, String description, String minecraftVersion, String loader, String tags, boolean isPublished, String bundleId) throws IOException, APIException {
        return upload(file, title, description, minecraftVersion, loader, tags, isPublished, bundleId, null);
    }

    /**
     * Uploads schematic with full options including screenshot images.
     * @param images Optional list of image file paths (PNG/JPEG) to attach as screenshots.
     */
    public String upload(Path file, String title, String description, String minecraftVersion, String loader, String tags, boolean isPublished, String bundleId, List<Path> images) throws IOException, APIException {
        String boundary = "----SchematiCraft" + System.currentTimeMillis();
        HttpURLConnection conn = openConnection(baseUrl + "/schematics");
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setDoOutput(true);
        conn.setChunkedStreamingMode(8192); // Stream in 8KB chunks
        conn.setReadTimeout(180000); // 3 min for large uploads with images
        
        try {
            try (OutputStream out = conn.getOutputStream()) {
                writeField(out, boundary, "minecraftVersion", minecraftVersion);
                writeField(out, boundary, "loader", loader);
                if (title != null) writeField(out, boundary, "title", title);
                if (description != null) writeField(out, boundary, "description", description);
                if (tags != null) writeField(out, boundary, "tags", tags);
                if (isPublished) writeField(out, boundary, "isPublished", "true");
                if (bundleId != null) writeField(out, boundary, "bundleId", bundleId);
                writeFileStreaming(out, boundary, "file", file);
                if (images != null) {
                    for (Path imagePath : images) {
                        writeFileStreaming(out, boundary, "images", imagePath);
                    }
                }
                out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException writeEx) {
            // Server rejected the request mid-stream. Read the actual error response.
            try {
                int status = conn.getResponseCode();
                String body = readError(conn);
                throw new APIException("Upload rejected (HTTP " + status + "): " + body, body, status);
            } catch (APIException ae) {
                throw ae;
            } catch (Exception readEx) {
                // Could not read server response either
                throw new IOException(writeEx.getMessage() + " (server response unreadable: " + readEx.getMessage() + ")", writeEx);
            }
        }
        
        return readResponse(conn);
    }
    
    // ========== Feedback ==========
    
    /** Submit feedback for a download. outcome: "worked", "had_issues", "didnt_work" */
    public String submitFeedback(String downloadId, String outcome, String issueCategory, String notes) throws IOException, APIException {
        return submitFeedback(downloadId, outcome, issueCategory, notes, null, null, null);
    }

    /**
     * Submit detailed feedback for a download.
     * @param outcome "worked", "had_issues", "didnt_work"
     * @param issueCategory Required if outcome != "worked". One of: "import_error_or_crash", "missing_or_wrong_blocks", "wrong_orientation", "file_seems_corrupt", "other"
     * @param notes Free text notes about the issue
     * @param minecraftVersion e.g. "1.21.1"
     * @param modLoader e.g. "neoforge", "fabric", "forge"
     * @param modpack Optional modpack name
     */
    public String submitFeedback(String downloadId, String outcome, String issueCategory, String notes,
                                  String minecraftVersion, String modLoader, String modpack) throws IOException, APIException {
        StringBuilder json = new StringBuilder("{\"outcome\":\"" + escapeJson(outcome) + "\"");
        if (issueCategory != null) json.append(",\"issueCategory\":\"").append(escapeJson(issueCategory)).append("\"");
        if (notes != null) json.append(",\"additionalNotes\":\"").append(escapeJson(notes)).append("\"");
        if (minecraftVersion != null) json.append(",\"minecraftVersion\":\"").append(escapeJson(minecraftVersion)).append("\"");
        if (modLoader != null) json.append(",\"modLoader\":\"").append(escapeJson(modLoader)).append("\"");
        if (modpack != null) json.append(",\"modpack\":\"").append(escapeJson(modpack)).append("\"");
        json.append("}");
        return post(baseUrl + "/downloads/" + downloadId + "/feedback", json.toString());
    }
    
    // ========== Status ==========
    
    public String getStatus() throws IOException, APIException {
        return get(baseUrl + "/me/status");
    }
    
    // ========== Library ==========
    
    /** 
     * Get user's schematic library organized by bundles.
     * Returns JSON with: bundles (array with schematics) and unbundled (schematics not in any bundle).
     */
    public String getLibrary() throws IOException, APIException {
        return get(baseUrl + "/me/library");
    }

    // ========== Bundles ==========

    /**
     * Create a new bundle.
     * @param name Bundle name (required)
     * @param description Bundle description (optional)
     * @return JSON with: id, name, description
     */
    public String createBundle(String name, String description) throws IOException, APIException {
        StringBuilder json = new StringBuilder("{\"name\":\"" + escapeJson(name) + "\"");
        if (description != null) json.append(",\"description\":\"").append(escapeJson(description)).append("\"");
        json.append("}");
        return post(baseUrl + "/bundles", json.toString());
    }
    
    // ========== HTTP Helpers ==========
    
    private String get(String url) throws IOException, APIException {
        HttpURLConnection conn = openConnection(url);
        conn.setRequestMethod("GET");
        return readResponse(conn);
    }
    
    private String post(String url, String json) throws IOException, APIException {
        HttpURLConnection conn = openConnection(url);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return readResponse(conn);
    }
    
    private HttpURLConnection openConnection(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Accept", "application/json");
        if (clientIdentifier != null) {
            conn.setRequestProperty("X-Schematicraft-Client", clientIdentifier);
        }
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        return conn;
    }
    
    private String readResponse(HttpURLConnection conn) throws IOException, APIException {
        int status = conn.getResponseCode();
        
        if (status == 402) {
            throw new QuotaExceededException("Quota exhausted", readError(conn));
        }
        if (status == 429) {
            int retryAfter = parseRetryAfter(conn);
            throw new RateLimitException("Rate limit exceeded", retryAfter);
        }
        if (status >= 400) {
            throw new APIException("HTTP " + status, readError(conn), status);
        }
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
    
    private String readError(HttpURLConnection conn) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (Exception e) {
            return "Unknown error";
        }
    }
    
    private int parseRetryAfter(HttpURLConnection conn) {
        String header = conn.getHeaderField("Retry-After");
        if (header != null) {
            try { return Integer.parseInt(header); } catch (NumberFormatException ignored) {}
        }
        // Fallback: check JSON body for retryAfterSeconds
        return 10;
    }
    
    private void writeField(OutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write((value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }
    
    private void writeFileStreaming(OutputStream out, String boundary, String name, Path file) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + file.getFileName() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        Files.copy(file, out); // Streams directly, no buffering
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
    
    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
    
    // ========== Result Types ==========
    
    public static class DownloadResult {
        public final String downloadId;
        public final Path file;
        public final String requestId;
        public DownloadResult(String downloadId, Path file, String requestId) {
            this.downloadId = downloadId;
            this.file = file;
            this.requestId = requestId;
        }
    }
    
    // ========== Exceptions ==========
    
    public static class APIException extends Exception {
        public final String body;
        public final int statusCode;
        public APIException(String message, String body, int statusCode) {
            super(message);
            this.body = body;
            this.statusCode = statusCode;
        }
    }
    
    /** 429 - Rate limit exceeded. Retry after retryAfterSeconds. */
    public static class RateLimitException extends APIException {
        public final int retryAfterSeconds;
        public RateLimitException(String message, int retryAfterSeconds) {
            super(message, null, 429);
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }
    
    /** 402 - Quota exhausted. Upgrade plan or wait for reset. */
    public static class QuotaExceededException extends APIException {
        public QuotaExceededException(String message, String body) {
            super(message, body, 402);
        }
    }
    
    /** 409 - Analysis pending. Retry download after delay. */
    public static class AnalysisPendingException extends APIException {
        public final int retryAfterSeconds;
        public final String requestId;
        public AnalysisPendingException(String message, int retryAfterSeconds, String requestId) {
            super(message, null, 409);
            this.retryAfterSeconds = retryAfterSeconds;
            this.requestId = requestId;
        }
    }
    
    // ========== Example Usage ==========
    
    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("SCHEMATICRAFT_API_KEY");
        if (apiKey == null && args.length > 0) apiKey = args[0];
        if (apiKey == null) {
            System.err.println("Usage: java SchematiCraftAPI <api_key>");
            System.err.println("Or set SCHEMATICRAFT_API_KEY environment variable");
            System.exit(1);
        }
        
        String baseUrl = System.getenv("SCHEMATICRAFT_API_URL");
        SchematiCraftAPI api = baseUrl != null 
            ? new SchematiCraftAPI(apiKey, baseUrl)
            : new SchematiCraftAPI(apiKey);
        
        System.out.println("=== SchematiCraft API Example ===\n");
        
        // 1. Check status
        System.out.println("1. Checking API status...");
        String status = api.getStatus();
        System.out.println("   " + status.substring(0, Math.min(200, status.length())) + "...\n");
        
        // 2. Search for schematics
        System.out.println("2. Searching for 'castle'...");
        String results = api.search("castle", 1, 5);
        System.out.println("   " + results.substring(0, Math.min(300, results.length())) + "...\n");
        
        // 3. Get library (bundles + unbundled schematics)
        System.out.println("3. Getting schematic library...");
        String library = api.getLibrary();
        System.out.println("   " + library.substring(0, Math.min(300, library.length())) + "...\n");
        
        // 4. Upload example (uncomment to test)
        // Path testFile = Path.of("my_schematic.schematic");
        // if (Files.exists(testFile)) {
        //     // Upload as private (default)
        //     String result = api.upload(testFile, "My Build", "1.20.4", "fabric");
        //     System.out.println("Uploaded (private): " + result);
        //     
        //     // Upload as public
        //     String result2 = api.upload(testFile, "My Build", "A cool build", 
        //         "1.20.4", "fabric", "castle,medieval", true, null);
        //     System.out.println("Uploaded (public): " + result2);
        //     
        //     // Upload to a specific bundle
        //     String result3 = api.upload(testFile, "My Build", "A cool build", 
        //         "1.20.4", "fabric", "castle", false, "bundle-uuid-here");
        //     System.out.println("Uploaded to bundle: " + result3);
        // }
        
        // 5. Download example (uncomment with a real schematic ID)
        // try {
        //     DownloadResult dl = api.download("schematic-uuid", Path.of("output.schematic"));
        //     System.out.println("Downloaded: " + dl.file + ", ID: " + dl.downloadId);
        //     
        //     // Submit feedback
        //     api.submitFeedback(dl.downloadId, "worked", null, null);
        // } catch (AnalysisPendingException e) {
        //     System.out.println("Analysis pending, retry in " + e.retryAfterSeconds + "s");
        // } catch (QuotaExceededException e) {
        //     System.out.println("Quota exhausted: " + e.body);
        // }
        
        System.out.println("Done!");
    }
}
