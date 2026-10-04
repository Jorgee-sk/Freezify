package com.freezify.ai.internal;

import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

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
     * @param image        a picture that goes with the input, if any
     */
    record StructuredPrompt(
            String instructions,
            String input,
            String schemaName,
            Map<String, Object> schema,
            @Nullable Picture image) {

        StructuredPrompt(String instructions, String input, String schemaName, Map<String, Object> schema) {
            this(instructions, input, schemaName, schema, null);
        }
    }

    /** @param mediaType {@code image/jpeg}, {@code image/png} or {@code image/webp} */
    record Picture(byte[] bytes, String mediaType) {}
}

/** Used when no model is configured: the application works without one. */
class NoAiProvider implements AiProvider {

    @Override
    public Optional<String> complete(StructuredPrompt prompt) {
        return Optional.empty();
    }
}
