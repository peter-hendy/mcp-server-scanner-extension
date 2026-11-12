package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SessionIdGuessabilityTest {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final SessionIdGuessability guessability = SessionIdGuessability.standard();

    @Test
    void randomVersionFourUuidsAreNotGuessable() {
        assertThat(weaknesses(ids(index -> UUID.randomUUID().toString()))).isEmpty();
    }

    @Test
    void randomHexTokensAreNotGuessable() {
        assertThat(weaknesses(ids(index -> randomHex(16)))).isEmpty();
    }

    @Test
    void randomBase64UrlTokensAreNotGuessable() {
        assertThat(weaknesses(ids(index -> Base64.getUrlEncoder().withoutPadding()
                .encodeToString(randomBytes(16))))).isEmpty();
    }

    @Test
    void twoOrThreeRandomSixtyFourBitIdsAreNotJudgedOnEntropy() {
        for (int sample = 0; sample < 50; sample++) {
            assertThat(weaknesses(List.of(randomHex(8), randomHex(8)))).isEmpty();
            assertThat(weaknesses(List.of(randomHex(8), randomHex(8), randomHex(8)))).isEmpty();
        }
    }

    @Test
    void aRepeatedIdIsGuessable() {
        String reused = UUID.randomUUID().toString();
        assertThat(weaknesses(List.of(reused, UUID.randomUUID().toString(), reused)))
                .singleElement().asString().contains("same session ID");
    }

    @Test
    void counterIdsAreGuessable() {
        assertThat(weaknesses(ids(index -> String.format("session-%06d", index + 9))))
                .anySatisfy(reason -> assertThat(reason).contains("increase"));
    }

    @Test
    void millisecondTimestampIdsAreGuessable() {
        long start = 1_762_700_000_000L;
        assertThat(weaknesses(ids(index -> Long.toString(start + index * 37L))))
                .anySatisfy(reason -> assertThat(reason).contains("increase"));
    }

    @Test
    void hexCounterIdsAreGuessable() {
        assertThat(weaknesses(ids(index -> "a1b2" + Long.toHexString(0xfffaL + index))))
                .anySatisfy(reason -> assertThat(reason).contains("increase"));
    }

    @Test
    void timeBasedUuidsAreGuessable() {
        assertThat(weaknesses(ids(SessionIdGuessabilityTest::versionOneUuid)))
                .anySatisfy(reason -> assertThat(reason).contains("version 1"));
    }

    @Test
    void shortRandomIdsHaveTooLittleEntropy() {
        assertThat(weaknesses(ids(index -> randomHex(3))))
                .anySatisfy(reason -> assertThat(reason).contains("bits"));
    }

    @Test
    void increasingButLargeStepsAreNotCounters() {
        List<String> sorted = IntStream.range(0, 8).mapToObj(index -> RANDOM.nextLong() >>> 24).sorted()
                .map(String::valueOf).toList();
        assertThat(weaknesses(sorted)).noneSatisfy(reason -> assertThat(reason).contains("increase"));
    }

    @Test
    void versionSixUuidsAreTimeBased() {
        assertThat(weaknesses(ids(index -> clockUuid('6'))))
                .anySatisfy(reason -> assertThat(reason).contains("time-based"));
    }

    @Test
    void versionOneUuidsWithIrregularTimeFieldsAreTimeBased() {
        assertThat(weaknesses(ids(index -> clockUuid('1'))))
                .anySatisfy(reason -> assertThat(reason).contains("time-based"))
                .noneSatisfy(reason -> assertThat(reason).contains("increase"));
    }

    @Test
    void fiftySixRandomBitsAreTooFewAndSixtyFourAreEnough() {
        assertThat(weaknesses(ids(index -> randomHex(7))))
                .anySatisfy(reason -> assertThat(reason).contains("bits"));
        assertThat(weaknesses(ids(index -> randomHex(8)))).isEmpty();
    }

    @Test
    void decreasingOrIrregularNumbersAreNotCounters() {
        assertThat(weaknesses(List.of("9000000000000000000001", "1000000000000000000002",
                "5000000000000000000003"))).noneSatisfy(reason -> assertThat(reason).contains("increase"));
    }

    @Test
    void versionOneUuidsIssuedNowNameTheIssueTime() {
        assertThat(weaknesses(ids(index -> IdFixtures.versionOneUuid(Instant.now(), index))))
                .singleElement().asString()
                .contains("time-based version 1 UUID").contains("time the session was issued");
    }

    @Test
    void randomVersionSevenUuidsAreNotGuessable() {
        assertThat(guessability.assess(sample(ids(index -> IdFixtures.versionSevenUuid(Instant.now(),
                RANDOM.nextInt(4096))))).weaknesses()).isEmpty();
    }

    @Test
    void aVersionSevenCounterIsUndecidedInEightIdsAndReportedInThirty() {
        Instant now = Instant.now();
        int start = RANDOM.nextInt(1000);
        assertThat(guessability.assess(sample(ids(index -> IdFixtures.versionSevenUuid(now, start + index)))))
                .satisfies(assessment -> {
                    assertThat(assessment.weaknesses()).isEmpty();
                    assertThat(assessment.undecided()).isTrue();
                });
        assertThat(guessability.assess(sample(ids(30, index -> IdFixtures.versionSevenUuid(now, start + index))))
                .weaknesses()).singleElement().asString()
                .contains("version 7 UUID").contains("counter").doesNotContain("at most 0");
    }

    @Test
    void monotonicUlidsAreACounterNotZeroRandomBits() {
        Instant now = Instant.now();
        byte[] random = randomBytes(10);
        assertThat(guessability.assess(sample(ids(index -> IdFixtures.ulid(now, random, index)))).undecided())
                .isTrue();
        assertThat(guessability.assess(sample(ids(30, index -> IdFixtures.ulid(now, random, index)))).weaknesses())
                .singleElement().asString().contains("ULID").contains("counter").doesNotContain("bits that vary");
    }

    @Test
    void randomUlidsAreNotGuessable() {
        assertThat(weaknesses(ids(index -> IdFixtures.ulid(Instant.now(), randomBytes(10), 0)))).isEmpty();
    }

    @Test
    void ksuidsAreNotGuessable() {
        assertThat(guessability.assess(sample(ids(index -> IdFixtures.ksuid(Instant.now(), randomBytes(16)))))
                .weaknesses()).isEmpty();
    }

    @Test
    void mongoObjectIdsAreGuessable() {
        Instant now = Instant.now();
        byte[] process = randomBytes(5);
        int counter = RANDOM.nextInt(1 << 20);
        assertThat(weaknesses(ids(index -> IdFixtures.objectId(now, process, counter + index))))
                .singleElement().asString().contains("MongoDB ObjectId").contains("sequence number")
                .contains("same in every ID");
    }

    @Test
    void randomTwentyFourHexTokensAreNotObjectIds() {
        assertThat(weaknesses(ids(index -> randomHex(12)))).isEmpty();
    }

    @Test
    void snowflakeIdsAreGuessable() {
        long epoch = 1_288_834_974_657L;
        long millis = System.currentTimeMillis() - epoch;
        assertThat(weaknesses(ids(index -> IdFixtures.snowflake(millis + index * 3L, 17, index))))
                .singleElement().asString().contains("Snowflake").contains("advances with the clock");
    }

    @Test
    void randomNineteenDigitNumbersAreNotSnowflakes() {
        assertThat(weaknesses(ids(index -> Long.toString(1_000_000_000_000_000_000L
                + Math.floorMod(RANDOM.nextLong(), 8_000_000_000_000_000_000L)))))
                .noneSatisfy(reason -> assertThat(reason).contains("Snowflake"));
    }

    @Test
    void base64EncodedTextIsJudgedOnItsDecodedBits() {
        assertThat(weaknesses(ids(index -> Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("sid:" + randomHex(4) + ":v1").getBytes()))))
                .anySatisfy(reason -> assertThat(reason).contains("bits"));
    }

    @Test
    void sixtyTwoRandomBitsAreUndecidedInEightIdsAndWeakInThirty() {
        assertThat(guessability.assess(sample(everyBitVarying(8)))).satisfies(assessment -> {
            assertThat(assessment.weaknesses()).isEmpty();
            assertThat(assessment.undecided()).isTrue();
        });
        assertThat(guessability.assess(sample(ids(30, index -> sixtyTwoBitHex()))).weaknesses())
                .anySatisfy(reason -> assertThat(reason).contains("bits"));
    }

    @Test
    void sixtyFourRandomBitsInThirtyIdsAreDecidedSound() {
        assertThat(guessability.assess(sample(ids(30, index -> randomHex(8))))).satisfies(assessment -> {
            assertThat(assessment.weaknesses()).isEmpty();
            assertThat(assessment.undecided()).isFalse();
        });
    }

    @Test
    void clearlyRandomIdsAreNotUndecided() {
        assertThat(guessability.assess(sample(ids(index -> UUID.randomUUID().toString()))).undecided()).isFalse();
    }

    private List<String> weaknesses(List<String> ids) {
        return guessability.assess(sample(ids)).weaknesses();
    }

    private static IdSample sample(List<String> ids) {
        Instant now = Instant.now();
        return new IdSample(ids, now.minusSeconds(1), now.plusSeconds(1));
    }

    private static List<String> ids(IntFunction<String> id) {
        return ids(8, id);
    }

    private static List<String> ids(int count, IntFunction<String> id) {
        return IntStream.range(0, count).mapToObj(id).toList();
    }

    private static List<String> everyBitVarying(int count) {
        while (true) {
            List<String> ids = ids(count, index -> sixtyTwoBitHex());
            long first = Long.parseUnsignedLong(ids.get(0), 16);
            long varying = ids.stream().mapToLong(id -> Long.parseUnsignedLong(id, 16) ^ first)
                    .reduce(0, (left, right) -> left | right);
            if (Long.bitCount(varying) == 62) {
                return ids;
            }
        }
    }

    private static String sixtyTwoBitHex() {
        return Integer.toHexString(RANDOM.nextInt(4)) + randomHex(8).substring(1);
    }

    private static String randomHex(int bytes) {
        return HexFormat.of().formatHex(randomBytes(bytes));
    }

    private static byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String clockUuid(char version) {
        return String.format("%s-%s-%c%s-9c4e-0242ac120002", randomHex(4), randomHex(2), version,
                randomHex(2).substring(1));
    }

    private static String versionOneUuid(int index) {
        return String.format("%08x-1d0b-11f1-9c4e-0242ac120002", 0x5e3a7b10 + index * 7);
    }
}
