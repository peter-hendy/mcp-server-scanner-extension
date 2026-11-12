package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class SessionIdGuessability {

    private final IdPattern repeated;
    private final StructuredIds structured;
    private final List<IdPattern> unstructured;

    SessionIdGuessability(IdPattern repeated, StructuredIds structured, List<IdPattern> unstructured) {
        this.repeated = repeated;
        this.structured = structured;
        this.unstructured = List.copyOf(unstructured);
    }

    public static SessionIdGuessability standard() {
        return new SessionIdGuessability(new RepeatedIds(), new StructuredIds(IdFormats.known()),
                List.of(new IncrementingIds(), new LowEntropyIds()));
    }

    public IdAssessment assess(IdSample sample) {
        List<Judgement> judgements = Stream.concat(Stream.of(repeated.judge(sample)), shapeJudgements(sample))
                .toList();
        List<String> weaknesses = judgements.stream().map(Judgement::weakness).flatMap(Optional::stream).toList();
        return new IdAssessment(weaknesses,
                weaknesses.isEmpty() && judgements.stream().anyMatch(Judgement::isUndecided));
    }

    private Stream<Judgement> shapeJudgements(IdSample sample) {
        return structured.judge(sample).map(Stream::of)
                .orElseGet(() -> unstructured.stream().map(pattern -> pattern.judge(sample)));
    }
}
