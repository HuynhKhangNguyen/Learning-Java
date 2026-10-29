package lab.backend.learning.delivery;

import java.util.Map;
import java.util.UUID;
import lab.backend.learning.transport.WireCodec;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;

/** Container-level upload/parser failures must not become HTML or expose a
 * servlet exception. TLS handshake failures still have no HTTP response. */
public final class DeliveryErrorReportValve extends ErrorReportValve {
  @Override protected void report(Request request,Response response,Throwable failure) {
    if(response.getStatus()<400 || response.getContentWritten()>0 || !response.setErrorReported())return;
    if(failure==null)failure=request.getCoyoteRequest().getErrorException();
    boolean timeout=false;
    for(int depth=0;failure!=null && depth<8;depth++,failure=failure.getCause())if(failure instanceof java.net.SocketTimeoutException)timeout=true;
    String code=timeout?"REQUEST_TIMEOUT":"REQUEST_REJECTED";
    try {
      if(timeout)response.setStatus(408);
      response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
      response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff");
      var writer=response.getReporter();if(writer==null)return;
      writer.write(WireCodec.JSON.writeValueAsString(Map.of("schemaVersion",1,"code",code,"traceId",UUID.randomUUID(),"messageKey","learning.delivery."+code.toLowerCase(java.util.Locale.ROOT))));
      response.finishResponse();
    }catch(Exception disconnected){/* No input, exception or credential logging. */}
  }
}


