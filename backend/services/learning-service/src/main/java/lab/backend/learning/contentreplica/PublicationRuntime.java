package lab.backend.learning.contentreplica;

import java.util.UUID;
import java.util.function.Supplier;
import lab.backend.learning.transport.TransportSettings;
import org.postgresql.ds.PGSimpleDataSource;

/** Explicit R06b lab composition; runtime never migrates or provisions its database. */
public final class PublicationRuntime implements AutoCloseable {
  private final PublicationStore store;private final ContentBundleClient client;
  public PublicationRuntime(TransportSettings settings)throws Exception{var ds=new PGSimpleDataSource();ds.setServerNames(new String[]{"127.0.0.1"});ds.setPortNumbers(new int[]{settings.dbPort()});ds.setDatabaseName("learning_db");ds.setUser("learning_app");ds.setPassword(settings.dbPassword());ds.setConnectTimeout(1);ds.setSocketTimeout(5);store=new PublicationStore(ds,UUID.fromString(settings.realm()),UUID.fromString(settings.generation()),new PublicationGate());client=new ContentBundleClient(settings);}
  public byte[] apply(PublicationWire.Command command)throws Exception{if(!"STAGE".equals(command.phase()))return store.phase(command);byte[] durable=store.existing(command);if(durable!=null)return durable;var replica=client.pull(command);return store.stage(command,replica);}
  public java.util.Map<String,Object> status(UUID operation){return store.status(operation);}
  /** Local port cho learner-work tương lai; leaf này chỉ chứng minh gate/probe, chưa tạo T1/T2 effect. */
  public <T>T contentSensitive(Supplier<T> callback,Supplier<T> pending){return store.contentSensitive(callback,pending);}
  public void close(){client.close();}
}
