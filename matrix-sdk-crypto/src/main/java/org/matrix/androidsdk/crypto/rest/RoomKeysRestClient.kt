/*
 * Copyright 2018 New Vector Ltd
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

package org.matrix.androidsdk.crypto.rest

import org.matrix.androidsdk.crypto.api.RoomKeysApi
import org.matrix.androidsdk.crypto.model.keys.*
import org.matrix.androidsdk.util.callback.ApiCallback
import retrofit2.Converter

/**
 * Class used to make requests to the RoomKeys API.
 */
class RoomKeysRestClient(homeServerUrl: String, accessToken: String, converterFactory: Converter.Factory) :
        ParentRestClient<RoomKeysApi>(homeServerUrl, accessToken, RoomKeysApi::class.java, ParentRestClient.URI_API_PREFIX_PATH_R0, converterFactory) {

    /**
     * Get the key backup last version
     * If not supported by the server, an error is returned: {"errcode":"M_NOT_FOUND","error":"No backup found"}
     *
     * @param callback the callback
     */
    fun getKeysBackupLastVersion(callback: ApiCallback<KeysVersionResult>) {
        val description = "getKeysBackupVersion"

        mApi.getKeysBackupLastVersion()
                .enqueue(CryptoRestAdapterCallback(description, callback, null))
    }

    /**
     * Get a key backup specific version
     * If not supported by the server, an error is returned: {"errcode":"M_NOT_FOUND","error":"No backup found"}
     *
     * @param version  version
     * @param callback the callback
     */
    fun getKeysBackupVersion(version: String,
                             callback: ApiCallback<KeysVersionResult>) {
        val description = "getKeysBackupVersion"

        mApi.getKeysBackupVersion(version)
                .enqueue(CryptoRestAdapterCallback(description, callback, null))
    }

    /**
     * Create a keys backup version
     *
     * @param createKeysBackupVersionBody the body
     * @param callback                    the callback
     */
    fun createKeysBackupVersion(createKeysBackupVersionBody: CreateKeysBackupVersionBody, callback: ApiCallback<KeysVersion>) {
        val description = "createKeysBackupVersion"

        mApi.createKeysBackupVersion(createKeysBackupVersionBody)
                .enqueue(CryptoRestAdapterCallback(description, callback, null))
    }

    /**
     * Send room session data for the given room, session, and version
     *
     * @param roomId        the room id
     * @param sessionId     the session id
     * @param version       the version of the backup
     * @param keyBackupData the data to send
     * @param callback      the callback
     */
    fun sendKeyBackup(roomId: String,
                      sessionId: String,
                      version: String,
                      keyBackupData: KeyBackupData,
                      callback: ApiCallback<Void>) {
        val description = "sendKeyBackup"

        mApi.storeRoomSessionData(roomId, sessionId, version, keyBackupData)
                .enqueue(CryptoRestAdapterCallback(description, callback, null))
    }

    /**
     * Send room session data
     *
     * @param version        the version of the backup
     * @param keysBackupData the data to send
     * @param callback       the callback
     */
    fun sendKeysBackup(version: String,
                       keysBackupData: KeysBackupData,
                       callback: ApiCallback<Void>) {
        val description = "sendKeysBackup"

        mApi.storeSessionsData(version, keysBackupData)
                .enqueue(CryptoRestAdapterCallback(description, callback, null))
    }

    /**
     * Retrieve the key for the given session in the given room from the backup.
     *
     * @param roomId    the room id
     * @param sessionId the session id
     * @param version   the version of the backup, or empty String to retrieve the last version
     * @param callback  the callback
     */
    fun getRoomKeyBackup(roomId: String, sessionId: String, version: String, callback: ApiCallback<KeyBackupData>) {
        val description = "getKeyBackup"

        mApi.getRoomSessionData(roomId, sessionId, version)
                .enqueue(CryptoRestAdapterCallback(description, callback, null))
    }

    /**
     * Retrieve all the keys for the given room from the backup.
     *
     * @param roomId   the room id
     * @param version  the version of the backup, or empty String to retrieve the last version
     * @param callback the callback
     */
    fun getRoomKeysBackup(roomId: String, version: String, callback: ApiCallback<RoomKeysBackupData>) {
        val description = "getRoomKeysBackup"

        mApi.getRoomSessionsData(roomId, version)
                .enqueue(CryptoRestAdapterCallback(description, callback, null))
    }

    /**
     * Retrieve the complete sessions data for the given backup version
     *
     * @param version  the version of the backup, or empty String to retrieve the last version
     * @param callback the callback
     */
    fun getKeysBackup(version: String, callback: ApiCallback<KeysBackupData>) {
        val description = "getKeyBackup"

        mApi.getSessionsData(version)
                .enqueue(CryptoRestAdapterCallback(description, callback, null))
    }

    /**
     * @param roomId
     * @param sessionId
     * @param version
     * @param callback
     */
    fun deleteKeyBackup(roomId: String, sessionId: String, version: String, callback: ApiCallback<Void>) {
        val description = "deleteKeyBackup"

        mApi.deleteRoomSessionData(roomId, sessionId, version)
                .enqueue(CryptoRestAdapterCallback(description, callback, null))
    }

    /**
     * @param version
     * @param callback
     */
    fun deleteKeysBackup(version: String, callback: ApiCallback<Void>) {
        val description = "deleteKeyBackup"

        mApi.deleteSessionsData(version)
                .enqueue(CryptoRestAdapterCallback(description, callback, null))
    }
}
