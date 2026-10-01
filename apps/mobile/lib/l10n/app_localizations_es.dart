// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Spanish Castilian (`es`).
class AppLocalizationsEs extends AppLocalizations {
  AppLocalizationsEs([String locale = 'es']) : super(locale);

  @override
  String get appName => 'Freezify';

  @override
  String get appTagline => 'Sé qué tienes, qué caduca y qué puedes cocinar.';

  @override
  String get logout => 'Cerrar sesión';

  @override
  String get language => 'Idioma';

  @override
  String get loginTitle => 'Inicia sesión';

  @override
  String get registerTitle => 'Crea tu cuenta';

  @override
  String get email => 'Correo electrónico';

  @override
  String get password => 'Contraseña';

  @override
  String get passwordHint => 'Mínimo 8 caracteres';

  @override
  String get displayName => 'Tu nombre';

  @override
  String get loginAction => 'Entrar';

  @override
  String get registerAction => 'Crear cuenta';

  @override
  String get noAccount => '¿No tienes cuenta? Regístrate';

  @override
  String get haveAccount => '¿Ya tienes cuenta? Inicia sesión';

  @override
  String get fieldRequired => 'Obligatorio';

  @override
  String get passwordTooShort => 'Mínimo 8 caracteres';

  @override
  String greeting(String name) {
    return 'Hola, $name 👋';
  }

  @override
  String get householdsTitle => 'Mis hogares';

  @override
  String get householdsEmpty =>
      'Todavía no perteneces a ningún hogar. Crea uno o únete con un código de invitación.';

  @override
  String get createHousehold => 'Crear un hogar';

  @override
  String get householdName => 'Nombre del hogar';

  @override
  String get create => 'Crear';

  @override
  String get joinHousehold => 'Unirme con un código';

  @override
  String get invitationCode => 'Código de invitación';

  @override
  String get join => 'Unirme';

  @override
  String memberCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count miembros',
      one: '$count miembro',
    );
    return '$_temp0';
  }

  @override
  String get roleOwner => 'Propietario';

  @override
  String get roleMember => 'Miembro';

  @override
  String get membersTitle => 'Miembros';

  @override
  String get you => 'tú';

  @override
  String joinedOn(String date) {
    return 'Desde el $date';
  }

  @override
  String get inviteTitle => 'Invitar a alguien';

  @override
  String get inviteHelp =>
      'Genera un código y compártelo. Quien lo introduzca se unirá a este hogar.';

  @override
  String get inviteGenerate => 'Generar código';

  @override
  String inviteExpires(String date) {
    return 'Válido hasta el $date';
  }

  @override
  String get rename => 'Renombrar';

  @override
  String get save => 'Guardar';

  @override
  String get cancel => 'Cancelar';

  @override
  String get remove => 'Quitar';

  @override
  String get leaveHousehold => 'Abandonar hogar';

  @override
  String get deleteHousehold => 'Eliminar hogar';

  @override
  String confirmRemove(String name) {
    return '¿Quitar a $name de este hogar?';
  }

  @override
  String confirmLeave(String name) {
    return '¿Seguro que quieres abandonar «$name»?';
  }

  @override
  String confirmDelete(String name) {
    return 'Se eliminará «$name» para todos sus miembros. Esta acción no se puede deshacer.';
  }

  @override
  String get retry => 'Reintentar';

  @override
  String get errorGeneric => 'Algo ha salido mal. Inténtalo de nuevo.';

  @override
  String get errorNetwork => 'No se puede conectar con el servidor.';

  @override
  String get errorValidation => 'Revisa los datos introducidos.';

  @override
  String get errorEmailTaken => 'Ya existe una cuenta con ese correo.';

  @override
  String get errorInvalidCredentials => 'Correo o contraseña incorrectos.';

  @override
  String get errorPasswordTooLong => 'La contraseña es demasiado larga.';

  @override
  String get errorRateLimited => 'Demasiados intentos. Espera un momento.';

  @override
  String get errorHouseholdNotFound =>
      'Este hogar no existe o ya no perteneces a él.';

  @override
  String get errorNotOwner => 'Solo el propietario del hogar puede hacer esto.';

  @override
  String get errorOwnerCannotLeave =>
      'El propietario no puede abandonar el hogar. Elimínalo si ya no lo necesitas.';

  @override
  String get errorAlreadyMember => 'Ya perteneces a este hogar.';

  @override
  String get errorInvitationNotFound => 'El código no es válido o ha caducado.';

  @override
  String get errorMemberNotFound => 'Esa persona ya no pertenece al hogar.';
}
