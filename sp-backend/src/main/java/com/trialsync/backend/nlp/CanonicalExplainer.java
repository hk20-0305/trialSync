package com.trialsync.backend.nlp;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The deterministic explainer: {@code trialsync.nlp.chat.CanonicalExplainer}.
 *
 * <p>It answers entirely from the stored criterion evaluations, reciting the canonical explanations
 * the screening engine wrote at the time the result was saved. It contacts nothing, invents nothing
 * and has no model behind it, which is why it is both the default provider when no Groq key is
 * configured and the fallback when Groq fails mid-request.
 *
 * <p>Question routing, in the Python order - the first pattern that matches wins:
 *
 * <ol>
 *   <li>capability questions, answered with the fixed description of what this assistant does;
 *   <li>the refusal gate;
 *   <li>{@code missing|unknown|need(ed)} to the unknown criteria;
 *   <li>{@code fail(ed|ing)|ineligible} to the failed criteria;
 *   <li>{@code pass(ed)|eligible} to the passed criteria;
 *   <li>{@code why|result|summary|evidence|simpler|explain} to everything unresolved, or the first
 *       three evaluations when nothing is unresolved;
 *   <li>anything else, and any branch that selects no criteria, to
 *       {@code insufficient_evidence}.
 * </ol>
 */
public class CanonicalExplainer implements ScreeningChatProvider {

    private static final Pattern MISSING = Pattern.compile("\\b(missing|unknown|need(?:ed)?)\\b");
    private static final Pattern FAILING = Pattern.compile("\\b(fail(?:ed|ing)?|ineligible)\\b");
    private static final Pattern PASSING = Pattern.compile("\\b(pass(?:ed)?|eligible)\\b");
    private static final Pattern EXPLANATORY =
            Pattern.compile("\\b(why|result|summary|evidence|simpler|explain)\\b");

    @Override
    public String providerName() {
        return "canonical";
    }

    @Override
    public String modelId() {
        return "deterministic-canonical-1";
    }

    @Override
    public boolean enabled() {
        return true;
    }

    /** History is deliberately ignored: prior turns are never treated as evidence. */
    @Override
    public ChatAnswer answer(
            ScreeningChatContext context, List<ChatMessage> history, String message) {
        String question = Texts.casefold(message);

        if (ScreeningChatPolicy.isScreeningAssistantCapabilityQuestion(question)) {
            List<ChatEvaluation> first =
                    context.evaluations().stream().limit(1).collect(Collectors.toList());
            if (first.isEmpty()) {
                return new ChatAnswer(
                        ChatAnswer.INSUFFICIENT_EVIDENCE,
                        ScreeningChatPolicy.CAPABILITY_WITHOUT_EVALUATIONS_ANSWER,
                        ScreeningChatPolicy.contextualSuggestions(context));
            }
            return new ChatAnswer(
                    ChatAnswer.SUPPORTED,
                    ScreeningChatPolicy.CAPABILITY_ANSWER,
                    List.of(ScreeningChatPolicy.citationFor(first.get(0))),
                    ScreeningChatPolicy.contextualSuggestions(context));
        }

        if (ScreeningChatPolicy.mustRefuse(question)) {
            return new ChatAnswer(
                    ChatAnswer.REFUSED,
                    ScreeningChatPolicy.REFUSAL_ANSWER,
                    ScreeningChatPolicy.contextualSuggestions(context));
        }

        List<ChatEvaluation> selected;
        if (MISSING.matcher(question).find()) {
            selected = withResult(context, "unknown");
        } else if (FAILING.matcher(question).find()) {
            selected = withResult(context, "fail");
        } else if (PASSING.matcher(question).find()) {
            selected = withResult(context, "pass");
        } else if (EXPLANATORY.matcher(question).find()) {
            selected = new ArrayList<>();
            for (ChatEvaluation evaluation : context.evaluations()) {
                if ("unknown".equals(evaluation.result()) || "fail".equals(evaluation.result())) {
                    selected.add(evaluation);
                }
            }
            if (selected.isEmpty()) {
                // `... or list(context.evaluations[:3])`: a fully passing screening still gets a
                // grounded summary rather than a refusal.
                selected =
                        context.evaluations().stream().limit(3).collect(Collectors.toList());
            }
        } else {
            return insufficient(context);
        }

        if (selected.isEmpty()) {
            return insufficient(context);
        }

        List<ChatEvaluation> bounded =
                selected.stream()
                        .limit(ScreeningChatPolicy.MAX_CITATIONS)
                        .collect(Collectors.toList());
        String explanations =
                bounded.stream()
                        .map(item -> String.valueOf(item.canonicalExplanation()))
                        .collect(Collectors.joining(" "));
        List<Citation> citations = new ArrayList<>();
        for (ChatEvaluation evaluation : bounded) {
            citations.add(ScreeningChatPolicy.citationFor(evaluation));
        }
        String state = String.valueOf(context.overallState()).replace("_", " ");
        return new ChatAnswer(
                ChatAnswer.SUPPORTED,
                "The saved result is " + state + ". " + explanations,
                citations,
                ScreeningChatPolicy.contextualSuggestions(context));
    }

    private static List<ChatEvaluation> withResult(ScreeningChatContext context, String result) {
        List<ChatEvaluation> selected = new ArrayList<>();
        for (ChatEvaluation evaluation : context.evaluations()) {
            if (result.equals(evaluation.result())) {
                selected.add(evaluation);
            }
        }
        return selected;
    }

    private static ChatAnswer insufficient(ScreeningChatContext context) {
        return new ChatAnswer(
                ChatAnswer.INSUFFICIENT_EVIDENCE,
                ScreeningChatPolicy.SAFE_INSUFFICIENT_ANSWER,
                ScreeningChatPolicy.contextualSuggestions(context));
    }
}
