package lab.backend.learning.transport;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Set;

public final class CertificateIdentity {
  public static String identity(X509Certificate certificate,String environment,boolean client){
    try{
      certificate.checkValidity();
      if(certificate.getBasicConstraints()>=0 || certificate.getExtendedKeyUsage()==null || !certificate.getExtendedKeyUsage().contains(client?"1.3.6.1.5.5.7.3.2":"1.3.6.1.5.5.7.3.1"))throw new Exception();
      var uris=new ArrayList<String>();var sans=certificate.getSubjectAlternativeNames();if(sans==null)throw new Exception();
      for(var san:sans)if(Integer.valueOf(6).equals(san.getFirst()))uris.add((String)san.get(1));
      if(uris.size()!=1)throw new Exception();String prefix="spiffe://backend-lab/"+environment+"/";
      if(!uris.getFirst().startsWith(prefix))throw new Exception();String identity=uris.getFirst().substring(prefix.length());
      if(!Set.of("identity","content","learning","edge","operator").contains(identity))throw new Exception();return identity;
    }catch(Exception e){throw new WireCodec.Denied(403,"CERTIFICATE_IDENTITY_DENIED");}
  }
  private CertificateIdentity(){}
}
