package com.example.library.catalog.application;

import com.example.library.catalog.domain.Copy;
import com.example.library.catalog.domain.CopyId;
import com.example.library.catalog.domain.CopyNotFoundException;
import com.example.library.catalog.domain.CopyRepository;
import com.example.library.lending.domain.LoanClosed;
import com.example.library.lending.domain.LoanCreated;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reacts to lending domain events to keep the catalog copy availability in sync.
 * This is the single sanctioned cross-context coupling: the catalog observes
 * {@link LoanCreated}/{@link LoanClosed} via Spring Modulith's
 * {@link ApplicationModuleListener}, which runs asynchronously after the lending
 * transaction commits.
 */
@Component
public class DomainEventListener {
    private static final Logger LOGGER = Logger.getLogger(DomainEventListener.class.getName());
    private final CopyRepository copyRepository;

    public DomainEventListener(CopyRepository copyRepository) {
        this.copyRepository = copyRepository;
    }

    @ApplicationModuleListener
    public void onLoanCreated(LoanCreated event) {
        LOGGER.log(Level.INFO, "handling LoanCreated:{0}", new Object[]{event});
        var copyId = new CopyId(event.copyId().id());
        Copy copy = copyRepository.findById(copyId).orElseThrow(() -> new CopyNotFoundException(copyId));
        copy.makeUnavailable();
        copyRepository.save(copy);
    }

    @ApplicationModuleListener
    public void onLoanClosed(LoanClosed event) {
        LOGGER.log(Level.INFO, "handling LoanClosed:{0}", new Object[]{event});
        var copyId = new CopyId(event.copyId().id());
        Copy copy = copyRepository.findById(copyId).orElseThrow(() -> new CopyNotFoundException(copyId));
        copy.makeAvailable();
        copyRepository.save(copy);
    }
}
