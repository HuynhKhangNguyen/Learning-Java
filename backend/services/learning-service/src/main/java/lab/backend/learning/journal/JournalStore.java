package lab.backend.learning.journal;

import java.io.IOException;
import java.util.List;

/**
 * Cổng journal cục bộ của Learning. Payload là canonical bytes do business owner tạo;
 * storage chỉ đảm bảo tính bất biến, thứ tự và bằng chứng đọc lại.
 */
public interface JournalStore {
  Receipt append(String namespace, String recordId, byte[] canonicalBytes) throws JournalException;

  Inventory inventory(String namespace, String fixedHeadDigest) throws JournalException;

  record Receipt(
      String namespace,
      String recordId,
      String recordDigest,
      long sequence,
      String nodeDigest,
      String verifiedHeadDigest,
      int casRetries) {}

  record Entry(
      long sequence,
      String recordId,
      String recordDigest,
      String nodeDigest) {}

  record Inventory(
      String namespace,
      String fixedHeadDigest,
      String inventoryDigest,
      List<Entry> entries,
      List<String> orphanRecordIds,
      List<String> orphanNodeDigests) {}

  enum ErrorCode {
    INVALID_INPUT,
    CONFLICT,
    CORRUPT,
    IO_FAILURE
  }

  final class JournalException extends IOException {
    private final ErrorCode code;

    public JournalException(ErrorCode code, String message) {
      super(message);
      this.code = code;
    }

    public JournalException(ErrorCode code, String message, Throwable cause) {
      super(message, cause);
      this.code = code;
    }

    public ErrorCode code() {
      return code;
    }
  }
}
