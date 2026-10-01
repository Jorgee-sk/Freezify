import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../../core/providers.dart';
import '../auth/auth_controller.dart';

enum HouseholdRole {
  owner,
  member;

  static HouseholdRole parse(String value) => value == 'OWNER' ? owner : member;
}

class Household {
  const Household({required this.id, required this.name, required this.role, required this.memberCount});

  factory Household.fromJson(Map<String, dynamic> json) => Household(
    id: json['id'] as String,
    name: json['name'] as String,
    role: HouseholdRole.parse(json['role'] as String),
    memberCount: json['memberCount'] as int,
  );

  final String id;
  final String name;
  final HouseholdRole role;
  final int memberCount;

  bool get isOwner => role == HouseholdRole.owner;
}

class Member {
  const Member({
    required this.userId,
    required this.displayName,
    required this.email,
    required this.role,
    required this.joinedAt,
  });

  factory Member.fromJson(Map<String, dynamic> json) => Member(
    userId: json['userId'] as String,
    displayName: json['displayName'] as String,
    email: json['email'] as String,
    role: HouseholdRole.parse(json['role'] as String),
    joinedAt: DateTime.parse(json['joinedAt'] as String),
  );

  final String userId;
  final String displayName;
  final String email;
  final HouseholdRole role;
  final DateTime joinedAt;
}

class Invitation {
  const Invitation({required this.code, required this.expiresAt});

  factory Invitation.fromJson(Map<String, dynamic> json) =>
      Invitation(code: json['code'] as String, expiresAt: DateTime.parse(json['expiresAt'] as String));

  final String code;
  final DateTime expiresAt;
}

class HouseholdsRepository {
  const HouseholdsRepository(this._api);

  final ApiClient _api;

  Future<List<Household>> list() async {
    final json = await _api.get('/households') as List<dynamic>;
    return [for (final item in json) Household.fromJson(item as Map<String, dynamic>)];
  }

  Future<Household> get(String id) async => _household(await _api.get('/households/$id'));

  Future<Household> create(String name) async => _household(await _api.post('/households', body: {'name': name}));

  Future<Household> join(String code) async =>
      _household(await _api.post('/households/join', body: {'code': code}));

  Future<Household> rename(String id, String name) async =>
      _household(await _api.patch('/households/$id', body: {'name': name}));

  Future<void> delete(String id) => _api.delete('/households/$id');

  Future<List<Member>> members(String id) async {
    final json = await _api.get('/households/$id/members') as List<dynamic>;
    return [for (final item in json) Member.fromJson(item as Map<String, dynamic>)];
  }

  Future<void> removeMember(String id, String userId) => _api.delete('/households/$id/members/$userId');

  Future<Invitation> invite(String id) async =>
      Invitation.fromJson(await _api.post('/households/$id/invitations') as Map<String, dynamic>);

  Household _household(dynamic json) => Household.fromJson(json as Map<String, dynamic>);
}

final householdsRepositoryProvider = Provider<HouseholdsRepository>(
  (ref) => HouseholdsRepository(ref.watch(apiClientProvider)),
);

// These depend on the signed-in user so that nothing cached survives a change of account.
final householdsProvider = FutureProvider.autoDispose<List<Household>>((ref) {
  ref.watch(authControllerProvider.select((auth) => auth.value?.id));
  return ref.watch(householdsRepositoryProvider).list();
});

final householdProvider = FutureProvider.autoDispose.family<Household, String>((ref, id) {
  ref.watch(authControllerProvider.select((auth) => auth.value?.id));
  return ref.watch(householdsRepositoryProvider).get(id);
});

final membersProvider = FutureProvider.autoDispose.family<List<Member>, String>((ref, id) {
  ref.watch(authControllerProvider.select((auth) => auth.value?.id));
  return ref.watch(householdsRepositoryProvider).members(id);
});
