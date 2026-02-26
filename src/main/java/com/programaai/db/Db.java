package com.programaai.db;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;

public final class Db {

    private Db() {}

    /**
     * Cria um repositório (Proxy) em memória.
     *
     * Ex:
     *   ProductRepository repo = Db.create(ProductRepository.class, Produto.class);
     */
    @SuppressWarnings("unchecked")
    public static <R> R create(Class<R> repositoryInterface, Class<?> entityClass) {
        if (repositoryInterface == null || !repositoryInterface.isInterface()) {
            throw new IllegalArgumentException("repositoryInterface precisa ser uma interface.");
        }
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass não pode ser null.");
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

            // métodos básicos do Object
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }

            String name = method.getName();
            Map<Object, Object> table = InMemoryStore.table(entityClass);

            // =========================
            // 1) CRUD básico
            // =========================

            if (name.equals("save")) {
                validateArgs(method, args, 1);
                Object entity = args[0];

                if (entity == null) {
                    throw new IllegalArgumentException("save(entity): entity não pode ser null.");
                }
                if (!entityClass.isAssignableFrom(entity.getClass())) {
                    throw new IllegalArgumentException(
                            "save(entity): esperado " + entityClass.getSimpleName()
                                    + " mas recebeu " + entity.getClass().getSimpleName()
                    );
                }

                Object id = idField.get(entity);
                if (id == null) {
                    throw new IllegalStateException(
                            "ID está null. Preencha o campo @" + DbId.class.getSimpleName()
                                    + " antes de salvar. Entidade: " + entityClass.getSimpleName()
                    );
                }

                table.put(id, entity);
                return entity;
            }

            if (name.equals("findById")) {
                validateArgs(method, args, 1);
                Object id = args[0];
                return Optional.ofNullable(table.get(id));
            }

            if (name.equals("findAll")) {
                return new ArrayList<>(table.values());
            }

            if (name.equals("existsById")) {
                validateArgs(method, args, 1);
                Object id = args[0];
                return table.containsKey(id);
            }

            if (name.equals("deleteById")) {
                validateArgs(method, args, 1);
                Object id = args[0];
                table.remove(id);
                return null;
            }

            if (name.equals("deleteAll")) {
                table.clear();
                return null;
            }

            // =========================
            // 2) Update por ID (✅ AJUSTADO)
            // =========================
            // repo.update("P001", obj -> ((Produto)obj).setPreco(99.9));
            if (name.equals("update")) {
                validateArgs(method, args, 2);
                Object id = args[0];
                Object updaterObj = args[1];

                if (!(updaterObj instanceof Consumer)) {
                    throw new IllegalArgumentException("update(id, updater): updater precisa ser Consumer.");
                }

                @SuppressWarnings("unchecked")
                Consumer<Object> updater = (Consumer<Object>) updaterObj;

                Object entity = table.get(id);
                if (entity == null) {
                    throw new NoSuchElementException("update: entidade não encontrada para id=" + id);
                }

                updater.accept(entity);

                // garante que continua salvo
                table.put(id, entity);
                return entity;
            }

            // =========================
            // 3) Busca genérica findBy(field, value)
            // =========================
            if (name.equals("findBy")) {
                validateArgs(method, args, 2);
                String fieldName = String.valueOf(args[0]);
                Object expectedValue = args[1];

                Field field = findField(entityClass, fieldName);
                field.setAccessible(true);

                for (Object entity : table.values()) {
                    Object fieldValue = field.get(entity);
                    if (Objects.equals(fieldValue, expectedValue)) {
                        return Optional.of(entity);
                    }
                }
                return Optional.empty();
            }

            // =========================
            // 4) Busca genérica findAllBy(field, value)
            // =========================
            if (name.equals("findAllBy")) {
                validateArgs(method, args, 2);
                String fieldName = String.valueOf(args[0]);
                Object expectedValue = args[1];

                Field field = findField(entityClass, fieldName);
                field.setAccessible(true);

                List<Object> result = new ArrayList<>();
                for (Object entity : table.values()) {
                    Object fieldValue = field.get(entity);
                    if (Objects.equals(fieldValue, expectedValue)) {
                        result.add(entity);
                    }
                }
                return result;
            }

            // =========================
            // 5) Queries custom via @DbQuery
            // =========================
            // @DbQuery(field="codigoProduto")
            // Optional<Produto> findByCode(String code);
            DbQuery dbQuery = method.getAnnotation(DbQuery.class);
            if (dbQuery != null) {
                validateArgs(method, args, 1);

                String fieldName = dbQuery.field();
                Field field = findField(entityClass, fieldName);
                field.setAccessible(true);

                Object expectedValue = args[0];

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
                            + ". Use CRUD do DbRepository, findBy/findAllBy/update ou @DbQuery."
            );
        }

        private static void validateArgs(Method method, Object[] args, int expectedSize) {
            int actual = (args == null) ? 0 : args.length;
            if (actual != expectedSize) {
                throw new IllegalArgumentException("Método " + method.getName()
                        + " espera " + expectedSize + " argumento(s), mas recebeu " + actual);
            }
        }

        private static Field findIdField(Class<?> entityClass) {
            // 1) procura @DbId
            for (Field f : entityClass.getDeclaredFields()) {
                if (f.isAnnotationPresent(DbId.class)) {
                    return f;
                }
            }

            // 2) fallback: tenta campo "id"
            try {
                return entityClass.getDeclaredField("id");
            } catch (NoSuchFieldException ignored) {}

            throw new IllegalStateException(
                    "Nenhum campo com @DbId encontrado em " + entityClass.getSimpleName()
                            + " (nem campo 'id')."
            );
        }

        private static Field findField(Class<?> entityClass, String fieldName) {
            try {
                return entityClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException(
                        "Campo '" + fieldName + "' não existe em " + entityClass.getSimpleName(), e
                );
            }
        }
    }
}