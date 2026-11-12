package com.mcpscanner.ui.access;

import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.identity.AnonymousComparison;

sealed interface AccessColumn {

    String title();

    record Item() implements AccessColumn {
        @Override
        public String title() {
            return "Item";
        }
    }

    record Identity(Principal principal) implements AccessColumn {
        @Override
        public String title() {
            return PrincipalLabels.column(principal);
        }
    }

    record Anonymous() implements AccessColumn {
        @Override
        public String title() {
            return AnonymousComparison.NAME;
        }
    }

    record Expectation() implements AccessColumn {
        @Override
        public String title() {
            return "Who should reach it";
        }
    }
}
