package com.smarthotel.hotelmanagement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

/**
 * Fetches high-quality hotel images from Pexels API.
 * Set hotel.image.api.key in application.properties (get a free key at https://www.pexels.com/api/).
 * Rate limit: 200 requests/hour on free tier; seeder adds delay between calls.
 */
@Service
@ConditionalOnProperty(name = "hotel.image.api.key")
public class HotelImageService {

    private static final Logger log = LoggerFactory.getLogger(HotelImageService.class);
    private static final String PEXELS_SEARCH = "https://api.pexels.com/v1/search?query=%s&per_page=15&orientation=landscape&page=%d";
    private static final int MAX_PHOTOS_PER_QUERY = 15;
    /** Use different Pexels pages (1–50) so each hotel gets a different set of 15 images = 750 unique slots. */
    private static final int MAX_PAGE = 50;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    /** Set to true when API returns 429 (rate limit); caller should stop further requests. */
    private volatile boolean rateLimitHit = false;

    public HotelImageService(RestTemplate restTemplate,
                            @Value("${hotel.image.api.key}") String apiKey) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey != null ? apiKey.trim() : "";
    }

    public boolean isRateLimitHit() {
        return rateLimitHit;
    }

    /**
     * Search Pexels for a photo and return the best URL. Uses first result.
     */
    public Optional<String> fetchImageUrl(String searchQuery) {
        return fetchImageUrl(searchQuery, 0);
    }

    /**
     * Search Pexels for photos and return the URL. Uses different pages (1–50) and index (0–14)
     * so each hotel gets a different image and repeats are minimized.
     * @param searchQuery e.g. "Kumarakom Lake Resort Kerala India hotel"
     * @param resultIndex unique per hotel – used to pick page and index (750 unique slots)
     * @return image URL or empty if no results / API error / rate limit
     */
    public Optional<String> fetchImageUrl(String searchQuery, int resultIndex) {
        if (apiKey.isEmpty()) return Optional.empty();
        int safeIndex = Math.abs(resultIndex);
        int page = (safeIndex / MAX_PHOTOS_PER_QUERY) % MAX_PAGE + 1;
        int indexInPage = safeIndex % MAX_PHOTOS_PER_QUERY;

        String encoded = java.net.URLEncoder.encode(searchQuery, java.nio.charset.StandardCharsets.UTF_8);
        String url = String.format(PEXELS_SEARCH, encoded, page);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getBody() == null) return Optional.empty();
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode photos = root.path("photos");
            if (!photos.isArray() || photos.isEmpty()) return Optional.empty();
            int size = photos.size();
            int index = Math.min(indexInPage, size - 1);
            JsonNode chosen = photos.get(index);
            JsonNode src = chosen.path("src");
            String large2x = src.path("large2x").asText("");
            if (!large2x.isEmpty()) return Optional.of(large2x);
            String large = src.path("large").asText("");
            if (!large.isEmpty()) return Optional.of(large);
            String medium = src.path("medium").asText("");
            if (!medium.isEmpty()) return Optional.of(medium);
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() != null && e.getStatusCode().value() == 429) {
                rateLimitHit = true;
                log.warn("Pexels API rate limit (429) reached; remaining hotels will use default image.");
            }
            return Optional.empty();
        } catch (Exception e) {
            log.debug("Pexels image fetch failed for '{}' page {}: {}", searchQuery, page, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Try primary query first; if no results, try fallback query (e.g. state-only for states with few results).
     */
    public Optional<String> fetchImageUrlWithFallback(String primaryQuery, String fallbackQuery, int resultIndex) {
        Optional<String> url = fetchImageUrl(primaryQuery, resultIndex);
        if (url.isPresent()) return url;
        if (fallbackQuery != null && !fallbackQuery.isBlank() && !fallbackQuery.equals(primaryQuery)) {
            return fetchImageUrl(fallbackQuery.trim(), resultIndex);
        }
        return Optional.empty();
    }
}
