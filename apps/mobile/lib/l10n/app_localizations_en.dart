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
  String get appTagline =>
      'Know what you have, what expires and what you can cook.';

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
  String get householdsEmpty =>
      'You don\'t belong to any household yet. Create one or join with an invitation code.';

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
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count members',
      one: '$count member',
    );
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
  String get inviteHelp =>
      'Generate a code and share it. Whoever enters it will join this household.';

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
  String get errorHouseholdNotFound =>
      'This household doesn\'t exist or you no longer belong to it.';

  @override
  String get errorNotOwner => 'Only the household owner can do this.';

  @override
  String get errorOwnerCannotLeave =>
      'The owner cannot leave the household. Delete it if you no longer need it.';

  @override
  String get errorAlreadyMember => 'You already belong to this household.';

  @override
  String get errorInvitationNotFound => 'The code is invalid or has expired.';

  @override
  String get errorMemberNotFound =>
      'That person no longer belongs to the household.';
}
