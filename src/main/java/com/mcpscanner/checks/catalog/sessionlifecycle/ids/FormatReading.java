package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

final class FormatReading {

    private static final int MINIMUM_SAME_TICK_PAIRS = 2;
    private static final int LARGEST_COUNTER_STEP_BITS = 20;

    private final IdFormat format;
    private final List<DecodedId> ids;
    private final ClockReading clock;

    FormatReading(IdFormat format, List<DecodedId> ids, ClockReading clock) {
        this.format = format;
        this.ids = List.copyOf(ids);
        this.clock = clock;
    }

    Judgement judge(int samples) {
        Tally tally = new Tally();
        readTimestamp(tally);
        IntStream.range(0, format.fields().size()).forEach(index -> readField(index, tally));
        Judgement bits = bitsJudgement(tally, samples);
        if (bits.verdict() == Judgement.Verdict.WEAK || !tally.counterFound) {
            return bits;
        }
        return samples >= IdSample.DECISIVE_SIZE ? Judgement.weak(counterWeakness(tally)) : Judgement.undecided();
    }

    private Judgement bitsJudgement(Tally tally, int samples) {
        if (tally.upperBound < EntropyThreshold.MINIMUM_BITS) {
            return Judgement.weak(weakness(tally, "at most " + tally.upperBound));
        }
        return EntropyThreshold.countedBits(tally.counted, samples, weakness(tally, "about " + tally.counted));
    }

    private void readTimestamp(Tally tally) {
        tally.sentences.add(clock.description());
        if (!clock.followsClock()) {
            tally.addVarying(timestamps(), format.timestampWidth());
        }
    }

    private void readField(int index, Tally tally) {
        IdField field = format.fields().get(index);
        List<BigInteger> values = ids.stream().map(id -> id.fieldValues().get(index)).toList();
        String described = "Its " + field.width() + "-bit " + field.name();
        if (field.role() == FieldRole.SEQUENCE) {
            tally.sentences.add(described + " is a sequence number.");
        } else if (VaryingBits.count(values) == 0) {
            tally.sentences.add(described + " was the same in every ID.");
        } else if (counter(values, field.width())) {
            tally.addCounter(described + " increases by small steps between IDs that share a timestamp, so it "
                    + "is a counter: an ID issued in the same tick as a known ID can be predicted from it.",
                    field.width());
        } else {
            tally.addVarying(values, field.width());
        }
    }

    private boolean counter(List<BigInteger> values, int width) {
        List<BigInteger> timestamps = timestamps();
        BigInteger largestStep = BigInteger.ONE.shiftLeft(Math.min(LARGEST_COUNTER_STEP_BITS, Math.max(1, width / 2)));
        List<BigInteger> steps = IntStream.range(1, values.size())
                .filter(index -> timestamps.get(index).equals(timestamps.get(index - 1)))
                .mapToObj(index -> values.get(index).subtract(values.get(index - 1))).toList();
        return steps.size() >= MINIMUM_SAME_TICK_PAIRS
                && steps.stream().allMatch(step -> step.signum() > 0 && step.compareTo(largestStep) <= 0);
    }

    private List<BigInteger> timestamps() {
        return ids.stream().map(DecodedId::timestamp).toList();
    }

    private String counterWeakness(Tally tally) {
        return "Every session ID is a " + format.name() + ". " + String.join(" ", tally.sentences);
    }

    private String weakness(Tally tally, String bits) {
        return "Every session ID is a " + format.name() + ". " + String.join(" ", tally.sentences) + " That leaves "
                + bits + " bits that vary unpredictably, below the " + EntropyThreshold.MINIMUM_BITS
                + " bits a session ID needs.";
    }

    private static final class Tally {

        private final List<String> sentences = new ArrayList<>();
        private int counted;
        private int upperBound;
        private boolean counterFound;

        void addCounter(String sentence, int width) {
            sentences.add(sentence);
            counterFound = true;
            counted += width;
            upperBound += width;
        }

        void addVarying(List<BigInteger> values, int width) {
            int varying = VaryingBits.count(values);
            counted += varying;
            upperBound += varying == 0 ? 0 : width;
        }
    }
}
