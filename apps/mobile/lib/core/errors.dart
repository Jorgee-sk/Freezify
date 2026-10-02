import '../l10n/app_localizations.dart';
import 'api_client.dart';

/// Translates a failure by its stable error code; unknown codes get a generic message.
String errorMessage(AppLocalizations l10n, Object error) {
  if (error is! ApiException) return l10n.errorGeneric;
  return switch (error.code) {
    ApiException.networkCode => l10n.errorNetwork,
    'VALIDATION_ERROR' => l10n.errorValidation,
    'EMAIL_ALREADY_REGISTERED' => l10n.errorEmailTaken,
    'INVALID_CREDENTIALS' => l10n.errorInvalidCredentials,
    'PASSWORD_TOO_LONG' => l10n.errorPasswordTooLong,
    'RATE_LIMITED' => l10n.errorRateLimited,
    'HOUSEHOLD_NOT_FOUND' => l10n.errorHouseholdNotFound,
    'NOT_HOUSEHOLD_OWNER' => l10n.errorNotOwner,
    'OWNER_CANNOT_LEAVE' => l10n.errorOwnerCannotLeave,
    'ALREADY_MEMBER' => l10n.errorAlreadyMember,
    'INVITATION_NOT_FOUND' => l10n.errorInvitationNotFound,
    'MEMBER_NOT_FOUND' => l10n.errorMemberNotFound,
    'ITEM_NOT_FOUND' => l10n.errorItemNotFound,
    'ITEM_NOT_ACTIVE' => l10n.errorItemNotActive,
    'FOOD_NOT_FOUND' => l10n.errorFoodNotFound,
    'INCOMPATIBLE_UNIT' => l10n.errorIncompatibleUnit,
    'QUANTITY_EXCEEDS_AVAILABLE' => l10n.errorQuantityExceeds,
    'CONCURRENT_MODIFICATION' => l10n.errorConcurrentModification,
    'NOTIFICATION_NOT_FOUND' => l10n.errorNotificationNotFound,
    'RECIPE_NOT_FOUND' => l10n.errorRecipeNotFound,
    _ => l10n.errorGeneric,
  };
}
