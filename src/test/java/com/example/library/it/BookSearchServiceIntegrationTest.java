package com.example.library.it;

import com.example.library.catalog.domain.BookInformation;
import com.example.library.catalog.domain.BookNotFoundException;
import com.example.library.catalog.domain.BookSearchService;
import com.example.library.catalog.domain.Isbn;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the {@link BookSearchService} domain port against the real
 * Open Library API. It is tagged {@code integration} and excluded from the default
 * build because it requires outbound network access to {@code https://openlibrary.org/}.
 * Run it manually with {@code mvn test -Dgroups=integration}.
 */
@SpringBootTest
@Tag("integration")
public class BookSearchServiceIntegrationTest {

    @Autowired
    private BookSearchService bookSearchService;

    @Test
    public void searchEffectiveJavaIsbnReturnsBookInformation() {
        BookInformation result = bookSearchService.search(new Isbn("9780134685991"));

        assertThat(result.title()).isEqualTo("Effective Java");
    }

    @Test
    public void searchUnknownIsbnThrowsBookNotFoundException() {
        // 978-0-99999999-8 is a checksum-valid ISBN in an unallocated range:
        // Open Library returns 404, which the adapter maps to BookNotFoundException.
        assertThatThrownBy(() -> bookSearchService.search(new Isbn("9780999999998")))
                .isInstanceOf(BookNotFoundException.class)
                .hasMessageContaining("9780999999998");
    }
}
