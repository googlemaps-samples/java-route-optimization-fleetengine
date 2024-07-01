# Java Fleet Engine & Route Optimization Example

This repository provides a Java example demonstrating how to use the Google Maps Fleet Engine and Route Optimization APIs to optimize routes and manage vehicle tasks. 

## Product documentation

Learn more about Route Optimization by accessing our developer's [website](https://developers.google.com/maps/documentation/route-optimization).

## Setup

1. **Prerequisites:**
   - Java Development Kit (JDK) 11 or higher
   - Maven or Gradle build tool
   - Google Cloud Platform project with billing enabled
   - Ensure gcloud CLI is installed (including beta commands): https://cloud.google.com/sdk/docs/install
   - Run gcloud auth login, connecting your Google Account.
   - Run gcloud auth application-default login, allowing access to your Google Account
   - Run gcloud config set project <project_name> replacing <project_name> by your actual project name in Google Cloud.

2. **Enable APIs:**
   - Enable the Google Maps Fleet Engine API and the Google Maps Route Optimization API in your Google Cloud Platform project.
   - [Fleet Engine API](https://console.cloud.google.com/apis/library/fleetengine.googleapis.com)
   - [Route Optimization API](https://console.cloud.google.com/apis/library/maps.googleapis.com/maps.routeoptimization.v1)

3. **Set up Authentication:**
   - Create a service account in your Google Cloud Platform project and download the JSON key file.
    - Set the `PROVIDER_ID` variable in `App.java` pointing to the right Google Cloud project id which has Fleet Engine enabled.
    - Set the `DELIVERY_SERVER_SERVICE_ACCOUNT` variable in `App.java` to the email address of your service account that has the right IAM role to modify Fleet Engine. This service account should exist in the same project informed in the provider id above.
   - **Note:** For production deployments, consider using environment variables or a secure configuration mechanism to manage sensitive credentials.

4. **Create a Route Optimization Model:**
   - Create a protobuf file representing your shipment model. The `sync_request.textproto` file provides an example.
   - Ensure the model file includes:
     - **Shipments:** Define pickup and delivery locations, time windows, and load demands.
     - **Vehicles:** Define vehicle start and end locations, and load limits.
   - Replace the example data in the `sync_request.textproto` file with your own data.

5. **Configure Project ID:**
   - Set the `PROVIDER_ID` variable in `App.java` to your Google Cloud Platform project ID.

## Running the Example

1. **Build the Project:**
   - Use Maven or Gradle to build the project.
   - **Maven:** `mvn clean package`
   - **Gradle:** `gradle build`

2. **Run the App:**
   - Run the `App` class.
   - **Maven:** `mvn exec:java -Dexec.mainClass="com.google.maps.app.App"`
   - **Gradle:** `gradle run`

## Code Structure

The code is organized into several helper classes:

**1. App.java:**
   - Contains the main program logic, including three use cases:
     - `UC1_InitialPlanning`: Creates an initial plan based on a model file. Exemplifies the translation of a Route Optimization (RO) response to Fleet Engine (FE). At the beginning of the day, when the operations team uses Route Optimization and wants to sync the data with Fleet Engine.
     - `UC2_Reoptimization`: Re-optimizes the plan based on updated vehicle locations. Exemplifies intraday reoptimization. Data is retrieved from Fleet Engine to be sent to Route Optimization for reoptimization as an input scenario. After that the data needs to be put back into Fleet Engine. This example covers FE to RO, RO to FE.
     - `UC3_NewStop`: Adds a new stop to the plan. Exemplifies intraday reoptimization for a new shipment that needs to be allocated. This calls Route Optimization, cleans all vehicle routes in Fleet Engine and sends the new routes to these existing DeliveryVehicles in Fleet Engine. It is important to note that in real scenarios a sophisticated allocation would be required.

**2. AuthHelper.java:**
   - Provides a method `getAuthToken` to retrieve an authentication token for the Fleet Engine API.

**3. RouteOptimizationHelper.java:**
   - Provides Route Optimization methods for:
     - `buildFleetRoutingRequest`: Builds a request to the Route Optimization API.
     - `callCloudFleetRouting`: Calls the Route Optimization API and processes the response.
     - `getVisitLocation`: Retrieves the location of a visit.
     - `createNewShipment`: Creates a new shipment object.
     - `getVehicleStartEndLocation`: Retrieves the start or end location of a vehicle.

**4. LmfsHelper.java:**
   - Provides Fleet Engine methods for:
     - `getDeliveryServiceClient`: Creates a client for interacting with the Fleet Engine API.
     - `getDeliveryVehicle`: Retrieves a delivery vehicle by ID.
     - `updateDeliveryVehicleLocation`: Updates the location of a delivery vehicle.

**5. sync_request.textproto:**
   - An example protobuf file representing a shipment model.

## Usage

The `App.java` class demonstrates how to use the helper classes to:

1. Create a plan using the Route Optimization API.
2. Re-optimize the plan based on updated vehicle locations.
3. Add a new stop to the plan.
4. Create tasks and assign them to vehicles in Fleet Engine.

The `sync_request.textproto` file provides an example of a Route Optimization model. Modify the data in this file to create your own optimization requests.

## Errors linking tasks with journey segments

Note that in creating tasks and searching you give a full path:
providers/your-project-id/tasks/62ebd49b-0806-4885-9442-2eb463e868ec

but in setting the journey segments to set only the id:
62ebd49b-0806-4885-9442-2eb463e868ec

com.google.api.gax.rpc.FailedPreconditionException: io.grpc.StatusRuntimeException: FAILED_PRECONDITION: Tasks assigned to vehicle stops must already exist: No entity with id providers/your-project-id/tasks/9e5c6daf-30b9-4741-a8a4-d9eee097ff07 exists

## Re-optimization using injected_first_solution_routes

Do not modify ShipmentModel if you are running the previous plan injected!

EXCEPTION:
com.google.api.gax.rpc.InvalidArgumentException: io.grpc.StatusRuntimeException: INVALID_ARGUMENT: INVALID_REQUEST_AFTER_GETTING_TRAVEL_TIMES: Validation caught 1 error(s). Here is the first one: In `injected_first_solution_routes[0]`: some of the injected solution's transitions contained a travel time or distance that doesn't match the actual travel times we computed (offending value(s): `transition[0].travel_duration_seconds` inconsistent with the duration matrix (622 vs round(967 \* 1) = 967)) (23)

## Contributing

See the [Contributing guide](./CONTRIBUTING.md).

## Terms of Service

This package uses Google Maps Platform services, and any use of Google Maps Platform is subject to the [Terms of Service](https://cloud.google.com/maps-platform/terms).

For clarity, this package, and each underlying component, is not a Google Maps Platform Core Service.

## Support

This package is offered via an open source license. It is not governed by the Google Maps Platform Support [Technical Support Services Guidelines](https://cloud.google.com/maps-platform/terms/tssg), the [SLA](https://cloud.google.com/maps-platform/terms/sla), or the [Deprecation Policy](https://cloud.google.com/maps-platform/terms) (however, any Google Maps Platform services used by the library remain subject to the Google Maps Platform Terms of Service).

This package adheres to [semantic versioning](https://semver.org/) to indicate when backwards-incompatible changes are introduced. Accordingly, while the library is in version 0.x, backwards-incompatible changes may be introduced at any time. 

If you find a bug, or have a feature request, please [file an issue](https://github.com/googlemaps-samples/java-route-optimization-fleetengine/issues) on GitHub. If you would like to get answers to technical questions from other Google Maps Platform developers, ask through one of our [developer community channels](https://developers.google.com/maps/developer-community). If you'd like to contribute, please check the [Contributing guide](https://github.com/googlemaps-samples/java-route-optimization-fleetengine/blob/main/CONTRIBUTING.md).
