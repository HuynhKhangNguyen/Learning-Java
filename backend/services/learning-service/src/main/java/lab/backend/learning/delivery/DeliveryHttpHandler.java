package lab.backend.learning.delivery;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import lab.backend.learning.transport.WireCodec;

/** Called only after private listener/certificate/browser-header/route boundary checks. */
public final class DeliveryHttpHandler {
  private final DeliveryStore store;
  private final DeliveryStore.Consumer consumer;
  public DeliveryHttpHandler(DeliveryStore store,DeliveryStore.Consumer consumer){this.store=store;this.consumer=consumer;}
  public boolean handle(HttpServletRequest request,HttpServletResponse response,String caller)throws IOException{
    String path=request.getRequestURI();if(!path.startsWith(DeliveryWire.PATH))return false;
    if(!request.getMethod().equals("PUT"))throw new WireCodec.Denied(405,"METHOD_DENIED");
    if(!DeliveryWire.SERVICES.contains(caller)||caller.equals(DeliveryWire.SERVICE))throw new WireCodec.Denied(403,"DELIVERY_PEER_DENIED");
    String content=request.getContentType();if(content==null||!content.matches("(?i)application/json(?:; ?charset=utf-8)?"))throw new WireCodec.Denied(400,"BAD_CONTENT_TYPE");
    UUID message=UUID.fromString(WireCodec.idempotencyKey(path.substring(DeliveryWire.PATH.length())));
    byte[] bytes=request.getInputStream().readNBytes(DeliveryWire.MAX_ENVELOPE+1);
    if(!DeliveryWire.envelope(bytes).message().equals(message))throw new WireCodec.Denied(409,"MESSAGE_BINDING_DENIED");
    var result=store.receive(bytes,caller,consumer);byte[] ack=result.acknowledged();
    response.setStatus(200);response.getOutputStream().write(ack);return true;
  }
}



