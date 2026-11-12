package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.time.Instant;
import java.util.List;

final class IdFormats {

    private static final String HEX = "0123456789ABCDEF";
    private static final String DECIMAL = "0123456789";
    private static final String CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final Instant GREGORIAN_EPOCH = Instant.parse("1582-10-15T00:00:00Z");
    private static final Instant KSUID_EPOCH = Instant.ofEpochSecond(1_400_000_000L);
    private static final long HUNDRED_NANOS = 100;
    private static final long MILLIS = 1_000_000L;
    private static final long SECONDS = 1_000_000_000L;
    private static final IdField CLOCK_SEQUENCE = new IdField("clock sequence", FieldRole.PAYLOAD, new Slice(66, 14));
    private static final IdField NODE = new IdField("node identifier", FieldRole.PAYLOAD, new Slice(80, 48));

    private IdFormats() {
    }

    static List<IdFormat> known() {
        return List.of(versionOneUuid(), versionSixUuid(), versionSevenUuid(), ulid(), ksuid(), objectId(),
                snowflake());
    }

    private static IdFormat versionOneUuid() {
        return new IdFormat("time-based version 1 UUID", new UuidParser('1'), 128,
                List.of(new Slice(52, 12), new Slice(32, 16), new Slice(0, 32)),
                new AbsoluteClock(GREGORIAN_EPOCH, HUNDRED_NANOS), List.of(CLOCK_SEQUENCE, NODE), true);
    }

    private static IdFormat versionSixUuid() {
        return new IdFormat("time-based version 6 UUID", new UuidParser('6'), 128,
                List.of(new Slice(0, 32), new Slice(32, 16), new Slice(52, 12)),
                new AbsoluteClock(GREGORIAN_EPOCH, HUNDRED_NANOS), List.of(CLOCK_SEQUENCE, NODE), true);
    }

    private static IdFormat versionSevenUuid() {
        return new IdFormat("version 7 UUID", new UuidParser('7'), 128, List.of(new Slice(0, 48)),
                new AbsoluteClock(Instant.EPOCH, MILLIS),
                List.of(new IdField("rand_a field", FieldRole.PAYLOAD, new Slice(52, 12)),
                        new IdField("rand_b field", FieldRole.PAYLOAD, new Slice(66, 62))), true);
    }

    private static IdFormat ulid() {
        return new IdFormat("ULID", new RadixParser(CROCKFORD_BASE32, true, 26, 26, 128), 128,
                List.of(new Slice(0, 48)), new AbsoluteClock(Instant.EPOCH, MILLIS),
                List.of(new IdField("random part", FieldRole.PAYLOAD, new Slice(48, 80))), false);
    }

    private static IdFormat ksuid() {
        return new IdFormat("KSUID", new RadixParser(BASE62, false, 27, 27, 160), 160, List.of(new Slice(0, 32)),
                new AbsoluteClock(KSUID_EPOCH, SECONDS),
                List.of(new IdField("payload", FieldRole.PAYLOAD, new Slice(32, 128))), false);
    }

    private static IdFormat objectId() {
        return new IdFormat("MongoDB ObjectId", new RadixParser(HEX, true, 24, 24, 96), 96,
                List.of(new Slice(0, 32)), new AbsoluteClock(Instant.EPOCH, SECONDS),
                List.of(new IdField("process-unique value", FieldRole.PAYLOAD, new Slice(32, 40)),
                        new IdField("counter", FieldRole.SEQUENCE, new Slice(72, 24))), false);
    }

    private static IdFormat snowflake() {
        return new IdFormat("Snowflake ID", new RadixParser(DECIMAL, false, 16, 19, 63), 64,
                List.of(new Slice(1, 41)), new RelativeClock(),
                List.of(new IdField("worker identifier", FieldRole.PAYLOAD, new Slice(42, 10)),
                        new IdField("sequence field", FieldRole.SEQUENCE, new Slice(52, 12))), false);
    }
}
