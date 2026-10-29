package lab.backend.identity.session;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import lab.backend.identity.credential.*;
import lab.backend.identity.delivery.*;
import lab.backend.identity.transport.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.env.ConfigurableEnvironment;

/** Composition canonical chỉ cho named owned lab. Startup đọc/verify own DB,
 * không migrate/provision/repair. Worker nối durable operations tới hai receiver;
 * pending auth/lifecycle không được mở bằng timeout, restart hoặc peer down. */
public final class CanonicalRuntime implements AutoCloseable {
  private final TransportSettings transport;
  private final SessionAuthority sessions;
  private final ReauthAuthority reauth;
  private final IdentitySensitiveAuthority sensitive;
  private final AuthBarrierCoordinator barrier;
  private final String origin;
  private final Map<String,DeliveryPeerClient> peers;
  private final AuthDeliveryDispatcher dispatcher;
  private final AuthPendingWork pendingWork;
  private ScheduledExecutorService worker;
  private boolean closed;
  private CanonicalRuntime(PGSimpleDataSource source,TransportSettings transport,SessionAuthority sessions,
      ReauthAuthority reauth,IdentitySensitiveAuthority sensitive,AuthBarrierCoordinator barrier,String origin,Map<String,DeliveryPeerClient> peers){
    this.transport=transport;this.sessions=sessions;this.reauth=reauth;
    this.sensitive=sensitive;this.barrier=barrier;this.origin=origin;this.peers=Map.copyOf(peers);
    dispatcher=new AuthDeliveryDispatcher(barrier.deliveries());
    pendingWork=new AuthPendingWork(source,UUID.fromString(transport.realm()));
  }
  public SessionAuthority authority(){return sessions;}
  public SessionHttpHandler publicHandler(){return new SessionHttpHandler(sessions,origin,reauth,transport.publicPort(),sensitive);}
  public CanonicalAuthHttpHandler privateHandler(){return new CanonicalAuthHttpHandler(sessions,reauth,origin,transport.privatePort());}
  @Override public String toString(){return "CanonicalRuntime[ownedLab,configuration=REDACTED]";}

  /** Exact config/UUID/corpus/key bindings được kiểm trước DS; peers được tạo
   * sau readiness, có cleanup khi startup thất bại giữa hai HTTP clients. */
  public static CanonicalRuntime load(ConfigurableEnvironment env,TransportSettings transport){
    Map<String,DeliveryPeerClient> clients=new LinkedHashMap<>();
    try{
      if(!transport.canonicalLab()||!Arrays.equals(env.getActiveProfiles(),new String[]{"canonical"}))throw new Exception();
      String input=env.getProperty("backendlab.canonical.config");if(input==null||input.isBlank())throw new Exception();
      Path file=Path.of(input).toRealPath();if(!file.getParent().equals(transport.directory()))throw new Exception();
      Properties p=new Properties(){
        @Override public synchronized Object put(Object key,Object value){
          if(containsKey(key))throw new IllegalArgumentException("Duplicate canonical setting");
          return super.put(key,value);
        }
      };
      try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)){p.load(reader);}
      if(!p.stringPropertyNames().equals(Set.of("schemaVersion","enabled","listenerTopology","realm","generation","dbRunId","origin","corpusPath","corpusSha256","throttleKey","identityGeneration","contentGeneration","learningGeneration"))
          ||!"1".equals(p.getProperty("schemaVersion"))||!"true".equals(p.getProperty("enabled"))
          ||!TransportSettings.CANONICAL.equals(p.getProperty("listenerTopology"))
          ||!transport.realm().equals(p.getProperty("realm"))||!transport.generation().equals(p.getProperty("generation"))
          ||!transport.dbRunId().equals(p.getProperty("dbRunId"))||transport.clockOffsetMs()!=0)throw new Exception();
      UUID realm=id(p,"realm"),generation=id(p,"generation");id(p,"dbRunId");
      var generations=new LinkedHashMap<String,UUID>();for(String peer:List.of("identity","content","learning"))generations.put(peer,id(p,peer+"Generation"));
      if(!generation.equals(generations.get("identity"))||new HashSet<>(generations.values()).size()!=3||generations.containsValue(realm))throw new Exception();
      String origin=p.getProperty("origin");new SessionHttpHandler(null,origin);
      Path corpusFile=Path.of(p.getProperty("corpusPath")).toRealPath();
      if(!corpusFile.getParent().equals(transport.directory()))throw new Exception();
      var corpus=PasswordCorpus.load(corpusFile,p.getProperty("corpusSha256"));
      byte[] key=Base64.getDecoder().decode(p.getProperty("throttleKey"));
      if(key.length!=32||Arrays.equals(key,new byte[32]))throw new Exception();
      var ds=new PGSimpleDataSource();ds.setServerNames(new String[]{"127.0.0.1"});ds.setPortNumbers(new int[]{transport.dbPort()});
      ds.setDatabaseName("identity_db");ds.setUser("identity_app");ds.setPassword(transport.dbPassword());
      ds.setConnectTimeout(1);ds.setSocketTimeout(6);ds.setSslMode("disable");ds.setOptions("-c statement_timeout=5000 -c lock_timeout=2000");
      // Một singleton verifier dùng chung login/reauth và hai KDF slots; không
      // tạo verifier riêng theo endpoint để vô tình nhân giới hạn CPU.
      var throttle=new AuthThrottle(ds,key,100000);var verifier=new CredentialVerifier(new Argon2CredentialHasher());
      var barrier=new AuthBarrierCoordinator(ds,realm,generation,generations);
      var sessions=new SessionAuthority(ds,realm,verifier,throttle,new CredentialService(new CredentialWorkflow(ds),corpus,throttle),barrier);
      var reauth=new ReauthAuthority(ds,realm,sessions,barrier);
      // Dùng cùng verifier/corpus/key đã validate, không tạo KDF pool/secret riêng
      // cho I04/I07. Protected intent derive domain khác throttle từ stable key.
      var sensitive=new IdentitySensitiveAuthority(ds,realm,reauth,barrier,verifier,corpus,key);
      // R02c foundation verifier đã kiểm cả auth-outbox immutable/privileges;
      // chỉ đọc startup, không đăng ký LAB_APPLY hoặc phát strict delivery.
      // TransportSettings.load đã kiểm own runId/clock, các authority kiểm lại
      // own current realm/generations/DB clock bằng connection riêng dưới đây.
      new DeliveryStore(ds,realm,generation,generations).verifyRuntime();
      barrier.verifyRuntime();sessions.verifyRuntime();reauth.verifyRuntime();sensitive.verifyRuntime();
      for(String peer:List.of("content","learning"))clients.put(peer,new DeliveryPeerClient(transport,peer,transport.peerPrivatePort(peer)));
      return new CanonicalRuntime(ds,transport,sessions,reauth,sensitive,barrier,origin,clients);
    }catch(Exception failure){
      clients.values().forEach(DeliveryPeerClient::close);
      throw new IllegalStateException("Canonical config/schema/readiness unavailable; no protected readiness");
    }
  }
  private static UUID id(Properties p,String key){return UUID.fromString(WireCodec.idempotencyKey(p.getProperty(key)));}

  /** Một worker bounded/replica, durable lease và DB locks quyết quyền. HTTP
   * chạy sau claim commit, completion chạy sau stored ACK; không network/KDF
   * trong transaction canonical. Replica restart không mất queued operations. */
  public synchronized ScheduledExecutorService startWorker(){
    if(closed)throw new IllegalStateException("Canonical runtime closed");
    if(worker!=null)return worker;
    worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"identity-auth-barrier");t.setDaemon(true);return t;});
    worker.scheduleWithFixedDelay(this::iteration,1,1,TimeUnit.SECONDS);return worker;
  }
  private void iteration(){
    // Mỗi bước lỗi giữ deny/pending; không cho exception làm scheduler ngừng
    // vĩnh viễn hoặc chuyển dependency unavailable thành COMPLETED.
    try{sessions.sweepExpired(32);}catch(Exception unavailable){/* Request vẫn kiểm canonical deadline, không renew. */}
    try{
      // Cursor/high-water riêng từng pha đi qua cả backlog, không chọn mãi16
      // history lỗi đầu tiên. Từng effect vẫn transaction/locked reread riêng;
      // scan tiến dù effect lỗi nhưng ID lỗi không bị xóa hoặc đổi binding.
      for(UUID id:pendingWork.next(AuthPendingWork.Phase.TRANSITIONS)){
        try{barrier.reconcileTransition(id);}catch(Exception unavailable){/* Giữ chính ID lỗi pending. */}
      }
      for(UUID id:pendingWork.next(AuthPendingWork.Phase.RECONCILE)){
        try{barrier.reconcile(id);}catch(Exception unavailable){/* Không retarget operation lỗi/old generation. */}
      }
    }catch(Exception unavailable){/* Legacy deny không bị xóa khi reconcile thiếu schema/current realm. */}
    try{
      for(int i=0;i<2;i++)dispatcher.once(lease->{
        var peer=peers.get(lease.command().consumer());if(peer==null)throw new WireCodec.Denied(403,"AUTH_DELIVERY_PEER_DENIED");
        return peer.fence(lease.command().operation(),lease.command().bytes());
      });
    }catch(Exception unavailable){/* Lease/retry/outbox là durable; không local ACK giả. */}
    try{
      for(UUID id:pendingWork.next(AuthPendingWork.Phase.COMPLETE)){
        try{barrier.complete(id);}catch(Exception unavailable){/* Không complete ID sai bindings dù query đếm2ACK. */}
      }
    }catch(Exception unavailable){/* Completion locked-reread vẫn là cổng cuối; không clear pending từ query này. */}
  }
  @Override public synchronized void close(){
    if(closed)return;closed=true;if(worker!=null)worker.shutdownNow();peers.values().forEach(DeliveryPeerClient::close);
  }
}
