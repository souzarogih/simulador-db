package com.programaai.db;

import java.util.List;
import java.util.Optional;

public interface DbRepository {
    Object save(Object entity);

    Optional<Object> findById(Object id);

    List<Object> findAll();

    boolean existsById(Object id);

    void deleteById(Object id);

    void deleteAll();
}
