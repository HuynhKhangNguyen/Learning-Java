package lab.backend.content.publication;

import java.security.MessageDigest;
import java.util.HexFormat;

/** Tính digest trên đúng byte sequence đã nhận; không parse/reserialize payload. */
public final class PublicationDigest {
  private PublicationDigest() {}
  public static String sha256(byte[] bytes) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch (Exception impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
  }
}

