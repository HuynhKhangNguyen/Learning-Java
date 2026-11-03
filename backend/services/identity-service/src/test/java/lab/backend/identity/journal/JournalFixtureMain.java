package lab.backend.identity.journal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Process fixture: tạo writer/crash thật; class này chỉ nằm trong test artifact. */
public final class JournalFixtureMain {
  private JournalFixtureMain() {}

  public static void main(String[] args) throws Exception {
    Path root = Path.of(args[0]);
    String id = args[1];
    Path barrier = Path.of(args[2]);
    String mode = args[3];
    Files.writeString(barrier.resolve(id + ".ready"), "ready", UTF_8);
    while (Files.list(barrier).count() < 2) Thread.onSpinWait();
    var store = new FilesystemJournalStore(root);
    if (mode.equals("after-record")) {
      Path record = root.resolve("lab/records").resolve(id + ".bin");
      Files.createDirectories(record.getParent());
      Files.write(record, ("payload-" + id).getBytes(UTF_8));
      Runtime.getRuntime().halt(71);
    }
    if (mode.equals("after-node")) {
      byte[] payload = ("payload-" + id).getBytes(UTF_8);
      Path record = root.resolve("lab/records").resolve(id + ".bin");
      Files.createDirectories(record.getParent()); Files.createDirectories(root.resolve("lab/nodes"));
      Files.write(record, payload);
      byte[] node = ("lab\n1\n" + "0".repeat(64) + "\n" + id + "\n" + sha(payload) + "\n")
          .getBytes(UTF_8);
      Files.write(root.resolve("lab/nodes").resolve(sha(node) + ".node"), node);
      Runtime.getRuntime().halt(72);
    }
    var receipt = store.append("lab", id, ("payload-" + id).getBytes(UTF_8));
    if (mode.equals("after-head")) Runtime.getRuntime().halt(73);
    System.out.println(receipt.recordId() + ":" + receipt.verifiedHeadDigest());
  }

  private static String sha(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }
}
