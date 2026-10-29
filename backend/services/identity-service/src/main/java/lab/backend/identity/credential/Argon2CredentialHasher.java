package lab.backend.identity.credential;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

public final class Argon2CredentialHasher implements PasswordHasher {
  public static final int SALT_BYTES=16, OUTPUT_BYTES=32, PARALLELISM=1, MEMORY_KIB=19456, ITERATIONS=2;
  private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(SALT_BYTES, OUTPUT_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS);
  public String hash(String password) { PasswordPolicy.requireValid(password); return encoder.encode(password); }
  public boolean matches(String password, String encoded) {
    PasswordPolicy.requireValid(password);
    if(encoded!=null&&!encoded.matches("\\$argon2id\\$v=19\\$m=19456,t=2,p=1\\$[A-Za-z0-9+/]{22}\\$[A-Za-z0-9+/]{43}"))
      throw new IllegalStateException("Unsupported credential hash");
    return encoded != null && encoder.matches(password, encoded);
  }
}
