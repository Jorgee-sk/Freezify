import 'dart:ui';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'receipt_ocr_mlkit.dart' if (dart.library.js_interop) 'receipt_ocr_unavailable.dart';

/// Where the photo of a receipt comes from.
enum PhotoSource { camera, gallery }

/// Reads the text of a photographed receipt on the device. The photo never leaves the phone: only its text is
/// sent, and the photo is deleted once read. The rest of the app never talks to the OCR library, so tests can
/// stand in for it.
abstract interface class ReceiptOcr {
  /// Whether photos can be read here (Android and iOS). Elsewhere the text can still be pasted.
  bool get available;

  /// Takes or picks a photo and reads it. Returns `null` when the person cancelled, and an empty text when
  /// nothing could be read.
  Future<String?> read(PhotoSource source);
}

final receiptOcrProvider = Provider<ReceiptOcr>((ref) => createReceiptOcr());

/// A line of text the OCR found, where it found it.
class OcrLine {
  const OcrLine(this.text, this.box);

  final String text;
  final Rect box;
}

/// Puts back together the rows of a receipt. OCR reads columns as separate blocks (the products, then the
/// prices), so the lines are joined again by their height on the photo: what is side by side is one row.
String receiptText(List<OcrLine> lines) {
  final sorted = [...lines.where((line) => line.text.trim().isNotEmpty)]
    ..sort((a, b) => a.box.center.dy.compareTo(b.box.center.dy));
  final rows = <List<OcrLine>>[];
  for (final line in sorted) {
    final row = rows.isEmpty ? null : rows.last;
    if (row != null) {
      final rowCenter = row.map((other) => other.box.center.dy).reduce((a, b) => a + b) / row.length;
      final rowHeight = row.map((other) => other.box.height).reduce((a, b) => a + b) / row.length;
      final tolerance = (rowHeight < line.box.height ? rowHeight : line.box.height) / 2;
      if ((line.box.center.dy - rowCenter).abs() <= tolerance) {
        row.add(line);
        continue;
      }
    }
    rows.add([line]);
  }
  return [
    for (final row in rows)
      ([...row]..sort((a, b) => a.box.left.compareTo(b.box.left))).map((line) => line.text.trim()).join(' '),
  ].join('\n');
}
