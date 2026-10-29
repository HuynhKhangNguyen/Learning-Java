package lab.backend.content.delivery;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import lab.backend.content.transport.WireCodec;

/** A bounded single iteration; scheduling belongs to the owning runtime/coordinator. */
public final class DeliveryDispatcher {
  public enum Result {EMPTY,DELIVERED,RETRY_SCHEDULED,PAUSED,STALE_LEASE}
  @FunctionalInterface public interface Sender {byte[] send(DeliveryStore.Lease lease)throws Exception;}
  private final DeliveryStore store;
  public DeliveryDispatcher(DeliveryStore store){this.store=Objects.requireNonNull(store);}
  public Result once(Sender sender){
    var selected=store.claim();if(selected.isEmpty())return Result.EMPTY;var lease=selected.get();
    try{
      // claim has committed and released all DB locks before invoking any network code.
      byte[] ack=sender.send(lease);
      return store.delivered(lease,ack)?Result.DELIVERED:Result.STALE_LEASE;
    }catch(Exception failed){
      boolean permanent=failed instanceof WireCodec.Denied d&&d.status>=400&&d.status<500&&d.status!=408&&d.status!=429;
      String reason=failed instanceof WireCodec.Denied?"PEER_OR_ACK_DENIED":"TRANSPORT_UNAVAILABLE";
      if(failed instanceof InterruptedException)Thread.currentThread().interrupt();
      boolean current=store.failed(lease,reason,permanent,ThreadLocalRandom.current().nextInt(251));
      return !current?Result.STALE_LEASE:permanent||lease.attempt()==8?Result.PAUSED:Result.RETRY_SCHEDULED;
    }
  }
}

