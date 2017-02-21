/* 
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 OpenMarket Ltd
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.matrix.androidsdk.rest.client;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.api.ThirdPidApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.BulkLookupParams;
import org.matrix.androidsdk.rest.model.BulkLookupResponse;
import org.matrix.androidsdk.rest.model.PidResponse;
import org.matrix.androidsdk.rest.model.RequestEmailValidationResponse;
import org.matrix.androidsdk.rest.model.RequestPhoneNumberValidationResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class ThirdPidRestClient extends RestClient<ThirdPidApi> {

    /**
     * {@inheritDoc}
     */
    public ThirdPidRestClient(HomeserverConnectionConfig hsConfig) {
        super(hsConfig, ThirdPidApi.class, URI_API_PREFIX_IDENTITY, false, true);
    }

    /**
     * Retrieve user matrix id from a 3rd party id.
     * @param address 3rd party id
     * @param medium the media.
     * @param callback the 3rd party callback
     */
    public void lookup3Pid(String address, String medium, final ApiCallback<String> callback) {
        mApi.lookup3Pid(address, medium, new Callback<PidResponse>() {
            @Override
            public void success(PidResponse pidResponse, Response response) {
                callback.onSuccess((null == pidResponse.mxid) ? "" : pidResponse.mxid);
            }

            @Override
            public void failure(RetrofitError error) {
                callback.onUnexpectedError(error);
            }
        });
    }

    /**
     * Request an email validation token.
     * @param address the email address
     * @param clientSecret the client secret number
     * @param attempt the attempt count
     * @param nextLink the next link.
     * @param callback the callback.
     */
    public void requestEmailValidationToken(final String address, final String clientSecret, final int attempt,
                                            final String nextLink, final ApiCallback<RequestEmailValidationResponse> callback) {
        final String description = "requestEmailValidationToken";

        mApi.requestEmailValidation(clientSecret, address, new Integer(attempt), nextLink, new RestAdapterCallback<RequestEmailValidationResponse>(description, mUnsentEventsManager, callback,
                new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        requestEmailValidationToken(address, clientSecret, attempt, nextLink, callback);
                    }
                }
        ) {
            @Override
            public void success(RequestEmailValidationResponse requestEmailValidationResponse, Response response) {
                onEventSent();
                requestEmailValidationResponse.email = address;
                requestEmailValidationResponse.clientSecret = clientSecret;
                requestEmailValidationResponse.sendAttempt = attempt;

                callback.onSuccess(requestEmailValidationResponse);
            }
        });
    }

    /**
     * Request a phone number validation token.
     * @param phoneNumber the phone number
     * @param countryCode the country code of the phone number
     * @param clientSecret the client secret number
     * @param attempt the attempt count
     * @param nextLink the next link.
     * @param callback the callback.
     */
    public void requestPhoneNumberValidationToken(final String phoneNumber, final String countryCode,
                                                  final String clientSecret, final int attempt, final String nextLink,
                                                  final ApiCallback<RequestPhoneNumberValidationResponse> callback) {
        final String description = "requestPhoneNUmberValidationToken";

        mApi.requestPhoneNumberValidation(clientSecret, phoneNumber, countryCode, attempt, nextLink, new RestAdapterCallback<RequestPhoneNumberValidationResponse>(description, mUnsentEventsManager, callback,
                new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        requestPhoneNumberValidationToken(phoneNumber, countryCode, clientSecret, attempt, nextLink, callback);
                    }
                }
        ) {
            @Override
            public void success(RequestPhoneNumberValidationResponse requestPhoneNumberValidationResponse, Response response) {
                onEventSent();
                requestPhoneNumberValidationResponse.clientSecret = clientSecret;
                requestPhoneNumberValidationResponse.sendAttempt = attempt;

                callback.onSuccess(requestPhoneNumberValidationResponse);
            }
        });
    }

    /**
     * Request the ownership validation of an email address previously set
     * by {@link #requestEmailValidationToken(String, String, int, String, ApiCallback)}.
     * @param token the token generated by the requestEmailValidationToken call
     * @param clientSecret the client secret which was supplied in the requestEmailValidationToken call
     * @param sid the sid for the session
     * @param callback asynchronous callback response
     */
    public void submitEmailValidationToken(final String token, final String clientSecret, final String sid, final ApiCallback<Map<String,Object>> callback) {

        mApi.requestEmailOwnershipValidation(token, clientSecret, sid, new Callback<Map<String,Object>> () {
            @Override
            public void success (Map<String,Object> aDataRespMap, Response response){
                callback.onSuccess(aDataRespMap);
            }

            @Override
            public void failure (RetrofitError error){
                callback.onUnexpectedError(error);
            }
        });
    }

    /**
     * Request the ownership validation of a phone number previously set
     * by {@link #requestPhoneNumberValidationToken(String, String, String, int, String, ApiCallback)}.
     * @param token the token generated by the requestPhoneNumberValidationToken call
     * @param clientSecret the client secret which was supplied in the requestPhoneNumberValidationToken call
     * @param sid the sid for the session
     * @param callback asynchronous callback response
     */
    public void submitPhoneNumberValidationToken(final String token, final String clientSecret, final String sid, final ApiCallback<Map<String,Object>> callback) {

        mApi.requestPhoneNumberOwnershipValidation(token, clientSecret, sid, new Callback<Map<String,Object>> () {
            @Override
            public void success (Map<String,Object> aDataRespMap, Response response){
                callback.onSuccess(aDataRespMap);
            }

            @Override
            public void failure (RetrofitError error){
                callback.onUnexpectedError(error);
            }
        });
    }

    /**
     * Retrieve user matrix id from a 3rd party id.
     * @param addresses 3rd party ids
     * @param mediums the medias.
     * @param callback the 3rd parties callback
     */
    public void lookup3Pids(final List<String> addresses, final List<String> mediums, final ApiCallback<List<String>> callback) {
        // sanity checks
        if ((null == addresses) || (null == mediums) || (addresses.size() != mediums.size())) {
            callback.onUnexpectedError(new Exception("invalid params"));
            return;
        }

        // nothing to check
        if (0 == mediums.size()) {
            callback.onSuccess(new ArrayList<String>());
            return;
        }

        BulkLookupParams threePidsParams = new BulkLookupParams();

        ArrayList<List<String>> list = new ArrayList<>();

        for(int i = 0; i < addresses.size(); i++) {
            list.add(Arrays.asList(mediums.get(i), addresses.get(i)));
        }

        threePidsParams.threepids = list;

        mApi.bulkLookup(threePidsParams, new Callback<BulkLookupResponse>() {
            @Override
            public void success(BulkLookupResponse bulkLookupResponse, Response response) {
                HashMap<String, String> mxidByAddress = new HashMap<>();

                if (null != bulkLookupResponse.threepids) {
                    for (int i = 0; i < bulkLookupResponse.threepids.size(); i++) {
                        List<String> items = bulkLookupResponse.threepids.get(i);
                        // [0] : medium
                        // [1] : address
                        // [2] : matrix id
                        mxidByAddress.put(items.get(1), items.get(2));
                    }
                }

                ArrayList<String> matrixIds = new ArrayList<>();

                for(String address : addresses) {
                    if (mxidByAddress.containsKey(address)) {
                        matrixIds.add(mxidByAddress.get(address));
                    } else {
                        matrixIds.add("");
                    }
                }

                callback.onSuccess(matrixIds);
            }

            @Override
            public void failure(RetrofitError error) {
                callback.onUnexpectedError(error);
            }
        });
    }
}
