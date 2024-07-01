// Copyright 2024 Google LLC
// 
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// 
//   https://www.apache.org/licenses/LICENSE-2.0
// 
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.maps.app;

import com.google.maps.list.LinkedList;

import static com.google.maps.utilities.StringUtils.join;
import static com.google.maps.utilities.StringUtils.split;

import org.apache.commons.text.WordUtils;

// Java imports
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Google generic imports
import com.google.auto.value.AutoValue;
import com.google.protobuf.Duration;
import com.google.protobuf.FieldMask;
import com.google.protobuf.TextFormat;
import com.google.type.LatLng;
import com.google.api.gax.rpc.AlreadyExistsException;

// Route Optimization Imports
import com.google.maps.routeoptimization.v1.OptimizeToursRequest;
import com.google.maps.routeoptimization.v1.OptimizeToursRequest.SearchMode;
import com.google.maps.routeoptimization.v1.OptimizeToursResponse;
import com.google.maps.routeoptimization.v1.Shipment;
import com.google.maps.routeoptimization.v1.ShipmentModel;
import com.google.maps.routeoptimization.v1.ShipmentRoute;
import com.google.maps.routeoptimization.v1.ShipmentRoute.Visit;
import com.google.maps.routeoptimization.v1.Vehicle;
import com.google.maps.routeoptimization.v1.Vehicle.Builder;

// Fleet Engine imports
import google.maps.fleetengine.delivery.v1.CreateDeliveryVehicleRequest;
import google.maps.fleetengine.delivery.v1.CreateTaskRequest;
import google.maps.fleetengine.delivery.v1.DeliveryVehicle;
import google.maps.fleetengine.delivery.v1.DeliveryVehicleLocation;
import google.maps.fleetengine.delivery.v1.DeliveryVehicleNavigationStatus;
import google.maps.fleetengine.delivery.v1.DeliveryServiceClient;

import google.maps.fleetengine.delivery.v1.LocationInfo;
import google.maps.fleetengine.delivery.v1.Task;
import google.maps.fleetengine.delivery.v1.UpdateDeliveryVehicleRequest;
import google.maps.fleetengine.delivery.v1.VehicleJourneySegment;
import google.maps.fleetengine.delivery.v1.VehicleStop;
//import google.maps.fleetengine.delivery.v1.Task.Type;

// OAuth Token imports
import com.google.fleetengine.auth.AuthTokenMinter;
import com.google.fleetengine.auth.client.FleetEngineClientSettingsModifier;
import com.google.fleetengine.auth.token.factory.signer.SignerInitializationException;

/**
 * Main application class.
 */
public class App {

  public static final String PROVIDER_ID = "your-provider-id";
  public static final String DELIVERY_SERVER_SERVICE_ACCOUNT = "delivery@your-provider-id.iam.gserviceaccount.com";
  public static final String FLEET_ENGINE_ADDRESS = "fleetengine.googleapis.com:443";
  // Audience of the JWT token.
  // The address is the same as the FLEET_ENGINE_ADDRESS without the port number.
  public static final String FLEET_ENGINE_AUDIENCE = "https://fleetengine.googleapis.com/";

  private static final Task.Type LMFS_PICKUP_TASK_TYPE = Task.Type.SCHEDULED_STOP; // use this for pickups, as there is pricing attached to pickup types! Note: check latest terms!
  private static final Task.Type LMFS_DELIVERY_TASK_TYPE = Task.Type.DELIVERY;

  public static OptimizeToursRequest fleetRoutingRequest;
  public static OptimizeToursResponse planResponse;
  public static AuthTokenMinter minter;
  public static int TIMEOUT_SECONDS = 100;
  public static DeliveryServiceClient client;

  /**
   * Main method.
   *
   * @param args Command line arguments.
   */
  public static void main(String[] args) {
    // LMFS OAuth
    minter = AuthHelper.getAuthToken( DELIVERY_SERVER_SERVICE_ACCOUNT, FLEET_ENGINE_AUDIENCE );
    // LMFS Service client
    client = LmfsHelper.getDeliveryServiceClient( FLEET_ENGINE_ADDRESS, minter );

    System.out.println( "\n*** Use Case 1 - STARTED! ***\n");
    UC1_InitialPlanning( "UC1_InitialPlanning.textproto" );
    System.out.println( "\n*** Use Case 1 - DONE! ***\n");

    System.out.println( "\n*** Use Case 2 - STARTED! ***\n");
    UC2_Reoptimization("UC2_Reoptimization.textproto");
    System.out.println( "\n*** Use Case 2 - DONE! ***\n");

    System.out.println( "\n*** Use Case 3 - STARTED! ***\n");
    UC3_NewStop("UC3_NewStop.textproto");
    System.out.println( "\n*** Use Case 3 - DONE! ***\n");
  }

  /**
   * Use Case 1: Initial Planning.
   *
   * @param modelPath Path to the model file.
   */
  public static void UC1_InitialPlanning( String modelPath )
  {
    try
    {
      // Route Optimization
      // For easy testing in development environment, this example uses a text format protobuf message
      String projectParent = "projects/" + PROVIDER_ID;
      fleetRoutingRequest = RouteOptimizationHelper.buildFleetRoutingRequest(projectParent, modelPath);
      planResponse = RouteOptimizationHelper.callCloudFleetRouting(fleetRoutingRequest);
      //System.out.println( planResponse.toString() ) ;
      createRoutes();
    }
    catch( Exception ex )
    {
      System.out.println("\nEXCEPTION:");
      System.out.println(ex);
    }
  }

  /**
   * Use Case 2: Re-optimization.
   *
   * @param modelPath Path to the model file.
   */
  public static void UC2_Reoptimization( String modelPath )
  {
    try
    {
      // First run initial setup using UC1 code with UC2 data
      UC1_InitialPlanning( modelPath );

      //System.out.println( planResponse.toString() ) ;

      ShipmentModel shipmentModel = fleetRoutingRequest.getModel();

      String projectParent = "projects/" + PROVIDER_ID;
      //ShipmentModel.Builder modelBuilder = ShipmentModel.newBuilder().mergeFrom(shipmentModel);

      /*
      // Let's update the delivery vehicles locations for this use case to make sense.
      // This loop just updates all vehicles to a new start location chosen by you
      List<Vehicle> vehicles = shipmentModel.getVehiclesList();

      for (int i=0; i< vehicles.size(); i++) {

        String id = vehicles.get(i).getLabel();
        DeliveryVehicle dv = LmfsHelper.getDeliveryVehicle(client, PROVIDER_ID, id);
        if( dv != null )
        {
          LatLng latLng = LatLng.newBuilder().setLatitude(60.169455).setLongitude(24.940909).build();
          dv = LmfsHelper.updateDeliveryVehicleLocation( client, PROVIDER_ID, id, latLng );
          // Merge existing vehicle with new location data using protobuf builders
          Vehicle v = Vehicle.newBuilder().mergeFrom(vehicles.get(i)).setStartLocation( latLng ).build();
          // set Route Optimization model to updated vehicle with new start location
          modelBuilder.setVehicles( i, v );
        }
      }

      shipmentModel = modelBuilder.build();
      //System.out.println("\nshipmentModel: \n" + shipmentModel.toString() );
      */
      // get routes from initial plan that we just ran as first step of UC2
      List<ShipmentRoute> routes = planResponse.getRoutesList();

      // Now the initial setup for use case is done in Fleet Engine and Route Optimization shipmentModel
      // Build new request to Re-optimize with Route Optimization
      OptimizeToursRequest.Builder requestBuilder =
          OptimizeToursRequest.newBuilder()
              .setModel( shipmentModel )
              .setTimeout(Duration.newBuilder().setSeconds(TIMEOUT_SECONDS).build())
              .setParent(projectParent)
              .setSearchMode( SearchMode.CONSUME_ALL_AVAILABLE_TIME)
              .addAllInjectedFirstSolutionRoutes(routes);

      fleetRoutingRequest = requestBuilder.build();

      System.out.println("\n Re-optimize request\n");

      planResponse = RouteOptimizationHelper.callCloudFleetRouting(fleetRoutingRequest);
      /*
      System.out.println("\n Re-optimize response\n");
      System.out.println(planResponse.toString() );
      */

      createRoutes();
    }
    catch( Exception ex )
    {
      System.out.println("\nEXCEPTION:");
      System.out.println(ex);
    }
  }

  /**
   * Use Case 3: New Stop.
   *
   * @param modelPath Path to the model file.
   */
  public static void UC3_NewStop( String modelPath )
  {
    try
    {
      UC1_InitialPlanning( modelPath );

      //System.out.println( planResponse.toString() ) ;
      ShipmentModel shipmentModel = fleetRoutingRequest.getModel();

      String projectParent = "projects/" + PROVIDER_ID;
      ShipmentModel.Builder modelBuilder = ShipmentModel.newBuilder().mergeFrom(shipmentModel);
        //60.19181950808375, 25.025756338117166
      LatLng pickupPoint = LatLng.newBuilder().setLatitude(60.191819).setLongitude(25.025756).build();
      // 60.17787246025874, 24.812258567690137
      LatLng deliveryPoint = LatLng.newBuilder().setLatitude(60.177872).setLongitude(24.812258).build();
      modelBuilder.addShipments( RouteOptimizationHelper.createNewShipment( pickupPoint, deliveryPoint, 123, 123 ) );
      shipmentModel = modelBuilder.build();

      //System.out.println( shipmentModel.toString() ) ;

      OptimizeToursRequest.Builder requestBuilder =
          OptimizeToursRequest.newBuilder()
              .setModel( shipmentModel )
              .setTimeout(Duration.newBuilder().setSeconds(TIMEOUT_SECONDS).build())
              .setParent(projectParent);

      fleetRoutingRequest = requestBuilder.build();
      System.out.println("\n Creating new plan with added shipment \n");
      planResponse = RouteOptimizationHelper.callCloudFleetRouting(fleetRoutingRequest);
      System.out.println("\n Creating routes in Fleet Engine \n");
      createRoutes();
    }
    catch( Exception ex )
    {
      System.out.println("\nEXCEPTION:");
      System.out.println(ex);
    }
  }

  /**
   * Creates routes in Fleet Engine based on the optimized plan.
   */
  public static void createRoutes( )
  {
    try
    {
      List<ShipmentRoute> routes = planResponse.getRoutesList();

      ShipmentModel shipmentModel = fleetRoutingRequest.getModel();
      List<Vehicle> vehicles = shipmentModel.getVehiclesList();

      for (int i = 0; i < routes.size(); i++ ) {

        if( routes.get(i).getVehicleLabel().equals( vehicles.get(i).getLabel() ) )
        {
          int numberOfVisits = routes.get(i).getVisitsCount();
          if( numberOfVisits > 0 )
          {
            DeliveryVehicle dv = createDeliveryVehicle( PROVIDER_ID, minter, vehicles.get(i).getLabel(), vehicles.get(i).getStartLocation() );
            createLmfsRoute( shipmentModel, dv, PROVIDER_ID, minter, vehicles.get(i), routes.get(i) );
          }
          else
          {
            System.out.println( "\nThere are no visits for vehicle:'" + vehicles.get(i).getLabel() + "' \n");
          }
        }
      }
    }
    catch ( Exception ex )
    {
      System.out.println( "Error creating routes: " + ex );
    }
  }

  /**
   * Strips the full path from an ID.
   *
   * @param fullpath The full path to strip.
   * @return The ID without the full path.
   */
  public static String stripFullPathFromId( String fullpath )
  {
    return fullpath.substring(fullpath.lastIndexOf("/")+1 );
  }

  /**
   * Creates a route in Fleet Engine for a given vehicle.
   *
   * @param model The shipment model.
   * @param responseDeliveryVehicle The delivery vehicle.
   * @param provider_id The provider ID.
   * @param minter The auth token minter.
   * @param vehicle The vehicle.
   * @param route The shipment route.
   */
  public static void createLmfsRoute( ShipmentModel model, DeliveryVehicle responseDeliveryVehicle, String provider_id, AuthTokenMinter minter, Vehicle vehicle, ShipmentRoute route )
  {
    try
    {
      ArrayList<String> taskIds = new ArrayList<String>();

      String taskId = UUID.randomUUID().toString();
      taskIds.add( taskId );
      CreateTaskRequest createTaskRequest = buildTask( provider_id, taskId, vehicle.getStartLocation(), 0, Task.Type.SCHEDULED_STOP, null );
      Task startTask = client.createTask(createTaskRequest);
      //System.out.println("\nTASK: \n" + startTask.toString() );

      List<Visit> visits = route.getVisitsList();
      ArrayList<Task> tasks = new ArrayList<Task>();
      tasks.add( startTask );
      for (int i = 0; i < visits.size(); i++ ) {
        //System.out.println("\n Visit: " + visits.get(i).toString() );
        Task.Type type = Task.Type.SCHEDULED_STOP;

        UUID trackingId = UUID.randomUUID(); // THIS VALUE SHOULD COME FROM YOUR LOGISTICS SYSTEM / SHIPMENT TRACKING SERVICE
        if( !visits.get(i).getIsPickup() )
          type = LMFS_DELIVERY_TASK_TYPE;

        taskId = UUID.randomUUID().toString();
        taskIds.add( taskId );
        LatLng location = RouteOptimizationHelper.getVisitLocation( model, visits.get(i).getVisitLabel() );
        createTaskRequest = buildTask( provider_id, taskId, location, visits.get(i).getDetour().getSeconds(), type, trackingId );
        Task visitTask = client.createTask(createTaskRequest);
        //System.out.println("\nTASK: \n" + visitTask.toString() );
        tasks.add( visitTask );
      }

      taskId = UUID.randomUUID().toString();
      taskIds.add( taskId );
      createTaskRequest = buildTask( provider_id, taskId, vehicle.getEndLocation(), 0, Task.Type.SCHEDULED_STOP, null );
      Task endTask = client.createTask(createTaskRequest);
      //System.out.println("\nTASK: \n" + endTask.toString() );
      tasks.add( endTask );

      // Update remaining journey segments
      updateSegments( tasks, model, client, responseDeliveryVehicle );

      System.out.println("\nVehicle assigned:\n" + responseDeliveryVehicle.getName() );

    }
    catch( Exception ex )
    {
      System.out.println("\nEXCEPTION:");
      System.out.println(ex);
    }
  }

  /**
   * Updates the journey segments for a vehicle.
   *
   * @param tasks The list of tasks.
   * @param model The shipment model.
   * @param client The delivery service client.
   * @param responseDeliveryVehicle The delivery vehicle.
   */
  public static void updateSegments(ArrayList<Task> tasks, ShipmentModel model, DeliveryServiceClient client, DeliveryVehicle responseDeliveryVehicle)
  {
    DeliveryVehicle updatedResponseDeliveryVehicle = null;
    try
    {
      ArrayList<VehicleJourneySegment> vehicleJourneySegments = new ArrayList<>();

      for (int j = 0; j < tasks.size() ; j++ ) {
        LocationInfo locationInfo = tasks.get(j).getPlannedLocation(); // LocationInfo.newBuilder().setPoint( RouteOptimizationHelper.getLocationInfo( model, , isDelivery ) ).build();

        vehicleJourneySegments.add(
          createVehicleJourneySegment(
            tasks.get(j).getName(),
            locationInfo,
            tasks.get(j).getTaskDuration().getSeconds()
          )
        );
      }

      DeliveryVehicle.Builder vehicleBuilder = responseDeliveryVehicle.toBuilder();
      vehicleBuilder.addAllRemainingVehicleJourneySegments( vehicleJourneySegments);

      UpdateDeliveryVehicleRequest updateRequest =
        UpdateDeliveryVehicleRequest.newBuilder()
        .setDeliveryVehicle(vehicleBuilder)
        .setUpdateMask(FieldMask.newBuilder().addPaths("remaining_vehicle_journey_segments"))
        .build();
        //System.out.printf( "\nUPDATE REQUEST\n" + updateRequest.toString() );
      updatedResponseDeliveryVehicle = client.updateDeliveryVehicle(updateRequest);
    }
    catch( Exception ex )
    {
      System.out.printf( "\nAdding journey segments failed: \n" + ex );
    }
  }

  /**
   * Builds a task request.
   *
   * @param provider_id The provider ID.
   * @param taskId The task ID.
   * @param startLocation The start location.
   * @param durationSeconds The duration in seconds.
   * @param type The task type.
   * @param trackingId The tracking ID.
   * @return The task request.
   */
  public static CreateTaskRequest buildTask( String provider_id, String taskId, LatLng startLocation, long durationSeconds, Task.Type type, UUID trackingId )
  {
    Task task = null;

    if( type == LMFS_DELIVERY_TASK_TYPE )
      task = Task.newBuilder()
        .setType( type )
        .setState( Task.State.OPEN )
        .setTaskDuration( Duration.newBuilder().setSeconds( durationSeconds ) )
        .setPlannedLocation( LocationInfo.newBuilder().setPoint( startLocation ) )
        .setTrackingId( trackingId.toString() )
        .build();
    else
      task = Task.newBuilder()
        .setType( type )
        .setState( Task.State.OPEN )
        .setTaskDuration( Duration.newBuilder().setSeconds( durationSeconds ) )
        .setPlannedLocation( LocationInfo.newBuilder().setPoint( startLocation ) )
        .build();

    CreateTaskRequest createTaskRequest = CreateTaskRequest.newBuilder()
      .setTaskId( taskId )
      .setParent(String.format("providers/%s", provider_id))
      .setTask( task )
      .build();

    return createTaskRequest;
  }

  /**
   * Creates a delivery vehicle.
   *
   * @param provider_id The provider ID.
   * @param minter The auth token minter.
   * @param vehicleId The vehicle ID.
   * @param lastLocation The last location.
   * @return The delivery vehicle.
   * @throws SignerInitializationException If there is a problem initializing the signer.
   * @throws IOException If there is a problem with I/O.
   */
  public static DeliveryVehicle createDeliveryVehicle( String provider_id, AuthTokenMinter minter, String vehicleId, LatLng lastLocation ) throws SignerInitializationException, IOException {

    // Construct the delivery vehicle object
    DeliveryVehicle deliveryVehicle =
        DeliveryVehicle.newBuilder()
            // Set the delivery vehicle name to the specified format
            .setName( String.format( "providers/%s/deliveryVehicles/%s", provider_id, vehicleId))
            // Set the last vehicle location to a hardcoded value
            .setLastLocation( DeliveryVehicleLocation.newBuilder().setLocation(lastLocation).build())
            // Set the navigation status to unknown
            .setNavigationStatus(DeliveryVehicleNavigationStatus.UNKNOWN_NAVIGATION_STATUS)
            .build();

    CreateDeliveryVehicleRequest createDeliveryVehicleRequest =
        CreateDeliveryVehicleRequest.newBuilder()
            // Set the randomly generated delivery vehicle id
            .setDeliveryVehicleId(vehicleId)
            // Set the delivery vehicle to the one constructed above
            .setDeliveryVehicle(deliveryVehicle)
            // Set the parent to the specified format
            .setParent(String.format("providers/%s", provider_id))
            .build();

    try {
      deliveryVehicle = client.createDeliveryVehicle(createDeliveryVehicleRequest);
      System.out.printf("\nDelivery Vehicle with name '%s' created\n", deliveryVehicle.getName() + "\n" );
    }
    catch( AlreadyExistsException ex )
    {
      System.out.println( "This vehicle already exists! " + vehicleId );
    }

    return deliveryVehicle;
  }

  /**
   * Creates a vehicle journey segment.
   *
   * @param taskId The task ID.
   * @param location The location.
   * @param seconds The duration in seconds.
   * @return The vehicle journey segment.
   */
  public static final VehicleJourneySegment createVehicleJourneySegment(String taskId, LocationInfo location, long seconds ) {

      VehicleStop.Builder stopBuilder =
          VehicleStop.newBuilder()
              .setPlannedLocation( location )
              .setState(VehicleStop.State.NEW);

          stopBuilder.addTasks(
              VehicleStop.TaskInfo.newBuilder()
                  .setTaskId( stripFullPathFromId( taskId ) )
                  .setTaskDuration( Duration.newBuilder().setSeconds(seconds).build() )
                  .build() );

    return VehicleJourneySegment.newBuilder().setStop(stopBuilder).build();
  }
}