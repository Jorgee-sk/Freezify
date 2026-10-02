package com.freezify.recipes.web;

import com.freezify.common.CurrentUser;
import com.freezify.common.PageResponse;
import com.freezify.food.FoodTrait;
import com.freezify.recipes.internal.Diet;
import com.freezify.recipes.internal.Recipe.Course;
import com.freezify.recipes.internal.Recipe.Difficulty;
import com.freezify.recipes.internal.RecipeService;
import com.freezify.recipes.internal.RecipeService.Filter;
import com.freezify.recipes.internal.RecipeViews.Recommendation;
import com.freezify.recipes.internal.RecipeViews.RecipeDetail;
import com.freezify.recipes.internal.RecipeViews.RecipeSummary;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Recipes")
class RecipeController {

    private static final String LANGUAGE = "es|en";
    private static final String LANGUAGE_MESSAGE = "must be 'es' or 'en'";

    private final RecipeService recipes;

    RecipeController(RecipeService recipes) {
        this.recipes = recipes;
    }

    /** The recipes of the catalog. With {@code household}, without what that household does not eat. */
    @GetMapping("/recipes")
    PageResponse<RecipeSummary> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Nullable UUID household,
            @RequestParam(name = "q", required = false) @Nullable @Size(max = 80) String text,
            @RequestParam(required = false) @Nullable @Min(1) Integer maxMinutes,
            @RequestParam(required = false) @Nullable Difficulty difficulty,
            @RequestParam(required = false) @Nullable Course course,
            @RequestParam(defaultValue = "es") @Pattern(regexp = LANGUAGE, message = LANGUAGE_MESSAGE) String lang,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return recipes.list(
                new Filter(text, maxMinutes, difficulty, course, household), CurrentUser.id(jwt), lang, page, size);
    }

    @GetMapping("/recipes/{recipeId}")
    RecipeDetail get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID recipeId,
            @RequestParam(defaultValue = "es") @Pattern(regexp = LANGUAGE, message = LANGUAGE_MESSAGE) String lang) {
        return recipes.get(recipeId, CurrentUser.id(jwt), lang);
    }

    /** What to cook with what the household has, the best first, with the data that explains each suggestion. */
    @GetMapping("/households/{householdId}/recipes/recommendations")
    List<Recommendation> recommend(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID householdId,
            @RequestParam(defaultValue = "es") @Pattern(regexp = LANGUAGE, message = LANGUAGE_MESSAGE) String lang,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return recipes.recommend(householdId, CurrentUser.id(jwt), lang, limit);
    }

    /** What is not cooked in the household. */
    @GetMapping("/households/{householdId}/diet")
    Diet diet(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId) {
        return recipes.dietOf(householdId, CurrentUser.id(jwt));
    }

    /** Replaces what is not cooked in the household. */
    @PutMapping("/households/{householdId}/diet")
    Diet updateDiet(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId, @Valid @RequestBody DietRequest request) {
        return recipes.updateDiet(householdId, CurrentUser.id(jwt), new Diet(request.type(), request.avoided()));
    }

    record DietRequest(@NotNull Diet.Type type, @NotNull Set<@NotNull FoodTrait> avoided) {}

    /** The household cooked the recipe today. Does not change the inventory. */
    @PostMapping("/households/{householdId}/recipes/{recipeId}/cooked")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markCooked(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId, @PathVariable UUID recipeId) {
        recipes.markCooked(householdId, CurrentUser.id(jwt), recipeId);
    }
}
