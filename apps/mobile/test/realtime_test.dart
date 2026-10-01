import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:freezify/core/api_client.dart';
import 'package:freezify/features/realtime/household_event_stream.dart';

import 'support/fake_backend.dart';

const _events = 'GET /households/h1/events';
const _fast = Duration(milliseconds: 5);
const _settle = Duration(milliseconds: 80);

Uint8List _bytes(String text) => utf8.encode(text);

void main() {
  late InMemoryTokenStorage storage;

  setUp(() => storage = InMemoryTokenStorage());

  Future<ApiClient> clientFor(FakeBackend backend, {String accessToken = 'access-1'}) async {
    final client = ApiClient(baseUrl: FakeBackend.baseUrl, storage: storage, adapter: backend);
    await client.storeSession(accessToken: accessToken, refreshToken: 'refresh-1');
    return client;
  }

  /// A stream under test, with short delays and disposed when the test ends.
  HouseholdEventStream listen(ApiClient client, void Function() onChange, {Duration? idleTimeout}) {
    final stream = HouseholdEventStream(
      client,
      'h1',
      onInventoryChanged: onChange,
      retryDelay: _fast,
      maxRetryDelay: _fast * 4,
      idleTimeout: idleTimeout ?? const Duration(seconds: 30),
    )..start();
    addTearDown(stream.dispose);
    return stream;
  }

  test('reports inventory changes, even when an event arrives split across chunks', () async {
    final events = StreamController<Uint8List>();
    addTearDown(events.close);
    final backend = FakeBackend({_events: (_) => FakeResponse.stream(events.stream)});
    var changes = 0;
    listen(await clientFor(backend), () => changes++);

    events.add(_bytes('event:connected\ndata:{}\n\neve'));
    events.add(_bytes('nt:inventory-changed\ndata:{}\n\n:keepalive\n\n'));
    events.add(_bytes('event: inventory-changed\r\ndata:{}\r\n\r\n'));
    await Future<void>.delayed(_settle);

    expect(changes, 2);
    expect(backend.count(_events), 1);
    expect(backend.last(_events).authorization, 'Bearer access-1');
  });

  test('reconnects when the connection drops and reports that something may have been missed', () async {
    final first = StreamController<Uint8List>();
    final second = StreamController<Uint8List>();
    addTearDown(second.close);
    final streams = [first, second];
    final backend = FakeBackend({_events: (_) => FakeResponse.stream(streams.removeAt(0).stream)});
    var changes = 0;
    listen(await clientFor(backend), () => changes++);
    await Future<void>.delayed(_settle);
    expect(changes, 0);

    unawaited(first.close());
    await Future<void>.delayed(_settle);

    expect(backend.count(_events), 2);
    expect(changes, 1);

    second.add(_bytes('event:inventory-changed\ndata:{}\n\n'));
    await Future<void>.delayed(_settle);
    expect(changes, 2);
  });

  test('reconnects when the connection has gone silent', () async {
    final backend = FakeBackend({});
    listen(await clientFor(backend), () {}, idleTimeout: const Duration(milliseconds: 20));

    await Future<void>.delayed(const Duration(milliseconds: 200));

    expect(backend.count(_events), greaterThan(1));
  });

  test('keeps trying while the server cannot be reached', () async {
    var attempts = 0;
    final backend = FakeBackend({
      _events: (_) => ++attempts < 3
          ? FakeResponse.problem(503, 'UNAVAILABLE')
          : FakeResponse.stream(StreamController<Uint8List>().stream),
    });
    listen(await clientFor(backend), () {});

    await Future<void>.delayed(const Duration(milliseconds: 200));

    expect(backend.count(_events), 3);
  });

  test('renews an expired session before listening', () async {
    final backend = FakeBackend({
      _events: (call) => call.authorization == 'Bearer access-2'
          ? FakeResponse.stream(StreamController<Uint8List>().stream)
          : FakeResponse.problem(401, 'UNAUTHORIZED'),
      'POST /auth/refresh': (_) => const FakeResponse.ok({'accessToken': 'access-2', 'refreshToken': 'refresh-2'}),
    });
    listen(await clientFor(backend, accessToken: 'expired'), () {});

    await Future<void>.delayed(_settle);

    expect(backend.count(_events), 2);
    expect(backend.count('POST /auth/refresh'), 1);
  });

  test('stops for good when the user no longer belongs to the household', () async {
    final backend = FakeBackend({_events: (_) => FakeResponse.problem(404, 'HOUSEHOLD_NOT_FOUND')});
    listen(await clientFor(backend), () {});

    await Future<void>.delayed(_settle);

    expect(backend.count(_events), 1);
  });

  test('does not reconnect or report anything after being disposed', () async {
    final events = StreamController<Uint8List>();
    final backend = FakeBackend({_events: (_) => FakeResponse.stream(events.stream)});
    var changes = 0;
    final stream = listen(await clientFor(backend), () => changes++);
    await Future<void>.delayed(_settle);

    stream.dispose();
    events.add(_bytes('event:inventory-changed\ndata:{}\n\n'));
    unawaited(events.close());
    await Future<void>.delayed(_settle);

    expect(changes, 0);
    expect(backend.count(_events), 1);
  });
}
