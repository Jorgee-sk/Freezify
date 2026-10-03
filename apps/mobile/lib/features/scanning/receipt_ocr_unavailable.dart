import 'receipt_ocr.dart';

/// On the web there is no on-device OCR: receipts can only be pasted as text.
ReceiptOcr createReceiptOcr() => const _UnavailableReceiptOcr();

class _UnavailableReceiptOcr implements ReceiptOcr {
  const _UnavailableReceiptOcr();

  @override
  bool get available => false;

  @override
  Future<String?> read(PhotoSource source) async => null;
}
