package com.example.demo.audit;

import com.example.demo.dto.auth.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.validation.BeanPropertyBindingResult;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ParameterSanitizerTest {

    private final ParameterSanitizer sanitizer = new ParameterSanitizer(JsonMapper.builder().build());

    @Test
    void masksPasswordInsideRequestBody() {
        String params = sanitizer.sanitize(new String[]{"request"},
                new Object[]{new LoginRequest("ana", "S3creta!")});

        assertThat(params).isEqualTo("{\"request\":{\"username\":\"ana\",\"password\":\"***\"}}");
    }

    @Test
    void masksSensitiveParameterNameEvenWhenNull() {
        assertThat(sanitizer.sanitize(new String[]{"refreshToken"}, new Object[]{"abc.def"}))
                .isEqualTo("{\"refreshToken\":\"***\"}");
        assertThat(sanitizer.sanitize(new String[]{"refreshToken"}, new Object[]{null}))
                .isEqualTo("{\"refreshToken\":\"***\"}");
    }

    @Test
    void masksNestedSensitiveFieldsInRecordsListsAndMaps() {
        record Credentials(String user, String accessToken, String newPassword) {
        }
        record Wrapper(Credentials credentials, List<Credentials> history, Map<String, String> extra) {
        }
        Credentials credentials = new Credentials("ana", "tok-1", "pw-1");
        Wrapper wrapper = new Wrapper(credentials, List.of(credentials), Map.of("apiToken", "tok-2"));

        String params = sanitizer.sanitize(new String[]{"body"}, new Object[]{wrapper});

        assertThat(params)
                .contains("\"user\":\"ana\"")
                .contains("\"accessToken\":\"***\"")
                .contains("\"newPassword\":\"***\"")
                .contains("\"apiToken\":\"***\"")
                .doesNotContain("tok-1", "tok-2", "pw-1");
    }

    @Test
    void truncatesLongValuesTo200CharactersAndMarksThem() {
        String longValue = "x".repeat(300);

        String params = sanitizer.sanitize(new String[]{"team"}, new Object[]{longValue});

        String expected = ("\"" + "x".repeat(300) + "\"").substring(0, ParameterSanitizer.MAX_VALUE_LENGTH)
                + ParameterSanitizer.TRUNCATED_MARK;
        assertThat(params).isEqualTo("{\"team\":\"" + expected.replace("\"", "\\\"") + "\"}");
    }

    @Test
    void omitsContainerAndSecurityArguments() {
        Object[] args = {
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                new BeanPropertyBindingResult(new Object(), "target"),
                new UsernamePasswordAuthenticationToken("ana", null, List.of()),
                new MockHttpSession(),
                new MockMultipartFile("file", new byte[]{1}),
                new ByteArrayInputStream(new byte[]{1}),
                42L
        };
        String[] names = {"request", "response", "bindingResult", "authentication", "session", "file", "input", "id"};

        assertThat(sanitizer.sanitize(names, args)).isEqualTo("{\"id\":42}");
    }

    @Test
    void reportsNullValuesAndEmptySignatures() {
        assertThat(sanitizer.sanitize(new String[]{"league", "team"}, new Object[]{"PREMIER_LEAGUE", null}))
                .isEqualTo("{\"league\":\"PREMIER_LEAGUE\",\"team\":null}");
        assertThat(sanitizer.sanitize(new String[]{}, new Object[]{})).isEqualTo("{}");
    }

    @Test
    void reportsUnserializableArgumentsWithoutThrowing() {
        assertThat(sanitizer.sanitize(new String[]{"value"}, new Object[]{new Exploding()}))
                .isEqualTo("{\"value\":\"<unserializable:Exploding>\"}");
    }

    @Test
    void fallsBackToPositionalNamesWhenNamesAreMissing() {
        assertThat(sanitizer.sanitize(null, new Object[]{1, "a"})).isEqualTo("{\"arg0\":1,\"arg1\":\"a\"}");
    }

    static class Exploding {
        public String getValue() {
            throw new IllegalStateException("boom");
        }
    }
}
