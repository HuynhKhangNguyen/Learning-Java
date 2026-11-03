package lab.backend.learning.work;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import lab.backend.learning.contentreplica.PublicationRuntime;
import lab.backend.learning.exposure.ExposureJournalPump;
import lab.backend.learning.identityaccess.AdmissionRuntime;
import lab.backend.learning.identityaccess.LearningAccess;
import lab.backend.learning.journal.FilesystemJournalStore;
import lab.backend.learning.transport.TransportSettings;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.env.ConfigurableEnvironment;

/** Opt-in own lab composition, không provision/migrate hoặc tự nhận production journal qualification. */
public final class AllocationRuntime implements AutoCloseable {
  private final LearningAccess access;
  private final PublicationRuntime publication;
  private final AllocationHttpHandler handler;

  private AllocationRuntime(LearningAccess access, PublicationRuntime publication,
      AllocationHttpHandler handler) {
    this.access = access;
    this.publication = publication;
    this.handler = handler;
  }

  public AllocationHttpHandler handler() {
    return handler;
  }

  public PublicationRuntime publication() {
    return publication;
  }

  public static AllocationRuntime load(ConfigurableEnvironment environment,
      TransportSettings settings, AdmissionRuntime admission) {
    LearningAccess access = null;
    PublicationRuntime publication = null;
    try {
      if (!settings.canonicalLab()) throw new IllegalArgumentException();
      Path config = Path.of(environment.getRequiredProperty("backendlab.allocation.config")).toRealPath();
      if (!config.getParent().equals(settings.directory())) throw new IllegalArgumentException();
      var properties = new Properties();
      try (var reader = Files.newBufferedReader(config, java.nio.charset.StandardCharsets.UTF_8)) {
        properties.load(reader);
      }
      if (!properties.stringPropertyNames().equals(Set.of(
          "schemaVersion", "enabled", "realm", "generation", "dbRunId", "journalDirectory"))
          || !properties.getProperty("schemaVersion").equals("1")
          || !properties.getProperty("enabled").equals("true")
          || !properties.getProperty("realm").equals(settings.realm())
          || !properties.getProperty("generation").equals(settings.generation())
          || !properties.getProperty("dbRunId").equals(settings.dbRunId())) {
        throw new IllegalArgumentException();
      }
      Path journalRoot = Path.of(properties.getProperty("journalDirectory")).toAbsolutePath().normalize();
      if (!journalRoot.equals(settings.directory().resolve("allocation-journal"))) {
        throw new IllegalArgumentException();
      }
      var source = new PGSimpleDataSource();
      source.setServerNames(new String[]{"127.0.0.1"});
      source.setPortNumbers(new int[]{settings.dbPort()});
      source.setDatabaseName("learning_db");
      source.setUser("learning_app");
      source.setPassword(settings.dbPassword());
      source.setConnectTimeout(1);
      source.setSocketTimeout(7);
      source.setSslMode("disable");
      try (var connection = source.getConnection(); var statement = connection.createStatement()) {
        statement.executeQuery("SELECT attempt_id FROM learning.allocation_attempt LIMIT 0").close();
        statement.executeQuery("SELECT operation_id FROM learning.allocation_journal LIMIT 0").close();
      }
      access = admission.access();
      publication = new PublicationRuntime(settings);
      var coordinator = new AllocationCoordinator(access,
          new AllocationStore(UUID.fromString(settings.realm()), UUID.fromString(settings.generation())),
          new ExposureJournalPump(source, new FilesystemJournalStore(journalRoot)));
      return new AllocationRuntime(access, publication, new AllocationHttpHandler(coordinator));
    } catch (Exception unavailable) {
      if (access != null) access.close();
      if (publication != null) publication.close();
      throw new IllegalStateException("Allocation own-lab config/readiness unavailable");
    }
  }

  public void close() {
    access.close();
    publication.close();
  }
}
