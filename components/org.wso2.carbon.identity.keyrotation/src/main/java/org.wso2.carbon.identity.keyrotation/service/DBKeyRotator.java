/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.keyrotation.service;

import org.apache.commons.collections.CollectionUtils;
import org.apache.log4j.Logger;
import org.wso2.carbon.identity.keyrotation.config.KeyRotationConfig;
import org.wso2.carbon.identity.keyrotation.dao.BPSProfileDAO;
import org.wso2.carbon.identity.keyrotation.dao.DBConstants;
import org.wso2.carbon.identity.keyrotation.dao.IdentityDAO;
import org.wso2.carbon.identity.keyrotation.dao.OAuthDAO;
import org.wso2.carbon.identity.keyrotation.dao.RegistryDAO;
import org.wso2.carbon.identity.keyrotation.dao.WorkFlowDAO;
import org.wso2.carbon.identity.keyrotation.model.BPSPassword;
import org.wso2.carbon.identity.keyrotation.model.OAuthCode;
import org.wso2.carbon.identity.keyrotation.model.OAuthSecret;
import org.wso2.carbon.identity.keyrotation.model.OAuthToken;
import org.wso2.carbon.identity.keyrotation.model.RegistryProperty;
import org.wso2.carbon.identity.keyrotation.model.TOTPSecret;
import org.wso2.carbon.identity.keyrotation.util.KeyRotationException;
import org.wso2.carbon.identity.workflow.mgt.bean.RequestParameter;
import org.wso2.carbon.identity.workflow.mgt.dto.WorkflowRequest;

import java.util.ArrayList;
import java.util.List;

import static org.wso2.carbon.identity.keyrotation.util.EncryptionUtil.checkPlainText;
import static org.wso2.carbon.identity.keyrotation.util.EncryptionUtil.symmetricReEncryption;

/**
 * This class holds the DB re-encryption service.
 */
public class DBKeyRotator {

    private static final Logger log = Logger.getLogger(DBKeyRotator.class);
    private static final DBKeyRotator instance = new DBKeyRotator();
    private static final String password = "password";
    private static final String privatekeyPass = "privatekeyPass";
    private static final String subscriberPassword = "subscriberPassword";

    public static DBKeyRotator getInstance() {

        return instance;
    }

    /**
     * Re-encryption of the identity and registry DB data.
     *
     * @param keyRotationConfig Configuration data needed to perform the task.
     * @throws KeyRotationException Exception thrown if something unexpected happens during key rotation.
     */
    public void dbReEncryptor(KeyRotationConfig keyRotationConfig) throws KeyRotationException {

        log.info("Started re-encrypting identity and registry DB data...");
        reEncryptIdentityTOTPData(keyRotationConfig);
        log.info("Successfully updated totp data records in IDN_IDENTITY_USER_DATA: " + IdentityDAO.updateCount);
        log.info("Failed totp data records in IDN_IDENTITY_USER_DATA: " + IdentityDAO.failedCount);
        reEncryptOauthAuthData(keyRotationConfig);
        log.info("Successfully updated Oauth2 authorization code data records in IDN_OAUTH2_AUTHORIZATION_CODE: " +
                OAuthDAO.updateCodeCount);
        log.info("Failed Oauth2 authorization code data records in IDN_OAUTH2_AUTHORIZATION_CODE: " +
                OAuthDAO.failedCodeCount);
        reEncryptOauthTokenData(keyRotationConfig);
        log.info("Successfully updated Oauth2 access and refresh tokens data records in IDN_OAUTH2_ACCESS_TOKEN: " +
                OAuthDAO.updateTokenCount);
        log.info("Failed Oauth2 access and refresh tokens data records in IDN_OAUTH2_ACCESS_TOKEN: " +
                OAuthDAO.failedTokenCount);
        reEncryptOauthConsumerData(keyRotationConfig);
        log.info("Successfully updated Oauth consumer secret data records in IDN_OAUTH_CONSUMER_APPS: " +
                OAuthDAO.updateSecretCount);
        log.info("Failed Oauth consumer secret data records in IDN_OAUTH_CONSUMER_APPS: " + OAuthDAO.failedSecretCount);
        reEncryptBPSData(keyRotationConfig);
        log.info("Successfully updated BPS profile data records in WF_BPS_PROFILE: " + BPSProfileDAO.updateCount);
        log.info("Failed BPS profile data records in WF_BPS_PROFILE: " + BPSProfileDAO.failedCount);
        reEncryptWFRequestData(keyRotationConfig);
        log.info("Successfully updated WF request data records in WF_REQUEST: " + WorkFlowDAO.updateCount);
        log.info("Failed WF request data records in WF_REQUEST: " + WorkFlowDAO.failedCount);
        reEncryptKeystorePasswordData(keyRotationConfig);
        log.info("Successfully updated keystore password property data records in REG_PROPERTY: " +
                RegistryDAO.updateCount);
        log.info("Failed keystore password property data records in REG_PROPERTY: " + RegistryDAO.failedCount);
        reEncryptKeystorePrivatekeyPassData(keyRotationConfig);
        log.info("Successfully updated keystore privatekeyPass property data records in REG_PROPERTY: " +
                RegistryDAO.updateCount);
        log.info("Failed keystore privatekeyPass property data records in REG_PROPERTY: " + RegistryDAO.failedCount);
        reEncryptSubscriberPasswordData(keyRotationConfig);
        log.info("Successfully updated subscriber password property data records in REG_PROPERTY: " +
                RegistryDAO.updateCount);
        log.info("Failed subscriber password property data records in REG_PROPERTY: " + RegistryDAO.failedCount);
        log.info("Finished re-encrypting identity and registry DB data completed...\n");
    }

    /**
     * Re-encryption of the IDN_IDENTITY_USER_DATA table data.
     *
     * @param keyRotationConfig Configuration data needed to perform the task.
     * @throws KeyRotationException Exception thrown if something unexpected happens during key rotation.
     */
    private void reEncryptIdentityTOTPData(KeyRotationConfig keyRotationConfig) throws KeyRotationException {

        log.debug("Started re-encryption of the TOTP data...");
        int startIndex = 0;
        List<TOTPSecret> chunkList =
                IdentityDAO.getInstance().getTOTPSecretsChunks(startIndex, keyRotationConfig);
        while (CollectionUtils.isNotEmpty(chunkList)) {
            List<TOTPSecret> midChunkList = new ArrayList<>();
            for (TOTPSecret totpSecret : chunkList) {
                if (DBConstants.DATA_KEY.equals(totpSecret.getDataKey()) &&
                        !checkPlainText(totpSecret.getDataValue())) {
                    log.debug("Encrypted value " + totpSecret.getDataValue());
                    String reEncryptedValue = symmetricReEncryption(totpSecret.getDataValue(), keyRotationConfig);
                    totpSecret.setDataValue(reEncryptedValue);
                    log.debug("Re-encrypted value " + totpSecret.getDataValue());
                    midChunkList.add(totpSecret);
                }
            }
            IdentityDAO.getInstance().updateTOTPSecretsChunks(midChunkList, keyRotationConfig);
            startIndex = startIndex + DBConstants.CHUNK_SIZE;
            chunkList = IdentityDAO.getInstance().getTOTPSecretsChunks(startIndex, keyRotationConfig);
        }
        log.debug("Finished re-encryption of the TOTP data...");
    }

    /**
     * Re-encryption of the IDN_OAUTH2_AUTHORIZATION_CODE table data.
     *
     * @param keyRotationConfig Configuration data needed to perform the task.
     * @throws KeyRotationException Exception thrown if something unexpected happens during key rotation.
     */
    private void reEncryptOauthAuthData(KeyRotationConfig keyRotationConfig) throws KeyRotationException {

        log.debug("Started re-encryption of the Oauth2 authorization code data...");
        int startIndex = 0;
        List<OAuthCode> chunkList =
                OAuthDAO.getInstance().getOAuthCodeChunks(startIndex, keyRotationConfig);
        while (CollectionUtils.isNotEmpty(chunkList)) {
            List<OAuthCode> midChunkList = new ArrayList<>();
            for (OAuthCode oAuthCode : chunkList) {
                if (!checkPlainText(oAuthCode.getAuthorizationCode())) {
                    log.debug("Encrypted value " + oAuthCode.getAuthorizationCode());
                    String reEncryptedValue = symmetricReEncryption(oAuthCode.getAuthorizationCode(),
                            keyRotationConfig);
                    oAuthCode.setAuthorizationCode(reEncryptedValue);
                    log.debug("Re-encrypted value " + oAuthCode.getAuthorizationCode());
                    midChunkList.add(oAuthCode);
                }
            }
            OAuthDAO.getInstance().updateOAuthCodeChunks(midChunkList, keyRotationConfig);
            startIndex = startIndex + DBConstants.CHUNK_SIZE;
            chunkList = OAuthDAO.getInstance().getOAuthCodeChunks(startIndex, keyRotationConfig);
        }
        log.debug("Finished re-encryption of the Oauth2 authorization code data...");
    }

    /**
     * Re-encryption of the IDN_OAUTH2_ACCESS_TOKEN table data.
     *
     * @param keyRotationConfig Configuration data needed to perform the task.
     * @throws KeyRotationException Exception thrown if something unexpected happens during key rotation.
     */
    private void reEncryptOauthTokenData(KeyRotationConfig keyRotationConfig) throws KeyRotationException {

        log.debug("Started re-encryption of the Oauth2 access and refresh tokens data...");
        int startIndex = 0;
        List<OAuthToken> chunkList =
                OAuthDAO.getInstance().getOAuthTokenChunks(startIndex, keyRotationConfig);
        while (CollectionUtils.isNotEmpty(chunkList)) {
            List<OAuthToken> midChunkList = new ArrayList<>();
            for (OAuthToken oAuthToken : chunkList) {
                if (!checkPlainText(oAuthToken.getAccessToken()) && !checkPlainText(oAuthToken.getRefreshToken())) {
                    log.debug("Encrypted access token value " + oAuthToken.getAccessToken());
                    String accessTokenReEncryptedValue = symmetricReEncryption(oAuthToken.getAccessToken(),
                            keyRotationConfig);
                    oAuthToken.setAccessToken(accessTokenReEncryptedValue);
                    log.debug("Re-encrypted value " + oAuthToken.getAccessToken());
                    log.debug("Encrypted refresh token value " + oAuthToken.getRefreshToken());
                    String refreshTokenReEncryptedValue = symmetricReEncryption(oAuthToken.getRefreshToken(),
                            keyRotationConfig);
                    oAuthToken.setRefreshToken(refreshTokenReEncryptedValue);
                    log.debug("Re-encrypted value " + oAuthToken.getRefreshToken());
                    midChunkList.add(oAuthToken);
                }
            }
            OAuthDAO.getInstance().updateOAuthTokenChunks(midChunkList, keyRotationConfig);
            startIndex = startIndex + DBConstants.CHUNK_SIZE;
            chunkList = OAuthDAO.getInstance().getOAuthTokenChunks(startIndex, keyRotationConfig);
        }
        log.debug("Finished re-encryption of the Oauth2 access and refresh tokens data...");
    }

    /**
     * Re-encryption of the IDN_OAUTH_CONSUMER_APPS consumer table data.
     *
     * @param keyRotationConfig Configuration data needed to perform the task.
     * @throws KeyRotationException Exception thrown if something unexpected happens during key rotation.
     */
    private void reEncryptOauthConsumerData(KeyRotationConfig keyRotationConfig) throws KeyRotationException {

        log.debug("Started re-encryption of the Oauth consumer secret data...");
        int startIndex = 0;
        List<OAuthSecret> chunkList =
                OAuthDAO.getInstance().getOAuthSecretChunks(startIndex, keyRotationConfig);
        while (CollectionUtils.isNotEmpty(chunkList)) {
            List<OAuthSecret> midChunkList = new ArrayList<>();
            for (OAuthSecret oAuthSecret : chunkList) {
                if (!checkPlainText(oAuthSecret.getConsumerSecret())) {
                    log.debug("Encrypted value " + oAuthSecret.getConsumerSecret());
                    String reEncryptedValue = symmetricReEncryption(oAuthSecret.getConsumerSecret(),
                            keyRotationConfig);
                    oAuthSecret.setConsumerSecret(reEncryptedValue);
                    log.debug("Re-encrypted value " + oAuthSecret.getConsumerSecret());
                    midChunkList.add(oAuthSecret);
                }
            }
            OAuthDAO.getInstance().updateOAuthSecretChunks(midChunkList, keyRotationConfig);
            startIndex = startIndex + DBConstants.CHUNK_SIZE;
            chunkList = OAuthDAO.getInstance().getOAuthSecretChunks(startIndex, keyRotationConfig);
        }
        log.debug("Finished re-encryption of the Oauth consumer secret data...");
    }

    /**
     * Re-encryption of the WF_BPS_PROFILE table data.
     *
     * @param keyRotationConfig Configuration data needed to perform the task.
     * @throws KeyRotationException Exception thrown if something unexpected happens during key rotation.
     */
    private void reEncryptBPSData(KeyRotationConfig keyRotationConfig) throws KeyRotationException {

        log.debug("Started re-encryption of the BPS profile data...");
        int startIndex = 0;
        List<BPSPassword> chunkList =
                BPSProfileDAO.getInstance().getBpsPasswordChunks(startIndex, keyRotationConfig);
        while (CollectionUtils.isNotEmpty(chunkList)) {
            List<BPSPassword> midChunkList = new ArrayList<>();
            for (BPSPassword bpsPassword : chunkList) {
                if (!checkPlainText(bpsPassword.getPassword())) {
                    log.debug("Encrypted value " + bpsPassword.getPassword());
                    String reEncryptedValue = symmetricReEncryption(bpsPassword.getPassword(), keyRotationConfig);
                    bpsPassword.setPassword(reEncryptedValue);
                    log.debug("Re-encrypted value " + bpsPassword.getPassword());
                    midChunkList.add(bpsPassword);
                }
            }
            BPSProfileDAO.getInstance().updateBpsPasswordChunks(midChunkList, keyRotationConfig);
            startIndex = startIndex + DBConstants.CHUNK_SIZE;
            chunkList = BPSProfileDAO.getInstance().getBpsPasswordChunks(startIndex, keyRotationConfig);
        }
        log.debug("Finished re-encryption of the BPS profile data...");
    }

    /**
     * Re-encryption of the WF_REQUEST table data.
     *
     * @param keyRotationConfig Configuration data needed to perform the task.
     * @throws KeyRotationException Exception thrown if something unexpected happens during key rotation.
     */
    private void reEncryptWFRequestData(KeyRotationConfig keyRotationConfig) throws KeyRotationException {

        log.debug("Started re-encryption of the WF request data...");
        int startIndex = 0;
        List<WorkflowRequest> chunkList =
                WorkFlowDAO.getInstance().getWFRequestChunks(startIndex, keyRotationConfig);
        while (CollectionUtils.isNotEmpty(chunkList)) {
            List<WorkflowRequest> midChunkList = new ArrayList<>();
            for (WorkflowRequest wfRequest : chunkList) {
                for (RequestParameter parameter : wfRequest.getRequestParameters()) {
                    if (DBConstants.CREDENTIAL.equals(parameter.getName()) &&
                            !checkPlainText(parameter.getValue().toString())) {
                        log.debug("Encrypted value " + parameter.getValue().toString());
                        String reEncryptedValue = symmetricReEncryption(parameter.getValue().toString(),
                                keyRotationConfig);
                        parameter.setValue(reEncryptedValue);
                        log.debug("Re-encrypted value " + parameter.getValue().toString());
                        midChunkList.add(wfRequest);
                    }
                }
            }
            WorkFlowDAO.getInstance().updateWFRequestChunks(midChunkList, keyRotationConfig);
            startIndex = startIndex + DBConstants.CHUNK_SIZE;
            chunkList = WorkFlowDAO.getInstance().getWFRequestChunks(startIndex, keyRotationConfig);
        }
        log.debug("Finished re-encryption of the WF request data...");
    }

    /**
     * Re-encryption of keystore password in REG_PROPERTY table.
     *
     * @param keyRotationConfig Configuration data needed to perform the task.
     * @throws KeyRotationException Exception thrown if something unexpected happens during key rotation.
     */
    private void reEncryptKeystorePasswordData(KeyRotationConfig keyRotationConfig) throws KeyRotationException {

        log.debug("Started re-encryption of the keystore password property data...");
        RegistryDAO.updateCount = 0;
        RegistryDAO.failedCount = 0;
        int startIndex = 0;
        List<RegistryProperty> chunkList =
                RegistryDAO.getInstance().getRegPropertyDataChunks(startIndex, keyRotationConfig, password);
        while (CollectionUtils.isNotEmpty(chunkList)) {
            List<RegistryProperty> midChunkList = new ArrayList<>();
            for (RegistryProperty regProperty : chunkList) {
                if (!checkPlainText(regProperty.getRegValue())) {
                    log.debug("Encrypted value " + regProperty.getRegValue());
                    String reEncryptedValue = symmetricReEncryption(regProperty.getRegValue(), keyRotationConfig);
                    regProperty.setRegValue(reEncryptedValue);
                    log.debug("Re-encrypted value " + regProperty.getRegValue());
                    midChunkList.add(regProperty);
                }
            }
            RegistryDAO.getInstance().updateRegPropertyDataChunks(midChunkList, keyRotationConfig, password);
            startIndex = startIndex + DBConstants.CHUNK_SIZE;
            chunkList = RegistryDAO.getInstance().getRegPropertyDataChunks(startIndex, keyRotationConfig, password);
        }
        log.debug("Finished re-encryption of the keystore password property data...");
    }

    /**
     * Re-encryption of keystore privatekeyPass in REG_PROPERTY table.
     *
     * @param keyRotationConfig Configuration data needed to perform the task.
     * @throws KeyRotationException Exception thrown if something unexpected happens during key rotation.
     */
    private void reEncryptKeystorePrivatekeyPassData(KeyRotationConfig keyRotationConfig) throws KeyRotationException {

        log.debug("Started re-encryption of the keystore privatekeyPass property data...");
        RegistryDAO.updateCount = 0;
        RegistryDAO.failedCount = 0;
        int startIndex = 0;
        List<RegistryProperty> chunkList =
                RegistryDAO.getInstance().getRegPropertyDataChunks(startIndex, keyRotationConfig, privatekeyPass);
        while (CollectionUtils.isNotEmpty(chunkList)) {
            List<RegistryProperty> midChunkList = new ArrayList<>();
            for (RegistryProperty regProperty : chunkList) {
                if (!checkPlainText(regProperty.getRegValue())) {
                    log.debug("Encrypted value " + regProperty.getRegValue());
                    String reEncryptedValue = symmetricReEncryption(regProperty.getRegValue(), keyRotationConfig);
                    regProperty.setRegValue(reEncryptedValue);
                    log.debug("Re-encrypted value " + regProperty.getRegValue());
                    midChunkList.add(regProperty);
                }
            }
            RegistryDAO.getInstance().updateRegPropertyDataChunks(midChunkList, keyRotationConfig, privatekeyPass);
            startIndex = startIndex + DBConstants.CHUNK_SIZE;
            chunkList =
                    RegistryDAO.getInstance().getRegPropertyDataChunks(startIndex, keyRotationConfig, privatekeyPass);
        }
        log.debug("Finished re-encryption of the keystore privatekeyPass property data...");
    }

    /**
     * Re-encryption of subscriber password in REG_PROPERTY table.
     *
     * @param keyRotationConfig Configuration data needed to perform the task.
     * @throws KeyRotationException Exception thrown if something unexpected happens during key rotation.
     */
    private void reEncryptSubscriberPasswordData(KeyRotationConfig keyRotationConfig) throws KeyRotationException {

        log.debug("Started re-encryption of the subscriber password property data...");
        RegistryDAO.updateCount = 0;
        RegistryDAO.failedCount = 0;
        int startIndex = 0;
        List<RegistryProperty> chunkList =
                RegistryDAO.getInstance().getRegPropertyDataChunks(startIndex, keyRotationConfig, subscriberPassword);
        while (CollectionUtils.isNotEmpty(chunkList)) {
            List<RegistryProperty> midChunkList = new ArrayList<>();
            for (RegistryProperty regProperty : chunkList) {
                if (!checkPlainText(regProperty.getRegValue())) {
                    log.debug("Encrypted value " + regProperty.getRegValue());
                    String reEncryptedValue = symmetricReEncryption(regProperty.getRegValue(), keyRotationConfig);
                    regProperty.setRegValue(reEncryptedValue);
                    log.debug("Re-encrypted value " + regProperty.getRegValue());
                    midChunkList.add(regProperty);
                }
            }
            RegistryDAO.getInstance().updateRegPropertyDataChunks(midChunkList, keyRotationConfig, subscriberPassword);
            startIndex = startIndex + DBConstants.CHUNK_SIZE;
            chunkList =
                    RegistryDAO.getInstance()
                            .getRegPropertyDataChunks(startIndex, keyRotationConfig, subscriberPassword);
        }
        log.debug("Finished re-encryption of the subscriber password property data...");
    }
}
