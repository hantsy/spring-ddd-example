package com.example.library.lending.application;

import com.example.library.shared.UseCase;
import com.example.library.lending.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.LocalDateTime;

@UseCase
public class RentBookUseCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(RentBookUseCase.class);
    private final LoanRepository loanRepository;
    private final CopyAvailabilityValidator copyAvailabilityValidator;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RentBookUseCase(LoanRepository loanRepository,
                           CopyAvailabilityValidator copyAvailabilityValidator,
                           ApplicationEventPublisher events,
                           Clock clock) {
        this.loanRepository = loanRepository;
        this.copyAvailabilityValidator = copyAvailabilityValidator;
        this.events = events;
        this.clock = clock;
    }

    public void execute(CopyId copyId, UserId userId) {
        copyAvailabilityValidator.checkAvailable(copyId);
        var now = LocalDateTime.now(clock);
        loanRepository.save(new Loan(copyId, userId, now));

        LOGGER.info("firing LoanCreated with copy id = {}", copyId);
        events.publishEvent(new LoanCreated(copyId));
    }
}
