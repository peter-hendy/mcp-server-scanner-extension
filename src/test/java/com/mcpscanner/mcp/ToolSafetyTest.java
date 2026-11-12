package com.mcpscanner.mcp;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ToolSafetyTest {

    @Test
    void minesAToolAnnotatedReadOnlyAndNotDestructive() {
        assertThat(ToolSafety.minable(tool("campaigns", annotations(true, null)), Set.of())).isTrue();
        assertThat(ToolSafety.minable(tool("campaigns", annotations(true, false)), Set.of())).isTrue();
    }

    @Test
    void neverMinesAReadOnlyToolThatIsAlsoMarkedDestructive() {
        assertThat(ToolSafety.minable(tool("get_campaign", annotations(true, true)), Set.of())).isFalse();
    }

    @Test
    void stillExcludesAToolAnnotatedReadOnlyButNamedLikeAWrite() {
        assertThat(ToolSafety.minable(tool("delete_campaign", annotations(true, null)), Set.of())).isFalse();
        assertThat(ToolSafety.minable(tool("updateCampaign", annotations(true, false)), Set.of())).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"get_campaign", "list-users", "searchDocs", "read_note", "fetch_page", "find_user",
            "describe_table", "show_order", "view_invoice", "query_logs", "count_items", "lookup_customer",
            "GetCampaign", "list"})
    void minesAnUnannotatedToolWhoseFirstNameTokenIsAReadVerb(String name) {
        assertThat(ToolSafety.minable(tool(name, ToolAnnotations.EMPTY), Set.of())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"campaigns_get", "crm_list", "user_get", "notesSearch", "orders-count",
            "crm_target_lists_list"})
    void minesAnUnannotatedToolWhoseLastNameTokenIsAReadVerb(String name) {
        assertThat(ToolSafety.minable(tool(name, ToolAnnotations.EMPTY), Set.of())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"get_or_create_user", "list_and_delete", "campaigns_update_get", "get_send_status",
            "delete_then_list", "set_and_get"})
    void neverMinesAnUnannotatedReadVerbToolThatAlsoNamesAWriteVerb(String name) {
        assertThat(ToolSafety.minable(tool(name, ToolAnnotations.EMPTY), Set.of())).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"campaigns", "campaigns_export", "export_report", "whoami", "delete_project",
            "getaway", "crm_list_target_lists", "user_get_profile"})
    void neverMinesAnUnannotatedToolWithoutALeadingOrTrailingReadVerb(String name) {
        assertThat(ToolSafety.minable(tool(name, ToolAnnotations.EMPTY), Set.of())).isFalse();
    }

    @Test
    void readOnlyFalseOrDestructiveExcludesEvenAReadVerbName() {
        assertThat(ToolSafety.minable(tool("get_campaign", annotations(false, null)), Set.of())).isFalse();
        assertThat(ToolSafety.minable(tool("get_campaign", annotations(null, true)), Set.of())).isFalse();
    }

    @Test
    void anOptInAllowsAnyToolByName() {
        Set<String> optIn = Set.of("delete_project", "get_campaign");

        assertThat(ToolSafety.minable(tool("delete_project", annotations(false, true)), optIn)).isTrue();
        assertThat(ToolSafety.minable(tool("get_campaign", annotations(null, true)), optIn)).isTrue();
    }

    @Test
    void needsOptInExactlyForTheToolsNotMinedWithoutOne() {
        List<McpToolDefinition> tools = List.of(tool("get_a", ToolAnnotations.EMPTY), tool("a_export",
                ToolAnnotations.EMPTY), tool("b", annotations(true, null)), tool("c", annotations(true, true)));

        assertThat(tools.stream().filter(ToolSafety::needsOptIn).map(McpToolDefinition::name))
                .containsExactly("a_export", "c");
    }

    @Test
    void mayWriteUnlessExplicitlyReadOnly() {
        assertThat(ToolSafety.mayWrite(tool("delete_a", annotations(true, null)))).isFalse();
        assertThat(ToolSafety.mayWrite(tool("get_a", ToolAnnotations.EMPTY))).isTrue();
        assertThat(ToolSafety.mayWrite(tool("get_a", annotations(true, true)))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"delete_note", "updateCampaign", "send-email", "campaigns.create", "set_status",
            "reload_vault", "append_note", "copy_note", "enqueue_capture", "end_session", "replace_file",
            "lock_account", "unlock_door", "ban_user", "block_user", "close_issue", "accept_invite",
            "reject_request", "confirm_order", "approve_pr", "share_doc", "transition_issue", "process_payment",
            "checkout_cart", "deactivate_user", "activate_user", "assign_ticket", "revoke_token", "grant_role",
            "merge_branch", "publish_post", "cancel_order", "archive_repo", "restore_backup", "submit_form",
            "invite_member"})
    void namesSuggestingAWrite(String name) {
        assertThat(ToolSafety.nameSuggestsWrite(tool(name, ToolAnnotations.EMPTY))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"campaigns_get", "listNotes", "search_orders", "get_settings", "read_email",
            "list_processes", "get_blocks", "list_shares"})
    void namesNotSuggestingAWrite(String name) {
        assertThat(ToolSafety.nameSuggestsWrite(tool(name, ToolAnnotations.EMPTY))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"get_and_lock", "list_then_close", "approve_get", "get_share"})
    void neverMinesAReadVerbToolNamingANewWriteVerb(String name) {
        assertThat(ToolSafety.minable(tool(name, ToolAnnotations.EMPTY), Set.of())).isFalse();
    }

    private static McpToolDefinition tool(String name, ToolAnnotations annotations) {
        return new McpToolDefinition(name, "", "{\"type\":\"object\"}", List.of(), annotations);
    }

    private static ToolAnnotations annotations(Boolean readOnly, Boolean destructive) {
        return new ToolAnnotations(null, readOnly, destructive, null, null);
    }
}
