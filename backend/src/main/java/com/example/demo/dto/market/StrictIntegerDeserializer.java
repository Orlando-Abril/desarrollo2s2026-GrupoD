package com.example.demo.dto.market;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Acepta sólo enteros JSON. Jackson convierte por defecto {@code 1.5} en {@code 1} al leer un {@code Integer}; para la
 * cantidad de una orden eso aceptaría una orden distinta de la pedida (spec 027, research D11). Se aplica sólo a
 * {@link OrderRequest#quantity()}, sin cambiar la configuración global de Jackson.
 */
public class StrictIntegerDeserializer extends ValueDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            return parser.getIntValue();
        }
        return (Integer) context.handleUnexpectedToken(Integer.class, parser);
    }
}
