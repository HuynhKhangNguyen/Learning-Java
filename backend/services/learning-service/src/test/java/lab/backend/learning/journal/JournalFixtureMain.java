package lab.backend.learning.journal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.file.Files;
import java.nio.file.Path;

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

    var store = new FilesystemJournalStore(root);
    try {
      var receipt = store.append("lab", id, payload(id));
      if (mode.equals("after-publish")) {
        throw new IllegalStateException("fault did not interrupt read-back after publication");
      }
      System.out.println(
          receipt.recordId() + ":" + receipt.verifiedHeadDigest() + ":" + receipt.casRetries());
    } catch (JournalStore.JournalException error) {
      if (mode.equals("after-publish") && publishedHeadCount(root) >= 2) {
        // External test lock làm read-back thất bại sau atomic publish; process chết
        // trước khi có receipt/response để restart phải tự verify rồi hội tụ.
        Runtime.getRuntime().halt(73);
      }
      throw error;
    }
  }

  private static byte[] payload(String id) {
    return ("payload-" + id).getBytes(UTF_8);
  }

  private static long publishedHeadCount(Path root) throws Exception {
    try (var paths = Files.list(root.resolve("lab/heads"))) {
      return paths.filter(path -> path.getFileName().toString().endsWith(".head")).count();
    }
  }
}
