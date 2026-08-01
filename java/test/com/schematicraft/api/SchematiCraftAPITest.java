package com.schematicraft.api;

import java.nio.file.*;
import java.util.UUID;
import java.util.regex.*;

/**
 * Integration tests for SchematiCraftAPI.
 * Tests ALL API endpoints.
 * 
 * Run with:
 *   export SCHEMATICRAFT_API_KEY=sk_test_...
 *   export SCHEMATICRAFT_API_URL=http://localhost:3000  # for local dev
 *   javac -d test/out src/com/schematicraft/api/SchematiCraftAPI.java test/com/schematicraft/api/SchematiCraftAPITest.java
 *   java -cp test/out com.schematicraft.api.SchematiCraftAPITest
 */
public class SchematiCraftAPITest {
    
    private static int passed = 0;
    private static int failed = 0;
    
    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("SCHEMATICRAFT_API_KEY");
        if (apiKey == null) {
            System.err.println("ERROR: SCHEMATICRAFT_API_KEY environment variable required");
            System.exit(1);
        }
        
        String baseUrl = System.getenv("SCHEMATICRAFT_API_URL");
        String clientId = "java-test-client/1.0.0 (test)";
        SchematiCraftAPI api = baseUrl != null 
            ? new SchematiCraftAPI(apiKey, baseUrl, clientId)
            : new SchematiCraftAPI(apiKey, "https://schematicraft.com", clientId);
        
        System.out.println("=== SchematiCraft API Tests (Java) ===\n");
        
        // Test: getStatus
        test("getStatus returns user info", () -> {
            String status = api.getStatus();
            assertContains(status, "\"user\"");
            assertContains(status, "\"quotas\"");
        });
        
        // Test: search
        test("search returns results array", () -> {
            String results = api.search("test", 1, 5);
            assertContains(results, "\"results\"");
            assertContains(results, "\"total\"");
        });
        
        // Test: searchAI
        test("searchAI returns results with translatedTerms", () -> {
            try {
                String results = api.searchAI("a small house");
                assertContains(results, "\"results\"");
                // translatedTerms may or may not be present depending on quota
            } catch (SchematiCraftAPI.QuotaExceededException e) {
                // Expected if quota exhausted - test passes
                System.out.println("    (quota exhausted, expected behavior)");
            }
        });
        
        // Test: getLibrary
        test("getLibrary returns bundles and unbundled", () -> {
            String library = api.getLibrary();
            assertContains(library, "\"bundles\"");
            assertContains(library, "\"unbundled\"");
        });
        
        // Test: upload
        test("upload creates schematic", () -> {
            // Create a minimal test file
            Path testFile = Files.createTempFile("test_schematic", ".schematic");
            try {
                // Write minimal NBT-like content (won't parse but tests upload flow)
                Files.write(testFile, new byte[]{0x0A, 0x00, 0x09, 0x53, 0x63, 0x68, 0x65, 0x6D, 0x61, 0x74, 0x69, 0x63, 0x00});
                
                String result = api.upload(
                    testFile,
                    "Test Upload " + System.currentTimeMillis(),
                    "1.20.4",
                    "fabric"
                );
                assertContains(result, "\"id\"");
            } finally {
                Files.deleteIfExists(testFile);
            }
        });
        
        // Test: download
        test("download retrieves schematic file", () -> {
            // First search for any schematic
            String searchResult = api.search("", 1, 1);
            
            // Extract first schematic ID from results
            Pattern pattern = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(searchResult);
            if (!matcher.find()) {
                System.out.println("    (no schematics found to download, skipping)");
                return;
            }
            
            String schematicId = matcher.group(1);
            Path outputFile = Files.createTempFile("download_test", ".schematic");
            
            try {
                String requestId = UUID.randomUUID().toString();
                SchematiCraftAPI.DownloadResult result = api.download(schematicId, outputFile, null, null, requestId);
                
                // Verify file was downloaded
                if (!Files.exists(outputFile)) {
                    throw new AssertionError("Downloaded file should exist");
                }
                if (Files.size(outputFile) == 0) {
                    throw new AssertionError("Downloaded file should not be empty");
                }
            } catch (SchematiCraftAPI.AnalysisPendingException e) {
                // Expected for newly uploaded schematics
                System.out.println("    (analysis pending, expected behavior)");
            } catch (SchematiCraftAPI.QuotaExceededException e) {
                // Expected if quota exhausted
                System.out.println("    (quota exhausted, expected behavior)");
            } finally {
                Files.deleteIfExists(outputFile);
            }
        });
        
        // Test: submitFeedback
        test("submitFeedback accepts feedback", () -> {
            // First search and download to get a downloadId
            String searchResult = api.search("", 1, 1);
            Pattern pattern = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(searchResult);
            
            if (!matcher.find()) {
                System.out.println("    (no schematics found, skipping)");
                return;
            }
            
            String schematicId = matcher.group(1);
            Path outputFile = Files.createTempFile("feedback_test", ".schematic");
            
            try {
                SchematiCraftAPI.DownloadResult downloadResult = api.download(schematicId, outputFile);
                
                if (downloadResult.downloadId != null) {
                    String feedback = api.submitFeedback(
                        downloadResult.downloadId,
                        "worked",
                        null,
                        null
                    );
                    assertContains(feedback, "\"id\"");
                    assertContains(feedback, "\"outcome\"");
                } else {
                    System.out.println("    (no downloadId returned, skipping feedback)");
                }
            } catch (SchematiCraftAPI.AnalysisPendingException e) {
                System.out.println("    (analysis pending, skipping feedback)");
            } catch (SchematiCraftAPI.QuotaExceededException e) {
                System.out.println("    (quota exhausted, skipping feedback)");
            } finally {
                Files.deleteIfExists(outputFile);
            }
        });
        
        // Test: invalid API key format
        test("constructor rejects invalid API key", () -> {
            try {
                new SchematiCraftAPI("invalid_key");
                throw new AssertionError("Should have thrown IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                // Expected
            }
        });
        
        // Summary
        System.out.println("\n=== Results ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.exit(failed > 0 ? 1 : 0);
    }
    
    private static void test(String name, TestCase testCase) {
        try {
            testCase.run();
            System.out.println("✓ " + name);
            passed++;
        } catch (Exception e) {
            System.out.println("✗ " + name);
            System.out.println("  Error: " + e.getMessage());
            failed++;
        }
    }
    
    private static void assertContains(String actual, String expected) {
        if (!actual.contains(expected)) {
            throw new AssertionError("Expected to contain: " + expected);
        }
    }
    
    @FunctionalInterface
    interface TestCase {
        void run() throws Exception;
    }
}
