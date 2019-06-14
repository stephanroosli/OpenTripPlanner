package org.opentripplanner.index.transmodel.model.scalars;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseValueException;
import graphql.schema.GraphQLScalarType;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class LocalTimeScalarFactory {

    public static final String EXAMPLE_TIME = "18:25:SS";

    public static final String TIME_PATTERN = "HH:mm:SS";

    public static final String DATE_SCALAR_DESCRIPTION = "Time using the format: " + TIME_PATTERN + ". Example: " + EXAMPLE_TIME;

    private static DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(TIME_PATTERN);

    private LocalTimeScalarFactory() {
    }


    public static GraphQLScalarType createLocalTimeScalar() {
        return new GraphQLScalarType("LocalTime", DATE_SCALAR_DESCRIPTION, new Coercing() {
            @Override
            public String serialize(Object input) {
                if (input instanceof LocalTime) {
                    return FORMATTER.format((LocalTime) input);
                }
                return null;
            }

            @Override
            public LocalTime parseValue(Object input) {
                try {
                    return LocalTime.from(FORMATTER.parse((CharSequence) input));
                } catch (DateTimeParseException dtpe) {
                    throw new CoercingParseValueException("Expected type 'LocalTime' but was '" + input + "'.");
                }
            }

            @Override
            public LocalTime parseLiteral(Object input) {
                if (input instanceof StringValue) {
                    return parseValue(((StringValue) input).getValue());
                }
                return null;
            }
        });
    }
}
