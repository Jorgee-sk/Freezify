import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';

void main() {
  runApp(
    // Failures are shown with an explicit retry action instead of being retried silently.
    ProviderScope(retry: (_, _) => null, child: const FreezifyApp()),
  );
}
