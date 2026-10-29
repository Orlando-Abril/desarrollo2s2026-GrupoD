package com.example.demo.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tests MockMvc del mercado (spec 027) con un contexto nuevo por clase. Todos los contextos de test comparten la
 * misma base H2 con {@code create-drop}: cuando un test con {@code @DirtiesContext} cierra su contexto, borra el
 * esquema, y un contexto cacheado que se reutilice después queda sin tablas. Un contexto nuevo vuelve a crear el
 * esquema al arrancar, así estos tests no dependen del orden de ejecución.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
public @interface MarketMockMvcTest {
}
