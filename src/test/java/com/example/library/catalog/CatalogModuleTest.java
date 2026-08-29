package com.example.library.catalog;

import com.example.library.catalog.domain.BarCode;
import com.example.library.catalog.domain.BookId;
import com.example.library.catalog.domain.Copy;
import com.example.library.catalog.domain.CopyId;
import com.example.library.catalog.domain.CopyRepository;
import com.example.library.lending.domain.LoanClosed;
import com.example.library.lending.domain.LoanCreated;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Module-scoped scenarios for the catalog bounded context, booted in isolation
 * via {@link ApplicationModuleTest}. Verifies that the catalog observes the
 * lending domain events ({@code LoanCreated}/{@code LoanClosed}) and keeps the
 * copy availability in sync.
 */
@ApplicationModuleTest
class CatalogModuleTest {

    @Autowired
    private CopyRepository copyRepository;

    @Test
    void loanCreatedMakesCopyUnavailable(Scenario scenario) {
        var copyId = new CopyId();
        copyRepository.save(new Copy(copyId, new BookId(), new BarCode("BC001")));

        scenario.publish(new LoanCreated(com.example.library.lending.domain.CopyId.of(copyId.id())))
                .andWaitForStateChange(
                        () -> copyRepository.findById(copyId).map(Copy::isAvailable).orElse(true),
                        available -> !available)
                .andVerify(available -> assertThat(available).isFalse());
    }

    @Test
    void loanClosedMakesCopyAvailable(Scenario scenario) {
        var copyId = new CopyId();
        var copy = new Copy(copyId, new BookId(), new BarCode("BC001"));
        copy.makeUnavailable();
        copyRepository.save(copy);

        scenario.publish(new LoanClosed(com.example.library.lending.domain.CopyId.of(copyId.id())))
                .andWaitForStateChange(
                        () -> copyRepository.findById(copyId).map(Copy::isAvailable).orElse(false),
                        available -> available)
                .andVerify(available -> assertThat(available).isTrue());
    }
}
