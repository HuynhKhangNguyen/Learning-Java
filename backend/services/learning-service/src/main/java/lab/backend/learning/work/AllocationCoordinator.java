package lab.backend.learning.work;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import lab.backend.learning.exposure.ExposureJournalPump;
import lab.backend.learning.identityaccess.AdmissionWire;
import lab.backend.learning.identityaccess.LearningAccess;

/** Hai local transactions và journal ngoài locks; final transaction luôn có admission mới. */
public final class AllocationCoordinator {
  private final LearningAccess access;
  private final AllocationStore store;
  private final ExposureJournalPump pump;

  public AllocationCoordinator(LearningAccess access, AllocationStore store, ExposureJournalPump pump) {
    this.access = access;
    this.store = store;
    this.pump = pump;
  }

  public Map<String, Object> create(Supplier<AdmissionWire.Request> admission, UUID key,
      AllocationWire.Create request) {
    UUID attempt = access.execute(admission.get(),
        (connection, owner) -> store.create(connection, owner, key, request));
    boolean verified = pump.verify(attempt);
    return access.execute(admission.get(),
        (connection, owner) -> store.disclose(connection, owner, attempt, verified));
  }

  public Map<String, Object> read(Supplier<AdmissionWire.Request> admission, UUID attempt) {
    access.execute(admission.get(), (connection, owner) -> store.locate(connection, owner, attempt));
    boolean verified = pump.verify(attempt);
    return access.execute(admission.get(),
        (connection, owner) -> store.disclose(connection, owner, attempt, verified));
  }
}
