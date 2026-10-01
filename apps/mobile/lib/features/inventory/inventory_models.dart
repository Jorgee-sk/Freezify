/// An enum whose values travel over the API under a fixed name.
abstract interface class WireEnum {
  String get wire;
}

T _parse<T extends WireEnum>(List<T> values, String wire) => values.firstWhere((value) => value.wire == wire);

enum UnitDimension { mass, volume, count }

enum Unit implements WireEnum {
  unit('UNIT', UnitDimension.count),
  gram('GRAM', UnitDimension.mass),
  kilogram('KILOGRAM', UnitDimension.mass),
  milliliter('MILLILITER', UnitDimension.volume),
  liter('LITER', UnitDimension.volume);

  const Unit(this.wire, this.dimension);

  @override
  final String wire;
  final UnitDimension dimension;

  static Unit parse(String wire) => _parse(values, wire);

  /// Units a quantity in this unit can be expressed in; grams can become kilograms but never liters.
  List<Unit> get compatible => [
    for (final other in values)
      if (other.dimension == dimension) other,
  ];
}

enum StorageLocation implements WireEnum {
  refrigerator('REFRIGERATOR'),
  freezer('FREEZER'),
  pantry('PANTRY'),
  other('OTHER');

  const StorageLocation(this.wire);

  @override
  final String wire;

  static StorageLocation parse(String wire) => _parse(values, wire);
}

enum FoodCategory implements WireEnum {
  vegetables('VEGETABLES'),
  fruits('FRUITS'),
  meat('MEAT'),
  fish('FISH'),
  dairy('DAIRY'),
  eggs('EGGS'),
  bakery('BAKERY'),
  pantry('PANTRY'),
  frozen('FROZEN'),
  beverages('BEVERAGES'),
  prepared('PREPARED'),
  other('OTHER');

  const FoodCategory(this.wire);

  @override
  final String wire;

  static FoodCategory parse(String wire) => _parse(values, wire);
}

enum ItemStatus implements WireEnum {
  available('AVAILABLE'),
  opened('OPENED'),
  expired('EXPIRED'),
  consumed('CONSUMED'),
  discarded('DISCARDED');

  const ItemStatus(this.wire);

  @override
  final String wire;

  static ItemStatus parse(String wire) => _parse(values, wire);

  /// Whether the food is still physically in the household.
  bool get isActive => this == available || this == opened || this == expired;
}

/// Which items a list shows.
enum ItemState implements WireEnum {
  active('ACTIVE'),
  finished('FINISHED'),
  all('ALL');

  const ItemState(this.wire);

  @override
  final String wire;
}

enum WasteReason implements WireEnum {
  expired('EXPIRED'),
  spoiled('SPOILED'),
  leftover('LEFTOVER'),
  other('OTHER');

  const WasteReason(this.wire);

  @override
  final String wire;
}

/// Where an expiration date comes from. An estimate must never be shown as the date on the package.
enum ExpirationSource implements WireEnum {
  user('USER'),
  estimated('ESTIMATED');

  const ExpirationSource(this.wire);

  @override
  final String wire;

  static ExpirationSource parse(String wire) => _parse(values, wire);
}

class Quantity {
  const Quantity(this.amount, this.unit);

  factory Quantity.fromJson(Map<String, dynamic> json) =>
      Quantity((json['amount'] as num).toDouble(), Unit.parse(json['unit'] as String));

  final double amount;
  final Unit unit;

  Map<String, dynamic> toJson() => {'amount': amount, 'unit': unit.wire};
}

/// A canonical food of the catalog, as offered by the autocomplete.
class Food {
  const Food({
    required this.id,
    required this.name,
    required this.category,
    required this.defaultUnit,
    required this.defaultStorage,
  });

  factory Food.fromJson(Map<String, dynamic> json) => Food(
    id: json['id'] as String,
    name: json['name'] as String,
    category: FoodCategory.parse(json['category'] as String),
    defaultUnit: Unit.parse(json['defaultUnit'] as String),
    defaultStorage: StorageLocation.parse(json['defaultStorage'] as String),
  );

  final String id;
  final String name;
  final FoodCategory category;
  final Unit defaultUnit;
  final StorageLocation defaultStorage;
}

/// A food the household added before, offered to add it again in one tap.
class RecentFood {
  const RecentFood({
    required this.name,
    required this.foodId,
    required this.category,
    required this.quantity,
    required this.storageLocation,
  });

  factory RecentFood.fromJson(Map<String, dynamic> json) => RecentFood(
    name: json['name'] as String,
    foodId: json['foodId'] as String?,
    category: FoodCategory.parse(json['category'] as String),
    quantity: Quantity.fromJson(json['quantity'] as Map<String, dynamic>),
    storageLocation: StorageLocation.parse(json['storageLocation'] as String),
  );

  final String name;
  final String? foodId;
  final FoodCategory category;
  final Quantity quantity;
  final StorageLocation storageLocation;
}

class InventoryItem {
  const InventoryItem({
    required this.id,
    required this.foodId,
    required this.name,
    required this.category,
    required this.quantity,
    required this.storageLocation,
    required this.status,
    required this.purchaseDate,
    required this.expirationDate,
    required this.expirationSource,
    required this.openedDate,
    required this.barcode,
    required this.brand,
    required this.estimatedPrice,
    required this.notes,
  });

  factory InventoryItem.fromJson(Map<String, dynamic> json) => InventoryItem(
    id: json['id'] as String,
    foodId: json['foodId'] as String?,
    name: json['name'] as String,
    category: FoodCategory.parse(json['category'] as String),
    quantity: Quantity.fromJson(json['quantity'] as Map<String, dynamic>),
    storageLocation: StorageLocation.parse(json['storageLocation'] as String),
    status: ItemStatus.parse(json['status'] as String),
    purchaseDate: json['purchaseDate'] as String,
    expirationDate: json['expirationDate'] as String?,
    expirationSource: switch (json['expirationSource']) {
      final String source => ExpirationSource.parse(source),
      _ => null,
    },
    openedDate: json['openedDate'] as String?,
    barcode: json['barcode'] as String?,
    brand: json['brand'] as String?,
    estimatedPrice: (json['estimatedPrice'] as num?)?.toDouble(),
    notes: json['notes'] as String?,
  );

  final String id;
  final String? foodId;
  final String name;
  final FoodCategory category;
  final Quantity quantity;
  final StorageLocation storageLocation;
  final ItemStatus status;

  /// Calendar days travel as "YYYY-MM-DD" and are kept as such: they are not instants.
  final String purchaseDate;
  final String? expirationDate;
  final ExpirationSource? expirationSource;
  final String? openedDate;
  final String? barcode;
  final String? brand;
  final double? estimatedPrice;
  final String? notes;
}

/// What the user can set on an item. The backend replaces the whole item with it.
class ItemInput {
  const ItemInput({
    required this.foodId,
    required this.name,
    required this.category,
    required this.quantity,
    required this.storageLocation,
    required this.purchaseDate,
    required this.expirationDate,
    required this.openedDate,
    required this.barcode,
    required this.brand,
    required this.estimatedPrice,
    required this.notes,
  });

  final String? foodId;
  final String name;
  final FoodCategory? category;
  final Quantity quantity;
  final StorageLocation storageLocation;
  final String? purchaseDate;
  final String? expirationDate;
  final String? openedDate;
  final String? barcode;
  final String? brand;
  final double? estimatedPrice;
  final String? notes;

  Map<String, dynamic> toJson() => {
    'foodId': foodId,
    'name': name,
    'category': category?.wire,
    'quantity': quantity.toJson(),
    'storageLocation': storageLocation.wire,
    'purchaseDate': purchaseDate,
    'expirationDate': expirationDate,
    'openedDate': openedDate,
    'barcode': barcode,
    'brand': brand,
    'estimatedPrice': estimatedPrice,
    'notes': notes,
  };
}

class InventoryPage {
  const InventoryPage({required this.items, required this.page, required this.totalItems, required this.totalPages});

  factory InventoryPage.fromJson(Map<String, dynamic> json) => InventoryPage(
    items: [for (final item in json['items'] as List<dynamic>) InventoryItem.fromJson(item as Map<String, dynamic>)],
    page: json['page'] as int,
    totalItems: json['totalItems'] as int,
    totalPages: json['totalPages'] as int,
  );

  final List<InventoryItem> items;
  final int page;
  final int totalItems;
  final int totalPages;
}

/// Everything that selects one page of one household's inventory. Records compare by value, so two equal
/// queries share the same cached result.
typedef InventoryQuery = ({
  String householdId,
  ItemState state,
  StorageLocation? location,
  FoodCategory? category,
  String text,
  int page,
});
