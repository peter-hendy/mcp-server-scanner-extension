package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

public final class IdFixtures {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final long GREGORIAN_OFFSET_100NS = 0x01b21dd213814000L;
    private static final long KSUID_EPOCH_SECONDS = 1_400_000_000L;

    private IdFixtures() {
    }

    public static String versionOneUuid(Instant at, int tick) {
        long timestamp = at.getEpochSecond() * 10_000_000L + at.getNano() / 100 + GREGORIAN_OFFSET_100NS + tick;
        long mostSignificant = (timestamp & 0xffffffffL) << 32 | (timestamp >>> 32 & 0xffffL) << 16
                | 0x1000L | timestamp >>> 48 & 0x0fffL;
        return new java.util.UUID(mostSignificant, 0x9c4e0242ac120002L).toString();
    }

    public static String versionSevenUuid(Instant at, int randA) {
        long mostSignificant = at.toEpochMilli() << 16 | 0x7000L | randA & 0x0fffL;
        long leastSignificant = RANDOM.nextLong() & 0x3fffffffffffffffL | 0x8000000000000000L;
        return new java.util.UUID(mostSignificant, leastSignificant).toString();
    }

    public static String ulid(Instant at, byte[] random, int increment) {
        BigInteger value = BigInteger.valueOf(at.toEpochMilli()).shiftLeft(80)
                .add(new BigInteger(1, random)).add(BigInteger.valueOf(increment));
        return encode(value, CROCKFORD, 26);
    }

    public static String ksuid(Instant at, byte[] payload) {
        BigInteger value = BigInteger.valueOf(at.getEpochSecond() - KSUID_EPOCH_SECONDS).shiftLeft(128)
                .add(new BigInteger(1, payload));
        return encode(value, BASE62, 27);
    }

    public static String objectId(Instant at, byte[] process, int counter) {
        return String.format("%08x", at.getEpochSecond()) + HexFormat.of().formatHex(process)
                + String.format("%06x", counter & 0xffffff);
    }

    public static String snowflake(long millisSinceEpoch, int worker, int sequence) {
        return Long.toString(millisSinceEpoch << 22 | (long) worker << 12 | sequence);
    }

    private static String encode(BigInteger value, String alphabet, int length) {
        StringBuilder encoded = new StringBuilder();
        BigInteger radix = BigInteger.valueOf(alphabet.length());
        BigInteger remaining = value;
        for (int position = 0; position < length; position++) {
            BigInteger[] division = remaining.divideAndRemainder(radix);
            encoded.append(alphabet.charAt(division[1].intValue()));
            remaining = division[0];
        }
        return encoded.reverse().toString();
    }
}
