import 'package:intl/intl.dart';

import '../../l10n/app_localizations.dart';
import 'inventory_models.dart';

String formatAmount(double amount, String language) => NumberFormat('0.###', language).format(amount);

/// "500 g", "1,5 kg", "6 uds".
String formatQuantity(AppLocalizations l10n, Quantity quantity) {
  final amount = formatAmount(quantity.amount, l10n.localeName);
  final unit = switch (quantity.unit) {
    Unit.gram => 'g',
    Unit.kilogram => 'kg',
    Unit.milliliter => 'ml',
    Unit.liter => 'l',
    Unit.unit => l10n.unitAbbreviation(quantity.amount),
  };
  return '$amount $unit';
}

/// Formats a calendar day ("2026-10-05") as day/month/year.
String formatDay(String isoDay) {
  final parts = isoDay.split('-');
  return '${parts[2]}/${parts[1]}/${parts[0]}';
}

/// A calendar day as it travels over the API: "YYYY-MM-DD".
String toIsoDay(DateTime date) =>
    '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';

DateTime parseIsoDay(String isoDay) {
  final parts = isoDay.split('-').map(int.parse).toList();
  return DateTime(parts[0], parts[1], parts[2]);
}

/// Accepts what people type in Spain ("1,5") as well as "1.5". Returns null unless it is a positive number.
double? parseAmount(String text) {
  final value = double.tryParse(text.trim().replaceAll(',', '.'));
  return value != null && value.isFinite && value > 0 ? value : null;
}

String unitName(AppLocalizations l10n, Unit unit) => switch (unit) {
  Unit.unit => l10n.unitNameUnit,
  Unit.gram => l10n.unitNameGram,
  Unit.kilogram => l10n.unitNameKilogram,
  Unit.milliliter => l10n.unitNameMilliliter,
  Unit.liter => l10n.unitNameLiter,
};

String locationName(AppLocalizations l10n, StorageLocation location) => switch (location) {
  StorageLocation.refrigerator => l10n.locationRefrigerator,
  StorageLocation.freezer => l10n.locationFreezer,
  StorageLocation.pantry => l10n.locationPantry,
  StorageLocation.other => l10n.locationOther,
};

String categoryName(AppLocalizations l10n, FoodCategory category) => switch (category) {
  FoodCategory.vegetables => l10n.categoryVegetables,
  FoodCategory.fruits => l10n.categoryFruits,
  FoodCategory.meat => l10n.categoryMeat,
  FoodCategory.fish => l10n.categoryFish,
  FoodCategory.dairy => l10n.categoryDairy,
  FoodCategory.eggs => l10n.categoryEggs,
  FoodCategory.bakery => l10n.categoryBakery,
  FoodCategory.pantry => l10n.categoryPantry,
  FoodCategory.frozen => l10n.categoryFrozen,
  FoodCategory.beverages => l10n.categoryBeverages,
  FoodCategory.prepared => l10n.categoryPrepared,
  FoodCategory.other => l10n.categoryOther,
};

String statusName(AppLocalizations l10n, ItemStatus status) => switch (status) {
  ItemStatus.available => l10n.statusAvailable,
  ItemStatus.opened => l10n.statusOpened,
  ItemStatus.expired => l10n.statusExpired,
  ItemStatus.consumed => l10n.statusConsumed,
  ItemStatus.discarded => l10n.statusDiscarded,
};

String stateName(AppLocalizations l10n, ItemState state) => switch (state) {
  ItemState.active => l10n.stateActive,
  ItemState.finished => l10n.stateFinished,
  ItemState.all => l10n.stateAll,
};

String reasonName(AppLocalizations l10n, WasteReason reason) => switch (reason) {
  WasteReason.expired => l10n.reasonExpired,
  WasteReason.spoiled => l10n.reasonSpoiled,
  WasteReason.leftover => l10n.reasonLeftover,
  WasteReason.other => l10n.reasonOther,
};

/// "500 g · Nevera · Caduca el 03/10/2026", with estimated dates labelled as such.
String itemSummary(AppLocalizations l10n, InventoryItem item) {
  final expiration = switch (item.expirationDate) {
    null => l10n.noExpirationDate,
    final date when item.expirationSource == ExpirationSource.estimated => l10n.expiresEstimated(formatDay(date)),
    final date => l10n.expiresOn(formatDay(date)),
  };
  return [
    formatQuantity(l10n, item.quantity),
    locationName(l10n, item.storageLocation),
    expiration,
    if (item.openedDate != null) l10n.openedOn(formatDay(item.openedDate!)),
  ].join(' · ');
}
