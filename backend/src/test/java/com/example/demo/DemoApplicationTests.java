package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class DemoApplicationTests {
	@Autowired
	ApplicationContext context;

	@Test
	void contextLoads() {
		// Verifica que el contexto de Spring levanta; no requiere aserciones.
	}

	@Test
	void architectureFixturesAreNotSpringBeans() {
		assertFalse(context.containsBean("wrongName"));
		assertFalse(context.containsBean("misplacedAdvice"));
		assertFalse(context.containsBean("indirectSpringData"));
		assertFalse(context.containsBean("wrongSuffixSpringData"));
	}

}
