package com.example.demo.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller de sólo-test: esta feature no agrega endpoints de negocio, así que
 * {@link ApiKeyAuthFilterTest} necesita una ruta protegida real contra la que verificar
 * el caso "200 con ApiKey válida". Vive en src/test para no formar parte del artefacto productivo.
 */
@RestController
class TestProtectedController {

    @GetMapping("/test/protected")
    String ok() {
        return "ok";
    }
}
