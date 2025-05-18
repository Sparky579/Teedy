package com.sismics.docs.core.util;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;

/**
 * Utility for interacting with large language models.
 *
 * @author bgamard
 */
public class LLMUtil {
    /**
     * Logger.
     */
    private static final Logger log = LoggerFactory.getLogger(LLMUtil.class);

    /**
     * API key for calling the LLM.
     */
    private static final String API_KEY = "sk-d6a2d46469a343358208d70f427e313e";

    /**
     * API base URL.
     */
    private static final String API_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /**
     * Model name.
     */
    private static final String MODEL_NAME = "deepseek-v3";

    /**
     * Default temperature.
     */
    private static final double TEMPERATURE = 0.7;

    /**
     * HTTP client.
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Suggests tags based on document content.
     *
     * @param content Document content text
     * @param maxCharacters Maximum characters to use from content
     * @return List of suggested tags
     */
    public static List<String> suggestTags(String content, int maxCharacters) {
        if (content == null || content.isEmpty()) {
            return Lists.newArrayList();
        }

        // Limit content length
        String truncatedContent = content.length() > maxCharacters 
                ? content.substring(0, maxCharacters) 
                : content;
        
        try {
            // Prepare the API request
            JsonObjectBuilder requestBuilder = Json.createObjectBuilder()
                    .add("model", MODEL_NAME)
                    .add("temperature", TEMPERATURE)
                    .add("messages", Json.createArrayBuilder()
                            .add(Json.createObjectBuilder()
                                    .add("role", "system")
                                    .add("content", "You are a document tagging system. Extract 3-5 relevant keyword tags from the provided text. " +
                                            "Return ONLY a JSON array of strings with the tags. Nothing else. Make tags simple, one or two words maximum per tag."))
                            .add(Json.createObjectBuilder()
                                    .add("role", "user")
                                    .add("content", truncatedContent)));

            // Convert to JSON string
            String requestBody = requestBuilder.build().toString();
            
            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE_URL + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            // Send request and get response
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            // Parse response
            if (response.statusCode() == 200) {
                return parseTagsFromResponse(response.body());
            } else {
                log.error("Error calling LLM API: HTTP " + response.statusCode() + " - " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error calling LLM API", e);
        }

        return Lists.newArrayList();
    }

    /**
     * Parse tags from the LLM API response.
     *
     * @param responseBody Response body
     * @return List of tags
     */
    private static List<String> parseTagsFromResponse(String responseBody) {
        List<String> tags = Lists.newArrayList();
        
        try (JsonReader jsonReader = Json.createReader(new StringReader(responseBody))) {
            JsonObject responseJson = jsonReader.readObject();
            
            if (responseJson.containsKey("choices") && responseJson.getJsonArray("choices").size() > 0) {
                JsonObject message = responseJson.getJsonArray("choices")
                        .getJsonObject(0)
                        .getJsonObject("message");
                
                String content = message.getString("content", "").trim();
                
                // Try to parse as JSON array
                try (JsonReader contentReader = Json.createReader(new StringReader(content))) {
                    JsonArray tagsArray = contentReader.readArray();
                    for (int i = 0; i < tagsArray.size(); i++) {
                        tags.add(tagsArray.getString(i));
                    }
                } catch (Exception e) {
                    // If not valid JSON, try to extract from text
                    log.info("Response not in JSON format, attempting to extract tags from text: " + content);
                    
                    if (content.contains("[") && content.contains("]")) {
                        String arrayContent = content.substring(
                                content.indexOf("[") + 1, 
                                content.lastIndexOf("]")
                        );
                        
                        for (String tag : arrayContent.split(",")) {
                            tag = tag.trim().replace("\"", "").replace("'", "");
                            if (!tag.isEmpty()) {
                                tags.add(tag);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing LLM API response", e);
        }
        
        return tags;
    }
} 