package lab.backend.identity.credential;

public final class PasswordPolicy {
  private PasswordPolicy() {}
  public static void requireValid(String password) {
    if (password == null) throw new IllegalArgumentException("Password is required");
    for (int i = 0; i < password.length(); i++) {
      char ch = password.charAt(i);
      if (Character.isHighSurrogate(ch)) {
        if (++i >= password.length() || !Character.isLowSurrogate(password.charAt(i)))
          throw new IllegalArgumentException("Password contains invalid Unicode");
      } else if (Character.isLowSurrogate(ch)) {
        throw new IllegalArgumentException("Password contains invalid Unicode");
      }
    }
    int points = password.codePointCount(0, password.length());
    if (points < 15 || points > 128) throw new IllegalArgumentException("Password must contain 15 to 128 Unicode code points");
  }
}
