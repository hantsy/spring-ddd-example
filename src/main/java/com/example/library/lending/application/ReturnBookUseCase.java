package com.example.library.lending.application;

import com.example.library.common.UseCase;
import com.example.library.lending.domain.Loan;
import com.example.library.lending.domain.LoanClosed;
import com.example.library.lending.domain.LoanId;
import com.example.library.lending.domain.LoanRepository;
import org.springframework.context.ApplicationEventPublisher;

import java.util.logging.Level;
import java.util.logging.Logger;

@UseCase
public class ReturnBookUseCase {
    private static final Logger LOGGER = Logger.getLogger(ReturnBookUseCase.class.getName());
    private final LoanRepository loanRepository;
    private final ApplicationEventPublisher events;

    public ReturnBookUseCase(LoanRepository loanRepository,
                             ApplicationEventPublisher events) {
        this.loanRepository = loanRepository;
        this.events = events;
    }

    public void execute(LoanId loanId) {
        Loan loan = loanRepository.findByIdOrThrow(loanId);
        loan.returned();
        loanRepository.save(loan);

        LOGGER.log(Level.INFO, "firing returned event for loan with id = " + loanId);
        events.publishEvent(new LoanClosed(loan.copyId()));
    }
}
