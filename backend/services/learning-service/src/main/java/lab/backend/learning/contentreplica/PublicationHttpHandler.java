package lab.backend.learning.contentreplica;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import lab.backend.learning.transport.WireCodec;

/** INT-04 private provider; transport certificate identity remains the caller authority. */
public final class PublicationHttpHandler {
  public static final String PREFIX="/internal/v1/publication-operations/";private final PublicationRuntime runtime;
  public PublicationHttpHandler(PublicationRuntime runtime){this.runtime=runtime;}
  public boolean handle(HttpServletRequest req,HttpServletResponse res,String caller)throws IOException{if(!req.getRequestURI().startsWith(PREFIX))return false;try{if(!"content".equals(caller))throw new WireCodec.Denied(403,"ACTION_DENIED");String tail=req.getRequestURI().substring(PREFIX.length());String[] parts=tail.split("/",-1);if(parts.length==1&&"GET".equals(req.getMethod())){try{write(res,200,runtime.status(UUID.fromString(parts[0])));}catch(IllegalArgumentException missing){write(res,404,WireCodec.error("OPERATION_NOT_FOUND"));}return true;}if(parts.length!=3||!"phases".equals(parts[1]))throw new WireCodec.Denied(404,"ROUTE_NOT_AVAILABLE");if(!"PUT".equals(req.getMethod()))throw new WireCodec.Denied(405,"METHOD_DENIED");String type=req.getContentType();if(type==null||!type.toLowerCase(java.util.Locale.ROOT).matches("application/json(?:; ?charset=utf-8)?"))throw new WireCodec.Denied(400,"BAD_CONTENT_TYPE");UUID operation;PublicationWire.Command command;try{operation=UUID.fromString(parts[0]);command=PublicationWire.command(req.getInputStream().readNBytes(PublicationWire.MAX_BYTES+1),operation,parts[2]);}catch(IllegalArgumentException invalid){throw new WireCodec.Denied(400,"COMMAND_INVALID");}try{writeBytes(res,200,runtime.apply(command));}catch(IllegalArgumentException conflict){write(res,409,WireCodec.error("PUBLICATION_CONFLICT"));}return true;}catch(WireCodec.Denied e){write(res,e.status,WireCodec.error(e.code));return true;}catch(Exception e){write(res,503,WireCodec.error("PUBLICATION_UNAVAILABLE"));return true;}}
  private static void write(HttpServletResponse res,int status,Object body)throws IOException{writeBytes(res,status,PublicationWire.JSON.writeValueAsBytes(body));}
  private static void writeBytes(HttpServletResponse res,int status,byte[] body)throws IOException{res.setStatus(status);res.setHeader("Cache-Control","no-store");res.setContentType("application/json");res.setContentLength(body.length);res.getOutputStream().write(body);}
}
