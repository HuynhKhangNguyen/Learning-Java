package lab.backend.content.identityaccess;

import java.util.UUID;
import lab.backend.content.transport.TransportPeerClient;
import lab.backend.content.transport.TransportSettings;
import lab.backend.content.transport.WireCodec;

/** Endpoint integration port: fresh direct admission, then local SQL under the gate. */
public final class ContentAccess implements AutoCloseable {
  private final AdmissionGate gate;
  private final TransportSettings transport;
  private final TransportPeerClient peer;
  private final int identityPort;
  public ContentAccess(AdmissionGate gate,TransportSettings transport,int identityPort)throws Exception{
    this.gate=gate;this.transport=transport;this.identityPort=identityPort;peer=new TransportPeerClient(transport,"identity");
  }
  public <T>T read(AdmissionWire.Request request,AdmissionGate.Work<T> work){
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
