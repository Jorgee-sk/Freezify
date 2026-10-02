import '../inventory/inventory_models.dart';

/// Something a food contains that some people do not eat.
enum FoodTrait implements WireEnum {
  meat('MEAT'),
  pork('PORK'),
  fish('FISH'),
  shellfish('SHELLFISH'),
  dairy('DAIRY'),
  egg('EGG'),
  gluten('GLUTEN'),
  nuts('NUTS'),
  soy('SOY'),
  sesame('SESAME'),
  alcohol('ALCOHOL');

  const FoodTrait(this.wire);

  @override
  final String wire;

  static FoodTrait parse(String wire) => values.firstWhere((value) => value.wire == wire);

  /// In the order they are offered to be avoided: allergens first.
  static const avoidable = [gluten, dairy, egg, nuts, soy, sesame, fish, shellfish, meat, pork, alcohol];
}

enum DietType implements WireEnum {
  none('NONE', []),
  vegetarian('VEGETARIAN', [FoodTrait.meat, FoodTrait.pork, FoodTrait.fish, FoodTrait.shellfish]),
  vegan('VEGAN', [FoodTrait.meat, FoodTrait.pork, FoodTrait.fish, FoodTrait.shellfish, FoodTrait.dairy, FoodTrait.egg]);

  const DietType(this.wire, this.excludes);

  @override
  final String wire;

  /// What the diet rules out.
  final List<FoodTrait> excludes;

  static DietType parse(String wire) => values.firstWhere((value) => value.wire == wire);
}

/// What is not cooked in a household: a diet plus anything else to avoid. A hard filter on recipes.
class Diet {
  const Diet({required this.type, required this.avoided});

  factory Diet.fromJson(Map<String, dynamic> json) => Diet(
    type: DietType.parse(json['type'] as String),
    avoided: {for (final trait in json['avoided'] as List<dynamic>) FoodTrait.parse(trait as String)},
  );

  final DietType type;
  final Set<FoodTrait> avoided;

  bool get isEmpty => type == DietType.none && avoided.isEmpty;

  Diet copyWith({DietType? type, Set<FoodTrait>? avoided}) =>
      Diet(type: type ?? this.type, avoided: avoided ?? this.avoided);

  /// What a recipe contains that the household does not eat. The server already leaves such recipes out of
  /// every list; this is for a recipe opened some other way, so that it never looks fine when it is not.
  List<FoodTrait> conflictsWith(List<FoodTrait> contains) => [
    for (final trait in contains)
      if (type.excludes.contains(trait) || avoided.contains(trait)) trait,
  ];

  Map<String, Object?> toJson() => {
    'type': type.wire,
    // In the order of the enum, so that the same restrictions always travel the same.
    'avoided': [
      for (final trait in FoodTrait.values)
        if (avoided.contains(trait)) trait.wire,
    ],
  };
}
