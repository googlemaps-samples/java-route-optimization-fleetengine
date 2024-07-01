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

import com.google.fleetengine.auth.AuthTokenMinter;
import com.google.fleetengine.auth.token.factory.FleetEngineTokenFactory;
import com.google.fleetengine.auth.token.factory.FleetEngineTokenFactorySettings;
import com.google.fleetengine.auth.token.factory.signer.ImpersonatedSigner;
import com.google.fleetengine.auth.token.factory.signer.SignerInitializationException;

/**
 * Helper class for managing authentication tokens.
 */
public class AuthHelper
{

  /**
   * Gets an authentication token.
   *
   * @param serverTokenAccount The server token account.
   * @param fleetEngineAudience The Fleet Engine audience.
   * @return The authentication token minter.
   */
  public static AuthTokenMinter getAuthToken( String serverTokenAccount, String fleetEngineAudience )
  {
    // LMFS OAuth
    try
    {
      AuthTokenMinter minter = AuthTokenMinter.deliveryBuilder()
        .setDeliveryServerSigner( ImpersonatedSigner.create(serverTokenAccount))
        .setTokenFactory(
              new FleetEngineTokenFactory(
                  FleetEngineTokenFactorySettings.builder()
                      .setAudience(fleetEngineAudience)
                      .build()))
        .build();

      return minter;
    }
    catch( SignerInitializationException ex )
    {
      System.out.println( "\nSignerInitializationException! Problem creating a minter: " + ex.toString() );
      return null;
    }
  }
}