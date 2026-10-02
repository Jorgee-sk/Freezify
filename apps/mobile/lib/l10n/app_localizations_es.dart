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
  String get householdsEmpty => 'Todavía no perteneces a ningún hogar. Crea uno o únete con un código de invitación.';

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
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count miembros', one: '$count miembro');
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
  String get inviteHelp => 'Genera un código y compártelo. Quien lo introduzca se unirá a este hogar.';

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
  String get errorHouseholdNotFound => 'Este hogar no existe o ya no perteneces a él.';

  @override
  String get errorNotOwner => 'Solo el propietario del hogar puede hacer esto.';

  @override
  String get errorOwnerCannotLeave => 'El propietario no puede abandonar el hogar. Elimínalo si ya no lo necesitas.';

  @override
  String get errorAlreadyMember => 'Ya perteneces a este hogar.';

  @override
  String get errorInvitationNotFound => 'El código no es válido o ha caducado.';

  @override
  String get errorMemberNotFound => 'Esa persona ya no pertenece al hogar.';

  @override
  String get settings => 'Miembros y ajustes';

  @override
  String get inventoryTitle => 'Inventario';

  @override
  String inventoryCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count alimentos', one: '$count alimento');
    return '$_temp0';
  }

  @override
  String get inventoryEmpty => 'Aún no hay alimentos. Añade lo que tienes en casa para empezar.';

  @override
  String get inventoryEmptyFiltered => 'Ningún alimento coincide con el filtro.';

  @override
  String get addFood => 'Añadir alimento';

  @override
  String get newFood => 'Nuevo alimento';

  @override
  String get editFood => 'Editar alimento';

  @override
  String get searchInventory => 'Buscar en el inventario…';

  @override
  String get allLocations => 'Todo';

  @override
  String get filters => 'Filtros';

  @override
  String get category => 'Categoría';

  @override
  String get allCategories => 'Todas las categorías';

  @override
  String get show => 'Mostrar';

  @override
  String get stateActive => 'En casa';

  @override
  String get stateFinished => 'Terminados';

  @override
  String get stateAll => 'Todos';

  @override
  String get foodName => 'Alimento';

  @override
  String get suggestions => 'Sugerencias';

  @override
  String get recentlyAdded => 'Añadidos recientemente';

  @override
  String get amount => 'Cantidad';

  @override
  String get unit => 'Unidad';

  @override
  String get location => 'Ubicación';

  @override
  String get expirationDate => 'Fecha de caducidad';

  @override
  String get noDateSet => 'Sin fecha';

  @override
  String get clearDate => 'Quitar fecha';

  @override
  String get moreDetails => 'Más detalles';

  @override
  String get purchaseDate => 'Fecha de compra';

  @override
  String get brand => 'Marca';

  @override
  String get price => 'Precio estimado (€)';

  @override
  String get notes => 'Notas';

  @override
  String get invalidAmount => 'Introduce una cantidad mayor que cero.';

  @override
  String expiresOn(String date) {
    return 'Caduca el $date';
  }

  @override
  String expiresEstimated(String date) {
    return 'Caduca hacia el $date (fecha estimada)';
  }

  @override
  String get noExpirationDate => 'Sin fecha de caducidad';

  @override
  String openedOn(String date) {
    return 'Abierto el $date';
  }

  @override
  String get statusAvailable => 'Disponible';

  @override
  String get statusOpened => 'Abierto';

  @override
  String get statusExpired => 'Caducado';

  @override
  String get statusConsumed => 'Consumido';

  @override
  String get statusDiscarded => 'Tirado';

  @override
  String get consume => 'Consumir';

  @override
  String get discard => 'Tirar';

  @override
  String get markOpened => 'Marcar como abierto';

  @override
  String get edit => 'Editar';

  @override
  String get delete => 'Eliminar';

  @override
  String get itemActions => 'Acciones';

  @override
  String get consumeTitle => '¿Cuánto has consumido?';

  @override
  String get discardTitle => '¿Cuánto has tirado?';

  @override
  String get reason => 'Motivo';

  @override
  String get reasonExpired => 'Caducado';

  @override
  String get reasonSpoiled => 'En mal estado';

  @override
  String get reasonLeftover => 'Sobras';

  @override
  String get reasonOther => 'Otro';

  @override
  String get confirm => 'Confirmar';

  @override
  String confirmDeleteItem(String name) {
    return '¿Eliminar «$name» del inventario? No contará como consumido ni como desperdicio.';
  }

  @override
  String get previousPage => 'Anterior';

  @override
  String get nextPage => 'Siguiente';

  @override
  String pageOf(int page, int total) {
    return 'Página $page de $total';
  }

  @override
  String unitAbbreviation(num count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: 'uds', one: 'ud');
    return '$_temp0';
  }

  @override
  String get unitNameUnit => 'unidades';

  @override
  String get unitNameGram => 'gramos';

  @override
  String get unitNameKilogram => 'kilogramos';

  @override
  String get unitNameMilliliter => 'mililitros';

  @override
  String get unitNameLiter => 'litros';

  @override
  String get locationRefrigerator => 'Nevera';

  @override
  String get locationFreezer => 'Congelador';

  @override
  String get locationPantry => 'Despensa';

  @override
  String get locationOther => 'Otro';

  @override
  String get categoryVegetables => 'Verduras';

  @override
  String get categoryFruits => 'Frutas';

  @override
  String get categoryMeat => 'Carne';

  @override
  String get categoryFish => 'Pescado';

  @override
  String get categoryDairy => 'Lácteos';

  @override
  String get categoryEggs => 'Huevos';

  @override
  String get categoryBakery => 'Panadería';

  @override
  String get categoryPantry => 'Despensa';

  @override
  String get categoryFrozen => 'Congelados';

  @override
  String get categoryBeverages => 'Bebidas';

  @override
  String get categoryPrepared => 'Platos preparados';

  @override
  String get categoryOther => 'Otros';

  @override
  String get errorItemNotFound => 'Este alimento ya no está en el inventario.';

  @override
  String get errorItemNotActive => 'Este alimento ya se había consumido o tirado.';

  @override
  String get errorFoodNotFound => 'Ese alimento no existe en el catálogo.';

  @override
  String get errorIncompatibleUnit => 'Esa unidad no es compatible con la del alimento.';

  @override
  String get errorQuantityExceeds => 'Queda menos cantidad de la indicada.';

  @override
  String get errorConcurrentModification => 'Alguien acaba de cambiar esto. Inténtalo de nuevo.';

  @override
  String get priorityExpired => 'Caducado';

  @override
  String get priorityToday => 'Vence hoy';

  @override
  String get priorityUrgent => 'Urgente';

  @override
  String get prioritySoon => 'Consumir pronto';

  @override
  String get priorityUpcoming => 'Próximo';

  @override
  String daysLeft(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'quedan $count días',
      one: 'queda $count día',
    );
    return '$_temp0';
  }

  @override
  String daysAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: 'hace $count días', one: 'hace $count día');
    return '$_temp0';
  }

  @override
  String get consumeFirstTitle => 'Consume primero';

  @override
  String consumeFirstCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count alimentos que no pueden esperar',
      one: '$count alimento que no puede esperar',
    );
    return '$_temp0';
  }

  @override
  String andMore(int count) {
    return 'y $count más';
  }
}
