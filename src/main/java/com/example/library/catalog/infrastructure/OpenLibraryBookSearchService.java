package com.example.library.catalog.infrastructure;

import com.example.library.catalog.domain.BookInformation;
import com.example.library.catalog.domain.BookNotFoundException;
import com.example.library.catalog.domain.BookSearchException;
import com.example.library.catalog.domain.BookSearchService;
import com.example.library.catalog.domain.Isbn;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Open Library adapter implementing the {@link BookSearchService} domain port,
 * backed by the JDK HTTP client and Jackson. It replaces the JAX-RS client of the
 * original Jakarta EE implementation.
 */
@Component
public class OpenLibraryBookSearchService implements BookSearchService {
    private static final Logger LOGGER = Logger.getLogger(OpenLibraryBookSearchService.class.getName());

    /** The default Open Library API base URL. */
    public static final String DEFAULT_BASE_URL = "https://openlibrary.org/";

    /** Maximum number of HTTP redirects to follow before giving up. */
    private static final int MAX_REDIRECTS = 5;

    private final HttpClient client;
    private final ObjectMapper objectMapper;
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
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.baseUrl = baseUrl;
    }

    public BookInformation search(Isbn isbn) {
        var targetUri = URI.create(baseUrl + "isbn/" + isbn.value() + ".json");
        try {
            var response = getFollowingRedirects(targetUri);
            int status = response.statusCode();
            if (status == 404) {
                throw new BookNotFoundException(isbn);
            }
            if (status != 200) {
                LOGGER.log(Level.WARNING, "OpenLibrary returned unexpected status {0} for isbn {1}",
                        new Object[]{status, isbn.value()});
                throw new BookSearchException(
                        "failed to search book, upstream returned status " + status);
            }
            var result = objectMapper.readValue(response.body(), OpenLibraryIsbnSearchResult.class);
            LOGGER.log(Level.FINEST, "Book search result: {0}", result);
            return new BookInformation(result.title());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "network error searching isbn {0}: {1}",
                    new Object[]{isbn.value(), e.getMessage()});
            throw new BookSearchException("failed to search book for isbn: " + isbn.value(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.SEVERE, "interrupted searching isbn {0}: {1}",
                    new Object[]{isbn.value(), e.getMessage()});
            throw new BookSearchException("failed to search book for isbn: " + isbn.value(), e);
        }
    }

    /**
     * Performs the GET, transparently following HTTP redirects. Open Library's
     * {@code /isbn/{isbn}.json} endpoint answers with a 302 to the canonical
     * {@code /books/{key}.json} location, which the client (configured with
     * {@code Redirect.NEVER}) does not follow on its own. The {@code requestUri}
     * is kept so a relative {@code Location} header can be resolved against it.
     */
    private HttpResponse<String> getFollowingRedirects(URI requestUri) throws IOException, InterruptedException {
        var response = get(requestUri);
        int redirects = 0;
        while (isRedirect(response.statusCode()) && redirects < MAX_REDIRECTS) {
            Optional<String> location = response.headers().firstValue("Location");
            if (location.isEmpty()) {
                return response;
            }
            redirects++;
            response = get(requestUri.resolve(location.get()));
        }
        return response;
    }

    private HttpResponse<String> get(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static boolean isRedirect(int status) {
        return status >= 300 && status < 400;
    }
}
