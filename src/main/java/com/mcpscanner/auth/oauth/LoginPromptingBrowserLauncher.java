package com.mcpscanner.auth.oauth;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class LoginPromptingBrowserLauncher implements BrowserLauncher {

    private static final String PROMPT = "prompt";
    private static final String LOGIN = "login";
    private static final String NONE = "none";
    private static final String ENCODED_SPACE = "%20";

    private final BrowserLauncher delegate;

    LoginPromptingBrowserLauncher(BrowserLauncher delegate) {
        this.delegate = delegate;
    }

    @Override
    public void open(URI uri) {
        delegate.open(withLoginPrompt(uri));
    }

    private static URI withLoginPrompt(URI uri) {
        String uriText = uri.toString();
        String fragment = uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment();
        String withoutFragment = uriText.substring(0, uriText.length() - fragment.length());
        int queryStart = withoutFragment.indexOf('?');
        String base = queryStart < 0 ? withoutFragment : withoutFragment.substring(0, queryStart);
        String query = queryStart < 0 ? "" : withoutFragment.substring(queryStart + 1);
        return URI.create(base + "?" + queryWithLoginPrompt(query) + fragment);
    }

    private static String queryWithLoginPrompt(String query) {
        Set<String> promptValues = new LinkedHashSet<>();
        String otherParameters = parameters(query)
                .filter(parameter -> !collectedPrompt(parameter, promptValues))
                .collect(Collectors.joining("&"));
        promptValues.remove(NONE);
        promptValues.add(LOGIN);
        return Stream.of(otherParameters, PROMPT + "=" + encoded(promptValues))
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining("&"));
    }

    private static Stream<String> parameters(String query) {
        return Arrays.stream(query.split("&")).filter(parameter -> !parameter.isEmpty());
    }

    private static boolean collectedPrompt(String parameter, Set<String> promptValues) {
        if (parameter.equals(PROMPT)) {
            return true;
        }
        if (!parameter.startsWith(PROMPT + "=")) {
            return false;
        }
        String decoded = URLDecoder.decode(parameter.substring(PROMPT.length() + 1), StandardCharsets.UTF_8);
        Arrays.stream(decoded.split(" ")).filter(value -> !value.isEmpty()).forEach(promptValues::add);
        return true;
    }

    private static String encoded(Set<String> promptValues) {
        return promptValues.stream()
                .map(value -> URLEncoder.encode(value, StandardCharsets.UTF_8))
                .collect(Collectors.joining(ENCODED_SPACE));
    }
}
