package lab.backend.learning.delivery;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;
import lab.backend.learning.transport.WireCodec;

/** Servlet checks only; certificate/listener/durability require packaged TLS/PG IT. */
class DeliveryHttpHandlerTests {
  static final String PEER="content";
  static final UUID REALM=UUID.randomUUID(),GEN=UUID.randomUUID(),PEER_GEN=UUID.randomUUID();
  static byte[] bytes(UUID message){
    // Own producer fixture is rewritten to an incoming named peer; store validation is separate.
    var e=DeliveryWire.create(message,UUID.randomUUID(),UUID.randomUUID(),PEER,REALM,GEN,PEER_GEN,"fixture",1,"LAB_APPLY","APPLY","{\"delta\":1}".getBytes(),java.time.Instant.now());
    var map=new java.util.LinkedHashMap<String,Object>();WireCodec.strict(e.bytes()).properties().forEach(p->map.put(p.getKey(),p.getValue()));map.put("producer",PEER);map.put("consumer",DeliveryWire.SERVICE);return WireCodec.JSON.writeValueAsBytes(map);
  }
  static MockHttpServletRequest request(UUID id,byte[] body){var r=new MockHttpServletRequest("PUT",DeliveryWire.PATH+id);r.setContentType("application/json");r.setContent(body);return r;}
  @Test void noUnknownRouteOrMethodCanCallStore()throws Exception{
    var store=mock(DeliveryStore.class);var consumer=mock(DeliveryStore.Consumer.class);var h=new DeliveryHttpHandler(store,consumer);
    var other=new MockHttpServletRequest("PUT","/api/v1/anything");assertThat(h.handle(other,new MockHttpServletResponse(),PEER)).isFalse();verifyNoInteractions(store);
    UUID id=UUID.randomUUID();var post=request(id,bytes(id));post.setMethod("POST");denied(()->h.handle(post,new MockHttpServletResponse(),PEER),"METHOD_DENIED");verifyNoInteractions(store);
  }
  @ParameterizedTest @ValueSource(strings={"operator","edge","learning"})
  void namedUnauthorizedPeersCannotApply(String caller)throws Exception{
    var store=mock(DeliveryStore.class);var h=new DeliveryHttpHandler(store,mock(DeliveryStore.Consumer.class));UUID id=UUID.randomUUID();
    denied(()->h.handle(request(id,bytes(id)),new MockHttpServletResponse(),caller),"DELIVERY_PEER_DENIED");verifyNoInteractions(store);
  }
  @Test void exactCommittedAckIsForwardedWithoutReserialization()throws Exception{
    var store=mock(DeliveryStore.class);var consumer=mock(DeliveryStore.Consumer.class);var h=new DeliveryHttpHandler(store,consumer);UUID id=UUID.randomUUID();byte[] body=bytes(id);var e=DeliveryWire.envelope(body);var ack=DeliveryWire.receipt(e,"APPLIED","a".repeat(64));
    when(store.receive(any(byte[].class),eq(PEER),same(consumer))).thenReturn(new DeliveryStore.Received(ack,null));
    var response=new MockHttpServletResponse();assertThat(h.handle(request(id,body),response,PEER)).isTrue();assertThat(response.getStatus()).isEqualTo(200);assertThat(response.getContentAsByteArray()).isEqualTo(ack.bytes());
    verify(store).receive(org.mockito.ArgumentMatchers.argThat(value->java.util.Arrays.equals(value,body)),eq(PEER),same(consumer));
  }
  @Test void routeIdTypeOversizeAndMalformedBodyFailBeforeStore()throws Exception{
    var store=mock(DeliveryStore.class);var h=new DeliveryHttpHandler(store,mock(DeliveryStore.Consumer.class));UUID id=UUID.randomUUID();
    denied(()->h.handle(request(UUID.randomUUID(),bytes(id)),new MockHttpServletResponse(),PEER),"MESSAGE_BINDING_DENIED");
    var wrong=request(id,bytes(id));wrong.setContentType("text/plain");denied(()->h.handle(wrong,new MockHttpServletResponse(),PEER),"BAD_CONTENT_TYPE");
    denied(()->h.handle(request(id,new byte[8193]),new MockHttpServletResponse(),PEER),"ENVELOPE_TOO_LARGE");verifyNoInteractions(store);
  }
  @Test void poisonOrGapNeverEmitSuccessAck()throws Exception{
    var store=mock(DeliveryStore.class);var consumer=mock(DeliveryStore.Consumer.class);var h=new DeliveryHttpHandler(store,consumer);UUID id=UUID.randomUUID();
    when(store.receive(any(byte[].class),eq(PEER),same(consumer))).thenReturn(new DeliveryStore.Received(null,"POISON_QUARANTINED"));
    denied(()->h.handle(request(id,bytes(id)),new MockHttpServletResponse(),PEER),"POISON_QUARANTINED");
  }
  @FunctionalInterface interface Checked{void run()throws Exception;}
  static void denied(Checked call,String code){assertThatThrownBy(call::run).isInstanceOf(WireCodec.Denied.class).extracting("code").isEqualTo(code);}
}

