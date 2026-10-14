package com.example.demo.service.valuation.strategy;

import com.example.demo.model.Position;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Protege las versiones publicadas de las estrategias (contracts/valuation-strategies.md §5):
 * <ol>
 *   <li>Una versión publicada (con huella en {@link #PUBLISHED}) no se modifica nunca: ni pesos, ni baseValue,
 *       ni scaleFactor, ni maxVariation, ni type, ni momentum.</li>
 *   <li>Para cambiar algo se crea una clase nueva con la versión siguiente, el catálogo pasa a exponerla y se
 *       agrega su huella a {@link #PUBLISHED}; la entrada de la versión anterior queda como registro histórico.</li>
 *   <li>Las cotizaciones guardadas conservan su versión, así que el historial sigue explicando cada precio.</li>
 *   <li>Formato numérico ({@code 0.10} → {@code 0.1}), orden de los enums y comentarios no alteran la huella.</li>
 * </ol>
 */
class PublishedStrategyVersionsTest {

    private static final Map<String, String> PUBLISHED = Map.of(
            "OFFICIAL:1", "85bf5b11888f28c21126279303776db25be43da1c922022369da91245dad04ed",
            "MOMENTUM:1", "0b5e92f6aaa9c33a7ea98d7204ef89ecafd4d63ae0043f4ee268dbdfac9245a8");

    @Test
    void catalogDefinitionsMatchTheirPublishedFingerprints() {
        List<String> problems = check(new ValuationStrategyCatalog("OFFICIAL").all(), PUBLISHED);

        assertThat(problems).as(String.join("\n", problems)).isEmpty();
    }

    @Test
    void changingAPublishedVersionWithoutBumpingItFails() {
        ValuationStrategyDefinition changed = withMidfielderGoals(OfficialStrategyV1.DEFINITION, 1, "0.26");

        assertThat(check(List.of(changed), PUBLISHED))
                .singleElement().asString().contains("OFFICIAL v1 ya está publicada");
    }

    @Test
    void aNewVersionWithItsFingerprintRegisteredPassesAndKeepsThePreviousOne() {
        ValuationStrategyDefinition v2 = withMidfielderGoals(OfficialStrategyV1.DEFINITION, 2, "0.26");
        Map<String, String> published = new HashMap<>(PUBLISHED);
        published.put("OFFICIAL:2", v2.fingerprint());

        assertThat(check(List.of(v2), published)).isEmpty();
        assertThat(published.get("OFFICIAL:1"))
                .isEqualTo(PUBLISHED.get("OFFICIAL:1"))
                .isEqualTo(OfficialStrategyV1.DEFINITION.fingerprint());
    }

    @Test
    void aNewVersionWithoutRegisteredFingerprintFails() {
        ValuationStrategyDefinition v2 = withMidfielderGoals(OfficialStrategyV1.DEFINITION, 2, "0.26");

        assertThat(check(List.of(v2), PUBLISHED))
                .singleElement().asString().contains("OFFICIAL v2 no tiene huella publicada");
    }

    static List<String> check(Collection<ValuationStrategyDefinition> definitions, Map<String, String> published) {
        List<String> problems = new ArrayList<>();
        for (ValuationStrategyDefinition definition : definitions) {
            String code = definition.code();
            int version = definition.version();
            String actual = definition.fingerprint();
            String expected = published.get(code + ":" + version);
            if (expected == null) {
                problems.add(code + " v" + version + " no tiene huella publicada: agregá \"" + code + ":" + version
                        + "\" -> \"" + actual + "\" a PUBLISHED.");
            } else if (!expected.equals(actual)) {
                problems.add(code + " v" + version + " ya está publicada y su contenido cambió (huella esperada "
                        + expected + ", actual " + actual + "). No modifiques una versión publicada: creá " + code
                        + " v" + (version + 1) + " con los nuevos valores y registrá su huella en PUBLISHED.");
            }
        }
        return problems;
    }

    private static ValuationStrategyDefinition withMidfielderGoals(ValuationStrategyDefinition base, int version,
                                                                   String goals) {
        Map<Position, Map<Metric, BigDecimal>> weights = new EnumMap<>(Position.class);
        base.weights().forEach((position, row) -> weights.put(position, new EnumMap<>(row)));
        weights.get(Position.MIDFIELDER).put(Metric.GOALS, new BigDecimal(goals));
        return new ValuationStrategyDefinition(base.code(), version, base.type(), base.baseValue(),
                base.scaleFactor(), base.maxVariation(), weights, base.momentum());
    }
}
