package com.example.library.lending.application;

import com.example.library.lending.domain.CopyId;
import com.example.library.lending.domain.CopyNotAvailableException;
import com.example.library.lending.domain.LoanRepository;
import org.springframework.stereotype.Component;

@Component
public class CopyAvailabilityValidator {
    private final LoanRepository loanRepository;

    public CopyAvailabilityValidator(LoanRepository loanRepository) {
        this.loanRepository = loanRepository;
    }

    public void checkAvailable(CopyId copyId) {
        if (!loanRepository.isAvailable(copyId)) {
            throw new CopyNotAvailableException(copyId);
        }
    }
}
