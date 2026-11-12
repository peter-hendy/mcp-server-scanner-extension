package com.mcpscanner.checks.catalog.siblingrouteauthparity;

import com.mcpscanner.checks.issue.IssueRating;
import com.mcpscanner.checks.shared.auth.CatalogAccess;

enum FindingKind {
    KNOWN_SIBLING(
            "MCP sibling route lists the catalog without authentication",
            CatalogAccess.ALTERNATE_ROUTE,
            "The MCP server refused the catalog listing without credentials on its main route, but a sibling "
                    + "route answered it anonymously. Any caller who can reach that route can list the server's "
                    + "catalog; whether its operations are also reachable was not tested.",
            "Attach the same authentication to every route that reaches the MCP handler, including legacy "
                    + "message endpoints and alias routes, or remove the routes that are not needed.") {
        @Override
        String lead(String mainRoute, String route) {
            return "The main MCP route " + mainRoute + " refused the request without credentials, but the "
                    + "sibling route " + route + " answered the same request without credentials.";
        }
    },
    ADVERTISED_MESSAGE_ROUTE(
            "MCP SSE message route lists the catalog without authentication",
            CatalogAccess.SESSION_CREDENTIAL,
            "The legacy SSE stream refused an anonymous client, but its advertised message route accepted a "
                    + "catalog listing without credentials when posted with the session id of an authenticated "
                    + "stream. Whoever holds a live session id can list the catalog without credentials.",
            "Authenticate every POST to the SSE message route, and bind each session to the principal that "
                    + "opened its stream, rather than treating the session id as the credential.") {
        @Override
        String lead(String mainRoute, String route) {
            return "The legacy SSE stream refused an anonymous client, but the message route it advertised, "
                    + route + ", accepted the request without credentials when it was posted with the session id "
                    + "of a stream the scanner had opened with credentials. A caller still needs a live session "
                    + "id from an authenticated stream.";
        }
    };

    private final String issueName;
    private final CatalogAccess access;
    private final String background;
    private final String remediation;

    FindingKind(String issueName, CatalogAccess access, String background, String remediation) {
        this.issueName = issueName;
        this.access = access;
        this.background = background;
        this.remediation = remediation;
    }

    abstract String lead(String mainRoute, String route);

    String issueName() {
        return issueName;
    }

    IssueRating rating() {
        return access.rating();
    }

    String background() {
        return background;
    }

    String remediation() {
        return remediation;
    }
}
