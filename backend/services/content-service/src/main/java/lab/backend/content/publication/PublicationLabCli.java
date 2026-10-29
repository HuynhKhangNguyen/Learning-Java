package lab.backend.content.publication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import lab.backend.content.transport.TransportSettings;
import org.springframework.core.env.StandardEnvironment;

/** Maintenance CLI fail-closed; peer/DB topology chỉ đến từ owned lab config đã kiểm. */
public final class PublicationLabCli {
  private static final Set<String> CANDIDATE_ACTIONS = Set.of("validate", "prepare");
  private static final Set<String> COORDINATOR_ACTIONS =
      Set.of("start", "dispatch", "status", "abort");

  public static void main(String[] args) {
    try {
      execute(args);
    } catch (Exception failure) {
      // Error cố định tránh đưa private expected, DB/config hay stack trace vào automation log.
      System.err.println("PUBLICATION_CANDIDATE_REJECTED");
      System.exit(1);
    }
  }

  private static void execute(String[] args) throws Exception {
    if (args.length == 3 && CANDIDATE_ACTIONS.contains(args[0])) {
      candidate(args);
      return;
    }
    if (args.length == 2 && COORDINATOR_ACTIONS.contains(args[0])) {
      coordinator(args[0], UUID.fromString(args[1]));
      return;
    }
    throw new IllegalArgumentException("CLI_ARGUMENTS_INVALID");
  }

  private static void candidate(String[] args) throws Exception {
    if (!CANDIDATE_ACTIONS.contains(args[0])) {
      throw new IllegalArgumentException("CLI_ARGUMENTS_INVALID");
    }
    Path candidate = Path.of(args[1]).toRealPath();
    Path bundle = Path.of(args[2]).toRealPath();
    if (!Files.isRegularFile(candidate) || !Files.isRegularFile(bundle)) {
      throw new IllegalArgumentException("CLI_INPUT_INVALID");
    }

    CandidateValidator.Valid valid = new CandidateValidator().validate(
        Files.readAllBytes(candidate), Files.readAllBytes(bundle));
    if ("prepare".equals(args[0])) {
      TransportSettings settings = TransportSettings.load(new StandardEnvironment());
      new PublicationRuntime(settings).store().prepare(valid);
    }

    // Chỉ ID/revision/digest bounded; tuyệt đối không in raw candidate/private bundle.
    System.out.println("VALID bundle=" + valid.bundleId()
        + " target=" + valid.target()
        + " digest=" + valid.bundleDigest());
  }

  private static void coordinator(String action, UUID operationId) throws Exception {
    TransportSettings settings = TransportSettings.load(new StandardEnvironment());
    PublicationRuntime runtime = new PublicationRuntime(settings);
    if ("start".equals(action)) {
      runtime.store().start(operationId);
      System.out.println("PUBLICATION_STARTED operation=" + operationId);
      return;
    }
    if ("status".equals(action)) {
      PublicationStore.Status status = runtime.store().status(operationId);
      if (status == null) {
        System.out.println("PUBLICATION_NOT_FOUND operation=" + operationId);
        return;
      }
      PublicationStore.Pending pending = runtime.store().pending(operationId);
      String phase = pending == null ? "NONE" : pending.phase();
      System.out.println(
          "PUBLICATION_STATUS operation=" + operationId
              + " state=" + status.state()
              + " phase=" + phase
              + " parent=" + status.parentRevision()
              + " target=" + status.targetRevision()
              + " abortRequested=" + status.abortRequested());
      return;
    }
    try (PublicationCoordinator coordinator = runtime.coordinator()) {
      if ("abort".equals(action)) {
        coordinator.abort(operationId);
        System.out.println("PUBLICATION_ABORT_PENDING operation=" + operationId);
      } else if ("dispatch".equals(action)) {
        boolean dispatched = coordinator.dispatchOnce(operationId);
        System.out.println(
            "PUBLICATION_DISPATCH operation=" + operationId + " sent=" + dispatched);
      } else {
        throw new IllegalArgumentException("CLI_ARGUMENTS_INVALID");
      }
    }
  }

  private PublicationLabCli() {}
}
