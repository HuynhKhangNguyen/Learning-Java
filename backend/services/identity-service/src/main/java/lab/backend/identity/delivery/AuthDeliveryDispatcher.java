package lab.backend.identity.delivery;

import java.util.concurrent.ThreadLocalRandom;
import lab.backend.identity.transport.WireCodec;

/** Auth-only bounded iteration; never reads a strict event cursor or clears pending authority. */
public final class AuthDeliveryDispatcher {
  @FunctionalInterface public interface Sender {byte[] send(AuthDeliveryStore.Lease lease)throws Exception;}
  private final AuthDeliveryStore store;
  public AuthDeliveryDispatcher(AuthDeliveryStore store){this.store=store;}
  public DeliveryDispatcher.Result once(Sender sender){
    var selected=store.claim();if(selected.isEmpty())return DeliveryDispatcher.Result.EMPTY;var lease=selected.get();
    try{return store.delivered(lease,sender.send(lease))?DeliveryDispatcher.Result.DELIVERED:DeliveryDispatcher.Result.STALE_LEASE;}
    catch(Exception failed){
      boolean permanent=failed instanceof WireCodec.Denied d&&d.status>=400&&d.status<500&&d.status!=408&&d.status!=429;
      if(failed instanceof InterruptedException)Thread.currentThread().interrupt();
      boolean current=store.failed(lease,failed instanceof WireCodec.Denied?"PEER_OR_ACK_DENIED":"TRANSPORT_UNAVAILABLE",permanent,ThreadLocalRandom.current().nextInt(251));
      return !current?DeliveryDispatcher.Result.STALE_LEASE:permanent||lease.attempt()==8?DeliveryDispatcher.Result.PAUSED:DeliveryDispatcher.Result.RETRY_SCHEDULED;
    }
  }
}
