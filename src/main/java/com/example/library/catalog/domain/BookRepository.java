package com.example.library.catalog.domain;

import org.springframework.data.repository.CrudRepository;

public interface BookRepository extends CrudRepository<Book, BookId> {

    boolean existsByIsbn(Isbn isbn);
}
