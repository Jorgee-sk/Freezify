import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../../core/providers.dart';
import 'scan_models.dart';

class ScanRepository {
  const ScanRepository(this._api);

  final ApiClient _api;

  /// Reads the text of a receipt into a draft. Nothing is stored.
  Future<ReceiptDraft> read(String householdId, String text, String language) async {
    final json =
        await _api.post('/households/$householdId/scans/receipt?lang=$language', body: {'text': text})
            as Map<String, dynamic>;
    return ReceiptDraft.fromJson(json);
  }

  /// Puts the reviewed lines in the inventory. Returns how many went in.
  Future<int> confirm(String householdId, String purchaseDate, List<DraftLine> lines) async {
    final json =
        await _api.post(
              '/households/$householdId/scans/receipt/confirm',
              body: {
                'purchaseDate': purchaseDate,
                'lines': [for (final line in lines) line.toJson()],
              },
            )
            as Map<String, dynamic>;
    return json['stocked'] as int;
  }
}

final scanRepositoryProvider = Provider<ScanRepository>((ref) => ScanRepository(ref.watch(apiClientProvider)));
