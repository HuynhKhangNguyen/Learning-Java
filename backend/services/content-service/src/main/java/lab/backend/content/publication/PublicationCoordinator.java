package lab.backend.content.publication;

import java.sql.SQLException;
import java.util.UUID;

/** Điều phối một bước bền; caller có thể gọi lại sau lost ACK hoặc process restart. */
public final class PublicationCoordinator implements AutoCloseable {
  private final PublicationStore store;
  private final PublicationPeerClient peer;

  public PublicationCoordinator(PublicationStore store, PublicationPeerClient peer) {
    this.store = store;
    this.peer = peer;
  }

  public void start(UUID operationId) throws SQLException {
    store.start(operationId);
  }

  public boolean dispatchOnce(UUID operationId) throws Exception {
    PublicationStore.Pending visible = store.pending(operationId);
    if (visible == null) {
      return false;
    }
    PublicationStore.LeasedPending leased = store.lease(operationId);
    PublicationStore.Pending pending = leased.pending();
    // Không giữ connection/row lock khi gọi peer; exact bytes đã bền trong outbox.
    byte[] receipt;
    try {
      receipt = peer.send(
          new PublicationPeerClient.UUIDAndPhase(operationId, pending.phase()),
          pending.commandBytes());
    } catch (Exception uncertainOutcome) {
      // Lost ACK không được suy thành success. Status chỉ được nhận như một
      // durable receipt nếu strict binding phía store khớp exact pending command.
      receipt = peer.status(operationId);
    }
    store.accept(leased, receipt);
    return true;
  }

  public void abort(UUID operationId) throws SQLException {
    store.requestAbort(operationId);
  }

  @Override
  public void close() {
    peer.close();
  }
}
