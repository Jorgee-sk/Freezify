import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:freezify/features/scanning/receipt_ocr.dart';

import 'support/app_harness.dart';
import 'support/fake_backend.dart';

const _ana = {'id': 'u1', 'email': 'ana@example.com', 'displayName': 'Ana', 'locale': 'es'};
const _casa = {'id': 'h1', 'name': 'Casa', 'role': 'OWNER', 'memberCount': 1};
const _emptyPage = {'items': <Object>[], 'page': 0, 'size': 50, 'totalItems': 0, 'totalPages': 0};
const _nothingToConsumeFirst = {
  'counts': {'EXPIRED': 0, 'TODAY': 0, 'URGENT': 0, 'SOON': 0, 'UPCOMING': 0, 'OK': 0, 'NO_DATE': 0},
  'items': <Object>[],
};

const _identify = 'POST /households/h1/scans/food';
const _create = 'POST /households/h1/inventory';

Map<String, Object?> _candidate(String name, double confidence, {bool catalog = true}) => {
  'foodId': catalog ? 'f-$name' : null,
  'name': name,
  'confidence': confidence,
  'category': catalog ? 'VEGETABLES' : 'OTHER',
  'defaultUnit': catalog ? 'UNIT' : null,
  'defaultStorage': catalog ? 'REFRIGERATOR' : null,
};

final _photo = Uint8List.fromList([0xFF, 0xD8, 0xFF, 0xE0]);

void main() {
  late InMemoryTokenStorage storage;

  setUp(() => storage = InMemoryTokenStorage('refresh-1'));

  FakeBackend backendWith([Map<String, FakeHandler> extra = const {}]) => FakeBackend({
    'POST /auth/refresh': (_) => const FakeResponse.ok({'accessToken': 'access-2', 'refreshToken': 'refresh-2'}),
    'GET /users/me': (_) => const FakeResponse.ok(_ana),
    'GET /households': (_) => const FakeResponse.ok([_casa]),
    'GET /households/h1': (_) => const FakeResponse.ok(_casa),
    'GET /households/h1/inventory': (_) => const FakeResponse.ok(_emptyPage),
    'GET /households/h1/inventory/recent': (_) => const FakeResponse.ok(<Object>[]),
    'GET /households/h1/inventory/consume-first': (_) => const FakeResponse.ok(_nothingToConsumeFirst),
    'GET /ai': (_) => const FakeResponse.ok({'enabled': true}),
    ...extra,
  });

  Future<void> openForm(WidgetTester tester, FakeBackend backend, FakePhotoPicker photos) async {
    await pumpFreezify(tester, backend, storage, photos: photos);
    await tester.tap(find.text('Casa'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Añadir alimento'));
    await tester.pumpAndSettle();
  }

  group('identifying a food from a photo', () {
    testWidgets('offers candidates with their confidence and fills in the one picked', (tester) async {
      final photos = FakePhotoPicker(photo: _photo);
      final backend = backendWith({
        _identify: (_) => FakeResponse.ok([
          _candidate('Tomate', 0.81),
          _candidate('Pimiento rojo', 0.12),
          _candidate('Caqui', 0.07, catalog: false),
        ]),
        _create: (_) => const FakeResponse(201, {}),
      });
      await openForm(tester, backend, photos);

      expect(find.textContaining('La foto se envía al servicio de IA'), findsOneWidget);
      await tester.tap(find.text('Identificar por foto'));
      await tester.pumpAndSettle();

      // Without a camera, the photo is picked from the files.
      expect(photos.picks, [PhotoSource.gallery]);
      expect(backend.last(_identify).body, isA<FormData>());
      expect(backend.last(_identify).query['lang'], 'es');
      expect(find.text('¿Qué es?'), findsOneWidget);
      expect(find.text('81 %'), findsOneWidget);
      expect(find.text('Caqui'), findsOneWidget);

      await tester.tap(find.text('Tomate'));
      await tester.pumpAndSettle();
      expect(tester.widget<TextFormField>(find.byType(TextFormField).first).controller!.text, 'Tomate');

      final save = find.widgetWithText(FilledButton, 'Guardar');
      await tester.ensureVisible(save);
      await tester.pumpAndSettle();
      await tester.tap(save);
      await tester.pumpAndSettle();
      final body = backend.last(_create).body as Map;
      expect(body['foodId'], 'f-Tomate');
      expect(body['name'], 'Tomate');
      expect(body['category'], 'VEGETABLES');
    });

    testWidgets('asks camera or gallery where there is a camera, and says when it is not sure', (tester) async {
      final photos = FakePhotoPicker(hasCamera: true, photo: _photo);
      final backend = backendWith({
        _identify: (_) => FakeResponse.ok([_candidate('Pera', 0.4), _candidate('Manzana', 0.35)]),
      });
      await openForm(tester, backend, photos);

      await tester.tap(find.text('Identificar por foto'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Hacer una foto'));
      await tester.pumpAndSettle();

      expect(photos.picks, [PhotoSource.camera]);
      expect(find.text('No estamos seguros. ¿Es alguno de estos?'), findsOneWidget);
    });

    testWidgets('says why a photo was not read, and sends nothing when cancelled', (tester) async {
      final photos = FakePhotoPicker();
      final backend = backendWith({_identify: (_) => FakeResponse.problem(429, 'AI_LIMIT_REACHED')});
      await openForm(tester, backend, photos);

      await tester.tap(find.text('Identificar por foto'));
      await tester.pumpAndSettle();
      expect(backend.count(_identify), 0);

      photos.photo = _photo;
      await tester.tap(find.text('Identificar por foto'));
      await tester.pumpAndSettle();
      expect(find.text('Has usado todas las peticiones de IA de hoy. Vuelve a intentarlo mañana.'), findsOneWidget);
    });

    testWidgets('is not offered without a model', (tester) async {
      await openForm(
        tester,
        backendWith({
          'GET /ai': (_) => const FakeResponse.ok({'enabled': false}),
        }),
        FakePhotoPicker(),
      );

      expect(find.text('Identificar por foto'), findsNothing);
    });
  });
}
