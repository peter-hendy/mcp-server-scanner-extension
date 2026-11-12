package com.mcpscanner.ui.completion;

import com.mcpscanner.client.CompletionOutcome;
import com.mcpscanner.client.CompletionReference;
import com.mcpscanner.client.CompletionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class ValueCompletionTest {

    private final FakeCompletionGateway gateway = new FakeCompletionGateway();
    private final RecordingCompletionDialogs dialogs = new RecordingCompletionDialogs();
    private final List<Boolean> ranInBackground = new ArrayList<>();
    private final ValueCompletion completion = new ValueCompletion(gateway, this::runRecordingBackground, dialogs);

    @Test
    void offersOneEnabledAskItemWhenTheServerCanBeAsked() {
        JMenuItem item = itemOf(completion.forPromptArguments("code_review").menuFor("language").orElseThrow());

        assertThat(item.getText()).isEqualTo("Ask server for values");
        assertThat(item.isEnabled()).isTrue();
        assertThat(item.getToolTipText()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = CompletionAvailability.class, names = {"DISCONNECTED", "NOT_ADVERTISED"})
    void theItemIsDisabledAndSaysWhyWhenTheServerCannotBeAsked(CompletionAvailability availability) {
        gateway.setAvailability(availability);

        JMenuItem item = itemOf(completion.forTemplateVariables("repo://{owner}").menuFor("owner").orElseThrow());

        assertThat(item.isEnabled()).isFalse();
        assertThat(item.getToolTipText()).isEqualTo(availability.unavailableReason());
    }

    @Test
    void anItemWithoutAUsableNameIsDisabledAsMalformed() {
        JMenuItem blankArgument = itemOf(completion.forPromptArguments("p").menuFor("").orElseThrow());
        JMenuItem missingTemplate = itemOf(completion.forTemplateVariables(null).menuFor("id").orElseThrow());

        assertThat(List.of(blankArgument, missingTemplate)).allSatisfy(item -> {
            assertThat(item.isEnabled()).isFalse();
            assertThat(item.getToolTipText())
                    .isEqualTo(CompletionAvailability.MALFORMED_ITEM.unavailableReason());
        });
    }

    @Test
    void buildingTheMenuSendsNothing() {
        completion.forPromptArguments("code_review").menuFor("language");
        completion.forTemplateVariables("repo://{owner}").menuFor("owner");

        assertThat(gateway.requests()).isEmpty();
        assertThat(dialogs.askedFor()).isEmpty();
    }

    @Test
    void clickingAsksForTheTypedValueThenSendsAPromptCompletionOffTheEdtAndShowsTheValues() {
        dialogs.typeValue("py");
        gateway.answerWith(request -> new CompletionOutcome.Values(List.of("python", "pytorch"), 2, false));
        JPopupMenu menu = completion.forPromptArguments("code_review").menuFor("language").orElseThrow();
        JPanel invoker = new JPanel();
        menu.setInvoker(invoker);

        itemOf(menu).doClick();

        assertThat(dialogs.askedFor()).containsExactly("language");
        assertThat(gateway.requests()).containsExactly(
                new CompletionRequest(new CompletionReference.Prompt("code_review"), "language", "py"));
        assertThat(ranInBackground).containsExactly(true);
        assertThat(dialogs.shown()).singleElement().satisfies(shown -> {
            assertThat(shown.parent()).isSameAs(invoker);
            assertThat(shown.title()).isEqualTo("Values for language");
            assertThat(shown.text()).startsWith("python\npytorch\n").contains("Shown: 2 of 2");
        });
    }

    @Test
    void aTemplateVariableIsAskedAboutByItsTemplate() {
        itemOf(completion.forTemplateVariables("repo://{owner}/{repo}").menuFor("repo").orElseThrow()).doClick();

        assertThat(gateway.requests()).containsExactly(
                new CompletionRequest(new CompletionReference.ResourceTemplate("repo://{owner}/{repo}"), "repo", ""));
    }

    @Test
    void cancellingTheValuePromptSendsNothing() {
        dialogs.cancelInput();

        itemOf(completion.forPromptArguments("code_review").menuFor("language").orElseThrow()).doClick();

        assertThat(gateway.requests()).isEmpty();
        assertThat(dialogs.shown()).isEmpty();
    }

    @Test
    void aFailureIsShownInsteadOfValues() {
        gateway.answerWith(request -> new CompletionOutcome.Failed("HTTP 500"));

        itemOf(completion.forPromptArguments("p").menuFor("a").orElseThrow()).doClick();

        assertThat(dialogs.shown()).singleElement()
                .satisfies(shown -> assertThat(shown.text()).isEqualTo("The server could not be asked: HTTP 500"));
    }

    @Test
    void anUnexpectedErrorIsShownAsAFailure() {
        gateway.answerWith(request -> {
            throw new IllegalStateException("boom");
        });

        itemOf(completion.forPromptArguments("p").menuFor("a").orElseThrow()).doClick();

        assertThat(dialogs.shown()).singleElement()
                .satisfies(shown -> assertThat(shown.text()).isEqualTo("The server could not be asked: boom"));
    }

    private void runRecordingBackground(Supplier<String> work, Consumer<String> showOnEdt) {
        ranInBackground.add(true);
        showOnEdt.accept(work.get());
    }

    private static JMenuItem itemOf(JPopupMenu menu) {
        assertThat(menu.getComponentCount()).isEqualTo(1);
        return (JMenuItem) menu.getComponent(0);
    }
}
