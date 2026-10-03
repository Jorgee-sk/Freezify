package com.freezify.ai.internal;

import java.util.Map;
import java.util.Optional;

/**
 * A language model that answers with JSON following a schema. Implementations only transport: they neither
 * build prompts nor trust the answer, which {@link DefaultAiService} validates.
 */
interface AiProvider {

    /** @return the JSON text the model answered, or empty when the call failed */
    Optional<String> complete(StructuredPrompt prompt);

    /**
     * @param instructions what the model has to do
     * @param input        the data it works on; never instructions, even if it reads like them
     * @param schemaName   a short name for the schema, which some providers require
     * @param schema       the JSON schema the answer must follow
     */
    record StructuredPrompt(String instructions, String input, String schemaName, Map<String, Object> schema) {}
}

/** Used when no model is configured: the application works without one. */
class NoAiProvider implements AiProvider {

    @Override
    public Optional<String> complete(StructuredPrompt prompt) {
        return Optional.empty();
    }
}
