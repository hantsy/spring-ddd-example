/**
 * The lending domain package is exposed as a named interface so the catalog
 * context can observe the {@code LoanCreated}/{@code LoanClosed} events (and the
 * {@code CopyId} value object they carry) without accessing the rest of the
 * lending module's internals. This is the single sanctioned cross-context
 * coupling in the application.
 */
@NamedInterface("domain")
package com.example.library.lending.domain;

import org.springframework.modulith.NamedInterface;
