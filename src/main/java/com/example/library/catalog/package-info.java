/**
 * The catalog module is explicitly allowed to depend on the lending module's
 * {@code domain} named interface only — the single sanctioned cross-context
 * coupling, used to observe the {@code LoanCreated}/{@code LoanClosed} events.
 * The shared kernel ({@code common}) is accessible to every module by default.
 */
@ApplicationModule(allowedDependencies = "lending :: domain")
package com.example.library.catalog;

import org.springframework.modulith.ApplicationModule;
