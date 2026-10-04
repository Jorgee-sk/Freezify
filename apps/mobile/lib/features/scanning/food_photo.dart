import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/providers.dart';
import '../../l10n/app_localizations.dart';
import '../inventory/inventory_models.dart';
import 'receipt_ocr.dart';

/// Below this the model is guessing, and the candidates are introduced as guesses.
const _sure = 0.6;

/// Takes or picks a photo of a food. The rest of the app never talks to the camera, so tests can stand in for it.
abstract interface class PhotoPicker {
  /// Whether a camera can be used here; elsewhere photos are picked from files.
  bool get hasCamera;

  /// The photo, already shrunk to be sent quickly; `null` when the person cancelled.
  Future<Uint8List?> pick(PhotoSource source);
}

class ImagePickerPhotoPicker implements PhotoPicker {
  final _picker = ImagePicker();

  @override
  bool get hasCamera =>
      !kIsWeb && (defaultTargetPlatform == TargetPlatform.android || defaultTargetPlatform == TargetPlatform.iOS);

  @override
  Future<Uint8List?> pick(PhotoSource source) async {
    final photo = await _picker.pickImage(
      source: source == PhotoSource.camera ? ImageSource.camera : ImageSource.gallery,
      // Enough to tell foods apart, small enough to send quickly.
      maxWidth: 1280,
      maxHeight: 1280,
      imageQuality: 85,
    );
    return photo?.readAsBytes();
  }
}

final photoPickerProvider = Provider<PhotoPicker>((ref) => ImagePickerPhotoPicker());

/// A food a photo may show, as a language model judged it.
class PhotoCandidate {
  const PhotoCandidate({
    required this.foodId,
    required this.name,
    required this.confidence,
    required this.category,
    required this.defaultUnit,
    required this.defaultStorage,
  });

  factory PhotoCandidate.fromJson(Map<String, dynamic> json) => PhotoCandidate(
    foodId: json['foodId'] as String?,
    name: json['name'] as String,
    confidence: (json['confidence'] as num).toDouble(),
    category: FoodCategory.parse(json['category'] as String),
    defaultUnit: json['defaultUnit'] == null ? null : Unit.parse(json['defaultUnit'] as String),
    defaultStorage: json['defaultStorage'] == null ? null : StorageLocation.parse(json['defaultStorage'] as String),
  );

  /// The catalog food, or `null` for something that is not in the catalog.
  final String? foodId;
  final String name;

  /// Between 0 and 1: how sure the model is.
  final double confidence;
  final FoodCategory category;
  final Unit? defaultUnit;
  final StorageLocation? defaultStorage;

  /// As a catalog food, when it is one.
  Food? get food => foodId == null || defaultUnit == null || defaultStorage == null
      ? null
      : Food(id: foodId!, name: name, category: category, defaultUnit: defaultUnit!, defaultStorage: defaultStorage!);
}

/// Sends the photo to the server, whose language model says which food it may show. Nothing is stored.
Future<List<PhotoCandidate>> identifyFood(WidgetRef ref, String householdId, String language, Uint8List photo) async {
  final form = FormData.fromMap({'image': MultipartFile.fromBytes(photo, filename: 'photo.jpg')});
  final json =
      await ref.read(apiClientProvider).post('/households/$householdId/scans/food?lang=$language', body: form)
          as List<dynamic>;
  return [for (final item in json) PhotoCandidate.fromJson(item as Map<String, dynamic>)];
}

/// Shows what the photo may be and returns the candidate the person picks; `null` if none.
Future<PhotoCandidate?> chooseCandidate(BuildContext context, List<PhotoCandidate> candidates) {
  return showModalBottomSheet<PhotoCandidate>(
    context: context,
    showDragHandle: true,
    builder: (context) {
      final l10n = AppLocalizations.of(context);
      final theme = Theme.of(context);
      final sure = candidates.isNotEmpty && candidates.first.confidence >= _sure;
      return SafeArea(
        child: ListView(
          shrinkWrap: true,
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          children: [
            Text(
              candidates.isEmpty ? l10n.photoNothing : (sure ? l10n.photoWhatIsIt : l10n.photoNotSure),
              style: theme.textTheme.titleMedium,
            ),
            for (final candidate in candidates)
              ListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(candidate.name),
                trailing: Text('${(candidate.confidence * 100).round()} %'),
                onTap: () => Navigator.of(context).pop(candidate),
              ),
            if (candidates.isNotEmpty)
              Text(
                l10n.photoNoneHelp,
                style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
          ],
        ),
      );
    },
  );
}
