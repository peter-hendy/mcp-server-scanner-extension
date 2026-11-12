package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

final class IdFormat {

    private final String name;
    private final IdParser parser;
    private final int width;
    private final List<Slice> timestamp;
    private final IdClock clock;
    private final List<IdField> fields;
    private final boolean recognisedBySyntax;

    IdFormat(String name, IdParser parser, int width, List<Slice> timestamp, IdClock clock, List<IdField> fields,
             boolean recognisedBySyntax) {
        this.name = name;
        this.parser = parser;
        this.width = width;
        this.timestamp = List.copyOf(timestamp);
        this.clock = clock;
        this.fields = List.copyOf(fields);
        this.recognisedBySyntax = recognisedBySyntax;
    }

    String name() {
        return name;
    }

    IdClock clock() {
        return clock;
    }

    List<IdField> fields() {
        return fields;
    }

    int timestampWidth() {
        return timestamp.stream().mapToInt(Slice::width).sum();
    }

    boolean recognisedBySyntax() {
        return recognisedBySyntax;
    }

    Optional<DecodedId> decode(String id) {
        return parser.parse(id).map(value -> new DecodedId(timestampOf(value),
                fields.stream().map(field -> field.slice().of(value, width)).toList()));
    }

    private BigInteger timestampOf(BigInteger value) {
        return timestamp.stream().reduce(BigInteger.ZERO,
                (joined, slice) -> joined.shiftLeft(slice.width()).or(slice.of(value, width)), BigInteger::or);
    }
}
