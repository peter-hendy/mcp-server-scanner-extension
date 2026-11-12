package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

final class StructuredIds {

    private final List<IdFormat> formats;

    StructuredIds(List<IdFormat> formats) {
        this.formats = List.copyOf(formats);
    }

    Optional<Judgement> judge(IdSample sample) {
        return formats.stream().map(format -> read(format, sample)).flatMap(Optional::stream).findFirst()
                .map(reading -> reading.judge(sample.size()));
    }

    private static Optional<FormatReading> read(IdFormat format, IdSample sample) {
        List<Optional<DecodedId>> decoded = sample.ids().stream().map(format::decode).toList();
        if (decoded.isEmpty() || !decoded.stream().allMatch(Optional::isPresent)) {
            return Optional.empty();
        }
        List<DecodedId> ids = decoded.stream().flatMap(Optional::stream).toList();
        List<BigInteger> timestamps = ids.stream().map(DecodedId::timestamp).toList();
        ClockReading clock = format.clock().read(timestamps, sample, format.timestampWidth());
        return clock.followsClock() || format.recognisedBySyntax()
                ? Optional.of(new FormatReading(format, ids, clock)) : Optional.empty();
    }
}
