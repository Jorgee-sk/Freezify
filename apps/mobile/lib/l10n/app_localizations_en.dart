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
}
