package com.example.library.catalog.infrastructure;

import com.example.library.catalog.domain.BookInformation;
import com.example.library.catalog.domain.BookNotFoundException;
import com.example.library.catalog.domain.BookSearchException;
import com.example.library.catalog.domain.BookSearchService;
import com.example.library.catalog.domain.Isbn;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Open Library adapter implementing the {@link BookSearchService} domain port,
 * backed by Spring's {@link RestClient} and Jackson 3. It replaces the JAX-RS
 * client of the original Jakarta EE implementation.
 */
@Component
public class OpenLibraryBookSearchService implements BookSearchService {
    private static final Logger LOGGER = Logger.getLogger(OpenLibraryBookSearchService.class.getName());

    /** The default Open Library API base URL. */
    public static final String DEFAULT_BASE_URL = "https://openlibrary.org/";

    /** Maximum number of HTTP redirects to follow before giving up. */
    private static final int MAX_REDIRECTS = 5;

    private final RestClient restClient;
    private final JsonMapper objectMapper;
    private final String baseUrl;

    public OpenLibraryBookSearchService() {
        this(DEFAULT_BASE_URL);
    }

    /**
     * Points the adapter at a custom base URL instead of the real Open Library
     * API. Intended for tests that mock the endpoint (e.g. with WireMock), where
     * the base URL is the WireMock server address.
     */
    OpenLibraryBookSearchService(String baseUrl) {
        this.restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
        this.objectMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        this.baseUrl = baseUrl;
    }

    public BookInformation search(Isbn isbn) {
        var targetUri = URI.create(baseUrl + "isbn/" + isbn.value() + ".json");
        try {
            var response = getFollowingRedirects(targetUri);
            int status = response.getStatusCode().value();
            if (status == 404) {
                throw new BookNotFoundException(isbn);
            }
            if (status != 200) {
                LOGGER.log(Level.WARNING, "OpenLibrary returned unexpected status {0} for isbn {1}",
                        new Object[]{status, isbn.value()});
                throw new BookSearchException(
                        "failed to search book, upstream returned status " + status);
            }
            var result = objectMapper.readValue(response.getBody(), OpenLibraryIsbnSearchResult.class);
            LOGGER.log(Level.FINEST, "Book search result: {0}", result);
            return new BookInformation(result.title());
        } catch (RestClientException e) {
            LOGGER.log(Level.SEVERE, "network error searching isbn {0}: {1}",
                    new Object[]{isbn.value(), e.getMessage()});
            throw new BookSearchException("failed to search book for isbn: " + isbn.value(), e);
        }
    }

    /**
     * Performs the GET, transparently following HTTP redirects. Open Library's
     * {@code /isbn/{isbn}.json} endpoint answers with a 302 to the canonical
     * {@code /books/{key}.json} location, which the client (backed by a
     * {@link JdkClientHttpRequestFactory} with redirects disabled) does not follow
     * on its own. The {@code requestUri} is kept so a relative {@code Location}
     * header can be resolved against it.
     */
    private ResponseEntity<String> getFollowingRedirects(URI requestUri) {
        var response = get(requestUri);
        int redirects = 0;
        while (isRedirect(response.getStatusCode().value()) && redirects < MAX_REDIRECTS) {
            URI location = response.getHeaders().getLocation();
            if (location == null) {
                return response;
            }
            redirects++;
            response = get(requestUri.resolve(location));
        }
        return response;
    }

    private ResponseEntity<String> get(URI uri) {
        try {
            return restClient.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toEntity(String.class);
        } catch (RestClientResponseException e) {
            // 4xx/5xx are surfaced as an exception by retrieve(); turn them back
            // into a ResponseEntity so the caller can map the status itself.
            return ResponseEntity.status(e.getStatusCode())
                    .headers(e.getResponseHeaders())
                    .body(e.getResponseBodyAsString());
        }
    }

    private static boolean isRedirect(int status) {
        return status >= 300 && status < 400;
    }
}
