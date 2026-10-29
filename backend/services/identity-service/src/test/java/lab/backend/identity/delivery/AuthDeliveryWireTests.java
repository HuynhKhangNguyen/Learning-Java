package lab.backend.identity.delivery;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import lab.backend.identity.transport.WireCodec;

class AuthDeliveryWireTests {
  static byte[] command(UUID operation,String consumer,UUID user,UUID session,UUID context,long floor,UUID realm,UUID generation){
    var n=new LinkedHashMap<String,Object>();String action=session==null?"RESET":"LOGOUT";
    n.put("schemaVersion",1);n.put("operationId",operation.toString());n.put("scope",session==null?"ACCOUNT":"SESSION");n.put("action",action);n.put("audience",consumer);n.put("userId",user.toString());n.put("sessionRef",session==null?null:session.toString());n.put("sessionContextId",context==null?null:context.toString());n.put("epochFloor",Long.toString(floor));n.put("authRealmId",realm.toString());n.put("topologyVersion","r1-static-v1");n.put("participantGeneration",generation.toString());
    n.put("commandDigest",DeliveryWire.sha(action+"\n"+user+"\n"+(session==null?"ACCOUNT":session)+"\n"+floor+"\n"+realm+"\nr1-static-v1\ncontent,learning"));
    return WireCodec.JSON.writeValueAsBytes(n);
  }
  static byte[] ack(AuthDeliveryWire.Command c,long floor){
    var n=new LinkedHashMap<String,Object>();n.put("schemaVersion",1);n.put("receiver",c.consumer());n.put("operationId",c.operation().toString());n.put("commandDigest",c.digest());n.put("authRealmId",c.realm().toString());n.put("participantGeneration",c.generation().toString());n.put("topologyVersion","r1-static-v1");n.put("scope",c.scope());n.put("appliedFloor",Long.toString(floor));n.put("sessionRef",c.session()==null?null:c.session().toString());n.put("sessionContextId",c.context()==null?null:c.context().toString());n.put("durable",true);return WireCodec.JSON.writeValueAsBytes(n);
  }
  static byte[] changed(byte[] bytes,String key,Object value){var n=new LinkedHashMap<String,Object>();WireCodec.strict(bytes).properties().forEach(p->n.put(p.getKey(),p.getValue()));n.put(key,value);return WireCodec.JSON.writeValueAsBytes(n);}
  static AuthDeliveryWire.Command fixture(){return AuthDeliveryWire.command(command(UUID.randomUUID(),"content",UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),9007199254740993L,UUID.randomUUID(),UUID.randomUUID()));}
  @Test void exactBytesAndMonotonicFloorAreBoundWithoutReserialization(){
    var c=fixture();byte[] original=c.bytes();byte[] copy=c.bytes();copy[0]=0;assertThat(c.bytes()).isEqualTo(original);
    assertThatCode(()->AuthDeliveryWire.ack(ack(c,c.floor()),c)).doesNotThrowAnyException();
    assertThatCode(()->AuthDeliveryWire.ack(ack(c,c.floor()+1),c)).doesNotThrowAnyException();
    assertThat(c.toString()).doesNotContain(c.user().toString()).contains("REDACTED");
  }
  @ParameterizedTest @ValueSource(strings={"schemaVersion","receiver","operationId","commandDigest","authRealmId","participantGeneration","topologyVersion","scope","appliedFloor","sessionRef","sessionContextId","durable","unknown"})
  void wrongAckCannotBeDeliverySuccess(String field){
    var c=fixture();Object value=switch(field){case "schemaVersion"->2;case "receiver"->"learning";case "commandDigest"->"0".repeat(64);case "topologyVersion"->"r1-static-v2";case "scope"->"ACCOUNT";case "appliedFloor"->Long.toString(c.floor()-1);case "durable"->false;default->UUID.randomUUID().toString();};
    assertThatThrownBy(()->AuthDeliveryWire.ack(changed(ack(c,c.floor()),field,value),c)).isInstanceOf(WireCodec.Denied.class);
  }
  @ParameterizedTest @ValueSource(strings={"schemaVersion","audience","commandDigest","epochFloor","topologyVersion","scope","action","sessionRef","unknown"})
  void malformedCommandCannotBeStoredOrDispatched(String field){
    var c=fixture();Object value=switch(field){case "schemaVersion"->2;case "audience"->"identity";case "commandDigest"->"f".repeat(64);case "epochFloor"->"0";case "topologyVersion"->"other";case "scope"->"ACCOUNT";case "action"->"RESET";case "sessionRef"->null;default->"unknown";};
    assertThatThrownBy(()->AuthDeliveryWire.command(changed(c.bytes(),field,value))).isInstanceOf(WireCodec.Denied.class);
  }
  @Test void accountCommandHasNullSessionBindings(){
    var c=AuthDeliveryWire.command(command(UUID.randomUUID(),"learning",UUID.randomUUID(),null,null,1,UUID.randomUUID(),UUID.randomUUID()));
    assertThat(c.session()).isNull();assertThatCode(()->AuthDeliveryWire.ack(ack(c,1),c)).doesNotThrowAnyException();
    assertThatThrownBy(()->AuthDeliveryWire.ack(changed(ack(c,1),"sessionRef",UUID.randomUUID().toString()),c)).isInstanceOf(WireCodec.Denied.class);
  }
}
