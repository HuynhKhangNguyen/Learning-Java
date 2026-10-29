package lab.backend.learning.delivery;

import static lab.backend.learning.delivery.DeliveryRuntimeIT.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import lab.backend.learning.transport.*;

/** TEST-ONLY bounded owned mTLS fault socket. Does not replace real receiver durability. */
final class DeliveryFaultPeer implements AutoCloseable {
  @FunctionalInterface interface Reply {void send(SSLSocket socket,DeliveryWire.Envelope envelope)throws Exception;}
  final SSLServerSocket server;
  final int destination;
  final ExecutorService pool=Executors.newCachedThreadPool();
  final Set<SSLSocket> sockets=ConcurrentHashMap.newKeySet();
  final java.util.concurrent.atomic.AtomicInteger requests=new java.util.concurrent.atomic.AtomicInteger();
  final Queue<Throwable> errors=new ConcurrentLinkedQueue<>();
  DeliveryFaultPeer(String key,Reply reply)throws Exception{
    server=(SSLServerSocket)TransportPeerClient.tls(settings,directory.resolve(key+".p12"),SERVICE).getServerSocketFactory().createServerSocket();
    server.setReuseAddress(true);server.setNeedClientAuth(true);server.setEnabledProtocols(new String[]{"TLSv1.3","TLSv1.2"});
    server.bind(new InetSocketAddress("127.0.0.1",0));destination=server.getLocalPort();if(destination<1024)throw new IllegalStateException();
    pool.submit(()->{try{while(!server.isClosed()){
      var socket=(SSLSocket)server.accept();sockets.add(socket);pool.submit(()->{
        try(socket){
          socket.setSoTimeout(6000);socket.startHandshake();
          var certificate=(java.security.cert.X509Certificate)socket.getSession().getPeerCertificates()[0];
          if(!CertificateIdentity.identity(certificate,"lab",true).equals(PRODUCER))throw new IllegalStateException("Fixture producer mismatch");
          var input=socket.getInputStream();var headers=new java.io.ByteArrayOutputStream();byte[] delimiter={'\r','\n','\r','\n'};int matched=0;
          while(matched<4){int b=input.read();if(b<0||headers.size()>=16384)throw new java.io.IOException("Bounded header");headers.write(b);matched=b==delimiter[matched]?matched+1:b=='\r'?1:0;}
          String text=headers.toString(StandardCharsets.US_ASCII);
          var length=java.util.regex.Pattern.compile("(?im)^Content-Length: ([0-9]+)\\r?$").matcher(text);
          if(!length.find())throw new IllegalStateException("Fixture length missing");int size=Integer.parseInt(length.group(1));if(size<1||size>DeliveryWire.MAX_ENVELOPE)throw new IllegalStateException("Fixture size");
          byte[] bytes=input.readNBytes(size);if(bytes.length!=size)throw new java.io.IOException("Partial fixture body");
          var envelope=DeliveryWire.envelope(bytes);
          if(!text.startsWith("PUT "+DeliveryWire.PATH+envelope.message()+" HTTP/1.1\r\n"))throw new IllegalStateException("Fixture path mismatch");
          requests.incrementAndGet();reply.send(socket,envelope);
        }catch(java.io.IOException expectedDisconnect){}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}catch(Throwable failure){errors.add(failure);}finally{sockets.remove(socket);}
      });
    }}catch(java.io.IOException closed){if(!server.isClosed())errors.add(closed);}});
  }
  int port(){return destination;}
  static void response(SSLSocket socket,String status,String extra,byte[] body)throws Exception{
    var output=socket.getOutputStream();output.write(("HTTP/1.1 "+status+"\r\nContent-Type: application/json\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n"+extra+"\r\n").getBytes(StandardCharsets.US_ASCII));output.write(body);output.flush();
  }
  @Override public void close()throws Exception{
    server.close();for(var socket:sockets)try{socket.close();}catch(java.io.IOException ignored){}
    pool.shutdownNow();if(!pool.awaitTermination(5,TimeUnit.SECONDS))throw new AssertionError("Owned fixture threads not stopped");
    closed(destination);if(!errors.isEmpty())throw new AssertionError("Bounded fault fixture error",errors.peek());
  }
}
