package wirelessmesh;

import com.akkaserverless.javasdk.AkkaServerless;
import com.akkaserverless.javasdk.testkit.junit.AkkaServerlessTestkitResource;
import io.grpc.StatusRuntimeException;
import org.junit.ClassRule;
import org.junit.Test;

import wirelessmesh.Wirelessmesh.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IntegrationTest {

    private static final AkkaServerless WIRELESS_MESH = WirelessMeshMain.wirelessMeshService;

    @ClassRule
    public static final AkkaServerlessTestkitResource testkit = new AkkaServerlessTestkitResource(WIRELESS_MESH);

    private final WirelessMeshServiceClient client;

    String accessToken = "accessToken";
    String room = "person-cave";
    String email = "me@you.com";

    public IntegrationTest() {
        this.client = WirelessMeshServiceClient.create(testkit.getGrpcClientSettings(), testkit.getActorSystem());
    }

    @Test
    public void addCustomerLocationTest() throws ExecutionException, InterruptedException {
        String customerLocationId = "customerId1";
        createAndAddCustomerLocation(customerLocationId);
        CustomerLocation location = client.getCustomerLocation(
                GetCustomerLocationCommand.newBuilder()
                        .setCustomerLocationId(customerLocationId)
                        .build()).toCompletableFuture().get();
        System.out.println(location);
        assertEquals(customerLocationId, location.getCustomerLocationId());
        assertTrue(location.getAdded());
    }

    private void createAndActivateDevice(String customerLocationId, String deviceId) throws ExecutionException, InterruptedException {
        client.activateDevice(ActivateDeviceCommand.newBuilder()
                .setCustomerLocationId(customerLocationId)
                .setDeviceId(deviceId)
                .build()).toCompletableFuture().get();
    }

    @Test
    public void activateDevicesTest() throws ExecutionException, InterruptedException {
        String customerLocationId = "customerId2";
        createAndAddCustomerLocation(customerLocationId);

        createAndActivateDevice(customerLocationId, "deviceId1");
        createAndActivateDevice(customerLocationId, "deviceId2");
        createAndActivateDevice(customerLocationId, "deviceId3");

        // Test get.
        GetCustomerLocationCommand command = GetCustomerLocationCommand.newBuilder()
                .setCustomerLocationId(customerLocationId)
                .build();

        CustomerLocation customerLocation = client.getCustomerLocation(command).toCompletableFuture().get();
        assertEquals(customerLocation.getDevicesList().size(), 3);
        assertEquals(customerLocation.getDevices(0).getDeviceId(), "deviceId1");
        assertEquals(customerLocation.getDevices(1).getDeviceId(), "deviceId2");
        assertEquals(customerLocation.getDevices(2).getDeviceId(), "deviceId3");
    }

    @Test
    public void removeCustomerLocationTest() throws ExecutionException, InterruptedException {
        String customerLocationId = "customerId3";
        createAndAddCustomerLocation(customerLocationId);

        client.removeCustomerLocation(RemoveCustomerLocationCommand.newBuilder()
            .setCustomerLocationId(customerLocationId).build()).toCompletableFuture().get();

        try {
            client.getCustomerLocation(GetCustomerLocationCommand.newBuilder().setCustomerLocationId(customerLocationId).build())
                    .toCompletableFuture().get();
        } catch (ExecutionException e) {
            StatusRuntimeException statusRuntimeException = (StatusRuntimeException)e.getCause();
            assertEquals("UNKNOWN: customerLocation does not exist.", statusRuntimeException.getMessage());
        }
    }

    @Test
    public void assignRoomTest() throws ExecutionException, InterruptedException {
        String customerLocationId = "customerId4";
        createAndAddCustomerLocation(customerLocationId);
        createAndActivateDevice(customerLocationId, "deviceId1");
        createAndActivateDevice(customerLocationId, "deviceId2");
        createAndActivateDevice(customerLocationId, "deviceId3");

        client.assignRoom(AssignRoomCommand.newBuilder()
                .setCustomerLocationId(customerLocationId)
                .setDeviceId("deviceId2")
                .setRoom(room)
                .build()).toCompletableFuture().get();

        GetCustomerLocationCommand command = GetCustomerLocationCommand.newBuilder()
                .setCustomerLocationId(customerLocationId)
                .build();

        List<Device> expected = new ArrayList<Device>();
        expected.add(defaultDevice(customerLocationId, "deviceId1"));
        expected.add(defaultDevice(customerLocationId, "deviceId2").toBuilder().setRoom(room).build());
        expected.add(defaultDevice(customerLocationId, "deviceId3"));
        CustomerLocation customerLocation = client.getCustomerLocation(command).toCompletableFuture().get();

        List<Device> sorted = customerLocation.getDevicesList().stream().sorted(Comparator
                .comparing(Device::getDeviceId)).collect(toList());

        assertEquals(sorted, expected);
    }

    @Test
    public void toggleNightlightTest() throws IOException, ExecutionException, InterruptedException {
        String customerLocationId = "customerId5";
        createAndAddCustomerLocation(customerLocationId);
        createAndActivateDevice(customerLocationId, "deviceId1");
        createAndActivateDevice(customerLocationId, "deviceId2");
        createAndActivateDevice(customerLocationId, "deviceId3");

        client.toggleNightlight(ToggleNightlightCommand.newBuilder()
                .setCustomerLocationId(customerLocationId)
                .setDeviceId("deviceId2")
                .build()).toCompletableFuture().get();

        GetCustomerLocationCommand command = GetCustomerLocationCommand.newBuilder()
                .setCustomerLocationId(customerLocationId)
                .build();

        List<Device> expected = new ArrayList<Device>();
        expected.add(defaultDevice(customerLocationId, "deviceId1"));
        expected.add(defaultDevice(customerLocationId, "deviceId2").toBuilder().setNightlightOn(true).build());
        expected.add(defaultDevice(customerLocationId, "deviceId3"));
        CustomerLocation customerLocation = client.getCustomerLocation(command).toCompletableFuture().get();
        List<Device> sorted = customerLocation.getDevicesList().stream().sorted(Comparator.comparing(Device::getDeviceId)).collect(toList());
        assertEquals(sorted, expected);
    }

    private void createAndAddCustomerLocation(String customerLocationId) throws ExecutionException, InterruptedException {
        client.addCustomerLocation(AddCustomerLocationCommand.newBuilder()
                .setCustomerLocationId(customerLocationId)
                .setAccessToken(accessToken)
                .setEmail(email)
                .build())
                .toCompletableFuture().get();
    }

    private Device defaultDevice(String customerLocationId, String deviceId) {
        return Device.newBuilder()
                .setDeviceId(deviceId)
                .setCustomerLocationId(customerLocationId)
                .setActivated(true).setRoom("")
                .setNightlightOn(false)
                .build();
    }
}
