// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appName => 'Freezify';

  @override
  String get appTagline => 'Know what you have, what expires and what you can cook.';

  @override
  String get logout => 'Sign out';

  @override
  String get language => 'Language';

  @override
  String get loginTitle => 'Sign in';

  @override
  String get registerTitle => 'Create your account';

  @override
  String get email => 'Email';

  @override
  String get password => 'Password';

  @override
  String get passwordHint => 'At least 8 characters';

  @override
  String get displayName => 'Your name';

  @override
  String get loginAction => 'Sign in';

  @override
  String get registerAction => 'Create account';

  @override
  String get noAccount => 'No account yet? Sign up';

  @override
  String get haveAccount => 'Already have an account? Sign in';

  @override
  String get fieldRequired => 'Required';

  @override
  String get passwordTooShort => 'At least 8 characters';

  @override
  String greeting(String name) {
    return 'Hi, $name 👋';
  }

  @override
  String get householdsTitle => 'My households';

  @override
  String get householdsEmpty => 'You don\'t belong to any household yet. Create one or join with an invitation code.';

  @override
  String get createHousehold => 'Create a household';

  @override
  String get householdName => 'Household name';

  @override
  String get create => 'Create';

  @override
  String get joinHousehold => 'Join with a code';

  @override
  String get invitationCode => 'Invitation code';

  @override
  String get join => 'Join';

  @override
  String memberCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count members', one: '$count member');
    return '$_temp0';
  }

  @override
  String get roleOwner => 'Owner';

  @override
  String get roleMember => 'Member';

  @override
  String get membersTitle => 'Members';

  @override
  String get you => 'you';

  @override
  String joinedOn(String date) {
    return 'Since $date';
  }

  @override
  String get inviteTitle => 'Invite someone';

  @override
  String get inviteHelp => 'Generate a code and share it. Whoever enters it will join this household.';

  @override
  String get inviteGenerate => 'Generate code';

  @override
  String inviteExpires(String date) {
    return 'Valid until $date';
  }

  @override
  String get rename => 'Rename';

  @override
  String get save => 'Save';

  @override
  String get cancel => 'Cancel';

  @override
  String get remove => 'Remove';

  @override
  String get leaveHousehold => 'Leave household';

  @override
  String get deleteHousehold => 'Delete household';

  @override
  String confirmRemove(String name) {
    return 'Remove $name from this household?';
  }

  @override
  String confirmLeave(String name) {
    return 'Are you sure you want to leave “$name”?';
  }

  @override
  String confirmDelete(String name) {
    return '“$name” will be deleted for all its members. This cannot be undone.';
  }

  @override
  String get retry => 'Retry';

  @override
  String get errorGeneric => 'Something went wrong. Please try again.';

  @override
  String get errorNetwork => 'Cannot reach the server.';

  @override
  String get errorValidation => 'Please check the information you entered.';

  @override
  String get errorEmailTaken => 'An account with that email already exists.';

  @override
  String get errorInvalidCredentials => 'Incorrect email or password.';

  @override
  String get errorPasswordTooLong => 'The password is too long.';

  @override
  String get errorRateLimited => 'Too many attempts. Please wait a moment.';

  @override
  String get errorHouseholdNotFound => 'This household doesn\'t exist or you no longer belong to it.';

  @override
  String get errorNotOwner => 'Only the household owner can do this.';

  @override
  String get errorOwnerCannotLeave => 'The owner cannot leave the household. Delete it if you no longer need it.';

  @override
  String get errorAlreadyMember => 'You already belong to this household.';

  @override
  String get errorInvitationNotFound => 'The code is invalid or has expired.';

  @override
  String get errorMemberNotFound => 'That person no longer belongs to the household.';

  @override
  String get settings => 'Members and settings';

  @override
  String get inventoryTitle => 'Inventory';

  @override
  String inventoryCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count foods', one: '$count food');
    return '$_temp0';
  }

  @override
  String get inventoryEmpty => 'No food yet. Add what you have at home to get started.';

  @override
  String get inventoryEmptyFiltered => 'No food matches the filter.';

  @override
  String get addFood => 'Add food';

  @override
  String get newFood => 'New food';

  @override
  String get editFood => 'Edit food';

  @override
  String get searchInventory => 'Search the inventory…';

  @override
  String get allLocations => 'All';

  @override
  String get filters => 'Filters';

  @override
  String get category => 'Category';

  @override
  String get allCategories => 'All categories';

  @override
  String get show => 'Show';

  @override
  String get stateActive => 'At home';

  @override
  String get stateFinished => 'Finished';

  @override
  String get stateAll => 'All';

  @override
  String get foodName => 'Food';

  @override
  String get suggestions => 'Suggestions';

  @override
  String get recentlyAdded => 'Recently added';

  @override
  String get amount => 'Quantity';

  @override
  String get unit => 'Unit';

  @override
  String get location => 'Location';

  @override
  String get expirationDate => 'Expiration date';

  @override
  String get noDateSet => 'Not set';

  @override
  String get clearDate => 'Remove date';

  @override
  String get moreDetails => 'More details';

  @override
  String get purchaseDate => 'Purchase date';

  @override
  String get brand => 'Brand';

  @override
  String get price => 'Estimated price (€)';

  @override
  String get notes => 'Notes';

  @override
  String get invalidAmount => 'Enter a quantity greater than zero.';

  @override
  String expiresOn(String date) {
    return 'Expires on $date';
  }

  @override
  String expiresEstimated(String date) {
    return 'Expires around $date (estimated date)';
  }

  @override
  String get noExpirationDate => 'No expiration date';

  @override
  String openedOn(String date) {
    return 'Opened on $date';
  }

  @override
  String get statusAvailable => 'Available';

  @override
  String get statusOpened => 'Opened';

  @override
  String get statusExpired => 'Expired';

  @override
  String get statusConsumed => 'Consumed';

  @override
  String get statusDiscarded => 'Discarded';

  @override
  String get consume => 'Consume';

  @override
  String get discard => 'Discard';

  @override
  String get markOpened => 'Mark as opened';

  @override
  String get edit => 'Edit';

  @override
  String get delete => 'Delete';

  @override
  String get itemActions => 'Actions';

  @override
  String get consumeTitle => 'How much did you consume?';

  @override
  String get discardTitle => 'How much did you throw away?';

  @override
  String get reason => 'Reason';

  @override
  String get reasonExpired => 'Expired';

  @override
  String get reasonSpoiled => 'Spoiled';

  @override
  String get reasonLeftover => 'Leftovers';

  @override
  String get reasonOther => 'Other';

  @override
  String get confirm => 'Confirm';

  @override
  String confirmDeleteItem(String name) {
    return 'Delete “$name” from the inventory? It will not count as consumed or wasted.';
  }

  @override
  String get previousPage => 'Previous';

  @override
  String get nextPage => 'Next';

  @override
  String pageOf(int page, int total) {
    return 'Page $page of $total';
  }

  @override
  String unitAbbreviation(num count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: 'units', one: 'unit');
    return '$_temp0';
  }

  @override
  String get unitNameUnit => 'units';

  @override
  String get unitNameGram => 'grams';

  @override
  String get unitNameKilogram => 'kilograms';

  @override
  String get unitNameMilliliter => 'milliliters';

  @override
  String get unitNameLiter => 'liters';

  @override
  String get locationRefrigerator => 'Fridge';

  @override
  String get locationFreezer => 'Freezer';

  @override
  String get locationPantry => 'Pantry';

  @override
  String get locationOther => 'Other';

  @override
  String get categoryVegetables => 'Vegetables';

  @override
  String get categoryFruits => 'Fruit';

  @override
  String get categoryMeat => 'Meat';

  @override
  String get categoryFish => 'Fish';

  @override
  String get categoryDairy => 'Dairy';

  @override
  String get categoryEggs => 'Eggs';

  @override
  String get categoryBakery => 'Bakery';

  @override
  String get categoryPantry => 'Pantry';

  @override
  String get categoryFrozen => 'Frozen';

  @override
  String get categoryBeverages => 'Drinks';

  @override
  String get categoryPrepared => 'Ready meals';

  @override
  String get categoryOther => 'Other';

  @override
  String get errorItemNotFound => 'This food is no longer in the inventory.';

  @override
  String get errorItemNotActive => 'This food was already consumed or discarded.';

  @override
  String get errorFoodNotFound => 'That food does not exist in the catalog.';

  @override
  String get errorIncompatibleUnit => 'That unit is not compatible with the unit of the food.';

  @override
  String get errorQuantityExceeds => 'There is less left than the quantity given.';

  @override
  String get errorConcurrentModification => 'Someone else just changed this. Try again.';

  @override
  String get priorityExpired => 'Expired';

  @override
  String get priorityToday => 'Expires today';

  @override
  String get priorityUrgent => 'Urgent';

  @override
  String get prioritySoon => 'Eat soon';

  @override
  String get priorityUpcoming => 'Coming up';

  @override
  String daysLeft(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count days left', one: '$count day left');
    return '$_temp0';
  }

  @override
  String daysAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count days ago', one: '$count day ago');
    return '$_temp0';
  }

  @override
  String get consumeFirstTitle => 'Eat first';

  @override
  String consumeFirstCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count foods that cannot wait',
      one: '$count food that cannot wait',
    );
    return '$_temp0';
  }

  @override
  String andMore(int count) {
    return 'and $count more';
  }

  @override
  String get estimateHelp => 'Leave it empty and we will estimate it from the food and where you keep it.';

  @override
  String currentEstimate(String date) {
    return 'Right now we estimate $date. Enter the date on the package if you know it.';
  }

  @override
  String get notificationsTitle => 'Notifications';

  @override
  String notificationsUnread(int count) {
    return 'Notifications: $count unread';
  }

  @override
  String get notificationsEmpty => 'You have no notifications. We will tell you when something is about to expire.';

  @override
  String get markAllRead => 'Mark all as read';

  @override
  String get notificationNew => 'New';

  @override
  String get notificationPreferences => 'Notification preferences';

  @override
  String notificationSummary(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'You have $count foods you should eat soon',
      one: 'You have $count food you should eat soon',
    );
    return '$_temp0';
  }

  @override
  String notifiedExpired(String name, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$name: the expiry date was $count days ago',
      one: '$name: the expiry date was $count day ago',
    );
    return '$_temp0';
  }

  @override
  String notifiedToday(String name) {
    return '$name: the expiry date is today';
  }

  @override
  String notifiedLeft(String name, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$name: $count days left before the expiry date',
      one: '$name: $count day left before the expiry date',
    );
    return '$_temp0';
  }

  @override
  String notifiedExpiredEstimated(String name, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$name: the expiry date was probably $count days ago (estimated date)',
      one: '$name: the expiry date was probably $count day ago (estimated date)',
    );
    return '$_temp0';
  }

  @override
  String notifiedTodayEstimated(String name) {
    return '$name: the expiry date is probably today (estimated date)';
  }

  @override
  String notifiedLeftEstimated(String name, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$name: about $count days left before the expiry date (estimated date)',
      one: '$name: about $count day left before the expiry date (estimated date)',
    );
    return '$_temp0';
  }

  @override
  String get prefEnabled => 'Tell me about food that is about to expire';

  @override
  String get prefAntiSpam =>
      'At most one notification a day per household, and only when there is something new to tell you.';

  @override
  String get prefHour => 'From';

  @override
  String get prefHourHelp => 'Spanish mainland time.';

  @override
  String get prefFrequency => 'At most';

  @override
  String get prefFrequencyDaily => 'Once a day';

  @override
  String get prefFrequencyEveryThreeDays => 'Every 3 days';

  @override
  String get prefFrequencyWeekly => 'Once a week';

  @override
  String get prefThreshold => 'Tell me when a food';

  @override
  String get prefThresholdToday => 'Expires today';

  @override
  String get prefThresholdUrgent => 'Expires in 2 days or less';

  @override
  String get prefThresholdSoon => 'Expires in 5 days or less';

  @override
  String get prefCategories => 'Tell me about these categories';

  @override
  String get preferencesSaved => 'Preferences saved';

  @override
  String get errorNotificationNotFound => 'This notification no longer exists.';

  @override
  String get recipesTitle => 'Recipes';

  @override
  String get recommendedTitle => 'What to cook with what you have';

  @override
  String get noRecommendations => 'No recipe uses what is in your inventory. Add some food and look again.';

  @override
  String get catalogTitle => 'All recipes';

  @override
  String get searchRecipes => 'Search recipes…';

  @override
  String get anyCourse => 'All';

  @override
  String get courseMain => 'Main course';

  @override
  String get courseBreakfast => 'Breakfast';

  @override
  String get courseDessert => 'Dessert';

  @override
  String get difficultyEasy => 'Easy';

  @override
  String get difficultyMedium => 'Medium';

  @override
  String get difficultyHard => 'Hard';

  @override
  String recipeMinutes(int count) {
    return '$count min';
  }

  @override
  String recipeServings(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count servings', one: '$count serving');
    return '$_temp0';
  }

  @override
  String upToMinutes(int count) {
    return 'Up to $count min';
  }

  @override
  String get recipesEmptyFiltered => 'No recipe matches the filter.';

  @override
  String recipeFit(int percent) {
    return '$percent% match';
  }

  @override
  String get recommendedBecause => 'Recommended because:';

  @override
  String reasonExpires(String food, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'You have $food due to expire in $count days.',
      one: 'You have $food due to expire in $count day.',
    );
    return '$_temp0';
  }

  @override
  String reasonExpiresEstimated(String food, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'You have $food estimated to expire in about $count days.',
      one: 'You have $food estimated to expire in about $count day.',
    );
    return '$_temp0';
  }

  @override
  String reasonExpiresToday(String food) {
    return 'You have $food due to expire today.';
  }

  @override
  String reasonExpiresTodayEstimated(String food) {
    return 'You have $food estimated to expire today.';
  }

  @override
  String get reasonHaveAll => 'You have every ingredient.';

  @override
  String reasonHave(int have, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'You have $have of $count ingredients.',
      one: 'You have $have of $count ingredient.',
    );
    return '$_temp0';
  }

  @override
  String reasonMissing(int count, String foods) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'You need: $foods.',
      one: 'You only need: $foods.',
    );
    return '$_temp0';
  }

  @override
  String reasonMissingMany(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'You need $count more ingredients.',
      one: 'You need $count more ingredient.',
    );
    return '$_temp0';
  }

  @override
  String reasonPartial(String food) {
    return 'You have less $food than the recipe asks for.';
  }

  @override
  String reasonUnknownQuantity(String food) {
    return 'Check how much $food you have: it cannot be compared with the recipe.';
  }

  @override
  String reasonTime(int minutes) {
    return 'About $minutes min.';
  }

  @override
  String get reasonCookedToday => 'You cooked it today.';

  @override
  String reasonCooked(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'You cooked it $count days ago.',
      one: 'You cooked it $count day ago.',
    );
    return '$_temp0';
  }

  @override
  String get ingredientsTitle => 'Ingredients';

  @override
  String get stapleLabel => 'kitchen staple';

  @override
  String get stepsTitle => 'Method';

  @override
  String get nothingAtHome => 'None of the ingredients of this recipe is in your inventory.';

  @override
  String get availabilityEnough => 'You have it';

  @override
  String get availabilityPartial => 'You have less';

  @override
  String get availabilityUnknown => 'Check the amount';

  @override
  String get availabilityMissing => 'Missing';

  @override
  String get badgeToday => 'Expires today';

  @override
  String get badgeTodayEstimated => 'Expires today (estimated)';

  @override
  String badgeDays(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Expires in $count days',
      one: 'Expires in $count day',
    );
    return '$_temp0';
  }

  @override
  String badgeDaysEstimated(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Expires in about $count days (estimated)',
      one: 'Expires in about $count day (estimated)',
    );
    return '$_temp0';
  }

  @override
  String get markCooked => 'I cooked it';

  @override
  String get cookedSaved => 'Noted';

  @override
  String get cookedHelp =>
      'It keeps the same recipe from being recommended every day. It does not change your inventory: consume the food you used.';

  @override
  String get errorRecipeNotFound => 'This recipe does not exist.';

  @override
  String get dietTitle => 'What this household does not eat';

  @override
  String get dietShared =>
      'These restrictions belong to the household: every member can see and change them. Recipes that contain any of this are not shown.';

  @override
  String get dietTypeLabel => 'Diet';

  @override
  String get dietNone => 'No diet';

  @override
  String get dietVegetarian => 'Vegetarian';

  @override
  String get dietVegan => 'Vegan';

  @override
  String get dietVegetarianName => 'Vegetarian diet';

  @override
  String get dietVeganName => 'Vegan diet';

  @override
  String get dietAvoidLabel => 'Also avoid';

  @override
  String dietWithout(String trait) {
    return 'no $trait';
  }

  @override
  String get dietDisclaimer =>
      'This is a help, not a guarantee. What each food contains is indicative: a given product may have other ingredients or traces. With an allergy, always check the label.';

  @override
  String get dietSaved => 'Restrictions saved';

  @override
  String dietApplied(String restrictions) {
    return 'Recipes filtered for this household: $restrictions.';
  }

  @override
  String get dietNotSet => 'This household has no dietary restrictions.';

  @override
  String get dietChange => 'Change';

  @override
  String dietConflict(String traits) {
    return 'This recipe contains something this household does not eat: $traits.';
  }

  @override
  String recipeContains(String traits) {
    return 'Contains: $traits.';
  }

  @override
  String get traitMeat => 'meat';

  @override
  String get traitPork => 'pork';

  @override
  String get traitFish => 'fish';

  @override
  String get traitShellfish => 'shellfish';

  @override
  String get traitDairy => 'dairy';

  @override
  String get traitEgg => 'egg';

  @override
  String get traitGluten => 'gluten';

  @override
  String get traitNuts => 'nuts';

  @override
  String get traitSoy => 'soy';

  @override
  String get traitSesame => 'sesame';

  @override
  String get traitAlcohol => 'alcohol';

  @override
  String get planTitle => 'Plan for the week';

  @override
  String get planPreviousWeek => 'Previous week';

  @override
  String get planNextWeek => 'Next week';

  @override
  String get planThisWeek => 'This week';

  @override
  String planWeekRange(String from, String to) {
    return '$from – $to';
  }

  @override
  String get planToday => 'Today';

  @override
  String get planPastWeek => 'This week is over.';

  @override
  String get planLunch => 'Lunch';

  @override
  String get planDinner => 'Dinner';

  @override
  String get planEmpty => 'Nothing planned';

  @override
  String get planChoose => 'Choose a recipe';

  @override
  String get planChange => 'Change';

  @override
  String get planMove => 'Move';

  @override
  String get planRemove => 'Remove';

  @override
  String get planMealActions => 'Meal options';

  @override
  String get planSuggested => 'Suggested';

  @override
  String get planGenerate => 'Fill the gaps';

  @override
  String get planRegenerate => 'Suggest again';

  @override
  String get planGenerateHelp =>
      'Fills the empty meals, from today on, using first what expires first. What you chose yourself is left alone.';

  @override
  String get planRegenerateConfirm =>
      'The meals suggested automatically for this week will be replaced. The ones you chose are kept.';

  @override
  String planFilled(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count meals have been planned.',
      one: '$count meal has been planned.',
    );
    return '$_temp0';
  }

  @override
  String get planNothingFilled => 'No new meal has been planned.';

  @override
  String planUnfilled(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count meals stay empty: no more recipes fit without too much repetition.',
      one: '$count meal stays empty: no more recipes fit without too much repetition.',
    );
    return '$_temp0';
  }

  @override
  String planNoteUses(String food, String date) {
    return 'Uses $food, due to expire on $date.';
  }

  @override
  String planNoteUsesEstimated(String food, String date) {
    return 'Uses $food, estimated to expire on $date.';
  }

  @override
  String get planNoteHaveAll => 'Every ingredient will be at home.';

  @override
  String planNoteMissing(String foods) {
    return 'To buy: $foods.';
  }

  @override
  String planNotePartial(String food) {
    return 'There will be less $food than the recipe asks for.';
  }

  @override
  String get planUnusedTitle => 'The plan lets these expire';

  @override
  String get planUnusedHelp => 'They expire before the week is over and no planned meal uses them up.';

  @override
  String planUnusedItem(String food, String quantity, String date) {
    return '$food ($quantity), due to expire on $date';
  }

  @override
  String planUnusedItemEstimated(String food, String quantity, String date) {
    return '$food ($quantity), estimated to expire on $date';
  }

  @override
  String get planPickerTitle => 'Choose a recipe';

  @override
  String get planMoveTitle => 'Move to';

  @override
  String planMovePlace(String day, String slot) {
    return '$day · $slot';
  }

  @override
  String planMoveSwap(String recipe) {
    return 'Swaps with $recipe';
  }

  @override
  String get errorMealNotFound => 'That meal is no longer in the plan.';

  @override
  String get errorWeekInThePast => 'A week that is over cannot be planned.';

  @override
  String get planCooked => 'Cooked';

  @override
  String get planAddTitle => 'Add to the plan';

  @override
  String get planAddDay => 'Day';

  @override
  String get planAdd => 'Add to the plan';

  @override
  String planAdded(String place) {
    return 'Added to the plan: $place.';
  }

  @override
  String planAddReplaces(String recipe) {
    return '“$recipe” is already there: it will be replaced.';
  }

  @override
  String get planView => 'See the plan';

  @override
  String get shoppingTitle => 'Shopping list';

  @override
  String get shoppingActions => 'List options';

  @override
  String get shoppingFromPlanThisWeek => 'Add what this week’s plan lacks';

  @override
  String get shoppingFromPlanNextWeek => 'Add what next week’s plan lacks';

  @override
  String get shoppingToList => 'Send what is missing to the list';

  @override
  String shoppingFilled(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'There are $count things on the list for that week’s plan.',
      one: 'There is $count thing on the list for that week’s plan.',
    );
    return '$_temp0';
  }

  @override
  String get shoppingNothingLacking => 'That week’s plan lacks nothing.';

  @override
  String get shoppingView => 'See the list';

  @override
  String get shoppingEmpty => 'The list is empty. Add what you need, or what the plan lacks.';

  @override
  String get shoppingAddTitle => 'Add to the list';

  @override
  String get shoppingWhat => 'What';

  @override
  String get shoppingAmountOptional => 'Amount (optional)';

  @override
  String get shoppingAdd => 'Add';

  @override
  String shoppingForPlan(String day) {
    return 'For the plan · $day';
  }

  @override
  String get shoppingChange => 'Change';

  @override
  String shoppingChangeTitle(String name) {
    return 'How much $name';
  }

  @override
  String get shoppingLineActions => 'Line options';

  @override
  String shoppingRemoveChecked(int count) {
    return 'Remove what was bought ($count)';
  }

  @override
  String shoppingRemoveCheckedConfirm(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count bought things will leave the list.',
      one: '$count bought thing will leave the list.',
    );
    return '$_temp0';
  }

  @override
  String get errorShoppingItemNotFound => 'That is no longer on the list.';
}
