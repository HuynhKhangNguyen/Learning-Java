package lab.backend.identity.credential;

interface PasswordHasher {
  String hash(String password);
  boolean matches(String password, String encoded);
}
