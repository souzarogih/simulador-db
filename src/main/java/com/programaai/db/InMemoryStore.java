package com.programaai.db;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryStore {
    // tabela por entidade: Produto.class -> (id -> produto)
    private static final Map<Class<?>, Map<Object, Object>> TABLES = new ConcurrentHashMap<>();

    static Map<Object, Object> table(Class<?> entityClass) {
        return TABLES.computeIfAbsent(entityClass, k -> new ConcurrentHashMap<>());
    }

    static void clear(Class<?> entityClass) {
        table(entityClass).clear();
    }
}
