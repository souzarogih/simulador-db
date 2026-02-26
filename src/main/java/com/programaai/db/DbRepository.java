package com.programaai.db;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface DbRepository {
    Object save(Object entity);

    Optional<Object> findById(Object id);

    List<Object> findAll();

    boolean existsById(Object id);

    void deleteById(Object id);

    void deleteAll();

    Object update(Object id, Consumer<Object> updater);

    Optional<Object> findBy(String field, Object value);

    List<Object> findAllBy(String field, Object value);
}
