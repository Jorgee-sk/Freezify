package com.freezify.scanning.internal;

import com.freezify.ai.AiService;
import com.freezify.ai.AiService.Answer;
import com.freezify.ai.AiService.FoodGuess;
import com.freezify.common.ApiException;
import com.freezify.food.Food;
import com.freezify.food.FoodCatalog;
import com.freezify.food.FoodCategory;
import com.freezify.food.StorageLocation;
import com.freezify.food.Unit;
import com.freezify.households.HouseholdAccess;
import com.freezify.scanning.ScanEvents.FoodScanned;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * "What is this?": a photo of a food, identified by a language model as candidates from the catalog, each with
 * how sure the model is. Nothing is stored, the photo included: the person picks a candidate (or none) and adds
 * the food as usual.
 */
@Service
public class FoodPhotoService {

    /** The apps shrink photos to well under this before sending them. */
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    /**
     * A food the photo may show.
     *
     * @param foodId     the catalog food, or {@code null} for something that is not in the catalog
     * @param confidence between 0 and 1, as the model gave it
     */
    public record Candidate(
            @Nullable UUID foodId,
            String name,
            double confidence,
            FoodCategory category,
            @Nullable Unit defaultUnit,
            @Nullable StorageLocation defaultStorage) {}

    private final HouseholdAccess access;
    private final FoodCatalog catalog;
    private final AiService ai;
    private final ApplicationEventPublisher events;

    FoodPhotoService(HouseholdAccess access, FoodCatalog catalog, AiService ai, ApplicationEventPublisher events) {
        this.access = access;
        this.catalog = catalog;
        this.ai = ai;
        this.events = events;
    }

    /** Not in a transaction: the model may take a while, and nothing is written. */
    public List<Candidate> identify(UUID householdId, UUID userId, byte[] image, String language) {
        access.requireMember(householdId, userId);
        if (!ai.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "No language model is configured.");
        }
        if (image.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "The file is too large.");
        }
        String mediaType = mediaType(image);
        if (mediaType == null) {
            throw new ApiException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE", "Only JPEG, PNG and WebP photos are read.");
        }

        Answer<List<FoodGuess>> answer = ai.identifyFood(image, mediaType, catalog.all(), language, userId);
        List<FoodGuess> guesses = switch (answer.outcome()) {
            case OK -> answer.value();
            case NOT_CONFIGURED -> throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "No language model is configured.");
            case LIMIT_REACHED -> throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS, "AI_LIMIT_REACHED", "Today's allowance of AI requests is spent.");
            case FAILED -> throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "AI_UNAVAILABLE", "The language model did not say what the photo shows.");
        };
        events.publishEvent(new FoodScanned(householdId, userId));
        return guesses.stream().map(this::candidate).toList();
    }

    private Candidate candidate(FoodGuess guess) {
        Food food = guess.foodId() == null ? null : catalog.findById(guess.foodId()).orElse(null);
        return food == null
                ? new Candidate(null, guess.name(), guess.confidence(), FoodCategory.OTHER, null, null)
                : new Candidate(
                        food.id(),
                        guess.name(),
                        guess.confidence(),
                        food.category(),
                        food.defaultUnit(),
                        food.defaultStorage());
    }

    /**
     * The type of an image by what it starts with, never by what the client says it is: anything else is not
     * sent anywhere.
     */
    static @Nullable String mediaType(byte[] bytes) {
        if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(bytes, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(bytes, 0, 'R', 'I', 'F', 'F') && startsWith(bytes, 8, 'W', 'E', 'B', 'P')) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, int offset, int... expected) {
        if (bytes.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
