import '../inventory/inventory_models.dart';

/// `plan`: what the meal plan lacks, recomputed when the list is filled from it again. `manual`: put by a person.
enum ShoppingOrigin implements WireEnum {
  manual('MANUAL'),
  plan('PLAN');

  const ShoppingOrigin(this.wire);

  @override
  final String wire;

  static ShoppingOrigin parse(String wire) => values.firstWhere((value) => value.wire == wire);
}

/// A line of the shopping list.
class ShoppingItem {
  const ShoppingItem({
    required this.id,
    required this.foodId,
    required this.name,
    required this.category,
    required this.quantity,
    required this.origin,
    required this.neededOn,
    required this.checked,
  });

  factory ShoppingItem.fromJson(Map<String, dynamic> json) => ShoppingItem(
    id: json['id'] as String,
    foodId: json['foodId'] as String?,
    name: json['name'] as String,
    category: FoodCategory.parse(json['category'] as String),
    quantity: json['quantity'] == null ? null : Quantity.fromJson(json['quantity'] as Map<String, dynamic>),
    origin: ShoppingOrigin.parse(json['origin'] as String),
    neededOn: json['neededOn'] as String?,
    checked: json['checked'] as bool,
  );

  final String id;

  /// The catalog food, when it is one.
  final String? foodId;
  final String name;
  final FoodCategory category;

  /// `null` when nobody said how much.
  final Quantity? quantity;
  final ShoppingOrigin origin;

  /// For lines from the plan, the day of the first meal that needs it.
  final String? neededOn;
  final bool checked;
}

/// What a person puts on the list: a catalog food, or free text.
class ShoppingItemInput {
  const ShoppingItemInput({this.foodId, this.name, this.category, this.quantity});

  final String? foodId;
  final String? name;
  final FoodCategory? category;
  final Quantity? quantity;

  Map<String, Object?> toJson() => {
    'foodId': foodId,
    'name': name,
    'category': category?.wire,
    'quantity': quantity?.toJson(),
  };
}
