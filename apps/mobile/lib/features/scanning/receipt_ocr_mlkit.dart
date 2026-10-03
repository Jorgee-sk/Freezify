import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';
import 'package:image_picker/image_picker.dart';

import 'receipt_ocr.dart';

ReceiptOcr createReceiptOcr() => MlKitReceiptOcr();

/// Google ML Kit text recognition, which runs on the phone, offline and at no cost.
class MlKitReceiptOcr implements ReceiptOcr {
  final _picker = ImagePicker();

  @override
  bool get available =>
      !kIsWeb && (defaultTargetPlatform == TargetPlatform.android || defaultTargetPlatform == TargetPlatform.iOS);

  @override
  Future<String?> read(PhotoSource source) async {
    final photo = await _picker.pickImage(
      source: source == PhotoSource.camera ? ImageSource.camera : ImageSource.gallery,
      // Large enough for small print, small enough to be read quickly.
      maxWidth: 2400,
      maxHeight: 2400,
    );
    if (photo == null) return null;
    final recognizer = TextRecognizer(script: TextRecognitionScript.latin);
    try {
      final recognized = await recognizer.processImage(InputImage.fromFilePath(photo.path));
      return receiptText([
        for (final block in recognized.blocks)
          for (final line in block.lines) OcrLine(line.text, line.boundingBox),
      ]);
    } finally {
      await recognizer.close();
      // The picker hands over a copy in the app's cache, also for gallery photos; the person's photos stay.
      try {
        await File(photo.path).delete();
      } catch (error) {
        debugPrint('Could not delete the copy of the receipt photo: $error');
      }
    }
  }
}
