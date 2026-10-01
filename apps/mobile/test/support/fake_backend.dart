import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:freezify/core/token_storage.dart';

class FakeResponse {
  const FakeResponse(this.status, [this.body]) : stream = null;

  const FakeResponse.ok([Object? body]) : this(200, body);

  FakeResponse.problem(int status, String code) : this(status, {'status': status, 'code': code, 'detail': code});

  /// A response whose body arrives over time, as server-sent events do.
  const FakeResponse.stream(Stream<Uint8List> this.stream) : status = 200, body = null;

  final int status;
  final Object? body;
  final Stream<Uint8List>? stream;
}

class RecordedCall {
  const RecordedCall(this.route, this.body, this.authorization, this.query);

  final String route;
  final Object? body;
  final String? authorization;
  final Map<String, String> query;
}

typedef FakeHandler = FakeResponse Function(RecordedCall call);

/// Stands in for the HTTP transport. Routes are keyed by "METHOD /path" (without the query string); an
/// unexpected request fails the test, except for event streams: unless a test serves one, they stay open and
/// silent.
class FakeBackend implements HttpClientAdapter {
  FakeBackend(this.routes);

  static const baseUrl = 'http://backend.test/api/v1';

  final Map<String, FakeHandler> routes;
  final List<RecordedCall> calls = [];

  /// When set, every request fails as if there were no connectivity.
  bool offline = false;

  int count(String route) => calls.where((call) => call.route == route).length;

  RecordedCall last(String route) => calls.lastWhere((call) => call.route == route);

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    if (offline) {
      throw DioException.connectionError(requestOptions: options, reason: 'offline');
    }
    final path = options.uri.path.replaceFirst('/api/v1', '');
    final call = RecordedCall(
      '${options.method} $path',
      options.data,
      options.headers['Authorization'] as String?,
      options.uri.queryParameters,
    );
    calls.add(call);

    final handler = routes[call.route];
    if (handler == null && path.endsWith('/events')) {
      return _eventStream(StreamController<Uint8List>().stream);
    }
    if (handler == null) throw StateError('Unexpected request: ${call.route}');
    final response = handler(call);
    if (response.stream != null) return _eventStream(response.stream!);
    return ResponseBody.fromString(
      response.body == null ? '' : jsonEncode(response.body),
      response.status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  ResponseBody _eventStream(Stream<Uint8List> stream) => ResponseBody(
    stream,
    200,
    headers: {
      Headers.contentTypeHeader: ['text/event-stream'],
    },
  );

  @override
  void close({bool force = false}) {}
}

class InMemoryTokenStorage implements TokenStorage {
  InMemoryTokenStorage([this.value]);

  String? value;

  @override
  Future<String?> read() async => value;

  @override
  Future<void> write(String refreshToken) async => value = refreshToken;

  @override
  Future<void> delete() async => value = null;
}
