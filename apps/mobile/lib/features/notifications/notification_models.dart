import '../inventory/inventory_models.dart';

/// How often, at most, the user hears about the same household.
enum NotificationFrequency implements WireEnum {
  daily('DAILY'),
  everyThreeDays('EVERY_THREE_DAYS'),
  weekly('WEEKLY');

  const NotificationFrequency(this.wire);

  @override
  final String wire;

  static NotificationFrequency parse(String wire) => values.firstWhere((value) => value.wire == wire);
}

/// How close to its date a food has to be before it is worth a notification.
enum NotificationThreshold implements WireEnum {
  today('TODAY'),
  urgent('URGENT'),
  soon('SOON');

  const NotificationThreshold(this.wire);

  @override
  final String wire;

  static NotificationThreshold parse(String wire) => values.firstWhere((value) => value.wire == wire);
}

class NotifiedItem {
  const NotifiedItem({
    required this.name,
    required this.expirationDate,
    required this.estimated,
    required this.daysUntilExpiration,
  });

  factory NotifiedItem.fromJson(Map<String, dynamic> json) => NotifiedItem(
    name: json['name'] as String,
    expirationDate: json['expirationDate'] as String,
    estimated: json['estimated'] as bool,
    daysUntilExpiration: json['daysUntilExpiration'] as int,
  );

  final String name;
  final String expirationDate;

  /// Estimated dates must be worded as estimates.
  final bool estimated;

  /// Counted from the day of the notification; negative when the date had passed.
  final int daysUntilExpiration;
}

/// Facts, not sentences: the wording is the app's, in the user's language.
class AppNotification {
  const AppNotification({
    required this.id,
    required this.householdId,
    required this.householdName,
    required this.day,
    required this.itemCount,
    required this.items,
    required this.read,
  });

  factory AppNotification.fromJson(Map<String, dynamic> json) => AppNotification(
    id: json['id'] as String,
    householdId: json['householdId'] as String,
    householdName: json['householdName'] as String,
    day: json['day'] as String,
    itemCount: json['itemCount'] as int,
    items: [for (final item in json['items'] as List<dynamic>) NotifiedItem.fromJson(item as Map<String, dynamic>)],
    read: json['read'] as bool,
  );

  final String id;
  final String householdId;
  final String householdName;

  /// The calendar day the notification is about.
  final String day;

  /// How many items needed attention that day; [items] holds only the most pressing ones.
  final int itemCount;
  final List<NotifiedItem> items;
  final bool read;
}

class NotificationPreferences {
  const NotificationPreferences({
    required this.expirationAlerts,
    required this.deliveryHour,
    required this.frequency,
    required this.threshold,
    required this.mutedCategories,
  });

  factory NotificationPreferences.fromJson(Map<String, dynamic> json) => NotificationPreferences(
    expirationAlerts: json['expirationAlerts'] as bool,
    deliveryHour: json['deliveryHour'] as int,
    frequency: NotificationFrequency.parse(json['frequency'] as String),
    threshold: NotificationThreshold.parse(json['threshold'] as String),
    mutedCategories: {
      for (final category in json['mutedCategories'] as List<dynamic>) FoodCategory.parse(category as String),
    },
  );

  final bool expirationAlerts;

  /// 0–23, on the clock of the application's time zone.
  final int deliveryHour;
  final NotificationFrequency frequency;
  final NotificationThreshold threshold;
  final Set<FoodCategory> mutedCategories;

  NotificationPreferences copyWith({
    bool? expirationAlerts,
    int? deliveryHour,
    NotificationFrequency? frequency,
    NotificationThreshold? threshold,
    Set<FoodCategory>? mutedCategories,
  }) => NotificationPreferences(
    expirationAlerts: expirationAlerts ?? this.expirationAlerts,
    deliveryHour: deliveryHour ?? this.deliveryHour,
    frequency: frequency ?? this.frequency,
    threshold: threshold ?? this.threshold,
    mutedCategories: mutedCategories ?? this.mutedCategories,
  );

  Map<String, Object?> toJson() => {
    'expirationAlerts': expirationAlerts,
    'deliveryHour': deliveryHour,
    'frequency': frequency.wire,
    'threshold': threshold.wire,
    // In the order of the enum, so that the same preferences always travel the same.
    'mutedCategories': [
      for (final category in FoodCategory.values)
        if (mutedCategories.contains(category)) category.wire,
    ],
  };
}
