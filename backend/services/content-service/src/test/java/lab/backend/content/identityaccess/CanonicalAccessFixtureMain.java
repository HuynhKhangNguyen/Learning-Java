package lab.backend.content.identityaccess;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import lab.backend.content.transport.*;
import org.springframework.core.env.*;

/** Test-only process dùng ContentAccess thật với canonical Identity mTLS.
 * Chỉ đọc SQL probe sau admission/gate; không learner API hoặc content body.
 * Cookie/context nhận qua stdin, output chỉ trạng thái và opaque probe ID. */
public final class CanonicalAccessFixtureMain {
  public static void main(String[] args){
    try{
      if(args.length!=2)throw new IllegalArgumentException();
      var env=new StandardEnvironment();env.setActiveProfiles("canonical");
      env.getPropertySources().addFirst(new MapPropertySource("ownedFixture",Map.of(
          "server.port","18082","backendlab.trust.config",Path.of(args[0]).toRealPath().toString(),
          "backendlab.admission.config",Path.of(args[1]).toRealPath().toString())));
      var settings=TransportSettings.load(env);
      if(!settings.canonicalLab())throw new IllegalArgumentException();
      var runtime=AdmissionRuntime.load(env,settings);
      var input=WireCodec.strict(line(8192));var keys=new HashSet<String>();
      input.properties().forEach(p->keys.add(p.getKey()));
      if(!keys.equals(Set.of("schemaVersion","sessionCredential","expectedContextId","topicId","unitId","probeId"))
          ||!input.get("schemaVersion").isInt()||input.get("schemaVersion").asInt()!=1)throw new IllegalArgumentException();
      UUID probe=UUID.fromString(WireCodec.idempotencyKey(WireCodec.string(input,"probeId")));
      var request=new AdmissionWire.Request(WireCodec.string(input,"sessionCredential"),
          UUID.fromString(WireCodec.idempotencyKey(WireCodec.string(input,"expectedContextId"))),
          WireCodec.string(input,"topicId"),WireCodec.string(input,"unitId"));
      try(var access=runtime.access()){
        try{
          access.read(request,(connection,principal)->{
            // Callback ở trong production receiver transaction/gate. Không
            // trả principal/body qua stdout; result chỉ chứng minh SQL đã chạy.
            try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT 1")){
              if(!rows.next()||rows.getInt(1)!=1)throw new java.sql.SQLException("Fixture read unavailable");
            }
            return probe;
          });
          emit("READ_COMMITTED",probe,null,0);
        }catch(WireCodec.Denied denied){emit("DENIED",probe,denied.code,denied.status);}
      }
    }catch(Exception failure){System.err.println("CANONICAL_FIXTURE_UNAVAILABLE");System.exit(1);}
  }
  /** Request một dòng bounded; malformed input không được ghi vào diagnostic. */
  private static byte[] line(int limit)throws IOException{
    var bytes=new ByteArrayOutputStream();
    for(int value;(value=System.in.read())!=-1;){if(value=='\n')return bytes.toByteArray();if(bytes.size()==limit)throw new IOException("Fixture input too large");bytes.write(value);}
    throw new EOFException("Fixture line missing");
  }
  private static void emit(String state,UUID probe,String code,int status){
    var result=new LinkedHashMap<String,Object>();result.put("schemaVersion",1);result.put("state",state);
    result.put("probeId",probe.toString());result.put("code",code);result.put("status",status);
    System.out.println(WireCodec.JSON.writeValueAsString(result));System.out.flush();
  }
  private CanonicalAccessFixtureMain(){}
}
