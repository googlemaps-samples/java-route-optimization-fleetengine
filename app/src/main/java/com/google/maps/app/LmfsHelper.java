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

import java.io.IOException;
import java.util.ArrayList;

import com.google.protobuf.FieldMask;
import com.google.protobuf.Timestamp;
import com.google.type.LatLng;

import google.maps.fleetengine.delivery.v1.DeliveryServiceClient;
import google.maps.fleetengine.delivery.v1.DeliveryServiceClient.ListDeliveryVehiclesPagedResponse;
import google.maps.fleetengine.delivery.v1.DeliveryServiceSettings;
import google.maps.fleetengine.delivery.v1.DeliveryVehicle;
import google.maps.fleetengine.delivery.v1.DeliveryVehicleLocation;
import google.maps.fleetengine.delivery.v1.DeliveryVehicleNavigationStatus;
import google.maps.fleetengine.delivery.v1.GetDeliveryVehicleRequest;
import google.maps.fleetengine.delivery.v1.GetTaskRequest;
import google.maps.fleetengine.delivery.v1.Task;
import google.maps.fleetengine.delivery.v1.UpdateDeliveryVehicleRequest;

import com.google.fleetengine.auth.AuthTokenMinter;
import com.google.fleetengine.auth.client.FleetEngineClientSettingsModifier;

/**
 * Helper class for interacting with the LMFS Fleet Engine.
 */
public class LmfsHelper
{

  /**
   * Gets the delivery service client.
   *
   * @param address The address of the Fleet Engine.
   * @param minter The auth token minter.
   * @return The delivery service client.
   */
  public static DeliveryServiceClient getDeliveryServiceClient( String address, AuthTokenMinter minter )
  {
    try
    {
      DeliveryServiceSettings settings =
          new FleetEngineClientSettingsModifier<DeliveryServiceSettings, DeliveryServiceSettings.Builder>(minter)
              .updateBuilder(DeliveryServiceSettings.newBuilder())
              .setEndpoint( address )
              .build();
      return DeliveryServiceClient.create(settings);
    }
    catch( IOException ex )
    {
      System.out.println( "\nERROR! Cannot create DeliveryServiceClient: \n" + ex );
      return null;
    }
  }

  /**
   * Gets a delivery vehicle by ID.
   *
   * @param client The delivery service client.
   * @param provider_id The provider ID.
   * @param vehicleId The vehicle ID.
   * @return The delivery vehicle.
   */
  public static DeliveryVehicle getDeliveryVehicle( DeliveryServiceClient client, String provider_id, String vehicleId )
  {
    DeliveryVehicle vehicle = null;
    try
    {
      GetDeliveryVehicleRequest getRequest = GetDeliveryVehicleRequest.newBuilder()
        .setName(String.format( "providers/%s/deliveryVehicles/%s", provider_id, vehicleId) )
        .build();

      vehicle = client.getDeliveryVehicle(getRequest);

    }
    catch( com.google.api.gax.rpc.NotFoundException ex )
    {
      System.out.println("Vehicle does not exist: " + vehicleId );
    }

    return vehicle;
  }

  /**
   * Updates the location of a delivery vehicle.
   *
   * @param client The delivery service client.
   * @param provider_id The provider ID.
   * @param vehicleId The vehicle ID.
   * @param newLocation The new location.
   * @return The updated delivery vehicle.
   */
  public static DeliveryVehicle updateDeliveryVehicleLocation(DeliveryServiceClient client, String provider_id, String vehicleId, LatLng newLocation )
  {
    long millis = System.currentTimeMillis();

    Timestamp timestamp = Timestamp.newBuilder().setSeconds(millis / 1000)
    .setNanos((int) ((millis % 1000) * 1000000)).build();

    DeliveryVehicle deliveryVehicle =
        DeliveryVehicle.newBuilder()
            // Set the delivery vehicle name to the specified format
            .setName( String.format( "providers/%s/deliveryVehicles/%s", provider_id, vehicleId))
            // Set the last vehicle location to a hardcoded value
            .setLastLocation( DeliveryVehicleLocation.newBuilder()
              .setLocation(newLocation)
              .setUpdateTime( timestamp )
              .build())
            // Set the navigation status to unknown
            .setNavigationStatus(DeliveryVehicleNavigationStatus.UNKNOWN_NAVIGATION_STATUS)
            .build();

    UpdateDeliveryVehicleRequest updateRequest =
      UpdateDeliveryVehicleRequest.newBuilder()
      .setDeliveryVehicle(deliveryVehicle)
      .setUpdateMask(FieldMask.newBuilder().addPaths("last_location"))
      .build();

    DeliveryVehicle updatedVehicle = client.updateDeliveryVehicle(updateRequest);
    //System.out.println( "\nUpdatedVehicle location: " + updatedVehicle.getLastLocation().toString() );
    return updatedVehicle;
  }

  // THIS IS NOT NEEDED, JUST FOR DEBUGGING
  /**
   * Checks the tasks.
   *
   * @param client The delivery service client.
   * @param tasks The list of tasks.
   */
  public static void checkTasks( DeliveryServiceClient client, ArrayList<Task> tasks )
  {
    for (int j = 0; j < tasks.size() ; j++ ) {
      GetTaskRequest getTaskRequest =
        GetTaskRequest.newBuilder()
          .setName( tasks.get(j).getName() )
          .build();

        Task task = client.getTask( getTaskRequest );
        System.out.println("\nTASK found: \n" + task.getName() );
    }
  }
}
