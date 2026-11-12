package com.mcpscanner.checks.catalog.filetraversal;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RootBoundaryProbe {

    private static final Pattern URI_SCHEME = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*://)");
    private static final String SIBLING_SEPARATOR = "_";
    private static final String MISSING_CHILD = "/missing";

    private final PayloadSender sender;

    RootBoundaryProbe(PayloadSender sender) {
        this.sender = sender;
    }

    Optional<Finding> run(String marker) {
        String form = uriSchemeOf(sender.baseValue());
        String controlValue = form + "/" + marker + MISSING_CHILD;
        HttpRequestResponse control = sender.send(controlValue);
        return FilesystemMessages.deniedRoot(TraversalReply.of(control).joinedText(), marker)
                .flatMap(root -> probeSibling(form, root, marker, controlValue, control));
    }

    private Optional<Finding> probeSibling(String form, String root, String marker, String controlValue,
                                           HttpRequestResponse control) {
        String siblingDirectory = root + SIBLING_SEPARATOR + marker;
        String siblingValue = form + siblingDirectory + MISSING_CHILD;
        HttpRequestResponse sibling = sender.send(siblingValue);
        if (!FilesystemMessages.reportsMissing(TraversalReply.of(sibling).joinedText(), siblingDirectory)) {
            return Optional.empty();
        }
        return Optional.of(new Finding(FindingKind.ROOT_BOUNDARY_BYPASS, List.of(
                "Outside-root control " + controlValue + " was denied and the denial named the root " + root + ".",
                "Prefix-sharing sibling " + siblingValue + " was not denied; the server reported it missing."),
                List.of(control, sibling)));
    }

    private static String uriSchemeOf(String baseValue) {
        Matcher matcher = URI_SCHEME.matcher(baseValue == null ? "" : baseValue);
        return matcher.find() ? matcher.group(1) : "";
    }
}
