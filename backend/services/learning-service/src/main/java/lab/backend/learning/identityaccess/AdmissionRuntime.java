package lab.backend.learning.identityaccess;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import lab.backend.learning.transport.TransportSettings;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.env.ConfigurableEnvironment;

/** Own Learning admission cho lab R04c và named canonical R04d. Runtime chỉ
 * verify own DB/config, không migrate, provision hoặc bật restored DB. */
public final class AdmissionRuntime {
  private final AdmissionGate gate;
  private final TransportSettings transport;
  private final int identityPort;
  private final String origin;
  private AdmissionRuntime(AdmissionGate gate,TransportSettings transport,int port,String origin){this.gate=gate;this.transport=transport;identityPort=port;this.origin=origin;}
  public AdmissionGate gate(){return gate;}
  public LearningAccess access()throws Exception{return new LearningAccess(gate,transport,identityPort,origin);}
  public static AdmissionRuntime load(ConfigurableEnvironment env,TransportSettings transport){
    try{
      String input=env.getProperty("backendlab.admission.config");if(input==null||input.isBlank())throw new Exception();
      Path config=Path.of(input).toRealPath();if(!config.getParent().equals(transport.directory()))throw new Exception();
      Properties p=new Properties();try(var reader=Files.newBufferedReader(config,StandardCharsets.UTF_8)){p.load(reader);}
      if(!p.stringPropertyNames().equals(Set.of("schemaVersion","enabled","realm","generation","topologyVersion","dbRunId","identityPort","origin"))
          || !"1".equals(p.getProperty("schemaVersion"))||!"true".equals(p.getProperty("enabled"))
          || !transport.realm().equals(p.getProperty("realm"))||!transport.generation().equals(p.getProperty("generation"))
          || !AdmissionWire.TOPOLOGY.equals(p.getProperty("topologyVersion"))||!transport.dbRunId().equals(p.getProperty("dbRunId"))||transport.clockOffsetMs()!=0)throw new Exception();
      int port=Integer.parseInt(p.getProperty("identityPort"));if(port<1024||port>65535||port==8080||port==8443)throw new Exception();
      // Canonical profile không nhận arbitrary provider endpoint; certificate
      // Identity và exact response binding vẫn phải kiểm trên từng request.
      if(transport.canonicalLab()&&port!=transport.peerPrivatePort("identity"))throw new Exception();
      String origin=allowedOrigin(p.getProperty("origin"),transport.canonicalLab());
      var ds=new PGSimpleDataSource();ds.setServerNames(new String[]{"127.0.0.1"});ds.setPortNumbers(new int[]{transport.dbPort()});
      ds.setDatabaseName("learning_db");ds.setUser("learning_app");ds.setPassword(transport.dbPassword());ds.setConnectTimeout(1);ds.setSocketTimeout(7);ds.setSslMode("disable");
      var gate=new AdmissionGate(ds,UUID.fromString(transport.realm()),UUID.fromString(transport.generation()));gate.verifyRuntime();
      return new AdmissionRuntime(gate,transport,port,origin);
    }catch(Exception e){throw new IllegalStateException("Admission config/schema/readiness unavailable");}
  }
  /** Exact allowlist trước DB/network: browser relay17441 chỉ thuộc named
   * canonical topology; profile R04c cũ không tự nhận Origin/port mới. Mọi
   * request vẫn phải khớp chính Origin đã chọn, không wildcard/normalization. */
  static String allowedOrigin(String origin,boolean canonical){
    if("https://127.0.0.1".equals(origin)
        ||(canonical&&"https://localhost:17441".equals(origin)))return origin;
    throw new IllegalArgumentException("Named admission Origin required");
  }
  @Override public String toString(){return "AdmissionRuntime[lab,secrets=REDACTED]";}
}
