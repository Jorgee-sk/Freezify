import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_es.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale) : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate = _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates = <LocalizationsDelegate<dynamic>>[
    delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
  ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[Locale('en'), Locale('es')];

  /// No description provided for @appName.
  ///
  /// In en, this message translates to:
  /// **'Freezify'**
  String get appName;

  /// No description provided for @appTagline.
  ///
  /// In en, this message translates to:
  /// **'Know what you have, what expires and what you can cook.'**
  String get appTagline;

  /// No description provided for @logout.
  ///
  /// In en, this message translates to:
  /// **'Sign out'**
  String get logout;

  /// No description provided for @language.
  ///
  /// In en, this message translates to:
  /// **'Language'**
  String get language;

  /// No description provided for @loginTitle.
  ///
  /// In en, this message translates to:
  /// **'Sign in'**
  String get loginTitle;

  /// No description provided for @registerTitle.
  ///
  /// In en, this message translates to:
  /// **'Create your account'**
  String get registerTitle;

  /// No description provided for @email.
  ///
  /// In en, this message translates to:
  /// **'Email'**
  String get email;

  /// No description provided for @password.
  ///
  /// In en, this message translates to:
  /// **'Password'**
  String get password;

  /// No description provided for @passwordHint.
  ///
  /// In en, this message translates to:
  /// **'At least 8 characters'**
  String get passwordHint;

  /// No description provided for @displayName.
  ///
  /// In en, this message translates to:
  /// **'Your name'**
  String get displayName;

  /// No description provided for @loginAction.
  ///
  /// In en, this message translates to:
  /// **'Sign in'**
  String get loginAction;

  /// No description provided for @registerAction.
  ///
  /// In en, this message translates to:
  /// **'Create account'**
  String get registerAction;

  /// No description provided for @noAccount.
  ///
  /// In en, this message translates to:
  /// **'No account yet? Sign up'**
  String get noAccount;

  /// No description provided for @haveAccount.
  ///
  /// In en, this message translates to:
  /// **'Already have an account? Sign in'**
  String get haveAccount;

  /// No description provided for @fieldRequired.
  ///
  /// In en, this message translates to:
  /// **'Required'**
  String get fieldRequired;

  /// No description provided for @passwordTooShort.
  ///
  /// In en, this message translates to:
  /// **'At least 8 characters'**
  String get passwordTooShort;

  /// No description provided for @greeting.
  ///
  /// In en, this message translates to:
  /// **'Hi, {name} 👋'**
  String greeting(String name);

  /// No description provided for @householdsTitle.
  ///
  /// In en, this message translates to:
  /// **'My households'**
  String get householdsTitle;

  /// No description provided for @householdsEmpty.
  ///
  /// In en, this message translates to:
  /// **'You don\'t belong to any household yet. Create one or join with an invitation code.'**
  String get householdsEmpty;

  /// No description provided for @createHousehold.
  ///
  /// In en, this message translates to:
  /// **'Create a household'**
  String get createHousehold;

  /// No description provided for @householdName.
  ///
  /// In en, this message translates to:
  /// **'Household name'**
  String get householdName;

  /// No description provided for @create.
  ///
  /// In en, this message translates to:
  /// **'Create'**
  String get create;

  /// No description provided for @joinHousehold.
  ///
  /// In en, this message translates to:
  /// **'Join with a code'**
  String get joinHousehold;

  /// No description provided for @invitationCode.
  ///
  /// In en, this message translates to:
  /// **'Invitation code'**
  String get invitationCode;

  /// No description provided for @join.
  ///
  /// In en, this message translates to:
  /// **'Join'**
  String get join;

  /// No description provided for @memberCount.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{count} member} other{{count} members}}'**
  String memberCount(int count);

  /// No description provided for @roleOwner.
  ///
  /// In en, this message translates to:
  /// **'Owner'**
  String get roleOwner;

  /// No description provided for @roleMember.
  ///
  /// In en, this message translates to:
  /// **'Member'**
  String get roleMember;

  /// No description provided for @membersTitle.
  ///
  /// In en, this message translates to:
  /// **'Members'**
  String get membersTitle;

  /// No description provided for @you.
  ///
  /// In en, this message translates to:
  /// **'you'**
  String get you;

  /// No description provided for @joinedOn.
  ///
  /// In en, this message translates to:
  /// **'Since {date}'**
  String joinedOn(String date);

  /// No description provided for @inviteTitle.
  ///
  /// In en, this message translates to:
  /// **'Invite someone'**
  String get inviteTitle;

  /// No description provided for @inviteHelp.
  ///
  /// In en, this message translates to:
  /// **'Generate a code and share it. Whoever enters it will join this household.'**
  String get inviteHelp;

  /// No description provided for @inviteGenerate.
  ///
  /// In en, this message translates to:
  /// **'Generate code'**
  String get inviteGenerate;

  /// No description provided for @inviteExpires.
  ///
  /// In en, this message translates to:
  /// **'Valid until {date}'**
  String inviteExpires(String date);

  /// No description provided for @rename.
  ///
  /// In en, this message translates to:
  /// **'Rename'**
  String get rename;

  /// No description provided for @save.
  ///
  /// In en, this message translates to:
  /// **'Save'**
  String get save;

  /// No description provided for @cancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get cancel;

  /// No description provided for @remove.
  ///
  /// In en, this message translates to:
  /// **'Remove'**
  String get remove;

  /// No description provided for @leaveHousehold.
  ///
  /// In en, this message translates to:
  /// **'Leave household'**
  String get leaveHousehold;

  /// No description provided for @deleteHousehold.
  ///
  /// In en, this message translates to:
  /// **'Delete household'**
  String get deleteHousehold;

  /// No description provided for @confirmRemove.
  ///
  /// In en, this message translates to:
  /// **'Remove {name} from this household?'**
  String confirmRemove(String name);

  /// No description provided for @confirmLeave.
  ///
  /// In en, this message translates to:
  /// **'Are you sure you want to leave “{name}”?'**
  String confirmLeave(String name);

  /// No description provided for @confirmDelete.
  ///
  /// In en, this message translates to:
  /// **'“{name}” will be deleted for all its members. This cannot be undone.'**
  String confirmDelete(String name);

  /// No description provided for @retry.
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get retry;

  /// No description provided for @errorGeneric.
  ///
  /// In en, this message translates to:
  /// **'Something went wrong. Please try again.'**
  String get errorGeneric;

  /// No description provided for @errorNetwork.
  ///
  /// In en, this message translates to:
  /// **'Cannot reach the server.'**
  String get errorNetwork;

  /// No description provided for @errorValidation.
  ///
  /// In en, this message translates to:
  /// **'Please check the information you entered.'**
  String get errorValidation;

  /// No description provided for @errorEmailTaken.
  ///
  /// In en, this message translates to:
  /// **'An account with that email already exists.'**
  String get errorEmailTaken;

  /// No description provided for @errorInvalidCredentials.
  ///
  /// In en, this message translates to:
  /// **'Incorrect email or password.'**
  String get errorInvalidCredentials;

  /// No description provided for @errorPasswordTooLong.
  ///
  /// In en, this message translates to:
  /// **'The password is too long.'**
  String get errorPasswordTooLong;

  /// No description provided for @errorRateLimited.
  ///
  /// In en, this message translates to:
  /// **'Too many attempts. Please wait a moment.'**
  String get errorRateLimited;

  /// No description provided for @errorHouseholdNotFound.
  ///
  /// In en, this message translates to:
  /// **'This household doesn\'t exist or you no longer belong to it.'**
  String get errorHouseholdNotFound;

  /// No description provided for @errorNotOwner.
  ///
  /// In en, this message translates to:
  /// **'Only the household owner can do this.'**
  String get errorNotOwner;

  /// No description provided for @errorOwnerCannotLeave.
  ///
  /// In en, this message translates to:
  /// **'The owner cannot leave the household. Delete it if you no longer need it.'**
  String get errorOwnerCannotLeave;

  /// No description provided for @errorAlreadyMember.
  ///
  /// In en, this message translates to:
  /// **'You already belong to this household.'**
  String get errorAlreadyMember;

  /// No description provided for @errorInvitationNotFound.
  ///
  /// In en, this message translates to:
  /// **'The code is invalid or has expired.'**
  String get errorInvitationNotFound;

  /// No description provided for @errorMemberNotFound.
  ///
  /// In en, this message translates to:
  /// **'That person no longer belongs to the household.'**
  String get errorMemberNotFound;

  /// No description provided for @settings.
  ///
  /// In en, this message translates to:
  /// **'Members and settings'**
  String get settings;

  /// No description provided for @inventoryTitle.
  ///
  /// In en, this message translates to:
  /// **'Inventory'**
  String get inventoryTitle;

  /// No description provided for @inventoryCount.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{count} food} other{{count} foods}}'**
  String inventoryCount(int count);

  /// No description provided for @inventoryEmpty.
  ///
  /// In en, this message translates to:
  /// **'No food yet. Add what you have at home to get started.'**
  String get inventoryEmpty;

  /// No description provided for @inventoryEmptyFiltered.
  ///
  /// In en, this message translates to:
  /// **'No food matches the filter.'**
  String get inventoryEmptyFiltered;

  /// No description provided for @addFood.
  ///
  /// In en, this message translates to:
  /// **'Add food'**
  String get addFood;

  /// No description provided for @newFood.
  ///
  /// In en, this message translates to:
  /// **'New food'**
  String get newFood;

  /// No description provided for @editFood.
  ///
  /// In en, this message translates to:
  /// **'Edit food'**
  String get editFood;

  /// No description provided for @searchInventory.
  ///
  /// In en, this message translates to:
  /// **'Search the inventory…'**
  String get searchInventory;

  /// No description provided for @allLocations.
  ///
  /// In en, this message translates to:
  /// **'All'**
  String get allLocations;

  /// No description provided for @filters.
  ///
  /// In en, this message translates to:
  /// **'Filters'**
  String get filters;

  /// No description provided for @category.
  ///
  /// In en, this message translates to:
  /// **'Category'**
  String get category;

  /// No description provided for @allCategories.
  ///
  /// In en, this message translates to:
  /// **'All categories'**
  String get allCategories;

  /// No description provided for @show.
  ///
  /// In en, this message translates to:
  /// **'Show'**
  String get show;

  /// No description provided for @stateActive.
  ///
  /// In en, this message translates to:
  /// **'At home'**
  String get stateActive;

  /// No description provided for @stateFinished.
  ///
  /// In en, this message translates to:
  /// **'Finished'**
  String get stateFinished;

  /// No description provided for @stateAll.
  ///
  /// In en, this message translates to:
  /// **'All'**
  String get stateAll;

  /// No description provided for @foodName.
  ///
  /// In en, this message translates to:
  /// **'Food'**
  String get foodName;

  /// No description provided for @suggestions.
  ///
  /// In en, this message translates to:
  /// **'Suggestions'**
  String get suggestions;

  /// No description provided for @recentlyAdded.
  ///
  /// In en, this message translates to:
  /// **'Recently added'**
  String get recentlyAdded;

  /// No description provided for @amount.
  ///
  /// In en, this message translates to:
  /// **'Quantity'**
  String get amount;

  /// No description provided for @unit.
  ///
  /// In en, this message translates to:
  /// **'Unit'**
  String get unit;

  /// No description provided for @location.
  ///
  /// In en, this message translates to:
  /// **'Location'**
  String get location;

  /// No description provided for @expirationDate.
  ///
  /// In en, this message translates to:
  /// **'Expiration date'**
  String get expirationDate;

  /// No description provided for @noDateSet.
  ///
  /// In en, this message translates to:
  /// **'Not set'**
  String get noDateSet;

  /// No description provided for @clearDate.
  ///
  /// In en, this message translates to:
  /// **'Remove date'**
  String get clearDate;

  /// No description provided for @moreDetails.
  ///
  /// In en, this message translates to:
  /// **'More details'**
  String get moreDetails;

  /// No description provided for @purchaseDate.
  ///
  /// In en, this message translates to:
  /// **'Purchase date'**
  String get purchaseDate;

  /// No description provided for @brand.
  ///
  /// In en, this message translates to:
  /// **'Brand'**
  String get brand;

  /// No description provided for @price.
  ///
  /// In en, this message translates to:
  /// **'Estimated price (€)'**
  String get price;

  /// No description provided for @notes.
  ///
  /// In en, this message translates to:
  /// **'Notes'**
  String get notes;

  /// No description provided for @invalidAmount.
  ///
  /// In en, this message translates to:
  /// **'Enter a quantity greater than zero.'**
  String get invalidAmount;

  /// No description provided for @expiresOn.
  ///
  /// In en, this message translates to:
  /// **'Expires on {date}'**
  String expiresOn(String date);

  /// No description provided for @expiresEstimated.
  ///
  /// In en, this message translates to:
  /// **'Expires around {date} (estimated date)'**
  String expiresEstimated(String date);

  /// No description provided for @noExpirationDate.
  ///
  /// In en, this message translates to:
  /// **'No expiration date'**
  String get noExpirationDate;

  /// No description provided for @openedOn.
  ///
  /// In en, this message translates to:
  /// **'Opened on {date}'**
  String openedOn(String date);

  /// No description provided for @statusAvailable.
  ///
  /// In en, this message translates to:
  /// **'Available'**
  String get statusAvailable;

  /// No description provided for @statusOpened.
  ///
  /// In en, this message translates to:
  /// **'Opened'**
  String get statusOpened;

  /// No description provided for @statusExpired.
  ///
  /// In en, this message translates to:
  /// **'Expired'**
  String get statusExpired;

  /// No description provided for @statusConsumed.
  ///
  /// In en, this message translates to:
  /// **'Consumed'**
  String get statusConsumed;

  /// No description provided for @statusDiscarded.
  ///
  /// In en, this message translates to:
  /// **'Discarded'**
  String get statusDiscarded;

  /// No description provided for @consume.
  ///
  /// In en, this message translates to:
  /// **'Consume'**
  String get consume;

  /// No description provided for @discard.
  ///
  /// In en, this message translates to:
  /// **'Discard'**
  String get discard;

  /// No description provided for @markOpened.
  ///
  /// In en, this message translates to:
  /// **'Mark as opened'**
  String get markOpened;

  /// No description provided for @edit.
  ///
  /// In en, this message translates to:
  /// **'Edit'**
  String get edit;

  /// No description provided for @delete.
  ///
  /// In en, this message translates to:
  /// **'Delete'**
  String get delete;

  /// No description provided for @itemActions.
  ///
  /// In en, this message translates to:
  /// **'Actions'**
  String get itemActions;

  /// No description provided for @consumeTitle.
  ///
  /// In en, this message translates to:
  /// **'How much did you consume?'**
  String get consumeTitle;

  /// No description provided for @discardTitle.
  ///
  /// In en, this message translates to:
  /// **'How much did you throw away?'**
  String get discardTitle;

  /// No description provided for @reason.
  ///
  /// In en, this message translates to:
  /// **'Reason'**
  String get reason;

  /// No description provided for @reasonExpired.
  ///
  /// In en, this message translates to:
  /// **'Expired'**
  String get reasonExpired;

  /// No description provided for @reasonSpoiled.
  ///
  /// In en, this message translates to:
  /// **'Spoiled'**
  String get reasonSpoiled;

  /// No description provided for @reasonLeftover.
  ///
  /// In en, this message translates to:
  /// **'Leftovers'**
  String get reasonLeftover;

  /// No description provided for @reasonOther.
  ///
  /// In en, this message translates to:
  /// **'Other'**
  String get reasonOther;

  /// No description provided for @confirm.
  ///
  /// In en, this message translates to:
  /// **'Confirm'**
  String get confirm;

  /// No description provided for @confirmDeleteItem.
  ///
  /// In en, this message translates to:
  /// **'Delete “{name}” from the inventory? It will not count as consumed or wasted.'**
  String confirmDeleteItem(String name);

  /// No description provided for @previousPage.
  ///
  /// In en, this message translates to:
  /// **'Previous'**
  String get previousPage;

  /// No description provided for @nextPage.
  ///
  /// In en, this message translates to:
  /// **'Next'**
  String get nextPage;

  /// No description provided for @pageOf.
  ///
  /// In en, this message translates to:
  /// **'Page {page} of {total}'**
  String pageOf(int page, int total);

  /// No description provided for @unitAbbreviation.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{unit} other{units}}'**
  String unitAbbreviation(num count);

  /// No description provided for @unitNameUnit.
  ///
  /// In en, this message translates to:
  /// **'units'**
  String get unitNameUnit;

  /// No description provided for @unitNameGram.
  ///
  /// In en, this message translates to:
  /// **'grams'**
  String get unitNameGram;

  /// No description provided for @unitNameKilogram.
  ///
  /// In en, this message translates to:
  /// **'kilograms'**
  String get unitNameKilogram;

  /// No description provided for @unitNameMilliliter.
  ///
  /// In en, this message translates to:
  /// **'milliliters'**
  String get unitNameMilliliter;

  /// No description provided for @unitNameLiter.
  ///
  /// In en, this message translates to:
  /// **'liters'**
  String get unitNameLiter;

  /// No description provided for @locationRefrigerator.
  ///
  /// In en, this message translates to:
  /// **'Fridge'**
  String get locationRefrigerator;

  /// No description provided for @locationFreezer.
  ///
  /// In en, this message translates to:
  /// **'Freezer'**
  String get locationFreezer;

  /// No description provided for @locationPantry.
  ///
  /// In en, this message translates to:
  /// **'Pantry'**
  String get locationPantry;

  /// No description provided for @locationOther.
  ///
  /// In en, this message translates to:
  /// **'Other'**
  String get locationOther;

  /// No description provided for @categoryVegetables.
  ///
  /// In en, this message translates to:
  /// **'Vegetables'**
  String get categoryVegetables;

  /// No description provided for @categoryFruits.
  ///
  /// In en, this message translates to:
  /// **'Fruit'**
  String get categoryFruits;

  /// No description provided for @categoryMeat.
  ///
  /// In en, this message translates to:
  /// **'Meat'**
  String get categoryMeat;

  /// No description provided for @categoryFish.
  ///
  /// In en, this message translates to:
  /// **'Fish'**
  String get categoryFish;

  /// No description provided for @categoryDairy.
  ///
  /// In en, this message translates to:
  /// **'Dairy'**
  String get categoryDairy;

  /// No description provided for @categoryEggs.
  ///
  /// In en, this message translates to:
  /// **'Eggs'**
  String get categoryEggs;

  /// No description provided for @categoryBakery.
  ///
  /// In en, this message translates to:
  /// **'Bakery'**
  String get categoryBakery;

  /// No description provided for @categoryPantry.
  ///
  /// In en, this message translates to:
  /// **'Pantry'**
  String get categoryPantry;

  /// No description provided for @categoryFrozen.
  ///
  /// In en, this message translates to:
  /// **'Frozen'**
  String get categoryFrozen;

  /// No description provided for @categoryBeverages.
  ///
  /// In en, this message translates to:
  /// **'Drinks'**
  String get categoryBeverages;

  /// No description provided for @categoryPrepared.
  ///
  /// In en, this message translates to:
  /// **'Ready meals'**
  String get categoryPrepared;

  /// No description provided for @categoryOther.
  ///
  /// In en, this message translates to:
  /// **'Other'**
  String get categoryOther;

  /// No description provided for @errorItemNotFound.
  ///
  /// In en, this message translates to:
  /// **'This food is no longer in the inventory.'**
  String get errorItemNotFound;

  /// No description provided for @errorItemNotActive.
  ///
  /// In en, this message translates to:
  /// **'This food was already consumed or discarded.'**
  String get errorItemNotActive;

  /// No description provided for @errorFoodNotFound.
  ///
  /// In en, this message translates to:
  /// **'That food does not exist in the catalog.'**
  String get errorFoodNotFound;

  /// No description provided for @errorIncompatibleUnit.
  ///
  /// In en, this message translates to:
  /// **'That unit is not compatible with the unit of the food.'**
  String get errorIncompatibleUnit;

  /// No description provided for @errorQuantityExceeds.
  ///
  /// In en, this message translates to:
  /// **'There is less left than the quantity given.'**
  String get errorQuantityExceeds;

  /// No description provided for @errorConcurrentModification.
  ///
  /// In en, this message translates to:
  /// **'Someone else just changed this. Try again.'**
  String get errorConcurrentModification;

  /// No description provided for @priorityExpired.
  ///
  /// In en, this message translates to:
  /// **'Expired'**
  String get priorityExpired;

  /// No description provided for @priorityToday.
  ///
  /// In en, this message translates to:
  /// **'Expires today'**
  String get priorityToday;

  /// No description provided for @priorityUrgent.
  ///
  /// In en, this message translates to:
  /// **'Urgent'**
  String get priorityUrgent;

  /// No description provided for @prioritySoon.
  ///
  /// In en, this message translates to:
  /// **'Eat soon'**
  String get prioritySoon;

  /// No description provided for @priorityUpcoming.
  ///
  /// In en, this message translates to:
  /// **'Coming up'**
  String get priorityUpcoming;

  /// No description provided for @daysLeft.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{count} day left} other{{count} days left}}'**
  String daysLeft(int count);

  /// No description provided for @daysAgo.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{count} day ago} other{{count} days ago}}'**
  String daysAgo(int count);

  /// No description provided for @consumeFirstTitle.
  ///
  /// In en, this message translates to:
  /// **'Eat first'**
  String get consumeFirstTitle;

  /// No description provided for @consumeFirstCount.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{count} food that cannot wait} other{{count} foods that cannot wait}}'**
  String consumeFirstCount(int count);

  /// No description provided for @andMore.
  ///
  /// In en, this message translates to:
  /// **'and {count} more'**
  String andMore(int count);

  /// No description provided for @estimateHelp.
  ///
  /// In en, this message translates to:
  /// **'Leave it empty and we will estimate it from the food and where you keep it.'**
  String get estimateHelp;

  /// No description provided for @currentEstimate.
  ///
  /// In en, this message translates to:
  /// **'Right now we estimate {date}. Enter the date on the package if you know it.'**
  String currentEstimate(String date);

  /// No description provided for @notificationsTitle.
  ///
  /// In en, this message translates to:
  /// **'Notifications'**
  String get notificationsTitle;

  /// No description provided for @notificationsUnread.
  ///
  /// In en, this message translates to:
  /// **'Notifications: {count} unread'**
  String notificationsUnread(int count);

  /// No description provided for @notificationsEmpty.
  ///
  /// In en, this message translates to:
  /// **'You have no notifications. We will tell you when something is about to expire.'**
  String get notificationsEmpty;

  /// No description provided for @markAllRead.
  ///
  /// In en, this message translates to:
  /// **'Mark all as read'**
  String get markAllRead;

  /// No description provided for @notificationNew.
  ///
  /// In en, this message translates to:
  /// **'New'**
  String get notificationNew;

  /// No description provided for @notificationPreferences.
  ///
  /// In en, this message translates to:
  /// **'Notification preferences'**
  String get notificationPreferences;

  /// No description provided for @notificationSummary.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{You have {count} food you should eat soon} other{You have {count} foods you should eat soon}}'**
  String notificationSummary(int count);

  /// No description provided for @notifiedExpired.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{name} expired {count} day ago} other{{name} expired {count} days ago}}'**
  String notifiedExpired(String name, int count);

  /// No description provided for @notifiedToday.
  ///
  /// In en, this message translates to:
  /// **'{name} expires today'**
  String notifiedToday(String name);

  /// No description provided for @notifiedLeft.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{name} expires in {count} day} other{{name} expires in {count} days}}'**
  String notifiedLeft(String name, int count);

  /// No description provided for @notifiedExpiredEstimated.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{name} probably expired {count} day ago (estimated date)} other{{name} probably expired {count} days ago (estimated date)}}'**
  String notifiedExpiredEstimated(String name, int count);

  /// No description provided for @notifiedTodayEstimated.
  ///
  /// In en, this message translates to:
  /// **'{name} probably expires today (estimated date)'**
  String notifiedTodayEstimated(String name);

  /// No description provided for @notifiedLeftEstimated.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{name} expires in about {count} day (estimated date)} other{{name} expires in about {count} days (estimated date)}}'**
  String notifiedLeftEstimated(String name, int count);

  /// No description provided for @prefEnabled.
  ///
  /// In en, this message translates to:
  /// **'Tell me about food that is about to expire'**
  String get prefEnabled;

  /// No description provided for @prefAntiSpam.
  ///
  /// In en, this message translates to:
  /// **'At most one notification a day per household, and only when there is something new to tell you.'**
  String get prefAntiSpam;

  /// No description provided for @prefHour.
  ///
  /// In en, this message translates to:
  /// **'From'**
  String get prefHour;

  /// No description provided for @prefHourHelp.
  ///
  /// In en, this message translates to:
  /// **'Spanish mainland time.'**
  String get prefHourHelp;

  /// No description provided for @prefFrequency.
  ///
  /// In en, this message translates to:
  /// **'At most'**
  String get prefFrequency;

  /// No description provided for @prefFrequencyDaily.
  ///
  /// In en, this message translates to:
  /// **'Once a day'**
  String get prefFrequencyDaily;

  /// No description provided for @prefFrequencyEveryThreeDays.
  ///
  /// In en, this message translates to:
  /// **'Every 3 days'**
  String get prefFrequencyEveryThreeDays;

  /// No description provided for @prefFrequencyWeekly.
  ///
  /// In en, this message translates to:
  /// **'Once a week'**
  String get prefFrequencyWeekly;

  /// No description provided for @prefThreshold.
  ///
  /// In en, this message translates to:
  /// **'Tell me when a food'**
  String get prefThreshold;

  /// No description provided for @prefThresholdToday.
  ///
  /// In en, this message translates to:
  /// **'Expires today'**
  String get prefThresholdToday;

  /// No description provided for @prefThresholdUrgent.
  ///
  /// In en, this message translates to:
  /// **'Expires in 2 days or less'**
  String get prefThresholdUrgent;

  /// No description provided for @prefThresholdSoon.
  ///
  /// In en, this message translates to:
  /// **'Expires in 5 days or less'**
  String get prefThresholdSoon;

  /// No description provided for @prefCategories.
  ///
  /// In en, this message translates to:
  /// **'Tell me about these categories'**
  String get prefCategories;

  /// No description provided for @preferencesSaved.
  ///
  /// In en, this message translates to:
  /// **'Preferences saved'**
  String get preferencesSaved;

  /// No description provided for @errorNotificationNotFound.
  ///
  /// In en, this message translates to:
  /// **'This notification no longer exists.'**
  String get errorNotificationNotFound;

  /// No description provided for @recipesTitle.
  ///
  /// In en, this message translates to:
  /// **'Recipes'**
  String get recipesTitle;

  /// No description provided for @recommendedTitle.
  ///
  /// In en, this message translates to:
  /// **'What to cook with what you have'**
  String get recommendedTitle;

  /// No description provided for @noRecommendations.
  ///
  /// In en, this message translates to:
  /// **'No recipe uses what is in your inventory. Add some food and look again.'**
  String get noRecommendations;

  /// No description provided for @catalogTitle.
  ///
  /// In en, this message translates to:
  /// **'All recipes'**
  String get catalogTitle;

  /// No description provided for @searchRecipes.
  ///
  /// In en, this message translates to:
  /// **'Search recipes…'**
  String get searchRecipes;

  /// No description provided for @anyCourse.
  ///
  /// In en, this message translates to:
  /// **'All'**
  String get anyCourse;

  /// No description provided for @courseMain.
  ///
  /// In en, this message translates to:
  /// **'Main course'**
  String get courseMain;

  /// No description provided for @courseBreakfast.
  ///
  /// In en, this message translates to:
  /// **'Breakfast'**
  String get courseBreakfast;

  /// No description provided for @courseDessert.
  ///
  /// In en, this message translates to:
  /// **'Dessert'**
  String get courseDessert;

  /// No description provided for @difficultyEasy.
  ///
  /// In en, this message translates to:
  /// **'Easy'**
  String get difficultyEasy;

  /// No description provided for @difficultyMedium.
  ///
  /// In en, this message translates to:
  /// **'Medium'**
  String get difficultyMedium;

  /// No description provided for @difficultyHard.
  ///
  /// In en, this message translates to:
  /// **'Hard'**
  String get difficultyHard;

  /// No description provided for @recipeMinutes.
  ///
  /// In en, this message translates to:
  /// **'{count} min'**
  String recipeMinutes(int count);

  /// No description provided for @recipeServings.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{count} serving} other{{count} servings}}'**
  String recipeServings(int count);

  /// No description provided for @upToMinutes.
  ///
  /// In en, this message translates to:
  /// **'Up to {count} min'**
  String upToMinutes(int count);

  /// No description provided for @recipesEmptyFiltered.
  ///
  /// In en, this message translates to:
  /// **'No recipe matches the filter.'**
  String get recipesEmptyFiltered;

  /// No description provided for @recipeFit.
  ///
  /// In en, this message translates to:
  /// **'{percent}% match'**
  String recipeFit(int percent);

  /// No description provided for @recommendedBecause.
  ///
  /// In en, this message translates to:
  /// **'Recommended because:'**
  String get recommendedBecause;

  /// No description provided for @reasonExpires.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{You have {food} due to expire in {count} day.} other{You have {food} due to expire in {count} days.}}'**
  String reasonExpires(String food, int count);

  /// No description provided for @reasonExpiresEstimated.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{You have {food} estimated to expire in about {count} day.} other{You have {food} estimated to expire in about {count} days.}}'**
  String reasonExpiresEstimated(String food, int count);

  /// No description provided for @reasonExpiresToday.
  ///
  /// In en, this message translates to:
  /// **'You have {food} due to expire today.'**
  String reasonExpiresToday(String food);

  /// No description provided for @reasonExpiresTodayEstimated.
  ///
  /// In en, this message translates to:
  /// **'You have {food} estimated to expire today.'**
  String reasonExpiresTodayEstimated(String food);

  /// No description provided for @reasonHaveAll.
  ///
  /// In en, this message translates to:
  /// **'You have every ingredient.'**
  String get reasonHaveAll;

  /// No description provided for @reasonHave.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{You have {have} of {count} ingredient.} other{You have {have} of {count} ingredients.}}'**
  String reasonHave(int have, int count);

  /// No description provided for @reasonMissing.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{You only need: {foods}.} other{You need: {foods}.}}'**
  String reasonMissing(int count, String foods);

  /// No description provided for @reasonMissingMany.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{You need {count} more ingredient.} other{You need {count} more ingredients.}}'**
  String reasonMissingMany(int count);

  /// No description provided for @reasonPartial.
  ///
  /// In en, this message translates to:
  /// **'You have less {food} than the recipe asks for.'**
  String reasonPartial(String food);

  /// No description provided for @reasonUnknownQuantity.
  ///
  /// In en, this message translates to:
  /// **'Check how much {food} you have: it cannot be compared with the recipe.'**
  String reasonUnknownQuantity(String food);

  /// No description provided for @reasonTime.
  ///
  /// In en, this message translates to:
  /// **'About {minutes} min.'**
  String reasonTime(int minutes);

  /// No description provided for @reasonCookedToday.
  ///
  /// In en, this message translates to:
  /// **'You cooked it today.'**
  String get reasonCookedToday;

  /// No description provided for @reasonCooked.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{You cooked it {count} day ago.} other{You cooked it {count} days ago.}}'**
  String reasonCooked(int count);

  /// No description provided for @ingredientsTitle.
  ///
  /// In en, this message translates to:
  /// **'Ingredients'**
  String get ingredientsTitle;

  /// No description provided for @stapleLabel.
  ///
  /// In en, this message translates to:
  /// **'kitchen staple'**
  String get stapleLabel;

  /// No description provided for @stepsTitle.
  ///
  /// In en, this message translates to:
  /// **'Method'**
  String get stepsTitle;

  /// No description provided for @nothingAtHome.
  ///
  /// In en, this message translates to:
  /// **'None of the ingredients of this recipe is in your inventory.'**
  String get nothingAtHome;

  /// No description provided for @availabilityEnough.
  ///
  /// In en, this message translates to:
  /// **'You have it'**
  String get availabilityEnough;

  /// No description provided for @availabilityPartial.
  ///
  /// In en, this message translates to:
  /// **'You have less'**
  String get availabilityPartial;

  /// No description provided for @availabilityUnknown.
  ///
  /// In en, this message translates to:
  /// **'Check the amount'**
  String get availabilityUnknown;

  /// No description provided for @availabilityMissing.
  ///
  /// In en, this message translates to:
  /// **'Missing'**
  String get availabilityMissing;

  /// No description provided for @badgeToday.
  ///
  /// In en, this message translates to:
  /// **'Expires today'**
  String get badgeToday;

  /// No description provided for @badgeTodayEstimated.
  ///
  /// In en, this message translates to:
  /// **'Expires today (estimated)'**
  String get badgeTodayEstimated;

  /// No description provided for @badgeDays.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{Expires in {count} day} other{Expires in {count} days}}'**
  String badgeDays(int count);

  /// No description provided for @badgeDaysEstimated.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{Expires in about {count} day (estimated)} other{Expires in about {count} days (estimated)}}'**
  String badgeDaysEstimated(int count);

  /// No description provided for @markCooked.
  ///
  /// In en, this message translates to:
  /// **'I cooked it'**
  String get markCooked;

  /// No description provided for @cookedSaved.
  ///
  /// In en, this message translates to:
  /// **'Noted'**
  String get cookedSaved;

  /// No description provided for @cookedHelp.
  ///
  /// In en, this message translates to:
  /// **'It keeps the same recipe from being recommended every day. It does not change your inventory: consume the food you used.'**
  String get cookedHelp;

  /// No description provided for @errorRecipeNotFound.
  ///
  /// In en, this message translates to:
  /// **'This recipe does not exist.'**
  String get errorRecipeNotFound;

  /// No description provided for @dietTitle.
  ///
  /// In en, this message translates to:
  /// **'What this household does not eat'**
  String get dietTitle;

  /// No description provided for @dietShared.
  ///
  /// In en, this message translates to:
  /// **'These restrictions belong to the household: every member can see and change them. Recipes that contain any of this are not shown.'**
  String get dietShared;

  /// No description provided for @dietTypeLabel.
  ///
  /// In en, this message translates to:
  /// **'Diet'**
  String get dietTypeLabel;

  /// No description provided for @dietNone.
  ///
  /// In en, this message translates to:
  /// **'No diet'**
  String get dietNone;

  /// No description provided for @dietVegetarian.
  ///
  /// In en, this message translates to:
  /// **'Vegetarian'**
  String get dietVegetarian;

  /// No description provided for @dietVegan.
  ///
  /// In en, this message translates to:
  /// **'Vegan'**
  String get dietVegan;

  /// No description provided for @dietVegetarianName.
  ///
  /// In en, this message translates to:
  /// **'Vegetarian diet'**
  String get dietVegetarianName;

  /// No description provided for @dietVeganName.
  ///
  /// In en, this message translates to:
  /// **'Vegan diet'**
  String get dietVeganName;

  /// No description provided for @dietAvoidLabel.
  ///
  /// In en, this message translates to:
  /// **'Also avoid'**
  String get dietAvoidLabel;

  /// No description provided for @dietWithout.
  ///
  /// In en, this message translates to:
  /// **'no {trait}'**
  String dietWithout(String trait);

  /// No description provided for @dietDisclaimer.
  ///
  /// In en, this message translates to:
  /// **'This is a help, not a guarantee. What each food contains is indicative: a given product may have other ingredients or traces. With an allergy, always check the label.'**
  String get dietDisclaimer;

  /// No description provided for @dietSaved.
  ///
  /// In en, this message translates to:
  /// **'Restrictions saved'**
  String get dietSaved;

  /// No description provided for @dietApplied.
  ///
  /// In en, this message translates to:
  /// **'Recipes filtered for this household: {restrictions}.'**
  String dietApplied(String restrictions);

  /// No description provided for @dietNotSet.
  ///
  /// In en, this message translates to:
  /// **'This household has no dietary restrictions.'**
  String get dietNotSet;

  /// No description provided for @dietChange.
  ///
  /// In en, this message translates to:
  /// **'Change'**
  String get dietChange;

  /// No description provided for @dietConflict.
  ///
  /// In en, this message translates to:
  /// **'This recipe contains something this household does not eat: {traits}.'**
  String dietConflict(String traits);

  /// No description provided for @recipeContains.
  ///
  /// In en, this message translates to:
  /// **'Contains: {traits}.'**
  String recipeContains(String traits);

  /// No description provided for @traitMeat.
  ///
  /// In en, this message translates to:
  /// **'meat'**
  String get traitMeat;

  /// No description provided for @traitPork.
  ///
  /// In en, this message translates to:
  /// **'pork'**
  String get traitPork;

  /// No description provided for @traitFish.
  ///
  /// In en, this message translates to:
  /// **'fish'**
  String get traitFish;

  /// No description provided for @traitShellfish.
  ///
  /// In en, this message translates to:
  /// **'shellfish'**
  String get traitShellfish;

  /// No description provided for @traitDairy.
  ///
  /// In en, this message translates to:
  /// **'dairy'**
  String get traitDairy;

  /// No description provided for @traitEgg.
  ///
  /// In en, this message translates to:
  /// **'egg'**
  String get traitEgg;

  /// No description provided for @traitGluten.
  ///
  /// In en, this message translates to:
  /// **'gluten'**
  String get traitGluten;

  /// No description provided for @traitNuts.
  ///
  /// In en, this message translates to:
  /// **'nuts'**
  String get traitNuts;

  /// No description provided for @traitSoy.
  ///
  /// In en, this message translates to:
  /// **'soy'**
  String get traitSoy;

  /// No description provided for @traitSesame.
  ///
  /// In en, this message translates to:
  /// **'sesame'**
  String get traitSesame;

  /// No description provided for @traitAlcohol.
  ///
  /// In en, this message translates to:
  /// **'alcohol'**
  String get traitAlcohol;

  /// No description provided for @planTitle.
  ///
  /// In en, this message translates to:
  /// **'Plan for the week'**
  String get planTitle;

  /// No description provided for @planPreviousWeek.
  ///
  /// In en, this message translates to:
  /// **'Previous week'**
  String get planPreviousWeek;

  /// No description provided for @planNextWeek.
  ///
  /// In en, this message translates to:
  /// **'Next week'**
  String get planNextWeek;

  /// No description provided for @planThisWeek.
  ///
  /// In en, this message translates to:
  /// **'This week'**
  String get planThisWeek;

  /// No description provided for @planWeekRange.
  ///
  /// In en, this message translates to:
  /// **'{from} – {to}'**
  String planWeekRange(String from, String to);

  /// No description provided for @planToday.
  ///
  /// In en, this message translates to:
  /// **'Today'**
  String get planToday;

  /// No description provided for @planPastWeek.
  ///
  /// In en, this message translates to:
  /// **'This week is over.'**
  String get planPastWeek;

  /// No description provided for @planLunch.
  ///
  /// In en, this message translates to:
  /// **'Lunch'**
  String get planLunch;

  /// No description provided for @planDinner.
  ///
  /// In en, this message translates to:
  /// **'Dinner'**
  String get planDinner;

  /// No description provided for @planEmpty.
  ///
  /// In en, this message translates to:
  /// **'Nothing planned'**
  String get planEmpty;

  /// No description provided for @planChoose.
  ///
  /// In en, this message translates to:
  /// **'Choose a recipe'**
  String get planChoose;

  /// No description provided for @planChange.
  ///
  /// In en, this message translates to:
  /// **'Change'**
  String get planChange;

  /// No description provided for @planMove.
  ///
  /// In en, this message translates to:
  /// **'Move'**
  String get planMove;

  /// No description provided for @planRemove.
  ///
  /// In en, this message translates to:
  /// **'Remove'**
  String get planRemove;

  /// No description provided for @planMealActions.
  ///
  /// In en, this message translates to:
  /// **'Meal options'**
  String get planMealActions;

  /// No description provided for @planSuggested.
  ///
  /// In en, this message translates to:
  /// **'Suggested'**
  String get planSuggested;

  /// No description provided for @planGenerate.
  ///
  /// In en, this message translates to:
  /// **'Fill the gaps'**
  String get planGenerate;

  /// No description provided for @planRegenerate.
  ///
  /// In en, this message translates to:
  /// **'Suggest again'**
  String get planRegenerate;

  /// No description provided for @planGenerateHelp.
  ///
  /// In en, this message translates to:
  /// **'Fills the empty meals, from today on, using first what expires first. What you chose yourself is left alone.'**
  String get planGenerateHelp;

  /// No description provided for @planRegenerateConfirm.
  ///
  /// In en, this message translates to:
  /// **'The meals suggested automatically for this week will be replaced. The ones you chose are kept.'**
  String get planRegenerateConfirm;

  /// No description provided for @planFilled.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{count} meal has been planned.} other{{count} meals have been planned.}}'**
  String planFilled(int count);

  /// No description provided for @planNothingFilled.
  ///
  /// In en, this message translates to:
  /// **'No new meal has been planned.'**
  String get planNothingFilled;

  /// No description provided for @planUnfilled.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{{count} meal stays empty: no more recipes fit without too much repetition.} other{{count} meals stay empty: no more recipes fit without too much repetition.}}'**
  String planUnfilled(int count);

  /// No description provided for @planNoteUses.
  ///
  /// In en, this message translates to:
  /// **'Uses {food}, due to expire on {date}.'**
  String planNoteUses(String food, String date);

  /// No description provided for @planNoteUsesEstimated.
  ///
  /// In en, this message translates to:
  /// **'Uses {food}, estimated to expire on {date}.'**
  String planNoteUsesEstimated(String food, String date);

  /// No description provided for @planNoteHaveAll.
  ///
  /// In en, this message translates to:
  /// **'Every ingredient will be at home.'**
  String get planNoteHaveAll;

  /// No description provided for @planNoteMissing.
  ///
  /// In en, this message translates to:
  /// **'To buy: {foods}.'**
  String planNoteMissing(String foods);

  /// No description provided for @planNotePartial.
  ///
  /// In en, this message translates to:
  /// **'There will be less {food} than the recipe asks for.'**
  String planNotePartial(String food);

  /// No description provided for @planUnusedTitle.
  ///
  /// In en, this message translates to:
  /// **'The plan lets these expire'**
  String get planUnusedTitle;

  /// No description provided for @planUnusedHelp.
  ///
  /// In en, this message translates to:
  /// **'They expire before the week is over and no planned meal uses them up.'**
  String get planUnusedHelp;

  /// No description provided for @planUnusedItem.
  ///
  /// In en, this message translates to:
  /// **'{food} ({quantity}), due to expire on {date}'**
  String planUnusedItem(String food, String quantity, String date);

  /// No description provided for @planUnusedItemEstimated.
  ///
  /// In en, this message translates to:
  /// **'{food} ({quantity}), estimated to expire on {date}'**
  String planUnusedItemEstimated(String food, String quantity, String date);

  /// No description provided for @planPickerTitle.
  ///
  /// In en, this message translates to:
  /// **'Choose a recipe'**
  String get planPickerTitle;

  /// No description provided for @planMoveTitle.
  ///
  /// In en, this message translates to:
  /// **'Move to'**
  String get planMoveTitle;

  /// No description provided for @planMovePlace.
  ///
  /// In en, this message translates to:
  /// **'{day} · {slot}'**
  String planMovePlace(String day, String slot);

  /// No description provided for @planMoveSwap.
  ///
  /// In en, this message translates to:
  /// **'Swaps with {recipe}'**
  String planMoveSwap(String recipe);

  /// No description provided for @errorMealNotFound.
  ///
  /// In en, this message translates to:
  /// **'That meal is no longer in the plan.'**
  String get errorMealNotFound;

  /// No description provided for @errorWeekInThePast.
  ///
  /// In en, this message translates to:
  /// **'A week that is over cannot be planned.'**
  String get errorWeekInThePast;
}

class _AppLocalizationsDelegate extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) => <String>['en', 'es'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'es':
      return AppLocalizationsEs();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
