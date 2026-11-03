package lab.backend.identity.journal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Process fixture của CMD-06; fault/barrier control chỉ tồn tại trong test artifact. */
public final class JournalFixtureMain {
  private JournalFixtureMain() {}

  public static void main(String[] args) throws Exception {
    Path root = Path.of(args[0]);
    String id = args[1];
    Path barrier = Path.of(args[2]);
    String mode = args[3];
    Files.writeString(barrier.resolve(id + ".ready"), "ready", UTF_8);
    while (!Files.exists(barrier.resolve("go"))) {
      Thread.onSpinWait();
    }

    if (mode.equals("after-record")) {
      writeRecord(root, id);
      Runtime.getRuntime().halt(71);
    }
    if (mode.equals("after-node")) {
      byte[] payload = writeRecord(root, id);
      Path nodes = root.resolve("lab/nodes");
      Files.createDirectories(nodes);
      byte[] node = ("lab\n1\n" + "0".repeat(64) + "\n" + id + "\n"
          + sha(payload) + "\n").getBytes(UTF_8);
      Files.write(nodes.resolve(sha(node) + ".node"), node);
      Runtime.getRuntime().halt(72);
    }

    var store = new FilesystemJournalStore(root);
    var receipt = store.append("lab", id, payload(id));
    if (mode.equals("after-head")) {
      Runtime.getRuntime().halt(73);
    }
    System.out.println(
        receipt.recordId() + ":" + receipt.verifiedHeadDigest() + ":" + receipt.casRetries());
  }

  private static byte[] writeRecord(Path root, String id) throws Exception {
    byte[] payload = payload(id);
    Path record = root.resolve("lab/records").resolve(id + ".bin");
    Files.createDirectories(record.getParent());
    Files.write(record, payload);
    return payload;
  }

  private static byte[] payload(String id) {
    return ("payload-" + id).getBytes(UTF_8);
  }

  private static String sha(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(bytes));
  }
}
