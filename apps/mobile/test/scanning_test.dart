import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:freezify/features/scanning/receipt_ocr.dart';

import 'support/app_harness.dart';
import 'support/fake_backend.dart';

const _ana = {'id': 'u1', 'email': 'ana@example.com', 'displayName': 'Ana', 'locale': 'es'};
const _casa = {'id': 'h1', 'name': 'Casa', 'role': 'OWNER', 'memberCount': 1};
const _emptyInventory = {'items': <Object>[], 'page': 0, 'size': 50, 'totalItems': 0, 'totalPages': 0};
const _nothingToConsumeFirst = {
  'counts': {'EXPIRED': 0, 'TODAY': 0, 'URGENT': 0, 'SOON': 0, 'UPCOMING': 0, 'OK': 0, 'NO_DATE': 0},
  'items': <Object>[],
};

const _read = 'POST /households/h1/scans/receipt';
const _confirm = 'POST /households/h1/scans/receipt/confirm';

Map<String, Object?> _line(
  String text,
  String name, {
  String? foodId,
  String match = 'NAME',
  List<Map<String, String>> candidates = const [],
  String category = 'OTHER',
  String storage = 'OTHER',
  Map<String, Object?> quantity = const {'amount': 1, 'unit': 'UNIT'},
  bool quantityFromReceipt = false,
  double? price,
  bool? include,
}) => {
  'text': text,
  'name': name,
  'foodId': foodId,
  'match': match,
  'candidates': candidates,
  'category': category,
  'storageLocation': storage,
  'quantity': quantity,
  'quantityFromReceipt': quantityFromReceipt,
  'price': price,
  'include': include ?? foodId != null,
};

final _draft = {
  'readBy': 'RULES',
  'purchaseDate': '2026-10-03',
  'purchaseDateFromReceipt': true,
  'lines': [
    _line(
      'LECHE ENTERA',
      'Leche',
      foodId: 'f-milk',
      candidates: [
        {'foodId': 'f-milk', 'name': 'Leche'},
      ],
      category: 'DAIRY',
      storage: 'REFRIGERATOR',
      quantity: {'amount': 1, 'unit': 'LITER'},
      quantityFromReceipt: true,
      price: 0.89,
    ),
    _line(
      'QUESO MOZZ',
      'Queso',
      foodId: 'f-cheese',
      candidates: [
        {'foodId': 'f-cheese', 'name': 'Queso'},
        {'foodId': 'f-mozzarella', 'name': 'Mozzarella'},
      ],
      category: 'DAIRY',
      storage: 'REFRIGERATOR',
      price: 1.95,
    ),
    _line('BOLSA PLASTICO', 'Bolsa plastico', match: 'NONE', price: 0.15),
  ],
};

void main() {
  late InMemoryTokenStorage storage;

  setUp(() => storage = InMemoryTokenStorage('refresh-1'));

  FakeBackend backendWith([Map<String, FakeHandler> extra = const {}]) => FakeBackend({
    'POST /auth/refresh': (_) => const FakeResponse.ok({'accessToken': 'access-2', 'refreshToken': 'refresh-2'}),
    'GET /users/me': (_) => const FakeResponse.ok(_ana),
    'GET /households': (_) => const FakeResponse.ok([_casa]),
    'GET /households/h1': (_) => const FakeResponse.ok(_casa),
    'GET /households/h1/inventory': (_) => const FakeResponse.ok(_emptyInventory),
    'GET /households/h1/inventory/consume-first': (_) => const FakeResponse.ok(_nothingToConsumeFirst),
    _read: (_) => FakeResponse.ok(_draft),
    _confirm: (call) => FakeResponse.ok({'stocked': ((call.body as Map)['lines'] as List).length}),
    ...extra,
  });

  Future<void> openScan(WidgetTester tester, FakeBackend backend, FakeReceiptOcr ocr) async {
    await pumpFreezify(tester, backend, storage, ocr: ocr);
    await tester.tap(find.text('Casa'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Escanear ticket'));
    await tester.pumpAndSettle();
  }

  Finder checkboxOf(String name) =>
      find.descendant(of: find.widgetWithText(ListTile, name), matching: find.byType(Checkbox));

  group('receipt layout', () {
    test('puts back together the rows that OCR reads as separate columns', () {
      final text = receiptText(const [
        OcrLine('LECHE ENTERA', Rect.fromLTWH(10, 100, 200, 20)),
        OcrLine('PAN', Rect.fromLTWH(10, 130, 60, 20)),
        // The price column, read as another block, slightly lower on a crooked photo.
        OcrLine('0,89', Rect.fromLTWH(300, 104, 50, 20)),
        OcrLine('1,15', Rect.fromLTWH(300, 133, 50, 20)),
        OcrLine('MERCADONA', Rect.fromLTWH(80, 20, 150, 30)),
        OcrLine('  ', Rect.fromLTWH(0, 60, 10, 10)),
      ]);

      expect(text, 'MERCADONA\nLECHE ENTERA 0,89\nPAN 1,15');
    });
  });

  group('scanning a receipt', () {
    testWidgets('reads a photo on the phone and adds what the person keeps', (tester) async {
      final ocr = FakeReceiptOcr(available: true, text: 'LECHE ENTERA 0,89\nQUESO MOZZ 1,95');
      final backend = backendWith();
      await openScan(tester, backend, ocr);

      expect(find.textContaining('la foto no se envía'), findsOneWidget);
      await tester.tap(find.text('Hacer foto del ticket'));
      await tester.pumpAndSettle();

      expect(ocr.reads, [PhotoSource.camera]);
      // Only the text travels.
      expect(backend.last(_read).body, {'text': 'LECHE ENTERA 0,89\nQUESO MOZZ 1,95'});
      expect(backend.last(_read).query['lang'], 'es');
      expect(find.text('Leído automáticamente. Revisa cada producto antes de añadirlo.'), findsOneWidget);
      expect(find.text('03/10/2026 · leída del ticket'), findsOneWidget);
      expect(find.text('1 l · del ticket · 0,89 € · Reconocido en el catálogo'), findsOneWidget);
      expect(find.text('1 ud · supuesto · 1,95 € · Reconocido en el catálogo'), findsOneWidget);
      expect(find.text('1 ud · supuesto · 0,15 € · Sin alimento del catálogo'), findsOneWidget);
      expect(find.text('QUESO MOZZ'), findsOneWidget);
      // Only catalog foods are proposed to begin with.
      expect(find.text('Añadir 2 productos'), findsOneWidget);

      await tester.tap(checkboxOf('Queso'));
      await tester.pumpAndSettle();
      await tester.tap(checkboxOf('Bolsa plastico'));
      await tester.pumpAndSettle();
      final inventoryLoads = backend.count('GET /households/h1/inventory');
      await tester.tap(find.text('Añadir 2 productos'));
      await tester.pumpAndSettle();

      final body = backend.last(_confirm).body as Map;
      expect(body['purchaseDate'], '2026-10-03');
      expect(body['lines'], [
        {
          'text': 'LECHE ENTERA',
          'foodId': 'f-milk',
          'name': 'Leche',
          // A catalog food brings its own category.
          'category': null,
          'quantity': {'amount': 1.0, 'unit': 'LITER'},
          'storageLocation': 'REFRIGERATOR',
          'price': 0.89,
          'expirationDate': null,
        },
        {
          'text': 'BOLSA PLASTICO',
          'foodId': null,
          'name': 'Bolsa plastico',
          'category': 'OTHER',
          'quantity': {'amount': 1.0, 'unit': 'UNIT'},
          'storageLocation': 'OTHER',
          'price': 0.15,
          'expirationDate': null,
        },
      ]);
      expect(find.text('2 productos añadidos al inventario'), findsOneWidget);
      // Back in the inventory, which shows what was added.
      expect(find.byTooltip('Escanear ticket'), findsOneWidget);
      expect(backend.count('GET /households/h1/inventory'), greaterThan(inventoryLoads));
    });

    testWidgets('lets the person correct a line before adding it', (tester) async {
      final backend = backendWith({
        'GET /foods': (_) => const FakeResponse.ok([
          {
            'id': 'f-mozzarella',
            'name': 'Mozzarella',
            'category': 'DAIRY',
            'defaultUnit': 'GRAM',
            'defaultStorage': 'REFRIGERATOR',
          },
        ]),
      });
      // Where photos cannot be read, a digital receipt can still be pasted.
      await openScan(tester, backend, FakeReceiptOcr());
      expect(find.text('Hacer foto del ticket'), findsNothing);
      expect(find.textContaining('Aquí no se pueden leer fotos'), findsOneWidget);

      await tester.enterText(find.widgetWithText(TextField, 'Texto del ticket'), 'QUESO MOZZ 1,95');
      await tester.pump();
      await tester.tap(find.text('Leer ticket'));
      await tester.pumpAndSettle();

      await tester.tap(find.byTooltip('Revisar Queso'));
      await tester.pumpAndSettle();
      expect(find.text('En el ticket: QUESO MOZZ'), findsOneWidget);
      await tester.tap(find.widgetWithText(ActionChip, 'Mozzarella'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextFormField, 'Cantidad'), '2');
      await tester.ensureVisible(find.text('Guardar'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Guardar'));
      await tester.pumpAndSettle();

      expect(find.text('Mozzarella'), findsOneWidget);
      expect(find.text('2 uds · 1,95 € · Revisado por ti'), findsOneWidget);
      await tester.tap(find.text('Añadir 2 productos'));
      await tester.pumpAndSettle();

      final lines = (backend.last(_confirm).body as Map)['lines'] as List;
      expect(lines[1], {
        'text': 'QUESO MOZZ',
        'foodId': 'f-mozzarella',
        'name': 'Mozzarella',
        'category': null,
        'quantity': {'amount': 2.0, 'unit': 'UNIT'},
        'storageLocation': 'REFRIGERATOR',
        'price': 1.95,
        'expirationDate': null,
      });
    });

    testWidgets('asks before leaving a review half done', (tester) async {
      final backend = backendWith();
      await openScan(tester, backend, FakeReceiptOcr(available: true, text: 'LECHE 0,89'));
      await tester.tap(find.text('Hacer foto del ticket'));
      await tester.pumpAndSettle();

      await tester.tap(find.byType(BackButton));
      await tester.pumpAndSettle();
      expect(find.text('Si sales ahora se pierde la revisión de este ticket.'), findsOneWidget);
      await tester.tap(find.text('Cancelar'));
      await tester.pumpAndSettle();
      expect(find.text('Leche'), findsOneWidget);

      await tester.tap(find.byType(BackButton));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(TextButton, 'Salir'));
      await tester.pumpAndSettle();
      expect(find.byTooltip('Escanear ticket'), findsOneWidget);
      expect(backend.count(_confirm), 0);
    });

    testWidgets('says so when nothing could be read', (tester) async {
      final ocr = FakeReceiptOcr(available: true);
      final backend = backendWith({
        _read: (_) => const FakeResponse.ok({
          'readBy': 'RULES',
          'purchaseDate': '2026-10-03',
          'purchaseDateFromReceipt': false,
          'lines': <Object>[],
        }),
      });
      await openScan(tester, backend, ocr);

      // Cancelled: nothing is sent.
      await tester.tap(find.text('Elegir una foto'));
      await tester.pumpAndSettle();
      expect(backend.count(_read), 0);

      ocr.text = '  ';
      await tester.tap(find.text('Elegir una foto'));
      await tester.pumpAndSettle();
      expect(find.textContaining('No se ha podido leer texto en la foto'), findsOneWidget);
      expect(backend.count(_read), 0);

      ocr.text = 'GRACIAS POR SU VISITA';
      await tester.tap(find.text('Elegir una foto'));
      await tester.pumpAndSettle();
      expect(find.text('No hemos encontrado productos en este ticket.'), findsOneWidget);
    });
  });
}
