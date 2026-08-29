package com.example.library.lending.domain;

import org.springframework.data.repository.CrudRepository;

public interface LoanRepository extends CrudRepository<Loan, LoanId> {

    boolean existsByCopyIdAndReturnedAtIsNull(CopyId copyId);

    default boolean isAvailable(CopyId id) {
        return !existsByCopyIdAndReturnedAtIsNull(id);
    }

    default Loan findByIdOrThrow(LoanId loanId) {
        return findById(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));
    }
}
