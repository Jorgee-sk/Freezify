import '../inventory/inventory_models.dart';

/// Who turned the receipt text into products.
enum ReadBy implements WireEnum {
  /// A language model, its answer checked by the backend.
  ai('AI'),

  /// The backend's rules alone.
  rules('RULES');

  const ReadBy(this.wire);

  @override
  final String wire;

  static ReadBy parse(String wire) => values.firstWhere((value) => value.wire == wire);
}

/// How the suggested food of a line was found.
enum MatchedBy implements WireEnum {
  /// This household chose that food for the same text before.
  learned('LEARNED'),

  /// Through a known receipt abbreviation.
  alias('ALIAS'),

  /// The line contains a name of the food.
  name('NAME'),

  /// Nothing found, or more than one food could be it.
  none('NONE');

  const MatchedBy(this.wire);

  @override
  final String wire;

  static MatchedBy parse(String wire) => values.firstWhere((value) => value.wire == wire);
}

class FoodCandidate {
  const FoodCandidate(this.foodId, this.name);

  factory FoodCandidate.fromJson(Map<String, dynamic> json) =>
      FoodCandidate(json['foodId'] as String, json['name'] as String);

  final String foodId;
  final String name;
}

/// A scanned receipt, not stored anywhere yet: every value is a proposal for the person to check.
class ReceiptDraft {
  const ReceiptDraft({
    required this.readBy,
    required this.purchaseDate,
    required this.purchaseDateFromReceipt,
    required this.lines,
  });

  factory ReceiptDraft.fromJson(Map<String, dynamic> json) => ReceiptDraft(
    readBy: ReadBy.parse(json['readBy'] as String),
    purchaseDate: json['purchaseDate'] as String,
    purchaseDateFromReceipt: json['purchaseDateFromReceipt'] as bool,
    lines: [for (final line in json['lines'] as List<dynamic>) DraftLine.fromJson(line as Map<String, dynamic>)],
  );

  final ReadBy readBy;
  final String purchaseDate;

  /// Whether the date was read from the receipt; otherwise it is today.
  final bool purchaseDateFromReceipt;
  final List<DraftLine> lines;
}

/// A product read from the receipt, as the person is reviewing it.
class DraftLine {
  DraftLine({
    required this.text,
    required this.name,
    required this.foodId,
    required this.match,
    required this.candidates,
    required this.category,
    required this.storageLocation,
    required this.quantity,
    required this.quantityFromReceipt,
    required this.price,
    required this.include,
    this.expirationDate,
  });

  factory DraftLine.fromJson(Map<String, dynamic> json) => DraftLine(
    text: json['text'] as String,
    name: json['name'] as String,
    foodId: json['foodId'] as String?,
    match: MatchedBy.parse(json['match'] as String),
    candidates: [
      for (final candidate in json['candidates'] as List<dynamic>)
        FoodCandidate.fromJson(candidate as Map<String, dynamic>),
    ],
    category: FoodCategory.parse(json['category'] as String),
    storageLocation: StorageLocation.parse(json['storageLocation'] as String),
    quantity: Quantity.fromJson(json['quantity'] as Map<String, dynamic>),
    quantityFromReceipt: json['quantityFromReceipt'] as bool,
    price: (json['price'] as num?)?.toDouble(),
    include: json['include'] as bool,
  );

  /// The product as printed. Sent back on confirming, so the household's choice of food is remembered.
  final String text;
  final List<FoodCandidate> candidates;

  String name;
  String? foodId;
  MatchedBy match;
  FoodCategory category;
  StorageLocation storageLocation;
  Quantity quantity;

  /// `false` when the receipt does not say how much and one unit is assumed.
  bool quantityFromReceipt;
  double? price;
  bool include;

  /// Only when the person reads it on the package; otherwise the backend estimates one.
  String? expirationDate;

  /// The person went through the line: what it says is theirs, not a guess.
  bool reviewed = false;

  Map<String, Object?> toJson() => {
    'text': text,
    'foodId': foodId,
    'name': name,
    // A catalog food brings its own category.
    'category': foodId == null ? category.wire : null,
    'quantity': quantity.toJson(),
    'storageLocation': storageLocation.wire,
    'price': price,
    'expirationDate': expirationDate,
  };
}
