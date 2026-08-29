package com.example.library.catalog.infrastructure;

import com.example.library.catalog.domain.BookInformation;
import com.example.library.catalog.domain.BookNotFoundException;
import com.example.library.catalog.domain.BookSearchException;
import com.example.library.catalog.domain.BookSearchService;
import com.example.library.catalog.domain.Isbn;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Open Library adapter implementing the {@link BookSearchService} domain port,
 * backed by Spring's {@link RestClient} and Jackson 3.
 */
@Component
public class OpenLibraryBookSearchService implements BookSearchService {
    private static final Logger LOGGER = Logger.getLogger(OpenLibraryBookSearchService.class.getName());

    /** The default Open Library API base URL. */
    public static final String DEFAULT_BASE_URL = "https://openlibrary.org/";

    private final RestClient restClient;

    public OpenLibraryBookSearchService() {
        this(DEFAULT_BASE_URL);
    }

    /**
     * Points the adapter at a custom base URL instead of the real Open Library
     * API. Intended for tests that mock the endpoint (e.g. with WireMock), where
     * the base URL is the WireMock server address.
     */
    OpenLibraryBookSearchService(String baseUrl) {
        var objectMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                // follow the 302 that Open Library's /isbn/{isbn}.json endpoint
                // answers with, without manual redirect handling
                .requestFactory(new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()))
                .messageConverters(converters -> converters.add(
                        new JacksonJsonHttpMessageConverter(objectMapper)))
                .build();
    }

    public BookInformation search(Isbn isbn) {
        try {
            var result = restClient.get()
                    .uri("/isbn/{isbn}.json", isbn.value())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(OpenLibraryIsbnSearchResult.class);
            LOGGER.log(Level.FINEST, "Book search result: {0}", result);
            return new BookInformation(result.title());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new BookNotFoundException(isbn);
            }
            LOGGER.log(Level.WARNING, "OpenLibrary returned unexpected status {0} for isbn {1}",
                    new Object[]{e.getStatusCode().value(), isbn.value()});
            throw new BookSearchException(
                    "failed to search book, upstream returned status " + e.getStatusCode().value());
        } catch (RestClientException e) {
            LOGGER.log(Level.SEVERE, "network error searching isbn {0}: {1}",
                    new Object[]{isbn.value(), e.getMessage()});
            throw new BookSearchException("failed to search book for isbn: " + isbn.value(), e);
        }
    }
}
