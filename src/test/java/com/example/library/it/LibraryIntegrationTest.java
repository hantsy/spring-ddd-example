package com.example.library.it;

import com.example.library.catalog.domain.BarCode;
import com.example.library.catalog.domain.Book;
import com.example.library.catalog.domain.BookRepository;
import com.example.library.catalog.domain.Copy;
import com.example.library.catalog.domain.CopyId;
import com.example.library.catalog.domain.CopyRepository;
import com.example.library.catalog.domain.Isbn;
import com.example.library.lending.application.RentBookUseCase;
import com.example.library.lending.application.ReturnBookUseCase;
import com.example.library.lending.domain.Loan;
import com.example.library.lending.domain.LoanId;
import com.example.library.lending.domain.LoanRepository;
import com.example.library.lending.domain.OverdueFee;
import com.example.library.lending.domain.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end integration test of the library use cases, backed by an in-memory
 * H2 database and exercising the cross-context domain events (the catalog observes
 * lending's {@code LoanCreated}/{@code LoanClosed} to keep copy availability in
 * sync, asynchronously after commit).
 */
@SpringBootTest
public class LibraryIntegrationTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private CopyRepository copyRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private RentBookUseCase rentBookUseCase;

    @Autowired
    private ReturnBookUseCase returnBookUseCase;

    private static <T> List<T> toList(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).toList();
    }

    @Test
    public void testLibraryCrud() {
        CopyId copyId = new CopyId();

        // Add a new Book
        Book book = new Book("Effective Java", new Isbn("9780134685991"));
        bookRepository.save(book);

        // Add some copies of the book
        Copy copy1 = new Copy(copyId, book.getId(), new BarCode("BC001"));
        Copy copy2 = new Copy(book.getId(), new BarCode("BC002"));
        copyRepository.save(copy1);
        copyRepository.save(copy2);

        // verify all copies
        assertThat(toList(copyRepository.findAll())).hasSize(2);

        UserId userId = new UserId();
        // Rent a book
        rentBookUseCase.execute(com.example.library.lending.domain.CopyId.of(copyId.id()), userId);

        // Verify that the book is NOT available (event handled asynchronously)
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var copyOptional = copyRepository.findById(copyId);
            assertThat(copyOptional).isPresent();
            assertThat(copyOptional.get().isAvailable()).isFalse();
        });

        // rent again should throw exception
        assertThrows(Exception.class,
                () -> rentBookUseCase.execute(com.example.library.lending.domain.CopyId.of(copyId.id()), userId));

        // verify ONLY one loan record
        var allLoans = toList(loanRepository.findAll());
        assertThat(allLoans).hasSize(1);

        // Retrieve Loan
        Loan loan = loanRepository.findByIdOrThrow(allLoans.getFirst().id());
        assertThat(loan.copyId().id()).isEqualTo(copyId.id());

        // Return the book
        returnBookUseCase.execute(loan.id());

        // Verify that the book is now available (event handled asynchronously)
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var returnedCopyOptional = copyRepository.findById(copyId);
            assertThat(returnedCopyOptional).isPresent();
            assertThat(returnedCopyOptional.get().isAvailable()).isTrue();
        });
    }

    @Test
    public void testOverdueReturn() {
        CopyId copyId = new CopyId();
        Book book = new Book("Domain-Driven Design", new Isbn("9780321125217"));
        bookRepository.save(book);
        copyRepository.save(new Copy(copyId, book.getId(), new BarCode("BC003")));

        var overdueLoanIdHolder = new AtomicReference<LoanId>();
        UserId userId = new UserId();

        // Create a loan with an expected return date 35 days in the past
        var pastDate = LocalDate.now().minusDays(35);
        var loan = new Loan(
                com.example.library.lending.domain.CopyId.of(copyId.id()),
                userId,
                LocalDateTime.now().minusDays(35),
                pastDate);
        loanRepository.save(loan);
        overdueLoanIdHolder.set(loan.id());

        // Return the book — should trigger overdue fee
        returnBookUseCase.execute(loan.id());

        var returned = loanRepository.findByIdOrThrow(overdueLoanIdHolder.get());
        assertThat(returned.returnedAt()).isNotNull();
        assertThat(returned.overdueFee()).isEqualTo(OverdueFee.BEYOND_A_MONTH.amount());
    }
}
