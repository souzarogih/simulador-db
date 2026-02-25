package com.programaai.db;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DbQuery {
    String field(); // nome do atributo no model
}
