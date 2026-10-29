package lab.backend.learning.identityaccess;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import lab.backend.learning.transport.*;
import org.springframework.core.env.*;

/** Process probe chỉ thuộc test: fresh INT01 tới packaged Identity rồi local
 * SQL qua LearningAccess thật. Không có learner endpoint, reset hoặc T1/T2.
 * Config truyền đường dẫn; cookie/CSRF/body chỉ qua stdin và không ra stdout. */
public final class CanonicalAccessFixtureMain {
  public static void main(String[] args){
    try{
      if(args.length!=2)throw new IllegalArgumentException();
      var env=new StandardEnvironment();env.setActiveProfiles("canonical");
      env.getPropertySources().addFirst(new MapPropertySource("ownedFixture",Map.of(
          "server.port","18083","backendlab.trust.config",Path.of(args[0]).toRealPath().toString(),
          "backendlab.admission.config",Path.of(args[1]).toRealPath().toString())));
      var settings=TransportSettings.load(env);
      if(!settings.canonicalLab())throw new IllegalArgumentException();
      var runtime=AdmissionRuntime.load(env,settings);
      // Một dòng bounded giữ protocol stdin rõ ràng: dòng JSON là request,
      // dòng RELEASE chỉ điều khiển barrier test, không cấp quyền admission.
      var input=WireCodec.strict(line(65536));var keys=new HashSet<String>();
      input.properties().forEach(p->keys.add(p.getKey()));
      if(!keys.equals(Set.of("schemaVersion","mode","sessionCredential","expectedContextId","draftId","csrfToken","origin","bodyBase64","probeId"))
          ||!input.get("schemaVersion").isInt()||input.get("schemaVersion").asInt()!=1)throw new IllegalArgumentException();
      String mode=WireCodec.string(input,"mode");
      if(!Set.of("MUTATE","HOLD_COMMIT","HOLD_ROLLBACK").contains(mode))throw new IllegalArgumentException();
      UUID probe=id(WireCodec.string(input,"probeId"));
      var request=new AdmissionWire.Request(WireCodec.string(input,"sessionCredential"),
          id(WireCodec.string(input,"expectedContextId")),id(WireCodec.string(input,"draftId")),
          WireCodec.string(input,"csrfToken"),WireCodec.string(input,"origin"),
          Base64.getDecoder().decode(WireCodec.string(input,"bodyBase64")));
      try(var access=runtime.access()){
        // RELEASE được đọc trên daemon thread để callback có wait bounded 4s,
        // nằm trong transaction budget 6s của gate; mọi query/clock vẫn có hạn.
        // Driver mất/crash -> rollback local SQL; không treo JVM vì stdin chưa đóng.
        var release=new CompletableFuture<String>();
        if(!mode.equals("MUTATE"))Thread.ofPlatform().daemon().name("owned-probe-release").start(()->{
          try{release.complete(new String(line(16),StandardCharsets.US_ASCII));}
          catch(Exception failure){release.completeExceptionally(failure);}
        });
        try{
          access.mutate(request,(connection,principal)->{
            // transaction_probe là migration test-only. Gate/account lock và
            // admission-use của production giữ cùng transaction tới commit.
            try(var statement=connection.prepareStatement("INSERT INTO learning.transaction_probe(id,revision,payload) VALUES (?,0,'canonical-runtime')")){
              statement.setString(1,probe.toString());statement.executeUpdate();
            }
            if(!mode.equals("MUTATE")){
              // PID của chính connection đang giữ gate giúp driver đối chiếu
              // pg_blocking_pids, không nhầm lock của một transaction khác.
              int backendPid;
              try(var statement=connection.prepareStatement("SELECT pg_backend_pid()");var rows=statement.executeQuery()){
                if(!rows.next())throw new SQLException("Fixture backend missing");backendPid=rows.getInt(1);
              }
              emit("HELD",probe,null,0,backendPid);
              try{if(!"RELEASE".equals(release.get(4,TimeUnit.SECONDS)))throw new SQLException("Fixture release invalid");}
              catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new SQLException("Fixture interrupted");}
              catch(ExecutionException|TimeoutException failure){
                // Chỉ log nhãn phase cố định, không exception message/request
                // bí mật. Driver cần phân biệt hết hold budget với requested rollback.
                System.err.println(failure instanceof TimeoutException?"CANONICAL_BARRIER_RELEASE_TIMEOUT":"CANONICAL_BARRIER_RELEASE_INPUT_FAILED");
                throw new SQLException("Fixture release unavailable");
              }
              // Timeout cũng rollback nhưng không được tính là HOLD_ROLLBACK
              // thành công: driver phải nhận RELEASED trước terminal event.
              emit("RELEASED",probe,null,0);
              if(mode.equals("HOLD_ROLLBACK"))throw new SQLException("Fixture requested rollback");
            }
            return probe;
          });
          // Access chỉ trả về sau commit; HELD phía trên chưa có nghĩa durable.
          emit("COMMITTED",probe,null,0);
        }catch(WireCodec.Denied denied){emit("DENIED",probe,denied.code,denied.status);}
      }
    }catch(Exception failure){System.err.println("CANONICAL_FIXTURE_UNAVAILABLE");System.exit(1);}
  }
  private static UUID id(String value){return UUID.fromString(WireCodec.idempotencyKey(value));}
  /** Đọc tới newline, giới hạn trước parse; không in bytes bí mật khi malformed. */
  private static byte[] line(int limit)throws IOException{
    var bytes=new ByteArrayOutputStream();
    for(int value;(value=System.in.read())!=-1;){if(value=='\n')return bytes.toByteArray();if(bytes.size()==limit)throw new IOException("Fixture input too large");bytes.write(value);}
    throw new EOFException("Fixture line missing");
  }
  private static void emit(String state,UUID probe,String code,int status){
    emit(state,probe,code,status,null);
  }
  private static void emit(String state,UUID probe,String code,int status,Integer backendPid){
    var result=new LinkedHashMap<String,Object>();result.put("schemaVersion",1);result.put("state",state);
    result.put("probeId",probe.toString());result.put("code",code);result.put("status",status);
    if(backendPid!=null)result.put("backendPid",backendPid);
    System.out.println(WireCodec.JSON.writeValueAsString(result));System.out.flush();
  }
  private CanonicalAccessFixtureMain(){}
}
