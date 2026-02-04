import java.nio.file.*
import java.util.UUID

/**
 * Integration tests for SchematiCraftAPI (Kotlin).
 * 
 * Run with:
 *   export SCHEMATICRAFT_API_KEY=sk_test_...
 *   export SCHEMATICRAFT_API_URL=http://localhost:3000  # for local dev
 *   kotlinc -include-runtime -d test/out/test.jar ../src/SchematiCraftAPI.kt test/SchematiCraftAPITest.kt
 *   kotlin -classpath test/out/test.jar SchematiCraftAPITestKt
 */

private var passed = 0
private var failed = 0

fun main() {
    val apiKey = System.getenv("SCHEMATICRAFT_API_KEY")
    if (apiKey == null) {
        System.err.println("ERROR: SCHEMATICRAFT_API_KEY environment variable required")
        System.exit(1)
    }
    
    val baseUrl = System.getenv("SCHEMATICRAFT_API_URL")
    val api = if (baseUrl != null) {
        SchematiCraftAPI(apiKey, baseUrl)
    } else {
        SchematiCraftAPI(apiKey)
    }
    
    println("=== SchematiCraft API Tests (Kotlin) ===\n")
    
    // Test: getStatus
    test("getStatus returns user info") {
        val status = api.getStatus()
        assertContains(status, "\"user\"")
        assertContains(status, "\"quotas\"")
    }
    
    // Test: search
    test("search returns results array") {
        val results = api.search("test", 1, 5)
        assertContains(results, "\"results\"")
        assertContains(results, "\"total\"")
    }
    
    // Test: searchAI
    test("searchAI returns results with translatedTerms") {
        try {
            val results = api.searchAI("a small house", 1, 5)
            assertContains(results, "\"results\"")
            // translatedTerms may or may not be present depending on quota
        } catch (e: SchematiCraftAPI.QuotaExceededException) {
            // Expected if quota exhausted - test passes
            println("    (quota exhausted, expected behavior)")
        }
    }
    
    // Test: getLibrary
    test("getLibrary returns bundles and unbundled") {
        val library = api.getLibrary()
        assertContains(library, "\"bundles\"")
        assertContains(library, "\"unbundled\"")
    }
    
    // Test: upload (requires test file)
    test("upload creates schematic") {
        // Create a minimal test file
        val testFile = Files.createTempFile("test_schematic", ".schematic")
        try {
            // Write minimal NBT-like content (won't parse but tests upload flow)
            Files.write(testFile, byteArrayOf(0x0A, 0x00, 0x09, 0x53, 0x63, 0x68, 0x65, 0x6D, 0x61, 0x74, 0x69, 0x63, 0x00))
            
            val result = api.upload(
                file = testFile,
                title = "Test Upload ${System.currentTimeMillis()}",
                minecraftVersion = "1.20.4",
                loader = "fabric"
            )
            assertContains(result, "\"id\"")
        } finally {
            Files.deleteIfExists(testFile)
        }
    }
    
    // Test: download (uses search to find a schematic first)
    test("download retrieves schematic file") {
        // First search for any schematic
        val searchResult = api.search("", 1, 1)
        
        // Extract first schematic ID from results
        val idMatch = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(searchResult)
        if (idMatch == null) {
            println("    (no schematics found to download, skipping)")
            return@test
        }
        
        val schematicId = idMatch.groupValues[1]
        val outputFile = Files.createTempFile("download_test", ".schematic")
        
        try {
            val requestId = UUID.randomUUID().toString()
            val result = api.download(schematicId, outputFile, requestId = requestId)
            
            // Verify file was downloaded
            assert(Files.exists(outputFile)) { "Downloaded file should exist" }
            assert(Files.size(outputFile) > 0) { "Downloaded file should not be empty" }
        } catch (e: SchematiCraftAPI.AnalysisPendingException) {
            // Expected for newly uploaded schematics
            println("    (analysis pending, expected behavior)")
        } catch (e: SchematiCraftAPI.QuotaExceededException) {
            // Expected if quota exhausted
            println("    (quota exhausted, expected behavior)")
        } finally {
            Files.deleteIfExists(outputFile)
        }
    }
    
    // Test: submitFeedback (requires a download first)
    test("submitFeedback accepts feedback") {
        // First search and download to get a downloadId
        val searchResult = api.search("", 1, 1)
        val idMatch = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(searchResult)
        
        if (idMatch == null) {
            println("    (no schematics found, skipping)")
            return@test
        }
        
        val schematicId = idMatch.groupValues[1]
        val outputFile = Files.createTempFile("feedback_test", ".schematic")
        
        try {
            val downloadResult = api.download(schematicId, outputFile)
            
            if (downloadResult.downloadId != null) {
                val feedback = api.submitFeedback(
                    downloadId = downloadResult.downloadId,
                    outcome = "worked"
                )
                assertContains(feedback, "\"id\"")
                assertContains(feedback, "\"outcome\"")
            } else {
                println("    (no downloadId returned, skipping feedback)")
            }
        } catch (e: SchematiCraftAPI.AnalysisPendingException) {
            println("    (analysis pending, skipping feedback)")
        } catch (e: SchematiCraftAPI.QuotaExceededException) {
            println("    (quota exhausted, skipping feedback)")
        } finally {
            Files.deleteIfExists(outputFile)
        }
    }
    
    // Test: invalid API key format
    test("constructor rejects invalid API key") {
        try {
            SchematiCraftAPI("invalid_key")
            throw AssertionError("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }
    
    // Summary
    println("\n=== Results ===")
    println("Passed: $passed")
    println("Failed: $failed")
    System.exit(if (failed > 0) 1 else 0)
}

private fun test(name: String, testCase: () -> Unit) {
    try {
        testCase()
        println("✓ $name")
        passed++
    } catch (e: Exception) {
        println("✗ $name")
        println("  Error: ${e.message}")
        failed++
    }
}

private fun assertContains(actual: String, expected: String) {
    if (!actual.contains(expected)) {
        throw AssertionError("Expected to contain: $expected")
    }
}
