package lab.backend.learning.identityaccess;

import java.util.UUID;
import lab.backend.learning.transport.TransportPeerClient;
import lab.backend.learning.transport.TransportSettings;
import lab.backend.learning.transport.WireCodec;

/** Endpoint integration port: fresh direct admission, then local SQL under the gate. */
public final class LearningAccess implements AutoCloseable {
  private final AdmissionGate gate;
  private final TransportSettings transport;
  private final TransportPeerClient peer;
  private final int identityPort;
  private final String expectedOrigin;
  public LearningAccess(AdmissionGate gate,TransportSettings transport,int identityPort,String expectedOrigin)throws Exception{
    this.gate=gate;this.transport=transport;this.identityPort=identityPort;this.expectedOrigin=expectedOrigin;peer=new TransportPeerClient(transport,"identity");
  }
  public <T>T mutate(AdmissionWire.Request request,AdmissionGate.Work<T> work){
    if(!expectedOrigin.equals(request.origin))throw AdmissionWire.deny(403,"ORIGIN_DENIED");
    AdmissionWire.Admission admission;
    try{
      var sentAt=transport.dbTime();
      byte[] response=peer.admission(request.bytes(),identityPort);
      admission=AdmissionWire.admission(response,request,UUID.fromString(transport.realm()),sentAt,transport.dbTime());
    }catch(WireCodec.Denied d){throw d;}catch(Exception unavailable){throw AdmissionWire.deny(503,"IDENTITY_UNAVAILABLE");}
    return gate.use(admission,request,work);
  }
  public void close(){peer.close();}
}
