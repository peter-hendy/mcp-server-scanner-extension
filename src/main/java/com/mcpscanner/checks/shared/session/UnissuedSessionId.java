package com.mcpscanner.checks.shared.session;

import com.mcpscanner.checks.shared.random.RandomHex;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class UnissuedSessionId {

    private static final String PREFIX = "invalid-";
    private static final int RANDOM_BYTES = 16;
    private static final int DRAWS = 64;
    private static final long ENUMERABLE = 4096;
    private static final List<String> CLASSES = List.of("0123456789", "abcdef", "ABCDEF",
            "ghijklmnopqrstuvwxyz", "GHIJKLMNOPQRSTUVWXYZ");
    private static final SecureRandom RANDOM = new SecureRandom();

    private UnissuedSessionId() {
    }

    public static String from(String canary) {
        return PREFIX + canary;
    }

    public static String random() {
        return from(RandomHex.of(RANDOM_BYTES));
    }

    public static String resembling(List<String> issued, Set<String> reserved) {
        Set<String> taken = new HashSet<>(issued);
        taken.addAll(reserved);
        List<String> observed = observedPerPosition(issued);
        return drawNotIn(taken, observed)
                .orElseGet(() -> drawNotIn(taken, widened(observed)).orElseGet(UnissuedSessionId::random));
    }

    private static List<String> observedPerPosition(List<String> issued) {
        String template = issued.get(0);
        return IntStream.range(0, template.length())
                .mapToObj(position -> issued.stream().filter(id -> id.length() > position)
                        .map(id -> String.valueOf(id.charAt(position))).distinct().collect(Collectors.joining()))
                .toList();
    }

    private static List<String> widened(List<String> observed) {
        boolean anyVaries = observed.stream().anyMatch(alphabet -> alphabet.length() > 1);
        IntPredicate widen = position -> !anyVaries || observed.get(position).length() > 1;
        return IntStream.range(0, observed.size())
                .mapToObj(position -> widen.test(position) ? classOf(observed.get(position)) : observed.get(position))
                .toList();
    }

    private static String classOf(String observed) {
        String classes = CLASSES.stream()
                .filter(alphabet -> observed.chars().anyMatch(character -> alphabet.indexOf(character) >= 0))
                .collect(Collectors.joining());
        return classes.isEmpty() ? observed : classes;
    }

    private static Optional<String> drawNotIn(Set<String> taken, List<String> alphabets) {
        if (alphabets.stream().allMatch(alphabet -> alphabet.length() == 1)) {
            return Optional.empty();
        }
        long space = candidateSpace(alphabets);
        return space <= ENUMERABLE ? anyUntaken(taken, alphabets, (int) space) : sampled(taken, alphabets);
    }

    private static long candidateSpace(List<String> alphabets) {
        long space = 1;
        for (String alphabet : alphabets) {
            space *= alphabet.length();
            if (space > ENUMERABLE) {
                return space;
            }
        }
        return space;
    }

    private static Optional<String> anyUntaken(Set<String> taken, List<String> alphabets, int space) {
        List<String> untaken = IntStream.range(0, space).mapToObj(index -> candidate(alphabets, index))
                .filter(candidate -> !taken.contains(candidate)).toList();
        return untaken.isEmpty() ? Optional.empty() : Optional.of(untaken.get(RANDOM.nextInt(untaken.size())));
    }

    private static String candidate(List<String> alphabets, int index) {
        StringBuilder control = new StringBuilder(alphabets.size());
        int remaining = index;
        for (String alphabet : alphabets) {
            control.append(alphabet.charAt(remaining % alphabet.length()));
            remaining /= alphabet.length();
        }
        return control.toString();
    }

    private static Optional<String> sampled(Set<String> taken, List<String> alphabets) {
        return IntStream.range(0, DRAWS).mapToObj(attempt -> draw(alphabets))
                .filter(candidate -> !taken.contains(candidate)).findFirst();
    }

    private static String draw(List<String> alphabets) {
        StringBuilder control = new StringBuilder(alphabets.size());
        alphabets.forEach(alphabet -> control.append(alphabet.charAt(RANDOM.nextInt(alphabet.length()))));
        return control.toString();
    }
}
