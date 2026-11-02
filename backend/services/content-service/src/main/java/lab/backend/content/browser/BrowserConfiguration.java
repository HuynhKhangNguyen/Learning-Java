package lab.backend.content.browser;

import lab.backend.content.identityaccess.ContentAccess;
import lab.backend.content.transport.TransportSettings;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Ghép opt-in API-C01–03 với Content DB và canonical admission.
 *
 * <p>Input lấy từ verified transport settings; output là handler chỉ-read bằng
 * role `content_app`. Thiếu privilege/binding làm startup fail trước listener và
 * bean không tự ghi hay sửa browser snapshot.
 */
@Configuration(proxyBeanMethods = false)
@Profile({"browser", "canonical"})
@ConditionalOnProperty(name = "backendlab.browser.enabled", havingValue = "true")
public class BrowserConfiguration {
  @Bean
  BrowserHandler browserHandler(TransportSettings settings, ContentAccess access) {
    var source = new PGSimpleDataSource();
    source.setServerNames(new String[] {"127.0.0.1"});
    source.setPortNumbers(new int[] {settings.dbPort()});
    source.setDatabaseName("content_db");
    source.setUser("content_app");
    source.setPassword(settings.dbPassword());
    source.setConnectTimeout(1);
    source.setSocketTimeout(7);
    source.setSslMode("disable");
    BrowserStore store = new BrowserStore(source);
    store.verifyRuntime();
    return new BrowserHandler(store, access);
  }
}
