package lab.backend.identity.transport;

import jakarta.servlet.DispatcherType;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods=false)
@Profile({"transport","session","delivery","canonical"})
public class TransportConfiguration {
  @Bean WebServerFactoryCustomizer<TomcatServletWebServerFactory> privateConnector(TransportSettings settings){
    return factory->{
      factory.addConnectorCustomizers(publicConnector->{
        publicConnector.setProperty("connectionTimeout","3000");
        publicConnector.setProperty("disableUploadTimeout","false");
        publicConnector.setProperty("connectionUploadTimeout","3000");
        publicConnector.setProperty("maxHttpRequestHeaderSize","16384");
      });
      // Chỉ port của named owned topology đã validate; không CLI endpoint override.
      Connector connector=new Connector("org.apache.coyote.http11.Http11NioProtocol");connector.setPort(settings.privatePort());connector.setScheme("https");connector.setSecure(true);
      connector.setProperty("address","127.0.0.1");connector.setProperty("SSLEnabled","true");connector.setProperty("connectionTimeout","5000");
      connector.setProperty("disableUploadTimeout","false");connector.setProperty("connectionUploadTimeout","3000");connector.setProperty("maxHttpRequestHeaderSize","16384");
      SSLHostConfig ssl=new SSLHostConfig();ssl.setCertificateVerification("required");ssl.setProtocols("TLSv1.2,TLSv1.3");
      ssl.setTruststoreFile(settings.trustStore().toString());ssl.setTruststorePassword(settings.storePassword());ssl.setTruststoreType("PKCS12");
      SSLHostConfigCertificate certificate=new SSLHostConfigCertificate(ssl,SSLHostConfigCertificate.Type.EC);
      certificate.setCertificateKeystoreFile(settings.keyStore().toString());certificate.setCertificateKeystorePassword(settings.storePassword());certificate.setCertificateKeystoreType("PKCS12");certificate.setCertificateKeyAlias("leaf");
      ssl.addCertificate(certificate);connector.addSslHostConfig(ssl);factory.addAdditionalConnectors(connector);
    };
  }
  @Bean FilterRegistrationBean<TransportBoundaryFilter> transportBoundary(TransportSettings settings,org.springframework.beans.factory.ObjectProvider<lab.backend.identity.session.SessionHttpHandler> session,org.springframework.beans.factory.ObjectProvider<lab.backend.identity.delivery.DeliveryRuntime> delivery,org.springframework.beans.factory.ObjectProvider<lab.backend.identity.session.CanonicalRuntime> canonical){
    var runtime=delivery.getIfAvailable();
    var auth=canonical.getIfAvailable();
    var filter=new FilterRegistrationBean<>(new TransportBoundaryFilter(settings,auth==null?session.getIfAvailable():auth.publicHandler(),runtime==null?null:runtime.handler(),auth==null?null:auth.privateHandler()));filter.setOrder(Ordered.HIGHEST_PRECEDENCE);filter.setDispatcherTypes(DispatcherType.REQUEST,DispatcherType.ERROR);filter.addUrlPatterns("/*");return filter;
  }
}
