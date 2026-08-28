package com.example.demo;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {

		Dotenv.configure()
				.ignoreIfMissing()
				.systemProperties()
				.load();

		SpringApplication.run(DemoApplication.class, args);
	}
}