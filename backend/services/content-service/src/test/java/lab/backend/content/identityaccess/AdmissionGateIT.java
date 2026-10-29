package lab.backend.content.identityaccess;

import static org.assertj.core.api.Assertions.*;
import static lab.backend.content.identityaccess.AdmissionWire.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;

/** Actual PostgreSQL two-connection receiver proof, with no Identity DB access. */
@Timeout(30)
public class AdmissionGateIT {
  static final Properties config=new Properties();
  static final UUID REALM=UUID.randomUUID(),GENERATION=UUID.randomUUID();
  static PGSimpleDataSource ds,migrator;
  static AdmissionGate gate;
  @BeforeAll static void setup()throws Exception{
    String file=System.getProperty("lab.config");if(file==null||file.startsWith("${"))throw new IllegalStateException("Managed PostgreSQL required");
    Path p=Path.of(file).toRealPath();assertThat(p.getParent().resolve("ownership.json")).isRegularFile();
    try(var r=Files.newBufferedReader(p)){config.load(r);}ds=source("app");migrator=source("migrator");
    try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT current_setting('backend_lab.run_id')")){r.next();assertThat(r.getString(1)).isEqualTo(config.getProperty("lab.runId"));}
    // DatabaseIsolationIT runs first and proves fresh/rerun/failed migration behavior.
    sql(migrator,"INSERT INTO content.auth_environment(singleton,realm_id,db_generation,topology_version,ready) VALUES (true,'"+REALM+"','"+GENERATION+"','r1-static-v1',true)");
    gate=new AdmissionGate(ds,REALM,GENERATION);gate.verifyRuntime();
  }
  static PGSimpleDataSource source(String kind){var s=new PGSimpleDataSource();s.setServerNames(new String[]{"127.0.0.1"});s.setPortNumbers(new int[]{Integer.parseInt(config.getProperty("lab.port"))});s.setDatabaseName("content_db");s.setUser(config.getProperty("content."+kind+".user"));s.setPassword(config.getProperty("content."+kind+".password"));s.setConnectTimeout(1);s.setSocketTimeout(8);return s;}
  static Request request(){return new Request("a".repeat(43),UUID.randomUUID(),"CL-T01","CL-U01");}
  static Admission admission(Request r,UUID user,long epoch){Instant t=Instant.now();return new Admission(UUID.randomUUID(),r.nonce,REALM,user,UUID.randomUUID(),r.context,epoch,r.binding,t,t,t.plusSeconds(15),t.plusSeconds(1800),t.plusSeconds(43200));}
  static Fence command(UUID user,UUID session,UUID context,long floor){UUID op=UUID.randomUUID();return fence(fenceBody(op,user,session,context,floor,REALM,GENERATION),op);}
  static byte[] fenceBody(UUID op,UUID user,UUID session,UUID context,long floor,UUID realm,UUID generation){
    var m=new LinkedHashMap<String,Object>();String action=session==null?"RESET":"LOGOUT";
    m.put("schemaVersion",1);m.put("operationId",op.toString());m.put("scope",session==null?"ACCOUNT":"SESSION");m.put("action",action);m.put("audience","content");m.put("userId",user.toString());m.put("sessionRef",session==null?null:session.toString());m.put("sessionContextId",context==null?null:context.toString());m.put("epochFloor",Long.toString(floor));m.put("authRealmId",realm.toString());m.put("topologyVersion",TOPOLOGY);m.put("participantGeneration",generation.toString());
    m.put("commandDigest",sha(action+"\n"+user+"\n"+(session==null?"ACCOUNT":session)+"\n"+floor+"\n"+realm+"\n"+TOPOLOGY+"\ncontent,learning"));return lab.backend.content.transport.WireCodec.JSON.writeValueAsBytes(m);
  }
  @Test @Tag("SEC-02") void absentSessionFenceDeniesLateAdmissionWithoutProvisioningLearner()throws Exception{
    var r=request();var a=admission(r,UUID.randomUUID(),1);var f=command(a.user(),a.session(),a.context(),1);
    gate.fence(f);denied(()->gate.use(a,r,(c,u)->"forbidden"),"SESSION_FENCED");
    assertThat(count("content.auth_admission_use","user_id='"+a.user()+"'")).isZero();
    assertThat(count("information_schema.tables","table_schema='content' AND table_name IN ('app_user','learner_profile','auth_session')")).isZero();
  }
  @Test @Tag("SEC-09") void olderCommandCannotLowerFloorAndNewEpochMayProceed()throws Exception{
    UUID user=UUID.randomUUID();gate.fence(command(user,null,null,9));var ack=gate.fence(command(user,null,null,2));assertThat(ack.floor()).isEqualTo(9);
    var old=request();denied(()->gate.use(admission(old,user,8),old,(c,u)->u),"EPOCH_FENCED");
    var fresh=request();assertThat(gate.<UUID>use(admission(fresh,user,10),fresh,(c,u)->u)).isEqualTo(user);
    assertThat(count("content.auth_account_gate","user_id='"+user+"' AND min_credential_epoch=10")).isEqualTo(1);
  }
  @Test @Tag("SEC-09") void duplicateCommandIsDurableAndChangedContextConflicts()throws Exception{
    var r=request();var a=admission(r,UUID.randomUUID(),1);var f=command(a.user(),a.session(),a.context(),1);
    var ack=gate.fence(f);assertThat(new AdmissionGate(ds,REALM,GENERATION).fence(f)).isEqualTo(ack);
    var changed=fence(fenceBody(f.operation(),a.user(),a.session(),UUID.randomUUID(),1,REALM,GENERATION),f.operation());
    denied(()->gate.fence(changed),"COMMAND_CONFLICT");assertThat(count("content.auth_command_receipt","operation_id='"+f.operation()+"'")).isEqualTo(1);
  }
  @Test @Tag("SEC-04") void twoReplicasFenceWaitsForReadTransactionCommit()throws Exception{
    var request=request();var a=admission(request,UUID.randomUUID(),1);var f=command(a.user(),a.session(),a.context(),1);
    var held=new CountDownLatch(1);var release=new CountDownLatch(1);var pid=new java.util.concurrent.atomic.AtomicInteger();
    try(var pool=Executors.newFixedThreadPool(2)){
      var first=pool.submit(()->gate.use(a,request,(c,u)->{try(var s=c.createStatement();var row=s.executeQuery("SELECT pg_backend_pid()")){row.next();pid.set(row.getInt(1));}held.countDown();try{if(!release.await(4,TimeUnit.SECONDS))throw new SQLException("test barrier timeout");}catch(InterruptedException e){throw new SQLException(e);}return u;}));
      assertThat(held.await(3,TimeUnit.SECONDS)).isTrue();var second=pool.submit(()->new AdmissionGate(ds,REALM,GENERATION).fence(f));
      try{long until=System.nanoTime()+Duration.ofSeconds(2).toNanos();boolean observed=false;
        while(System.nanoTime()<until){if(count("pg_stat_activity",pid.get()+"=ANY(pg_blocking_pids(pid))")>0){observed=true;break;}Thread.sleep(10);}
        assertThat(observed).as("PostgreSQL reports real blocking, not a timing assumption").isTrue();assertThat(second.isDone()).isFalse();
      }finally{release.countDown();}
      assertThat(first.get(3,TimeUnit.SECONDS)).isEqualTo(a.user());assertThat(second.get(3,TimeUnit.SECONDS).operation()).isEqualTo(f.operation());
    }
    denied(()->gate.use(a,request,(c,u)->u),"SESSION_FENCED");
  }
  @Test @Tag("SEC-04") void rollbackKeepsAdmissionAndLocalEffectAtomic()throws Exception{
    var r=request();var a=admission(r,UUID.randomUUID(),1);String key=UUID.randomUUID().toString();
    denied(()->gate.use(a,r,(c,u)->{try(var s=c.prepareStatement("INSERT INTO content.transaction_probe VALUES (?,0,'rollback')")){s.setString(1,key);s.executeUpdate();}throw new SQLException("injected after effect");}),"RECEIVER_UNAVAILABLE");
    assertThat(count("content.transaction_probe","id='"+key+"'")).isZero();assertThat(count("content.auth_admission_use","admission_id='"+a.id()+"'")).isZero();
    assertThat(gate.<UUID>use(a,r,(c,u)->u)).isEqualTo(a.user());denied(()->gate.use(a,r,(c,u)->u),"ADMISSION_ALREADY_USED");
  }
  @Test @Tag("SEC-15") void wrongContextNonceBindingAndOwnerCannotUseAdmission(){
    var r=request();var a=admission(r,UUID.randomUUID(),1);denied(()->gate.use(a,request(),(c,u)->u),"ADMISSION_BINDING_DENIED");
    denied(()->gate.use(a,r,(c,u)->{AdmissionGate.requireOwner(u,UUID.randomUUID());return u;}),"RESOURCE_NOT_FOUND");
  }
  @Test @Tag("SEC-09") void sameSessionUnderDifferentOwnerCannotReplaceTombstone()throws Exception{
    var session=UUID.randomUUID();var context=UUID.randomUUID();var first=command(UUID.randomUUID(),session,context,1);gate.fence(first);
    denied(()->gate.fence(command(UUID.randomUUID(),session,context,1)),"SESSION_BINDING_CONFLICT");
    assertThat(count("content.auth_session_fence","session_ref='"+session+"' AND user_id='"+first.user()+"'")).isEqualTo(1);
  }
  @Test @Tag("SEC-09") void runtimeRoleCannotEraseFenceReceiptOrLowerFloor()throws Exception{
    var f=command(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),4);gate.fence(f);
    for(String table:List.of("auth_session_fence","auth_command_receipt","auth_admission_use"))assertThatThrownBy(()->sql(ds,"DELETE FROM content."+table)).isInstanceOf(SQLException.class);
    assertThatThrownBy(()->sql(ds,"UPDATE content.auth_account_gate SET min_credential_epoch=1 WHERE user_id='"+f.user()+"'")).isInstanceOf(SQLException.class);
    assertThatThrownBy(()->sql(ds,"UPDATE content.auth_environment SET ready=false")).isInstanceOf(SQLException.class);
  }
  @Test @Tag("SEC-15") void wrongRealmGenerationAndNotReadyFailClosed()throws Exception{
    denied(()->new AdmissionGate(ds,REALM,UUID.randomUUID()).verifyRuntime(),"RECEIVER_NOT_READY");
    UUID op=UUID.randomUUID();var f=fence(fenceBody(op,UUID.randomUUID(),null,null,1,REALM,UUID.randomUUID()),op);denied(()->gate.fence(f),"FENCE_GENERATION_DENIED");
    sql(migrator,"UPDATE content.auth_environment SET ready=false");try{denied(()->gate.verifyRuntime(),"RECEIVER_NOT_READY");}finally{sql(migrator,"UPDATE content.auth_environment SET ready=true");}
  }
  @Test @Tag("SEC-10") void cutoffRejectedBeforeEffectAndDatabaseBoundsHeldTransaction()throws Exception{
    var r=request();var a=admission(r,UUID.randomUUID(),1);Instant now=Instant.now();
    var late=new Admission(a.id(),a.nonce(),a.realm(),a.user(),a.session(),a.context(),a.epoch(),a.binding(),now,now,now.plusSeconds(6),a.idle(),a.absolute());
    denied(()->gate.use(late,r,(c,u)->{throw new AssertionError("effect executed");}),"ADMISSION_EXPIRED");
    denied(()->gate.use(a,r,(c,u)->{try(var s=c.createStatement()){s.execute("SELECT pg_sleep(7)");}return u;}),"RECEIVER_UNAVAILABLE");
    assertThat(count("content.auth_admission_use","admission_id='"+a.id()+"'")).isZero();
  }
  static void denied(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,String code){assertThatThrownBy(action).isInstanceOf(lab.backend.content.transport.WireCodec.Denied.class).hasMessage(code);}
  static void sql(PGSimpleDataSource s,String sql)throws SQLException{try(var c=s.getConnection();var st=c.createStatement()){st.execute(sql);}}
  static int count(String table,String where)throws SQLException{try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM "+table+" WHERE "+where)){r.next();return r.getInt(1);}}
}
