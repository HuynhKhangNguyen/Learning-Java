package lab.backend.content.delivery;

import java.util.*;
import org.springframework.core.env.*;
import lab.backend.content.transport.*;

/** FX04 explicit owned-lab fixture driver. No browser API or canonical coordinator. */
public final class DeliveryLabCli {
  public static void main(String[] args){
    try{
      if(args.length<1||args.length>2)throw new IllegalArgumentException();
      var env=new StandardEnvironment();
      var transport=TransportSettings.load(env);var runtime=DeliveryRuntime.load(env,transport);
      String action=args[0];
      switch(action){
        case "enqueue"->{
          if(args.length!=1)throw new IllegalArgumentException();
          var e=DeliveryWire.envelope(System.in.readNBytes(8193));
          if(e.version()!=1||!e.payloadVerified()||!e.producer().equals(DeliveryWire.SERVICE)||!e.realm().toString().equals(transport.realm())||!e.producerGeneration().toString().equals(transport.generation())||!e.participantGeneration().equals(runtime.generations().get(e.consumer()))||!e.type().equals("LAB_APPLY")||!e.phase().equals("APPLY"))throw new IllegalArgumentException();
          runtime.store().enqueue(e.message(),e.operation(),e.correlation(),e.consumer(),e.stream(),e.type(),e.phase(),e.payload(),c->{
            try(var s=c.prepareStatement("INSERT INTO content.transaction_probe VALUES (?,0,'delivery-lab-producer')")){s.setString(1,e.message().toString());s.executeUpdate();}
          });
          System.out.println("PENDING");
        }
        case "dispatch"->{
          if(args.length!=2)throw new IllegalArgumentException();int port=port(args[1]);
          var result=new DeliveryDispatcher(runtime.store()).once(lease->{
            try(var client=new DeliveryPeerClient(transport,lease.envelope().consumer(),port)){return client.send(lease);}
          });
          System.out.println(result.name());
        }
        
        default->throw new IllegalArgumentException();
      }
    }catch(Exception failure){
      // No argument/input/property/exception echo: only a bounded diagnostic.
      System.err.println("DELIVERY_LAB_UNAVAILABLE");System.exit(1);
    }
  }
  static int port(String value){if(!value.matches("[1-9][0-9]{3,4}"))throw new IllegalArgumentException();int p=Integer.parseInt(value);if(p<1024||p>65535||p==8080)throw new IllegalArgumentException();return p;}
  private DeliveryLabCli(){}
}

