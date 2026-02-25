package com.programaai.db;

import java.lang.reflect.*;
import java.util.*;

public class Db {
    private Db() {}

    /**
     * Cria um repositório (proxy) em memória.
     *
     * Ex:
     *   ProductRepository repo = Db.create(ProductRepository.class, Produto.class);
     */
    @SuppressWarnings("unchecked")
    public static <R> R create(Class<R> repositoryInterface, Class<?> entityClass) {
        if (!repositoryInterface.isInterface()) {
            throw new IllegalArgumentException("Repository precisa ser interface.");
        }

        InvocationHandler handler = new RepositoryInvocationHandler(entityClass);

        return (R) Proxy.newProxyInstance(
                repositoryInterface.getClassLoader(),
                new Class<?>[]{ repositoryInterface },
                handler
        );
    }

    private static final class RepositoryInvocationHandler implements InvocationHandler {
        private final Class<?> entityClass;
        private final Field idField;

        RepositoryInvocationHandler(Class<?> entityClass) {
            this.entityClass = entityClass;
            this.idField = findIdField(entityClass);
            this.idField.setAccessible(true);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

            // toString/hashCode/equals básicos
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }

            String name = method.getName();
            Map<Object, Object> table = InMemoryStore.table(entityClass);

            // ------------------------
            // CRUD do DbRepository
            // ------------------------
            if (name.equals("save")) {
                Object entity = args[0];
                Object id = idField.get(entity);
                if (id == null) {
                    throw new IllegalStateException("ID está null. Preencha o campo @" + DbId.class.getSimpleName()
                            + " antes de salvar. Classe: " + entityClass.getSimpleName());
                }
                table.put(id, entity);
                return entity;
            }

            if (name.equals("findById")) {
                Object id = args[0];
                return Optional.ofNullable(table.get(id));
            }

            if (name.equals("findAll")) {
                return new ArrayList<>(table.values());
            }

            if (name.equals("existsById")) {
                Object id = args[0];
                return table.containsKey(id);
            }

            if (name.equals("deleteById")) {
                Object id = args[0];
                table.remove(id);
                return null;
            }

            if (name.equals("deleteAll")) {
                table.clear();
                return null;
            }

            // ------------------------
            // Query custom via @DbQuery
            // ------------------------
            DbQuery dbQuery = method.getAnnotation(DbQuery.class);
            if (dbQuery != null) {
                String fieldName = dbQuery.field();
                Field field = entityClass.getDeclaredField(fieldName);
                field.setAccessible(true);

                Object expectedValue = args[0];

                // suporte básico para Optional<Entidade>
                for (Object entity : table.values()) {
                    Object fieldValue = field.get(entity);
                    if (Objects.equals(fieldValue, expectedValue)) {
                        return Optional.of(entity);
                    }
                }
                return Optional.empty();
            }

            throw new UnsupportedOperationException(
                    "Método não suportado pelo simulator-db: " + method.getName()
                            + ". Use CRUD do DbRepository ou anote com @DbQuery."
            );
        }

        private static Field findIdField(Class<?> entityClass) {
            // 1) procura @DbId
            for (Field f : entityClass.getDeclaredFields()) {
                if (f.isAnnotationPresent(DbId.class)) {
                    return f;
                }
            }
            // 2) fallback: tenta "id"
            try {
                return entityClass.getDeclaredField("id");
            } catch (NoSuchFieldException ignored) {}

            throw new IllegalStateException(
                    "Nenhum campo com @DbId encontrado em " + entityClass.getSimpleName()
                            + " (nem campo 'id')."
            );
        }
    }
}
