package com.example.library.lending.application;

import com.example.library.shared.UseCase;
import com.example.library.lending.domain.Loan;
import com.example.library.lending.domain.LoanClosed;
import com.example.library.lending.domain.LoanId;
import com.example.library.lending.domain.LoanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.LocalDateTime;

@UseCase
public class ReturnBookUseCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReturnBookUseCase.class);
    private final LoanRepository loanRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ReturnBookUseCase(LoanRepository loanRepository,
                             ApplicationEventPublisher events,
                             Clock clock) {
        this.loanRepository = loanRepository;
        this.events = events;
        this.clock = clock;
    }

    public void execute(LoanId loanId) {
        Loan loan = loanRepository.findByIdOrThrow(loanId);
        loan.returned(LocalDateTime.now(clock));
        loanRepository.save(loan);

        LOGGER.info("firing returned event for loan with id = {}", loanId);
        events.publishEvent(new LoanClosed(loan.copyId()));
    }
}
