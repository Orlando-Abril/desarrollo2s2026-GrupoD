package com.example.demo.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyHasherTest {

    @Test
    void sha256HexConElMismoValorDeberiaDevolverElMismoHash() {
        String hash1 = ApiKeyHasher.sha256Hex("sk_raw-key-123");
        String hash2 = ApiKeyHasher.sha256Hex("sk_raw-key-123");

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void sha256HexDeberiaDevolverHexadecimalMinusculaDe64Caracteres() {
        String hash = ApiKeyHasher.sha256Hex("sk_raw-key-123");

        assertThat(hash).hasSize(64).matches("^[0-9a-f]{64}$");
    }

    @Test
    void sha256HexConValoresDistintosDeberiaDevolverHashesDistintos() {
        String hash1 = ApiKeyHasher.sha256Hex("sk_raw-key-123");
        String hash2 = ApiKeyHasher.sha256Hex("sk_raw-key-456");

        assertThat(hash1).isNotEqualTo(hash2);
    }
}
