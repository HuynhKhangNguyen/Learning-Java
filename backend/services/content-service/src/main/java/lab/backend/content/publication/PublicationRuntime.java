package lab.backend.content.publication;

import java.nio.file.Files;
import java.nio.file.Path;
import lab.backend.content.transport.TransportSettings;
import org.postgresql.ds.PGSimpleDataSource;

/** Runtime publication mở dưới profile explicit và chỉ dùng own Content DB role/topology. */
public final class PublicationRuntime {
  private final PublicationStore store;
  private final TransportSettings settings;

  public PublicationRuntime(TransportSettings settings) {
    this.settings = settings;
    var dataSource = new PGSimpleDataSource();
    dataSource.setServerNames(new String[] {"127.0.0.1"});
    dataSource.setPortNumbers(new int[] {settings.dbPort()});
    dataSource.setDatabaseName("content_db");
    dataSource.setUser("content_app");
    dataSource.setPassword(settings.dbPassword());
    dataSource.setConnectTimeout(1);
    dataSource.setSocketTimeout(5);
    dataSource.setSslMode("disable");
    store = new PublicationStore(dataSource, loadCapability(settings.directory()));
  }

  public PublicationStore store() {
    return store;
  }

  public PublicationCoordinator coordinator() throws Exception {
    return new PublicationCoordinator(store, new PublicationPeerClient(settings));
  }

  private static byte[] loadCapability(Path directory) {
    try {
      Path configured = directory.resolve("publication-coordinator.cap");
      if (!Files.exists(configured)) {
        return null;
      }
      Path capability = configured.toRealPath();
      if (!capability.getParent().equals(directory) || !Files.isRegularFile(capability)) {
        throw new IllegalStateException("Publication coordinator capability invalid");
      }
      byte[] bytes = Files.readAllBytes(capability);
      if (bytes.length < 32 || bytes.length > 128) {
        throw new IllegalStateException("Publication coordinator capability invalid");
      }
      return bytes;
    } catch (Exception failure) {
      throw new IllegalStateException("Publication coordinator capability unavailable");
    }
  }
}

