package com.ptit.rc_system.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class AiRecommendationClient {
    private static final Logger logger = LoggerFactory.getLogger(AiRecommendationClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String userBasedPath;
    private final String itemBasedPath;
    private final String hybridPath;
    private final String topRatedPath;

    public AiRecommendationClient(
            ObjectMapper objectMapper,
            @Value("${ai.recommendation.base-url}") String baseUrl,
            @Value("${ai.recommendation.endpoints.user-based:/api/ai/recommend/user}") String userBasedPath,
            @Value("${ai.recommendation.endpoints.item-based:/api/ai/recommend/item}") String itemBasedPath,
            @Value("${ai.recommendation.endpoints.hybrid:/api/ai/recommend/hybrid}") String hybridPath,
            @Value("${ai.recommendation.endpoints.top-rated:/api/ai/recommend/top-rated}") String topRatedPath,
            @Value("${ai.recommendation.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${ai.recommendation.read-timeout-ms:5000}") long readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.userBasedPath = userBasedPath;
        this.itemBasedPath = itemBasedPath;
        this.hybridPath = hybridPath;
        this.topRatedPath = topRatedPath;
    }

    public List<Long> recommendUserBased(Long userId, int topK) {
        return fetchIds(userBasedPath, Map.of("userId", userId, "topK", topK));
    }

    public List<Long> recommendItemBased(Long userId, int topK) {
        return fetchIds(itemBasedPath, Map.of("userId", userId, "topK", topK));
    }

    public List<Long> recommendHybrid(Long userId, int topK, double userWeight) {
        return fetchIds(hybridPath, Map.of("userId", userId, "topK", topK, "userWeight", userWeight));
    }

    public List<Long> getTopRatedFoodsByUser(Long userId, int topK) {
        return fetchIds(topRatedPath, Map.of("userId", userId, "topK", topK));
    }

    private List<Long> fetchIds(String path, Map<String, Object> params) {
        if (params.values().stream().anyMatch(v -> v == null)) {
            return Collections.emptyList();
        }

        URI uri = buildUri(path, params);
        try {
            String body = restTemplate.getForObject(uri, String.class);
            return parseIdList(body);
        } catch (RestClientException ex) {
            logger.warn("AI service call failed: {}", uri, ex);
            return Collections.emptyList();
        }
    }

    private URI buildUri(String path, Map<String, Object> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + path);
        params.forEach(builder::queryParam);
        return builder.build().toUri();
    }

    private List<Long> parseIdList(String body) {
        if (body == null || body.isBlank()) {
            return Collections.emptyList();
        }

        String trimmed = body.trim();
        try {
            if (trimmed.startsWith("[")) {
                return objectMapper.readValue(trimmed, new TypeReference<List<Long>>() {});
            }

            if (trimmed.startsWith("{")) {
                JsonNode root = objectMapper.readTree(trimmed);
                JsonNode data = root.get("data");
                if (data != null && data.isArray()) {
                    List<Long> ids = new ArrayList<>();
                    for (JsonNode node : data) {
                        if (node.isNumber()) {
                            ids.add(node.asLong());
                        } else if (node.isObject() && node.has("id")) {
                            ids.add(node.get("id").asLong());
                        }
                    }
                    return ids;
                }
            }
        } catch (Exception ex) {
            logger.warn("Failed to parse AI response body", ex);
        }

        return Collections.emptyList();
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
