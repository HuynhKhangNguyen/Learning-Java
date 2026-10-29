package lab.backend.content.delivery;

import static lab.backend.content.delivery.DeliveryWire.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.postgresql.ds.PGSimpleDataSource;
import lab.backend.content.transport.WireCodec;

/** TEST-ONLY cross-artifact maintenance driver. Never packaged in the service JAR.
 * Reads managed fixture credentials, selects ONLY this service's own migrator.
 * This guarded SQL probe is not production publication/canonical ABORT authority. */
public final class DeliveryRecoveryFixtureMain {
  public static void main(String[] args){
    try{
      if(args.length!=1||!Set.of("enqueue-poison","abort","accept").contains(args[0]))throw new IllegalArgumentException();
      Path labPath=Path.of(System.getProperty("lab.config")).toRealPath();
      Path trustPath=Path.of(System.getProperty("trust.config")).toRealPath();
      var marker=WireCodec.strict(Files.readAllBytes(labPath.getParent().resolve("ownership.json")));
      var trustMarker=WireCodec.strict(Files.readAllBytes(trustPath.getParent().resolve("ownership.json")));
      Path workspace=Path.of(WireCodec.string(marker,"workspace")).toRealPath();
      if(!workspace.equals(Path.of(WireCodec.string(trustMarker,"workspace")).toRealPath())||!labPath.startsWith(workspace.resolve(".tools/db-lab"))||!trustPath.startsWith(workspace.resolve(".tools/trust-lab")))throw new IllegalArgumentException();
      Properties lab=new Properties();try(var reader=Files.newBufferedReader(labPath)){lab.load(reader);}
      if(!lab.getProperty("lab.runId").equals(WireCodec.string(marker,"runId")))throw new IllegalArgumentException();
      var generations=new HashMap<String,UUID>();UUID realm=null;
      for(String service:List.of("identity","content","learning")){
        Properties p=new Properties();try(var reader=Files.newBufferedReader(trustPath.getParent().resolve(service+".properties"))){p.load(reader);}
        UUID current=UUID.fromString(p.getProperty("realm"));if(realm!=null&&!realm.equals(current))throw new IllegalArgumentException();realm=current;
        if(!lab.getProperty("lab.runId").equals(p.getProperty("dbRunId")))throw new IllegalArgumentException();
        generations.put(service,UUID.fromString(p.getProperty("generation")));
      }
      var source=new PGSimpleDataSource();source.setServerNames(new String[]{"127.0.0.1"});source.setPortNumbers(new int[]{Integer.parseInt(lab.getProperty("lab.port"))});source.setDatabaseName(SERVICE+"_db");
      source.setUser(lab.getProperty(SERVICE+".migrator.user"));source.setPassword(lab.getProperty(SERVICE+".migrator.password"));source.setConnectTimeout(1);source.setSocketTimeout(8);source.setSslMode("disable");
      try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT current_setting('backend_lab.run_id')")){r.next();if(!r.getString(1).equals(lab.getProperty("lab.runId")))throw new IllegalArgumentException();}
      var n=WireCodec.strict(System.in.readNBytes(24577));var keys=new HashSet<String>();n.properties().forEach(p->keys.add(p.getKey()));
      if(!keys.equals(Set.of("envelope","recoveryId","recoveryGeneration","coveredSequence","coveredPrefixDigest","reason","proof","ack")))throw new IllegalArgumentException();
      Envelope e=envelope(Base64.getDecoder().decode(WireCodec.string(n,"envelope")));
      String sequence=WireCodec.string(n,"coveredSequence");if(!sequence.matches("0|[1-9][0-9]{0,18}"))throw new IllegalArgumentException();
      var recovery=new DeliveryStore.Recovery(UUID.fromString(WireCodec.idempotencyKey(WireCodec.string(n,"recoveryId"))),UUID.fromString(WireCodec.idempotencyKey(WireCodec.string(n,"recoveryGeneration"))),Long.parseLong(sequence),WireCodec.string(n,"coveredPrefixDigest"),WireCodec.string(n,"reason"));
      var store=new DeliveryStore(source,realm,generations.get(SERVICE),generations);
      if(args[0].equals("enqueue-poison")){
        if(!e.producer().equals(SERVICE)||!e.type().equals("UNKNOWN_TYPE")||!e.phase().equals("APPLY")||!e.payloadVerified())throw new IllegalArgumentException();
        var stored=store.enqueue(e.message(),e.operation(),e.correlation(),e.consumer(),e.stream(),e.type(),e.phase(),e.payload(),c->{try(var s=c.prepareStatement("INSERT INTO content.transaction_probe VALUES (?,0,\'delivery-lab-producer\')")){s.setString(1,e.message().toString());s.executeUpdate();}});
        System.out.println(Base64.getEncoder().encodeToString(stored.bytes()));
      }else if(args[0].equals("abort")){
        store.authorizeRecovery(e.bytes(),recovery);
        var proof=store.abortBeforeCommit(e.bytes(),recovery,(c,message)->{
          try(var s=c.prepareStatement("SELECT revision,payload FROM content.transaction_probe WHERE id=? FOR UPDATE")){s.setString(1,message.message().toString());try(var r=s.executeQuery()){if(!r.next()||r.getLong(1)!=0||!Set.of("delivery-lab-producer","delivery-lab-aborted").contains(r.getString(2)))throw deny(409,"RECOVERY_COMMITTED_DENIED");}}
          try(var s=c.prepareStatement("UPDATE content.transaction_probe SET payload='delivery-lab-aborted' WHERE id=? AND revision=0")){s.setString(1,message.message().toString());if(s.executeUpdate()!=1)throw deny(409,"RECOVERY_COMMITTED_DENIED");}
        });
        System.out.println(Base64.getEncoder().encodeToString(proof.bytes()));
      }else{
        var proof=new DeliveryStore.AbortProof(Base64.getDecoder().decode(WireCodec.string(n,"proof")));
        store.acceptRecovered(e.bytes(),recovery,proof,Base64.getDecoder().decode(WireCodec.string(n,"ack")));System.out.println("ACCEPTED");
      }
    }catch(Exception denied){System.err.println("RECOVERY_FIXTURE_DENIED");System.exit(1);}
  }
  private DeliveryRecoveryFixtureMain(){}
}
