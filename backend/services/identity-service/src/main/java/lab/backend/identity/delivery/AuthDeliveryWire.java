package lab.backend.identity.delivery;

import static lab.backend.identity.delivery.DeliveryWire.*;
import java.util.*;
import lab.backend.identity.transport.WireCodec;
import tools.jackson.databind.JsonNode;

/** Own Identity INT02 delivery binding; no canonical revoke/completion authority. */
public final class AuthDeliveryWire {
  public static final String TOPOLOGY="r1-static-v1";
  static final Set<String> COMMAND=Set.of("schemaVersion","operationId","scope","action","audience","userId","sessionRef","sessionContextId","epochFloor","authRealmId","topologyVersion","participantGeneration","commandDigest");
  static final Set<String> ACK=Set.of("schemaVersion","receiver","operationId","commandDigest","authRealmId","participantGeneration","topologyVersion","scope","appliedFloor","sessionRef","sessionContextId","durable");
  public record Command(UUID operation,String consumer,UUID user,UUID session,UUID context,String scope,String action,long floor,UUID realm,UUID generation,String digest,String envelopeDigest,byte[] bytes){
    public Command{bytes=bytes.clone();} @Override public byte[] bytes(){return bytes.clone();}
    @Override public String toString(){return "AuthDeliveryCommand[operation="+operation+",payload=REDACTED]";}
  }
  public static Command command(byte[] bytes){
    JsonNode n=object(bytes,COMMAND);if(version(n)!=1||!TOPOLOGY.equals(str(n,"topologyVersion")))throw deny(400,"UNSUPPORTED_AUTH_DELIVERY");
    String scope=str(n,"scope"),action=str(n,"action"),consumer=str(n,"audience");
    if(!Set.of("content","learning").contains(consumer))throw deny(403,"AUTH_DELIVERY_PEER_DENIED");
    UUID session=n.get("sessionRef").isNull()?null:id(n,"sessionRef"),context=n.get("sessionContextId").isNull()?null:id(n,"sessionContextId");
    if(scope.equals("SESSION")){if(session==null||context==null||!Set.of("LOGOUT","EXPIRY").contains(action))throw deny(400,"BAD_AUTH_DELIVERY");}
    else if(scope.equals("ACCOUNT")){if(session!=null||context!=null||!Set.of("RESET","LOGOUT_ALL","PASSWORD_CHANGE","DISABLE","ENABLE","RECOVERY").contains(action))throw deny(400,"BAD_AUTH_DELIVERY");}
    else throw deny(400,"BAD_AUTH_DELIVERY");
    UUID user=id(n,"userId"),realm=id(n,"authRealmId"),generation=id(n,"participantGeneration");long floor=positive(n,"epochFloor");String digest=hex(n,"commandDigest");
    if(!sha(action+"\n"+user+"\n"+(session==null?"ACCOUNT":session)+"\n"+floor+"\n"+realm+"\n"+TOPOLOGY+"\ncontent,learning").equals(digest))throw deny(403,"COMMAND_DIGEST_DENIED");
    return new Command(id(n,"operationId"),consumer,user,session,context,scope,action,floor,realm,generation,digest,sha(bytes),bytes);
  }
  public static void ack(byte[] bytes,Command e){
    JsonNode n=object(bytes,ACK);
    UUID session=n.get("sessionRef").isNull()?null:id(n,"sessionRef"),context=n.get("sessionContextId").isNull()?null:id(n,"sessionContextId");
    if(version(n)!=1||!n.get("durable").isBoolean()||!n.get("durable").asBoolean()||!str(n,"receiver").equals(e.consumer())||!id(n,"operationId").equals(e.operation())
       ||!hex(n,"commandDigest").equals(e.digest())||!id(n,"authRealmId").equals(e.realm())||!id(n,"participantGeneration").equals(e.generation())||!str(n,"topologyVersion").equals(TOPOLOGY)
       ||!str(n,"scope").equals(e.scope())||positive(n,"appliedFloor")<e.floor()||!Objects.equals(session,e.session())||!Objects.equals(context,e.context()))throw deny(409,"AUTH_ACK_BINDING_DENIED");
  }
  private AuthDeliveryWire(){}
}
