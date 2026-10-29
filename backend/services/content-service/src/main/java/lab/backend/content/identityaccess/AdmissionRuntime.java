package lab.backend.content.identityaccess;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import lab.backend.content.transport.TransportSettings;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.env.ConfigurableEnvironment;

/** R04b owned-lab profile only. Runtime never migrates or enables a restored DB. */
public final class AdmissionRuntime {
  private final AdmissionGate gate;
  private final TransportSettings transport;
  private final int identityPort;
  private AdmissionRuntime(AdmissionGate gate,TransportSettings transport,int port){this.gate=gate;this.transport=transport;identityPort=port;}
  public AdmissionGate gate(){return gate;}
  public ContentAccess access()throws Exception{return new ContentAccess(gate,transport,identityPort);}
  public static AdmissionRuntime load(ConfigurableEnvironment env,TransportSettings transport){
    try{
      String input=env.getProperty("backendlab.admission.config");if(input==null||input.isBlank())throw new Exception();
      Path config=Path.of(input).toRealPath();if(!config.getParent().equals(transport.directory()))throw new Exception();
      Properties p=new Properties();try(var reader=Files.newBufferedReader(config,StandardCharsets.UTF_8)){p.load(reader);}
      if(!p.stringPropertyNames().equals(Set.of("schemaVersion","enabled","realm","generation","topologyVersion","dbRunId","identityPort"))
          || !"1".equals(p.getProperty("schemaVersion"))||!"true".equals(p.getProperty("enabled"))
          || !transport.realm().equals(p.getProperty("realm"))||!transport.generation().equals(p.getProperty("generation"))
          || !AdmissionWire.TOPOLOGY.equals(p.getProperty("topologyVersion"))||!transport.dbRunId().equals(p.getProperty("dbRunId"))||transport.clockOffsetMs()!=0)throw new Exception();
      int port=Integer.parseInt(p.getProperty("identityPort"));if(port<1024||port>65535||port==8080||port==8443)throw new Exception();
      // Canonical profile chỉ gọi Identity trong named owned topology; giữ
      // controlled-provider fixture riêng cho profile admission lịch sử.
      if(transport.canonicalLab()&&port!=transport.peerPrivatePort("identity"))throw new Exception();
      var ds=new PGSimpleDataSource();ds.setServerNames(new String[]{"127.0.0.1"});ds.setPortNumbers(new int[]{transport.dbPort()});
      ds.setDatabaseName("content_db");ds.setUser("content_app");ds.setPassword(transport.dbPassword());ds.setConnectTimeout(1);ds.setSocketTimeout(7);ds.setSslMode("disable");
      var gate=new AdmissionGate(ds,UUID.fromString(transport.realm()),UUID.fromString(transport.generation()));gate.verifyRuntime();
      return new AdmissionRuntime(gate,transport,port);
    }catch(Exception e){throw new IllegalStateException("Admission config/schema/readiness unavailable");}
  }
  @Override public String toString(){return "AdmissionRuntime[lab,secrets=REDACTED]";}
}
