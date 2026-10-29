package lab.backend.learning.identityaccess;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.*;
import lab.backend.learning.transport.*;

/** Test-only fault peer ở đúng named18443 sau khi driver dừng Learning thật.
 * Xác minh CA/caller Identity, nhận exact immutable command rồi trả bytes fault.
 * Không ghi receiver DB/ACK và không là bằng chứng durability của Learning. */
final class CanonicalFenceFaultPeer implements AutoCloseable {
  final SSLServerSocket server;
  final ExecutorService pool=Executors.newFixedThreadPool(2);
  final Set<SSLSocket> sockets=ConcurrentHashMap.newKeySet();
  final Queue<Throwable> errors=new ConcurrentLinkedQueue<>();
  final AtomicInteger accepted=new AtomicInteger();
  final AtomicInteger requests=new AtomicInteger();
  CanonicalFenceFaultPeer(TransportSettings settings,Path directory,String certificate,UUID operation,byte[] exact,byte[] reply)throws Exception {
    if(!Set.of("learning","edge","wrong-ca").contains(certificate))throw new IllegalArgumentException("Fixture certificate invalid");
    CanonicalThreeServiceRuntimeIT.closed(18443);
    server=(SSLServerSocket)TransportPeerClient.tls(settings,directory.resolve(certificate+".p12"),"learning").getServerSocketFactory().createServerSocket();
    try {
      server.setReuseAddress(true);server.setNeedClientAuth(true);server.setEnabledProtocols(new String[]{"TLSv1.3","TLSv1.2"});
      server.bind(new InetSocketAddress("127.0.0.1",18443),2);
    }catch(Exception failure){
      // Constructor lỗi vẫn đóng socket vừa tạo; không để fixture chiếm cổng
      // hoặc giữ pool khi driver chưa nhận được instance để gọi close().
      server.close();pool.shutdownNow();throw failure;
    }
    pool.submit(()->{
      try{while(!server.isClosed()){
        var socket=(SSLSocket)server.accept();
        // Accept loop duy nhất giữ cap2 sockets (kể cả queued); không tạo thread
        // theo request hoặc để TLS/body bị giữ vô hạn khi sender disconnect.
        if(sockets.size()>=2){socket.close();continue;}sockets.add(socket);accepted.incrementAndGet();
        pool.submit(()->{
          try(socket){
            socket.setSoTimeout(6000);socket.startHandshake();
            var client=(java.security.cert.X509Certificate)socket.getSession().getPeerCertificates()[0];
            if(!CertificateIdentity.identity(client,"lab",true).equals("identity"))throw new IllegalStateException("Fixture caller denied");
            var input=socket.getInputStream();var header=new java.io.ByteArrayOutputStream();byte[] delimiter={'\r','\n','\r','\n'};int matched=0;
            while(matched<4){int value=input.read();if(value<0||header.size()>=16384)throw new java.io.IOException("Fixture header bounded");header.write(value);matched=value==delimiter[matched]?matched+1:value=='\r'?1:0;}
            String text=header.toString(StandardCharsets.US_ASCII);
            var length=java.util.regex.Pattern.compile("(?im)^Content-Length: ([0-9]+)\\r?$").matcher(text);
            if(!length.find())throw new IllegalStateException("Fixture length missing");int size=Integer.parseInt(length.group(1));
            if(size!=exact.length||!text.startsWith("PUT "+AdmissionWire.FENCE_PATH+operation+" HTTP/1.1\r\n"))throw new IllegalStateException("Fixture request mismatch");
            if(!Arrays.equals(input.readNBytes(size),exact))throw new IllegalStateException("Fixture immutable bytes mismatch");
            requests.incrementAndGet();var output=socket.getOutputStream();
            output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "+reply.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));output.write(reply);output.flush();
          }catch(java.io.IOException disconnected){/* TLS denial/disconnect là expected negative; không ACK. */}
          catch(Throwable failure){errors.add(failure);}finally{sockets.remove(socket);}
        });
      }}catch(java.io.IOException closed){if(!server.isClosed())errors.add(closed);}
    });
  }
  @Override public void close()throws Exception {
    server.close();for(var socket:sockets)try{socket.close();}catch(java.io.IOException ignored){}
    pool.shutdownNow();if(!pool.awaitTermination(5,TimeUnit.SECONDS))throw new IllegalStateException("Owned fault threads not stopped");
    CanonicalThreeServiceRuntimeIT.closed(18443);if(!errors.isEmpty())throw new AssertionError("Owned fault peer invalid",errors.peek());
  }
}
