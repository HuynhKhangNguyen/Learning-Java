package lab.backend.learning.contentreplica;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PublicationWireTests {
  private static final UUID OP=UUID.fromString("10000000-0000-4000-8000-000000000001");
  private byte[] valid(){return ("{\"contractVersion\":1,\"operationId\":\""+OP+"\",\"phase\":\"STAGE\",\"parentRevision\":1,\"targetRevision\":2,\"bundleId\":\"fx05-p2\",\"bundleSha256\":\""+"a".repeat(64)+"\",\"manifestSha256\":\""+"b".repeat(64)+"\",\"realmId\":\"20000000-0000-4000-8000-000000000001\",\"producerGeneration\":\"30000000-0000-4000-8000-000000000001\",\"participantGeneration\":\"40000000-0000-4000-8000-000000000001\",\"deliverySequence\":1}").getBytes(StandardCharsets.UTF_8);}
  @Test void acceptsExactBoundCommandWithoutMutatingInput(){byte[] input=valid(),copy=input.clone();var c=PublicationWire.command(input,OP,"STAGE");assertEquals(2,c.targetRevision());assertArrayEquals(copy,input);}
  @Test void rejectsUnknownDuplicateUnsafeAndPathMismatch(){String json=new String(valid(),StandardCharsets.UTF_8);assertThrows(IllegalArgumentException.class,()->PublicationWire.command(json.replace("\"deliverySequence\":1","\"deliverySequence\":1,\"state\":\"RELEASED\"").getBytes(StandardCharsets.UTF_8),OP,"STAGE"));assertThrows(IllegalArgumentException.class,()->PublicationWire.command(json.replace("\"contractVersion\":1","\"contractVersion\":1,\"contractVersion\":1").getBytes(StandardCharsets.UTF_8),OP,"STAGE"));assertThrows(IllegalArgumentException.class,()->PublicationWire.command(json.replace("\"deliverySequence\":1","\"deliverySequence\":9007199254740992").getBytes(StandardCharsets.UTF_8),OP,"STAGE"));assertThrows(IllegalArgumentException.class,()->PublicationWire.command(valid(),OP,"FENCE"));}
  @Test void executableSchemasMatchStrictJavaAndRejectPrivateFields()throws Exception{
    Path contract=Path.of("../../../contracts/http/learning/internal/publication").toRealPath(),examples=contract.resolve("examples");
    // Dùng AJV đã khóa trong frontend; test không tải dependency hoặc tự tạo schema engine khác.
    assertSchema(contract.resolve("phase-v1.schema.json"),List.of(examples.resolve("phase-valid.json"),examples.resolve("phase-invalid-unknown.json")),List.of(true,false));
    assertSchema(contract.resolve("receipt-v1.schema.json"),List.of(examples.resolve("receipt-valid.json"),examples.resolve("receipt-invalid-private.json")),List.of(true,false));
    assertSchema(contract.resolve("status-v1.schema.json"),List.of(examples.resolve("receipt-valid.json"),examples.resolve("receipt-invalid-private.json")),List.of(true,false));
    byte[] accepted=Files.readAllBytes(examples.resolve("phase-valid.json")),rejected=Files.readAllBytes(examples.resolve("phase-invalid-unknown.json"));
    assertDoesNotThrow(()->PublicationWire.command(accepted,OP,"STAGE"));assertThrows(IllegalArgumentException.class,()->PublicationWire.command(rejected,OP,"STAGE"));
  }
  private static void assertSchema(Path schema,List<Path> candidates,List<Boolean> expected)throws Exception{var command=new java.util.ArrayList<String>();command.add("node");command.add(Path.of("../../../scripts/publication/schema-parity.mjs").toRealPath().toString());command.add(schema.toString());candidates.forEach(path->command.add(path.toString()));Process process=new ProcessBuilder(command).redirectErrorStream(true).start();assertTrue(process.waitFor(15,TimeUnit.SECONDS));String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);assertEquals(0,process.exitValue(),output);var verdicts=PublicationWire.JSON.readTree(output);assertEquals(expected.size(),verdicts.size());for(int i=0;i<expected.size();i++)assertEquals(expected.get(i),verdicts.get(i).asBoolean(),schema.getFileName()+" case "+i);}
}
