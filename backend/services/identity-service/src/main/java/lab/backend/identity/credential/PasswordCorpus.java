package lab.backend.identity.credential;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

public final class PasswordCorpus {
  private final Set<String> blocked;
  private PasswordCorpus(Set<String> blocked) { this.blocked=blocked; }
  public static PasswordCorpus load(Path path, String expectedSha256) throws IOException {
    if(expectedSha256==null||!expectedSha256.matches("[0-9a-fA-F]{64}")||Files.size(path)>64L*1024*1024)
      throw new IllegalStateException("Invalid configured password corpus");
    byte[] bytes=Files.readAllBytes(path);
    String actual=HexFormat.of().formatHex(digest(bytes));
    if (!actual.equalsIgnoreCase(expectedSha256)) throw new IllegalStateException("Configured password corpus hash mismatch");
    String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
    Set<String> words=text.lines().filter(s->!s.isEmpty()&&!s.startsWith("#")).collect(Collectors.toUnmodifiableSet());
    if(words.isEmpty()) throw new IllegalStateException("Configured password corpus is empty");
    return new PasswordCorpus(words);
  }
  public void requireAllowed(String password) { if(blocked.contains(password)) throw new IllegalArgumentException("Password is blocked by policy"); }
  private static byte[] digest(byte[] input) { try{return MessageDigest.getInstance("SHA-256").digest(input);}catch(Exception e){throw new IllegalStateException(e);} }
}
