package com.example.library.lending;

import com.example.library.lending.application.RentBookUseCase;
import com.example.library.lending.application.ReturnBookUseCase;
import com.example.library.lending.domain.CopyId;
import com.example.library.lending.domain.Loan;
import com.example.library.lending.domain.LoanClosed;
import com.example.library.lending.domain.LoanCreated;
import com.example.library.lending.domain.LoanRepository;
import com.example.library.lending.domain.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Module-scoped scenarios for the lending bounded context, booted in isolation
 * via {@link ApplicationModuleTest} (the catalog module and its beans are not
 * loaded). Verifies that the use cases publish the expected domain events.
 */
@ApplicationModuleTest
class LendingModuleTest {

    @Autowired
    private RentBookUseCase rentBookUseCase;

    @Autowired
    private ReturnBookUseCase returnBookUseCase;

    @Autowired
    private LoanRepository loanRepository;

    @Test
    void rentBookPublishesLoanCreated(Scenario scenario) {
        var copyId = CopyId.of(UUID.randomUUID());
        var userId = new UserId();

        scenario.stimulate(() -> rentBookUseCase.execute(copyId, userId))
                .andWaitForEventOfType(LoanCreated.class)
                .toArriveAndVerify(event -> assertThat(event.copyId()).isEqualTo(copyId));
    }

    @Test
    void returnBookPublishesLoanClosed(Scenario scenario) {
        var copyId = CopyId.of(UUID.randomUUID());
        var userId = new UserId();
        var loan = loanRepository.save(new Loan(copyId, userId));

        scenario.stimulate(() -> returnBookUseCase.execute(loan.id()))
                .andWaitForEventOfType(LoanClosed.class)
                .toArriveAndVerify(event -> assertThat(event.copyId()).isEqualTo(copyId));
    }
}
