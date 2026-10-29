package lab.backend.identity.session;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lab.backend.identity.credential.PasswordPolicy;

/** Binding payload của logout-all/password-change, không phải proof hoặc quyền.
 * Owner/session/epoch/realm còn phải được authority đối chiếu dưới canonical
 * locks. Không KDF, DB, cookie/idle effect hoặc tự bật API từ helper này.
 */
public final class IdentitySensitiveIntent {
  private static final byte[] DOMAIN="identity-sensitive-intent-v1\0".getBytes(StandardCharsets.UTF_8);
  private static final byte[] KEY_DOMAIN="identity-sensitive-storage-key-v1".getBytes(StandardCharsets.UTF_8);
  private static final Set<String> ACTIONS=Set.of("LOGOUT_ALL","PASSWORD_CHANGE");
  private IdentitySensitiveIntent() {}

  /** Hash command canon: domain rồi bốn UTF-8 fields action/context/key/password,
   * mỗi field có uint32 big-endian byte length. Không trim/normalize password;
   * length framing ngăn newline/delimiter trong password đổi ranh giới fields.
   * Logout-all dùng password field rỗng, không chấp nhận password phụ ẩn.
   */
  public static String digest(String action,UUID context,UUID operationKey,String newPassword) {
    if(!ACTIONS.contains(action==null?"":action)||context==null||operationKey==null)
      throw new IllegalArgumentException("Invalid Identity sensitive intent binding");
    if(action.equals("LOGOUT_ALL")) {
      if(newPassword!=null)throw new IllegalArgumentException("Logout-all has no password payload");
      newPassword="";
    } else {
      // Reuse R03 Unicode/code-point policy; không âm thầm encode surrogate lỗi
      // thành replacement byte. Corpus eligibility vẫn do credential owner kiểm.
      PasswordPolicy.requireValid(newPassword);
    }
    try {
      var hash=MessageDigest.getInstance("SHA-256");hash.update(DOMAIN);
      for(String value:new String[]{action,context.toString(),operationKey.toString(),newPassword}) {
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        hash.update(ByteBuffer.allocate(4).putInt(bytes.length).array());hash.update(bytes);
      }
      return HexFormat.of().formatHex(hash.digest());
    }catch(GeneralSecurityException unavailable){throw new IllegalStateException("Sensitive intent digest unavailable");}
  }

  /** DB chỉ lưu keyed HMAC của client intent, không lưu SHA password-derived
   * dễ dò dictionary khi DB bị đọc. Derive key theo domain riêng từ configured
   * 32-byte secret; key phải ổn định qua process restart. LEARNING_RESET vẫn
   * dùng owner binding riêng, không được reserve proof Identity sensitive.
   * Không trả key/hash trong error hoặc audit; byte[] output là storage binding.
   */
  public static byte[] protectedDigest(byte[] configuredKey,String intentHex) {
    if(configuredKey==null||configuredKey.length!=32||Arrays.equals(configuredKey,new byte[32]))
      throw new IllegalArgumentException("Sensitive intent key unavailable");
    if(intentHex==null||!intentHex.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("Invalid sensitive intent digest");
    byte[] derived=null;
    try {
      Mac derivation=Mac.getInstance("HmacSHA256");derivation.init(new SecretKeySpec(configuredKey,"HmacSHA256"));
      derived=derivation.doFinal(KEY_DOMAIN);
      Mac binding=Mac.getInstance("HmacSHA256");binding.init(new SecretKeySpec(derived,"HmacSHA256"));
      return binding.doFinal(HexFormat.of().parseHex(intentHex));
    }catch(GeneralSecurityException unavailable){throw new IllegalStateException("Sensitive intent binding unavailable");}
    finally{if(derived!=null)Arrays.fill(derived,(byte)0);}
  }
}
