import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.nio.file.*

/**
 * SchematiCraft In-Game API Client
 * 
 * A minimal, dependency-free Kotlin client for the SchematiCraft API.
 * Copy this file into your mod and customize as needed.
 * 
 * API Documentation: https://schematicraft.com/api/ingame/v1/docs
 */
class SchematiCraftAPI(
    private val apiKey: String,
    baseUrl: String = "https://schematicraft.com"
) {
    private val baseUrl: String = "$baseUrl/api/ingame/v1"
    
    init {
        require(apiKey.startsWith("sk_")) { 
            "API key must start with sk_live_ or sk_test_" 
        }
    }
    
    // ========== Search ==========
    
    fun search(query: String, page: Int = 1, limit: Int = 20): String {
        val url = "$baseUrl/search?query=${URLEncoder.encode(query, "UTF-8")}&page=$page&limit=$limit"
        return get(url)
    }
    
    fun searchAI(query: String, page: Int = 1, limit: Int = 20): String {
        val json = """{"query":"${escapeJson(query)}","page":$page,"limit":$limit}"""
        return post("$baseUrl/search/ai", json)
    }
    
    // ========== Download ==========
    
    /** Downloads schematic in original format. */
    fun download(schematicId: String, outputFile: Path): DownloadResult {
        return download(schematicId, outputFile, null, null, null)
    }
    
    /** Downloads with optional format conversion. Generates X-Request-Id for idempotency. */
    fun download(
        schematicId: String,
        outputFile: Path,
        format: String? = null,
        targetVersion: String? = null,
        requestId: String? = null
    ): DownloadResult {
        val params = mutableListOf<String>()
        format?.let { params.add("format=$it") }
        targetVersion?.let { params.add("targetVersion=$it") }
        
        val url = buildString {
            append("$baseUrl/schematics/$schematicId/download")
            if (params.isNotEmpty()) {
                append("?")
                append(params.joinToString("&"))
            }
        }
        
        val conn = openConnection(url)
        conn.requestMethod = "GET"
        requestId?.let { conn.setRequestProperty("X-Request-Id", it) }
        
        val status = conn.responseCode
        val responseRequestId = conn.getHeaderField("X-Request-Id")
        
        when (status) {
            409 -> {
                val retryAfter = parseRetryAfter(conn)
                throw AnalysisPendingException("Schematic analysis pending", retryAfter, responseRequestId)
            }
            402 -> throw QuotaExceededException("Download quota exhausted", readError(conn))
            429 -> {
                val retryAfter = parseRetryAfter(conn)
                throw RateLimitException("Rate limit exceeded", retryAfter)
            }
            in 400..599 -> throw APIException("Download failed: $status", readError(conn), status)
        }
        
        val downloadId = conn.getHeaderField("X-Download-Id")
        conn.inputStream.use { input ->
            Files.copy(input, outputFile, StandardCopyOption.REPLACE_EXISTING)
        }
        
        return DownloadResult(downloadId, outputFile, responseRequestId)
    }
    
    // ========== Upload ==========
    
    fun upload(
        file: Path,
        title: String? = null,
        minecraftVersion: String,
        loader: String
    ): String {
        return upload(file, title, null, minecraftVersion, loader, null, false, null)
    }

    /** Uploads schematic with streaming (no full file buffering). */
    fun upload(
        file: Path,
        title: String? = null,
        description: String? = null,
        minecraftVersion: String,
        loader: String,
        tags: String? = null
    ): String {
        return upload(file, title, description, minecraftVersion, loader, tags, false, null)
    }
    
    /**
     * Uploads schematic with full options.
     * @param isPublished If true, schematic is immediately public. Default is private.
     * @param bundleId Optional bundle ID to add the schematic to (must own the bundle).
     */
    fun upload(
        file: Path,
        title: String? = null,
        description: String? = null,
        minecraftVersion: String,
        loader: String,
        tags: String? = null,
        isPublished: Boolean = false,
        bundleId: String? = null
    ): String {
        val boundary = "----SchematiCraft${System.currentTimeMillis()}"
        val conn = openConnection("$baseUrl/schematics")
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        conn.doOutput = true
        conn.setChunkedStreamingMode(8192) // Stream in 8KB chunks
        
        conn.outputStream.use { out ->
            writeField(out, boundary, "minecraftVersion", minecraftVersion)
            writeField(out, boundary, "loader", loader)
            title?.let { writeField(out, boundary, "title", it) }
            description?.let { writeField(out, boundary, "description", it) }
            tags?.let { writeField(out, boundary, "tags", it) }
            if (isPublished) writeField(out, boundary, "isPublished", "true")
            bundleId?.let { writeField(out, boundary, "bundleId", it) }
            // Stream file directly without loading into memory
            writeFileStreaming(out, boundary, "file", file)
            out.write("--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
        }
        
        return readResponse(conn)
    }
    
    // ========== Feedback ==========
    
    /** Submit feedback for a download. outcome: "worked", "had_issues", "didnt_work" */
    fun submitFeedback(
        downloadId: String,
        outcome: String,
        issueCategory: String? = null,
        notes: String? = null
    ): String {
        val json = buildString {
            append("""{"outcome":"$outcome"""")
            issueCategory?.let { append(""","issueCategory":"$it"""") }
            notes?.let { append(""","additionalNotes":"${escapeJson(it)}"""") }
            append("}")
        }
        return post("$baseUrl/downloads/$downloadId/feedback", json)
    }
    
    // ========== Status ==========
    
    fun getStatus(): String = get("$baseUrl/me/status")
    
    // ========== Library ==========
    
    /**
     * Get user's schematic library organized by bundles.
     * Returns JSON with: bundles (array with schematics) and unbundled (schematics not in any bundle).
     */
    fun getLibrary(): String = get("$baseUrl/me/library")
    
    // ========== HTTP Helpers ==========
    
    private fun get(url: String): String {
        val conn = openConnection(url)
        conn.requestMethod = "GET"
        return readResponse(conn)
    }
    
    private fun post(url: String, json: String): String {
        val conn = openConnection(url)
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.outputStream.use { out ->
            out.write(json.toByteArray(StandardCharsets.UTF_8))
        }
        return readResponse(conn)
    }
    
    private fun openConnection(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        return conn
    }
    
    private fun readResponse(conn: HttpURLConnection): String {
        val status = conn.responseCode
        
        when (status) {
            402 -> throw QuotaExceededException("Quota exhausted", readError(conn))
            429 -> {
                val retryAfter = parseRetryAfter(conn)
                throw RateLimitException("Rate limit exceeded", retryAfter)
            }
            in 400..599 -> throw APIException("HTTP $status", readError(conn), status)
        }
        
        return conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }
    
    private fun readError(conn: HttpURLConnection): String {
        return try {
            conn.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: "Unknown error"
        } catch (e: Exception) {
            "Unknown error"
        }
    }
    
    private fun parseRetryAfter(conn: HttpURLConnection): Int {
        val header = conn.getHeaderField("Retry-After")
        return header?.toIntOrNull() ?: 10
    }
    
    private fun writeField(out: OutputStream, boundary: String, name: String, value: String) {
        out.write("--$boundary\r\n".toByteArray(StandardCharsets.UTF_8))
        out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
        out.write("$value\r\n".toByteArray(StandardCharsets.UTF_8))
    }
    
    private fun writeFileStreaming(out: OutputStream, boundary: String, name: String, file: Path) {
        out.write("--$boundary\r\n".toByteArray(StandardCharsets.UTF_8))
        out.write("Content-Disposition: form-data; name=\"$name\"; filename=\"${file.fileName}\"\r\n".toByteArray(StandardCharsets.UTF_8))
        out.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
        Files.copy(file, out) // Streams directly, no buffering
        out.write("\r\n".toByteArray(StandardCharsets.UTF_8))
    }
    
    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
    
    // ========== Result Types ==========
    
    data class DownloadResult(
        val downloadId: String?,
        val file: Path,
        val requestId: String?
    )
    
    // ========== Exceptions ==========
    
    open class APIException(
        message: String,
        val body: String?,
        val statusCode: Int
    ) : Exception(message)
    
    /** 429 - Rate limit exceeded. Retry after retryAfterSeconds. */
    class RateLimitException(
        message: String,
        val retryAfterSeconds: Int
    ) : APIException(message, null, 429)
    
    /** 402 - Quota exhausted. Upgrade plan or wait for reset. */
    class QuotaExceededException(
        message: String,
        body: String?
    ) : APIException(message, body, 402)
    
    /** 409 - Analysis pending. Retry download after delay. */
    class AnalysisPendingException(
        message: String,
        val retryAfterSeconds: Int,
        val requestId: String?
    ) : APIException(message, null, 409)
}

// ========== Example Usage ==========

fun main(args: Array<String>) {
    var apiKey = System.getenv("SCHEMATICRAFT_API_KEY")
    if (apiKey == null && args.isNotEmpty()) apiKey = args[0]
    if (apiKey == null) {
        System.err.println("Usage: kotlin SchematiCraftAPIKt <api_key>")
        System.err.println("Or set SCHEMATICRAFT_API_KEY environment variable")
        System.exit(1)
    }
    
    val baseUrl = System.getenv("SCHEMATICRAFT_API_URL")
    val api = if (baseUrl != null) {
        SchematiCraftAPI(apiKey, baseUrl)
    } else {
        SchematiCraftAPI(apiKey)
    }
    
    println("=== SchematiCraft API Example ===\n")
    
    // 1. Check status
    println("1. Checking API status...")
    val status = api.getStatus()
    println("   ${status.take(200)}...\n")
    
    // 2. Search for schematics
    println("2. Searching for 'castle'...")
    val results = api.search("castle", 1, 5)
    println("   ${results.take(300)}...\n")
    
    // 3. Get library (bundles + unbundled schematics)
    println("3. Getting schematic library...")
    val library = api.getLibrary()
    println("   ${library.take(300)}...\n")
    
    println("Done!")
}
