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

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

import com.google.auto.value.AutoValue;
import com.google.protobuf.Duration;
import com.google.protobuf.FieldMask;
import com.google.protobuf.TextFormat;
import com.google.protobuf.Timestamp;
import com.google.type.LatLng;

import com.google.maps.routeoptimization.v1.RouteOptimizationClient;
import com.google.maps.routeoptimization.v1.OptimizeToursRequest;
import com.google.maps.routeoptimization.v1.OptimizeToursResponse;
import com.google.maps.routeoptimization.v1.Shipment;
import com.google.maps.routeoptimization.v1.Shipment.Load;
import com.google.maps.routeoptimization.v1.Shipment.VisitRequest;
import com.google.maps.routeoptimization.v1.ShipmentModel;
import com.google.maps.routeoptimization.v1.TimeWindow;
import com.google.maps.routeoptimization.v1.Vehicle;

/**
 * Helper class for interacting with the Route Optimization API.
 */
public class RouteOptimizationHelper {

  /**
   * Builds a fleet routing request from a model file.
   *
   * @param projectParent The project parent.
   * @param modelPath The path to the model file.
   * @return The fleet routing request.
   * @throws Exception If there is an error building the request.
   */
  public static OptimizeToursRequest buildFleetRoutingRequest(String projectParent, String modelPath) throws Exception
  {
    int timeoutSeconds = 100;
    InputStream modelInputstream = new FileInputStream(modelPath);
    Reader modelInputStreamReader = new InputStreamReader(modelInputstream);
    OptimizeToursRequest.Builder requestBuilder =
        OptimizeToursRequest.newBuilder()
            .setTimeout(Duration.newBuilder().setSeconds(timeoutSeconds).build())
            .setParent(projectParent);

    TextFormat.getParser().merge(modelInputStreamReader, requestBuilder);

    return requestBuilder.build();
  }

  /**
   * Calls the Cloud Fleet Routing API to optimize tours.
   *
   * @param request The optimize tours request.
   * @return The optimize tours response.
   * @throws Exception If there is an error calling the API.
   */
  public static OptimizeToursResponse callCloudFleetRouting(OptimizeToursRequest request) throws Exception {

    RouteOptimizationClient fleetRoutingClient = RouteOptimizationClient.create();
    OptimizeToursResponse response = fleetRoutingClient.optimizeTours(request);
    fleetRoutingClient.shutdown();

    // Check metrics for unused vehicles or skipped shipments
    int usedVehicles = response.getMetrics().getUsedVehicleCount();
    System.out.println("Used vehicle count: " + usedVehicles );

    int skippedShipments = response.getMetrics().getSkippedMandatoryShipmentCount();
    if( skippedShipments > 0 )
      System.out.println( "\033[0;33m" + "There is a problem with your plan! " + skippedShipments + " shipment(s) skipped." + "\033[0;37m" );

    return response;
  }

  /**
   * Gets the location of a visit.
   *
   * @param model The shipment model.
   * @param label The label of the visit.
   * @return The location of the visit.
   */
  public static LatLng getVisitLocation( ShipmentModel model, String label )
  {
    List<Shipment> shipments = model.getShipmentsList();
    for (int i=0; i< shipments.size(); i++ ) {
      if( shipments.get(i).getDeliveriesList().get(0).getLabel().equals( label ) )
        return shipments.get(i).getDeliveriesList().get(0).getArrivalLocation();

        if( shipments.get(i).getPickupsList().get(0).getLabel().equals( label ) )
          return shipments.get(i).getPickupsList().get(0).getArrivalLocation();
    }

    return null;
  }

  /**
   * Creates a new shipment.
   *
   * @param pickupPoint The pickup point.
   * @param deliveryPoint The delivery point.
   * @param pickupDuration The pickup duration.
   * @param deliveryDuration The delivery duration.
   * @return The new shipment.
   */
  public static Shipment createNewShipment(LatLng pickupPoint, LatLng deliveryPoint, int pickupDuration, int deliveryDuration )
  {
    Timestamp pickupStart = Timestamp.newBuilder().setSeconds(1005).build();
    Timestamp pickupEnd = Timestamp.newBuilder().setSeconds(2005).build();
    Timestamp deliveryStart = Timestamp.newBuilder().setSeconds(3005).build();
    Timestamp deliveryEnd = Timestamp.newBuilder().setSeconds(4005).build();

    VisitRequest pickup = VisitRequest.newBuilder()
      .setArrivalLocation( pickupPoint )
      .setDuration( Duration.newBuilder().setSeconds( pickupDuration ).build() )
      .addTimeWindows( TimeWindow.newBuilder().setStartTime(pickupStart).setEndTime(pickupEnd).build() )
      .build();

    VisitRequest delivery = VisitRequest.newBuilder()
      .setArrivalLocation( deliveryPoint )
      .setDuration( Duration.newBuilder().setSeconds( deliveryDuration ).build() )
      .addTimeWindows( TimeWindow.newBuilder().setStartTime(deliveryStart).setEndTime(deliveryEnd).build() )
      .build();

    Shipment shipment = Shipment.newBuilder()
        .addPickups( pickup )
        .putLoadDemands( "Weight", Load.newBuilder().setAmount(10).build() )
        .addDeliveries( delivery )
        .build();

        return shipment;
  }

/*
  public static LatLng getLocationInfo( ShipmentModel model, int label, Boolean isDelivery )
  {
    LatLng latLng = null;
    List<Shipment> shipments = model.getShipmentsList(); // shipments are an entiy with both X deliveries and Y pickups
    //System.out.println("Shipments in model: " + shipments.size() + " is Delivery:" + isDelivery + " label: " +  );
    for ( int i=0; i < shipments.size(); i++ ) {
      if( shipments.get(i).getLabel().equals(label))
      {
        if(isDelivery )
        {
            Shipment.VisitRequest visit = shipments.get(i).getDeliveriesList().get(0);
            latLng = visit.getArrivalLocation();
            break;
        }
        else
        {
            Shipment.VisitRequest visit  = shipments.get(i).getPickupsList().get(0);
            latLng = visit.getArrivalLocation();
            break;
        }
        return latLng;
      }
    }
    return latLng;
  }
  */

  /**
   * Gets the start or end location of a vehicle.
   *
   * @param model The shipment model.
   * @param vehicleFullName The full name of the vehicle.
   * @param isStart Whether to get the start location.
   * @return The start or end location of the vehicle.
   */
  // Make sure to have a Label in your model data, example label : "vehicle-UC2-1-555965d6-e186-11ec-8fea-0242ac120002"
  public static LatLng getVehicleStartEndLocation( ShipmentModel model, String vehicleFullName, Boolean isStart )
  {
    List<Vehicle> vehicles = model.getVehiclesList();
    for ( int i=0; i < vehicles.size(); i++ ) {
      if( vehicleFullName.contains( vehicles.get(i).getLabel() ) ) //
      {
        LatLng point = null;
        if( isStart )
          point = vehicles.get(i).getStartLocation();
        else
          point = vehicles.get(i).getEndLocation();

        return point;
      }
    }

    return null;
  }
}