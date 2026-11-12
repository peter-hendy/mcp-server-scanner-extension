package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class HostVariants implements AuthVariantGenerator {

    private static final String HOST = "Host";
    private static final int HTTPS_PORT = 443;
    private static final String RECONSTRUCTED_SAFE_CHARACTERS = "-_.~";

    @Override
    public List<AuthVariant> variantsFor(HttpRequest seed) {
        String authority = seed.headerValue(HOST);
        List<HeaderProfile> profiles = new ArrayList<>();
        profiles.add(new HeaderProfile("host.localhost", HOST, "localhost", "nearhost"));
        if (HeaderValues.simpleAuthority(authority)) {
            profiles.addAll(authorityProfiles(seed, authority));
        }
        return profiles.stream()
                .filter(HeaderProfile::safeAscii)
                .filter(profile -> preservesDestination(seed, profile))
                .map(profile -> profile.replacing(VariantFamily.HOST))
                .toList();
    }

    private static List<HeaderProfile> authorityProfiles(HttpRequest seed, String authority) {
        List<HeaderProfile> profiles = new ArrayList<>();
        if (servedOnHttpsPort(seed.httpService())) {
            String host = authority.split(":", 2)[0];
            profiles.add(new HeaderProfile("host.explicit-443", HOST, host + ":443", host + ":9443"));
        }
        String path = seed.pathWithoutQuery();
        if (!"/".equals(path) && HeaderValues.safeAscii(seed.path())) {
            String shape = reconstructedShape(path);
            profiles.add(new HeaderProfile("host.reconstructed", HOST,
                    authority + "/?x=1&shape=" + shape, authority + path + "?x=2&shape=" + shape));
        }
        return profiles;
    }

    private static boolean servedOnHttpsPort(HttpService service) {
        return service.secure() && service.port() == HTTPS_PORT;
    }

    private static boolean preservesDestination(HttpRequest seed, HeaderProfile profile) {
        return preservesDestination(seed, profile.candidateValue())
                && preservesDestination(seed, profile.controlValue());
    }

    private static boolean preservesDestination(HttpRequest seed, String value) {
        HttpRequest mutated = seed.withHeader(HOST, value);
        HttpService before = seed.httpService();
        HttpService after = mutated.httpService();
        return Objects.equals(before.host(), after.host()) && before.port() == after.port()
                && before.secure() == after.secure() && value.equals(mutated.headerValue(HOST));
    }

    private static String reconstructedShape(String path) {
        StringBuilder shape = new StringBuilder(RECONSTRUCTED_SAFE_CHARACTERS);
        path.chars().filter(character -> !asciiLetterOrDigit(character))
                .filter(character -> character != '/')
                .filter(character -> RECONSTRUCTED_SAFE_CHARACTERS.indexOf(character) < 0)
                .distinct()
                .forEach(shape::appendCodePoint);
        return shape.toString();
    }

    private static boolean asciiLetterOrDigit(int character) {
        return character >= 'a' && character <= 'z'
                || character >= 'A' && character <= 'Z'
                || character >= '0' && character <= '9';
    }
}
