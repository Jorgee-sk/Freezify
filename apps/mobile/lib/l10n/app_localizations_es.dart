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
  String get errorOwnerCannotLeave =>
      'El propietario no puede abandonar el hogar. Haz propietario antes a otro miembro, o elimínalo.';

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

  @override
  String get estimateHelp => 'Si la dejas vacía, la estimamos según el alimento y dónde lo guardas.';

  @override
  String currentEstimate(String date) {
    return 'Ahora mismo estimamos el $date. Indica la fecha del envase si la sabes.';
  }

  @override
  String get notificationsTitle => 'Avisos';

  @override
  String notificationsUnread(int count) {
    return 'Avisos: $count sin leer';
  }

  @override
  String get notificationsEmpty => 'No tienes avisos. Te avisaremos cuando algo esté a punto de caducar.';

  @override
  String get markAllRead => 'Marcar todo como leído';

  @override
  String get notificationNew => 'Nuevo';

  @override
  String get notificationPreferences => 'Preferencias de avisos';

  @override
  String notificationSummary(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Tienes $count alimentos que deberías consumir pronto',
      one: 'Tienes $count alimento que deberías consumir pronto',
    );
    return '$_temp0';
  }

  @override
  String notifiedExpired(String name, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$name: su fecha de caducidad pasó hace $count días',
      one: '$name: su fecha de caducidad pasó hace $count día',
    );
    return '$_temp0';
  }

  @override
  String notifiedToday(String name) {
    return '$name: su fecha de caducidad es hoy';
  }

  @override
  String notifiedLeft(String name, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$name: quedan $count días para su fecha de caducidad',
      one: '$name: queda $count día para su fecha de caducidad',
    );
    return '$_temp0';
  }

  @override
  String notifiedExpiredEstimated(String name, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$name: su fecha de caducidad probablemente pasó hace $count días (fecha estimada)',
      one: '$name: su fecha de caducidad probablemente pasó hace $count día (fecha estimada)',
    );
    return '$_temp0';
  }

  @override
  String notifiedTodayEstimated(String name) {
    return '$name: su fecha de caducidad es probablemente hoy (fecha estimada)';
  }

  @override
  String notifiedLeftEstimated(String name, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$name: quedan aproximadamente $count días para su fecha de caducidad (fecha estimada)',
      one: '$name: queda aproximadamente $count día para su fecha de caducidad (fecha estimada)',
    );
    return '$_temp0';
  }

  @override
  String get prefEnabled => 'Avisarme de los alimentos que van a caducar';

  @override
  String get prefAntiSpam => 'Como mucho un aviso al día por hogar, y solo cuando haya algo nuevo que contarte.';

  @override
  String get prefHour => 'A partir de las';

  @override
  String get prefHourHelp => 'Hora peninsular española.';

  @override
  String get prefFrequency => 'Como mucho';

  @override
  String get prefFrequencyDaily => 'Una vez al día';

  @override
  String get prefFrequencyEveryThreeDays => 'Cada 3 días';

  @override
  String get prefFrequencyWeekly => 'Una vez a la semana';

  @override
  String get prefThreshold => 'Avisarme cuando un alimento';

  @override
  String get prefThresholdToday => 'Caduque hoy';

  @override
  String get prefThresholdUrgent => 'Caduque en 2 días o menos';

  @override
  String get prefThresholdSoon => 'Caduque en 5 días o menos';

  @override
  String get prefCategories => 'Avisarme de estas categorías';

  @override
  String get preferencesSaved => 'Preferencias guardadas';

  @override
  String get errorNotificationNotFound => 'Este aviso ya no existe.';

  @override
  String get recipesTitle => 'Recetas';

  @override
  String get recommendedTitle => 'Qué cocinar con lo que tienes';

  @override
  String get noRecommendations => 'Ninguna receta usa lo que hay en tu inventario. Añade alimentos y vuelve a mirar.';

  @override
  String get catalogTitle => 'Todas las recetas';

  @override
  String get searchRecipes => 'Buscar receta…';

  @override
  String get anyCourse => 'Todas';

  @override
  String get courseMain => 'Plato principal';

  @override
  String get courseBreakfast => 'Desayuno';

  @override
  String get courseDessert => 'Postre';

  @override
  String get difficultyEasy => 'Fácil';

  @override
  String get difficultyMedium => 'Media';

  @override
  String get difficultyHard => 'Difícil';

  @override
  String recipeMinutes(int count) {
    return '$count min';
  }

  @override
  String recipeServings(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count raciones', one: '$count ración');
    return '$_temp0';
  }

  @override
  String upToMinutes(int count) {
    return 'Hasta $count min';
  }

  @override
  String get recipesEmptyFiltered => 'Ninguna receta coincide con el filtro.';

  @override
  String recipeFit(int percent) {
    return 'Encaje $percent %';
  }

  @override
  String get recommendedBecause => 'Recomendada porque:';

  @override
  String reasonExpires(String food, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Tienes $food con caducidad en $count días.',
      one: 'Tienes $food con caducidad en $count día.',
    );
    return '$_temp0';
  }

  @override
  String reasonExpiresEstimated(String food, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Tienes $food con caducidad estimada en $count días.',
      one: 'Tienes $food con caducidad estimada en $count día.',
    );
    return '$_temp0';
  }

  @override
  String reasonExpiresToday(String food) {
    return 'Tienes $food con caducidad hoy.';
  }

  @override
  String reasonExpiresTodayEstimated(String food) {
    return 'Tienes $food con caducidad estimada hoy.';
  }

  @override
  String get reasonHaveAll => 'Tienes todos los ingredientes.';

  @override
  String reasonHave(int have, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Tienes $have de $count ingredientes.',
      one: 'Tienes $have de $count ingrediente.',
    );
    return '$_temp0';
  }

  @override
  String reasonMissing(int count, String foods) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Te faltan: $foods.',
      one: 'Solo te falta: $foods.',
    );
    return '$_temp0';
  }

  @override
  String reasonMissingMany(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Te faltan $count ingredientes.',
      one: 'Te falta $count ingrediente.',
    );
    return '$_temp0';
  }

  @override
  String reasonPartial(String food) {
    return 'Tienes menos $food de lo que pide la receta.';
  }

  @override
  String reasonUnknownQuantity(String food) {
    return 'Comprueba la cantidad de $food: no podemos compararla con la de la receta.';
  }

  @override
  String reasonTime(int minutes) {
    return 'Tiempo aproximado: $minutes min.';
  }

  @override
  String get reasonCookedToday => 'La has cocinado hoy.';

  @override
  String reasonCooked(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'La cocinaste hace $count días.',
      one: 'La cocinaste hace $count día.',
    );
    return '$_temp0';
  }

  @override
  String get ingredientsTitle => 'Ingredientes';

  @override
  String get stapleLabel => 'básico de cocina';

  @override
  String get stepsTitle => 'Preparación';

  @override
  String get nothingAtHome => 'No tienes en el inventario ninguno de los ingredientes de esta receta.';

  @override
  String get availabilityEnough => 'Lo tienes';

  @override
  String get availabilityPartial => 'Tienes menos';

  @override
  String get availabilityUnknown => 'Comprueba la cantidad';

  @override
  String get availabilityMissing => 'Te falta';

  @override
  String get badgeToday => 'Caduca hoy';

  @override
  String get badgeTodayEstimated => 'Caduca hoy (estimada)';

  @override
  String badgeDays(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Caduca en $count días',
      one: 'Caduca en $count día',
    );
    return '$_temp0';
  }

  @override
  String badgeDaysEstimated(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Caduca en unos $count días (estimada)',
      one: 'Caduca en unos $count día (estimada)',
    );
    return '$_temp0';
  }

  @override
  String get markCooked => 'La he cocinado';

  @override
  String get cookedSaved => 'Anotado';

  @override
  String get cookedHelp =>
      'Sirve para no recomendarte lo mismo cada día. No cambia tu inventario: consume los alimentos que hayas usado.';

  @override
  String get errorRecipeNotFound => 'Esta receta no existe.';

  @override
  String get dietTitle => 'Qué no se come en casa';

  @override
  String get dietShared =>
      'Estas restricciones son del hogar: cualquier miembro las ve y puede cambiarlas. Las recetas que contengan algo de esto no se muestran.';

  @override
  String get dietTypeLabel => 'Dieta';

  @override
  String get dietNone => 'Sin dieta';

  @override
  String get dietVegetarian => 'Vegetariana';

  @override
  String get dietVegan => 'Vegana';

  @override
  String get dietVegetarianName => 'Dieta vegetariana';

  @override
  String get dietVeganName => 'Dieta vegana';

  @override
  String get dietAvoidLabel => 'Además, evitar';

  @override
  String dietWithout(String trait) {
    return 'sin $trait';
  }

  @override
  String get dietDisclaimer =>
      'Es una ayuda, no una garantía. Lo que contiene cada alimento es orientativo: un producto concreto puede llevar otros ingredientes o trazas. Si hay una alergia, comprueba siempre la etiqueta.';

  @override
  String get dietSaved => 'Restricciones guardadas';

  @override
  String dietApplied(String restrictions) {
    return 'Recetas filtradas para este hogar: $restrictions.';
  }

  @override
  String get dietNotSet => 'Este hogar no tiene restricciones alimentarias.';

  @override
  String get dietChange => 'Cambiar';

  @override
  String dietConflict(String traits) {
    return 'Esta receta contiene algo que en este hogar no se come: $traits.';
  }

  @override
  String recipeContains(String traits) {
    return 'Contiene: $traits.';
  }

  @override
  String get traitMeat => 'carne';

  @override
  String get traitPork => 'cerdo';

  @override
  String get traitFish => 'pescado';

  @override
  String get traitShellfish => 'marisco';

  @override
  String get traitDairy => 'lácteos';

  @override
  String get traitEgg => 'huevo';

  @override
  String get traitGluten => 'gluten';

  @override
  String get traitNuts => 'frutos secos';

  @override
  String get traitSoy => 'soja';

  @override
  String get traitSesame => 'sésamo';

  @override
  String get traitAlcohol => 'alcohol';

  @override
  String get planTitle => 'Plan de la semana';

  @override
  String get planPreviousWeek => 'Semana anterior';

  @override
  String get planNextWeek => 'Semana siguiente';

  @override
  String get planThisWeek => 'Esta semana';

  @override
  String planWeekRange(String from, String to) {
    return '$from – $to';
  }

  @override
  String get planToday => 'Hoy';

  @override
  String get planPastWeek => 'Esta semana ya ha pasado.';

  @override
  String get planLunch => 'Comida';

  @override
  String get planDinner => 'Cena';

  @override
  String get planEmpty => 'Nada planificado';

  @override
  String get planChoose => 'Elegir receta';

  @override
  String get planChange => 'Cambiar';

  @override
  String get planMove => 'Mover';

  @override
  String get planRemove => 'Quitar';

  @override
  String get planMealActions => 'Opciones de la comida';

  @override
  String get planSuggested => 'Propuesta';

  @override
  String get planGenerate => 'Rellenar los huecos';

  @override
  String get planRegenerate => 'Rehacer la propuesta';

  @override
  String get planGenerateHelp =>
      'Rellena las comidas vacías, de hoy en adelante, usando primero lo que caduca antes. Lo que hayas elegido tú no se toca.';

  @override
  String get planRegenerateConfirm =>
      'Se sustituirán las comidas propuestas automáticamente de esta semana. Las que elegiste tú se mantienen.';

  @override
  String planFilled(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Se han planificado $count comidas.',
      one: 'Se ha planificado $count comida.',
    );
    return '$_temp0';
  }

  @override
  String get planNothingFilled => 'No se ha planificado ninguna comida nueva.';

  @override
  String planUnfilled(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count comidas se quedan vacías: no hay más recetas que encajen sin repetir demasiado.',
      one: '$count comida se queda vacía: no hay más recetas que encajen sin repetir demasiado.',
    );
    return '$_temp0';
  }

  @override
  String planNoteUses(String food, String date) {
    return 'Aprovecha $food, con caducidad el $date.';
  }

  @override
  String planNoteUsesEstimated(String food, String date) {
    return 'Aprovecha $food, con caducidad estimada el $date.';
  }

  @override
  String get planNoteHaveAll => 'Habrá en casa todos los ingredientes.';

  @override
  String planNoteMissing(String foods) {
    return 'Falta por comprar: $foods.';
  }

  @override
  String planNotePartial(String food) {
    return 'Habrá menos $food de lo que pide la receta.';
  }

  @override
  String get planUnusedTitle => 'El plan deja caducar';

  @override
  String get planUnusedHelp =>
      'Caducan antes de que acabe la semana y ninguna comida planificada los aprovecha del todo.';

  @override
  String planUnusedItem(String food, String quantity, String date) {
    return '$food ($quantity), con caducidad el $date';
  }

  @override
  String planUnusedItemEstimated(String food, String quantity, String date) {
    return '$food ($quantity), con caducidad estimada el $date';
  }

  @override
  String get planPickerTitle => 'Elige una receta';

  @override
  String get planMoveTitle => 'Mover a';

  @override
  String planMovePlace(String day, String slot) {
    return '$day · $slot';
  }

  @override
  String planMoveSwap(String recipe) {
    return 'Se intercambia con $recipe';
  }

  @override
  String get errorMealNotFound => 'Esa comida ya no está en el plan.';

  @override
  String get errorWeekInThePast => 'No se puede planificar una semana que ya ha pasado.';

  @override
  String get planCooked => 'Cocinada';

  @override
  String get planAddTitle => 'Añadir al plan';

  @override
  String get planAddDay => 'Día';

  @override
  String get planAdd => 'Añadir al plan';

  @override
  String planAdded(String place) {
    return 'Añadida al plan: $place.';
  }

  @override
  String planAddReplaces(String recipe) {
    return 'Ahí ya está «$recipe»: se sustituirá.';
  }

  @override
  String get planView => 'Ver el plan';

  @override
  String get shoppingTitle => 'Lista de la compra';

  @override
  String get shoppingActions => 'Opciones de la lista';

  @override
  String get shoppingFromPlanThisWeek => 'Añadir lo que falta para el plan de esta semana';

  @override
  String get shoppingFromPlanNextWeek => 'Añadir lo que falta para el plan de la semana que viene';

  @override
  String get shoppingToList => 'Llevar a la lista lo que falta';

  @override
  String shoppingFilled(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Hay $count cosas en la lista para el plan de esa semana.',
      one: 'Hay $count cosa en la lista para el plan de esa semana.',
    );
    return '$_temp0';
  }

  @override
  String get shoppingNothingLacking => 'Al plan de esa semana no le falta nada.';

  @override
  String get shoppingView => 'Ver la lista';

  @override
  String get shoppingEmpty => 'La lista está vacía. Añade lo que necesites o lo que le falta al plan.';

  @override
  String get shoppingAddTitle => 'Añadir a la lista';

  @override
  String get shoppingWhat => 'Qué';

  @override
  String get shoppingAmountOptional => 'Cantidad (opcional)';

  @override
  String get shoppingAdd => 'Añadir';

  @override
  String shoppingForPlan(String day) {
    return 'Para el plan · $day';
  }

  @override
  String get shoppingChange => 'Cambiar';

  @override
  String shoppingChangeTitle(String name) {
    return 'Cantidad de $name';
  }

  @override
  String get shoppingLineActions => 'Opciones';

  @override
  String shoppingRemoveChecked(int count) {
    return 'Quitar lo comprado ($count)';
  }

  @override
  String shoppingRemoveCheckedConfirm(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Se quitarán de la lista $count cosas compradas.',
      one: 'Se quitará de la lista $count cosa comprada.',
    );
    return '$_temp0';
  }

  @override
  String get errorShoppingItemNotFound => 'Eso ya no está en la lista.';

  @override
  String shoppingToInventory(int count) {
    return 'Pasar lo comprado al inventario ($count)';
  }

  @override
  String shoppingStocked(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Se han añadido $count alimentos al inventario.',
      one: 'Se ha añadido $count alimento al inventario.',
    );
    return '$_temp0';
  }

  @override
  String get shoppingStockedNone => 'No se ha añadido nada al inventario.';

  @override
  String shoppingLeftWithoutAmount(String names) {
    return 'Siguen en la lista porque no dicen cuánto se compró: $names. Indica la cantidad y vuelve a pasarlos.';
  }

  @override
  String get transferOwnership => 'Hacer propietario';

  @override
  String transferTo(String name) {
    return 'Hacer propietario a $name';
  }

  @override
  String confirmTransfer(String name) {
    return '$name pasará a ser el propietario del hogar y tú seguirás como miembro. Solo $name podrá devolvértelo.';
  }

  @override
  String get ownerLeavesHint => 'Para abandonar el hogar sin eliminarlo, haz propietario antes a otro miembro.';

  @override
  String get activeCodes => 'Códigos activos';

  @override
  String get activeCodesHelp =>
      'Cualquiera que tenga uno de estos códigos puede unirse mientras sea válido. Revócalo si ya no quieres que sirva.';

  @override
  String get revoke => 'Revocar';

  @override
  String revokeCode(String code) {
    return 'Revocar el código $code';
  }

  @override
  String confirmRevoke(String code) {
    return 'El código $code dejará de servir para unirse. Quien ya se unió con él se queda.';
  }

  @override
  String get scanReceipt => 'Escanear ticket';

  @override
  String get scanIntro =>
      'Haz una foto del ticket de la compra. El texto se lee en tu teléfono: la foto no se envía. Revisarás cada producto antes de añadir nada al inventario.';

  @override
  String get scanTakePhoto => 'Hacer foto del ticket';

  @override
  String get scanPickPhoto => 'Elegir una foto';

  @override
  String get scanNoPhotosHere => 'Aquí no se pueden leer fotos. Pega el texto del ticket o usa la app en el móvil.';

  @override
  String get scanNothingRead =>
      'No se ha podido leer texto en la foto. Prueba con más luz y el ticket bien estirado, o pega su texto.';

  @override
  String get scanNoProducts => 'No hemos encontrado productos en este ticket.';

  @override
  String get scanPasteTitle => '¿Tienes el ticket en digital?';

  @override
  String get scanPasteHelp => 'Pega aquí su texto.';

  @override
  String get scanReceiptText => 'Texto del ticket';

  @override
  String get scanReadText => 'Leer ticket';

  @override
  String get scanReadByAi => 'Leído con ayuda de IA. Revisa cada producto antes de añadirlo.';

  @override
  String get scanReadByRules => 'Leído automáticamente. Revisa cada producto antes de añadirlo.';

  @override
  String get scanPurchaseDate => 'Fecha de compra';

  @override
  String scanDateFromReceipt(String date) {
    return '$date · leída del ticket';
  }

  @override
  String scanDateNotOnReceipt(String date) {
    return '$date · no aparece en el ticket';
  }

  @override
  String scanQuantityFromReceipt(String quantity) {
    return '$quantity · del ticket';
  }

  @override
  String scanQuantityAssumed(String quantity) {
    return '$quantity · supuesto';
  }

  @override
  String get scanMatchLearned => 'Como la última vez';

  @override
  String get scanMatchFound => 'Reconocido en el catálogo';

  @override
  String get scanMatchNone => 'Sin alimento del catálogo';

  @override
  String scanExpiresOn(String date) {
    return 'Caduca el $date';
  }

  @override
  String scanInclude(String name) {
    return 'Añadir $name';
  }

  @override
  String scanEditLineOf(String name) {
    return 'Revisar $name';
  }

  @override
  String get scanReviewed => 'Revisado por ti';

  @override
  String get scanEditLine => 'Revisar producto';

  @override
  String scanPrintedAs(String text) {
    return 'En el ticket: $text';
  }

  @override
  String get scanCandidates => '¿Es alguno de estos?';

  @override
  String get scanExpirationHelp => 'Si la ves en el envase. Si no, se estima.';

  @override
  String scanAddToInventory(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Añadir $count productos',
      one: 'Añadir 1 producto',
      zero: 'Nada que añadir',
    );
    return '$_temp0';
  }

  @override
  String scanAdded(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count productos añadidos al inventario',
      one: '1 producto añadido al inventario',
    );
    return '$_temp0';
  }

  @override
  String get scanDiscardConfirm => 'Si sales ahora se pierde la revisión de este ticket.';

  @override
  String get scanDiscard => 'Salir';

  @override
  String get errorPurchaseDateInFuture => 'La fecha de compra no puede ser posterior a hoy.';

  @override
  String get generatedLink => 'Crear una receta con lo que tengo';

  @override
  String get generatedLinkHelp => 'Con IA, solo con lo que hay en casa y respetando la dieta del hogar.';

  @override
  String get generatedTitle => 'Receta con lo que tienes';

  @override
  String get generatedIntro =>
      'Una IA escribe una receta solo con los alimentos de tu inventario que se comen en tu hogar, y usa antes los que caducan antes.';

  @override
  String get generatedServings => 'Raciones';

  @override
  String get generatedCreate => 'Crear receta';

  @override
  String get generatedAgain => 'Crear otra';

  @override
  String get generatedWriting => 'Escribiendo la receta…';

  @override
  String get generatedNotice =>
      'Receta escrita por IA a partir de tu inventario. Revisa cantidades y tiempos, y que la carne, el pescado y los huevos queden bien hechos.';

  @override
  String get generatedOptional => 'opcional';

  @override
  String get errorAiNotConfigured => 'Esta instalación no tiene IA configurada.';

  @override
  String get errorAiLimitReached => 'Has usado todas las peticiones de IA de hoy. Vuelve a intentarlo mañana.';

  @override
  String get errorAiUnavailable => 'La IA no ha dado una receta válida. Inténtalo de nuevo.';

  @override
  String get errorNothingToCookWith => 'No hay en el inventario nada sin caducar que se coma en tu hogar.';

  @override
  String get generatedAvailable => 'Lo que la IA puede usar';

  @override
  String generatedMustUseHelp(int count) {
    return 'Marca hasta $count alimentos que la receta tenga que usar sí o sí.';
  }

  @override
  String get errorFoodNotAvailable => 'Ese alimento ya no está en casa, ha pasado su fecha o no se come en tu hogar.';

  @override
  String get photoCamera => 'Hacer una foto';

  @override
  String get photoTake => 'Identificar por foto';

  @override
  String get photoPrivacy => 'La foto se envía al servicio de IA para identificarla y no se guarda.';

  @override
  String get photoWhatIsIt => '¿Qué es?';

  @override
  String get photoNotSure => 'No estamos seguros. ¿Es alguno de estos?';

  @override
  String get photoNoneHelp => 'Si no es ninguno, escribe el nombre.';

  @override
  String get photoNothing => 'No hemos reconocido ningún alimento en la foto. Escribe el nombre.';

  @override
  String get errorUnsupportedImage => 'Solo se pueden leer fotos JPEG, PNG o WebP.';

  @override
  String get errorFileTooLarge => 'La foto es demasiado grande.';
}
