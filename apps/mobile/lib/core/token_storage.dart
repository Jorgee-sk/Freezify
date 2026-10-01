/// Where the refresh token is kept between app launches.
abstract interface class TokenStorage {
  Future<String?> read();
  Future<void> write(String refreshToken);
  Future<void> delete();
}
