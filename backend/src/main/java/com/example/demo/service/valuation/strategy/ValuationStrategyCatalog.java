package com.example.demo.service.valuation.strategy;

import com.example.demo.exception.UnknownValuationStrategyException;
import com.example.demo.model.ActiveStrategySetting;
import com.example.demo.repository.ActiveStrategySettingRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Catálogo de las estrategias de valuación definidas en código. {@link #all()} devuelve siempre todas: las dos
 * se calculan y guardan en cada corrida (feature 016). La estrategia activa sólo decide el precio de mercado.
 * <p>
 * Desde la spec 031 la activa puede cambiarse en ejecución: {@link #active()} lee primero la fila persistida
 * ({@link ActiveStrategySetting#getActiveCode()}) y, si no hay, usa {@code valuation.active-strategy} (por defecto
 * {@code OFFICIAL}, sobrescribible con {@code VALUATION_ACTIVE_STRATEGY} o {@code -Dvaluation.active-strategy}).
 * Un código inexistente, en la propiedad o persistido, impide arrancar la aplicación.
 */
@Component
public class ValuationStrategyCatalog implements SmartInitializingSingleton {

    private final List<ValuationStrategyDefinition> definitions;
    private final ValuationStrategyDefinition propertyDefault;
    private final Supplier<Optional<String>> persistedActiveCode;

    @Autowired
    public ValuationStrategyCatalog(@Value("${valuation.active-strategy:OFFICIAL}") String activeCode,
                                    ObjectProvider<ActiveStrategySettingRepository> settings) {
        this(List.of(OfficialStrategyV1.DEFINITION, MomentumStrategyV1.DEFINITION), activeCode,
                () -> Optional.ofNullable(settings.getIfAvailable())
                        .flatMap(repository -> repository.findById(ActiveStrategySetting.SINGLETON_ID))
                        .map(ActiveStrategySetting::getActiveCode));
    }

    public ValuationStrategyCatalog(String activeCode, ActiveStrategySettingRepository settings) {
        this(List.of(OfficialStrategyV1.DEFINITION, MomentumStrategyV1.DEFINITION), activeCode,
                () -> settings.findById(ActiveStrategySetting.SINGLETON_ID).map(ActiveStrategySetting::getActiveCode));
    }

    /** Catálogo sin persistencia: la activa es siempre la de la propiedad. */
    public ValuationStrategyCatalog(String activeCode) {
        this(List.of(OfficialStrategyV1.DEFINITION, MomentumStrategyV1.DEFINITION), activeCode, Optional::empty);
    }

    ValuationStrategyCatalog(List<ValuationStrategyDefinition> definitions, String activeCode) {
        this(definitions, activeCode, Optional::empty);
    }

    ValuationStrategyCatalog(List<ValuationStrategyDefinition> definitions, String activeCode,
                             Supplier<Optional<String>> persistedActiveCode) {
        this.definitions = List.copyOf(definitions);
        Set<String> codes = new HashSet<>();
        for (ValuationStrategyDefinition definition : this.definitions) {
            if (!codes.add(definition.code())) {
                throw new IllegalStateException("Código de estrategia duplicado en el catálogo: " + definition.code());
            }
        }
        this.propertyDefault = find(activeCode.trim());
        this.persistedActiveCode = persistedActiveCode;
    }

    public List<ValuationStrategyDefinition> all() {
        return definitions;
    }

    public ValuationStrategyDefinition find(String code) {
        return definitions.stream()
                .filter(definition -> definition.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new UnknownValuationStrategyException(code,
                        definitions.stream().map(ValuationStrategyDefinition::code).toList()));
    }

    /** Estrategia que fijará el precio de mercado en el próximo recálculo. */
    public ValuationStrategyDefinition active() {
        return persistedActiveCode.get().map(this::find).orElse(propertyDefault);
    }

    /** La única estrategia del catálogo distinta de la activa. */
    public ValuationStrategyDefinition alternative() {
        String active = active().code();
        List<ValuationStrategyDefinition> others = definitions.stream()
                .filter(definition -> !definition.code().equals(active))
                .toList();
        if (others.size() != 1) {
            throw new IllegalStateException("La alternativa requiere exactamente dos estrategias en el catálogo");
        }
        return others.get(0);
    }

    /** Estrategia de {@code valuation.active-strategy}, usada mientras no haya un cambio persistido. */
    public ValuationStrategyDefinition propertyDefault() {
        return propertyDefault;
    }

    /** Valida al arrancar que la estrategia persistida siga existiendo en el catálogo. */
    @Override
    public void afterSingletonsInstantiated() {
        active();
    }
}
