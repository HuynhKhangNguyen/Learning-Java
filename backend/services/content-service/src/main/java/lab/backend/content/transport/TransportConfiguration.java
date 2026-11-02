package lab.backend.content.transport;

import jakarta.servlet.DispatcherType;
import java.util.UUID;
import lab.backend.content.browser.BrowserHandler;
import lab.backend.content.delivery.DeliveryRuntime;
import lab.backend.content.identityaccess.AdmissionErrorReportValve;
import lab.backend.content.identityaccess.AdmissionGate;
import lab.backend.content.identityaccess.AdmissionRuntime;
import lab.backend.content.identityaccess.ContentAccess;
import lab.backend.content.publication.PublicationHttpHandler;
import lab.backend.content.publication.PublicationRuntime;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardHost;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@Profile({"transport", "admission", "delivery", "canonical", "publication", "browser"})
public class TransportConfiguration {
  @Bean
  WebServerFactoryCustomizer<TomcatServletWebServerFactory> privateConnector(
      TransportSettings settings) {
    return factory -> {
      // Chỉ port của named owned topology đã validate; không CLI endpoint override.
      Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
      connector.setPort(settings.privatePort());
      connector.setScheme("https");
      connector.setSecure(true);
      connector.setProperty("address", "127.0.0.1");
      connector.setProperty("SSLEnabled", "true");
      connector.setProperty("connectionTimeout", "5000");
      connector.setProperty("disableUploadTimeout", "false");
      connector.setProperty("connectionUploadTimeout", "3000");
      connector.setProperty("maxHttpRequestHeaderSize", "16384");

      SSLHostConfig ssl = new SSLHostConfig();
      ssl.setCertificateVerification("required");
      ssl.setProtocols("TLSv1.2,TLSv1.3");
      ssl.setTruststoreFile(settings.trustStore().toString());
      ssl.setTruststorePassword(settings.storePassword());
      ssl.setTruststoreType("PKCS12");

      SSLHostConfigCertificate certificate =
          new SSLHostConfigCertificate(ssl, SSLHostConfigCertificate.Type.EC);
      certificate.setCertificateKeystoreFile(settings.keyStore().toString());
      certificate.setCertificateKeystorePassword(settings.storePassword());
      certificate.setCertificateKeystoreType("PKCS12");
      certificate.setCertificateKeyAlias("leaf");
      ssl.addCertificate(certificate);
      connector.addSslHostConfig(ssl);
      factory.addAdditionalConnectors(connector);
    };
  }

  @Bean
  FilterRegistrationBean<TransportBoundaryFilter> transportBoundary(
      TransportSettings settings,
      ObjectProvider<AdmissionRuntime> admission,
      ObjectProvider<DeliveryRuntime> delivery,
      ObjectProvider<PublicationRuntime> publication,
      ObjectProvider<BrowserHandler> browser) {
    AdmissionRuntime runtime = admission.getIfAvailable();
    DeliveryRuntime relay = delivery.getIfAvailable();
    AdmissionGate gate = runtime == null ? null : runtime.gate();
    if (relay != null) {
      gate =
          new AdmissionGate(
              relay.source(),
              UUID.fromString(settings.realm()),
              UUID.fromString(settings.generation()));
      gate.verifyRuntime();
    }
    PublicationRuntime published = publication.getIfAvailable();
    PublicationHttpHandler publicationHandler =
        published == null ? null : new PublicationHttpHandler(published.store());
    var boundary =
        new TransportBoundaryFilter(
            settings,
            gate,
            relay == null ? null : relay.handler(),
            publicationHandler,
            browser.getIfAvailable());
    var filter = new FilterRegistrationBean<>(boundary);
    filter.setOrder(Ordered.HIGHEST_PRECEDENCE);
    filter.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR);
    filter.addUrlPatterns("/*");
    return filter;
  }

  @Bean(destroyMethod = "close")
  @Profile({"admission", "canonical", "browser"})
  ContentAccess contentAccess(AdmissionRuntime runtime) throws Exception {
    return runtime.access();
  }

  @Bean
  @Profile({"admission", "canonical", "browser"})
  WebServerFactoryCustomizer<TomcatServletWebServerFactory> admissionErrorEnvelope() {
    return factory ->
        factory.addContextCustomizers(
            context -> {
              if (!(context.getParent() instanceof StandardHost host)) {
                throw new IllegalStateException("Admission error boundary unavailable");
              }
              host.setErrorReportValveClass(AdmissionErrorReportValve.class.getName());
            });
  }
}
