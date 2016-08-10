/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bertramlabs.plugins.karman.aws

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.InstanceProfileCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.regions.RegionUtils
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.AmazonS3EncryptionClient
import com.amazonaws.services.s3.model.Bucket
import com.amazonaws.services.s3.model.CryptoConfiguration
import com.amazonaws.services.s3.model.CryptoStorageMode
import com.amazonaws.services.s3.model.EncryptionMaterials
import com.bertramlabs.plugins.karman.Directory
import com.bertramlabs.plugins.karman.StorageProvider
import org.apache.commons.lang.StringUtils

import javax.crypto.spec.SecretKeySpec

class S3StorageProvider extends StorageProvider {

    static String providerName = "s3"

    String accessKey = ''
    String secretKey = ''
    String region = ''
    String endpoint = ''
    String symmetricKey
    String protocol = 'https'
    Integer maxConnections = 50
    Boolean keepAlive = false
    Boolean useGzip = false
    AmazonS3Client client = null

    S3StorageProvider(Map options) {
        accessKey = options.accessKey ?: accessKey
        secretKey = options.secretKey ?: secretKey
        region = options.region ?: region
        endpoint = options.endpoint ?: endpoint
        symmetricKey = options.symmetricKey ?: symmetricKey
        protocol = options.protocol ?: protocol
        maxConnections = options.maxConnections ?: maxConnections
        keepAlive = options.keepAlive ?: keepAlive
        defaultFileACL = options.defaultFileACL ?: defaultFileACL
        useGzip = options.useGzip ?: useGzip
    }

    Directory getDirectory(String name) {
        new S3Directory(name: name, provider: this)
    }

    List<Directory> getDirectories() {
        List<Bucket> buckets = s3Client.listBuckets()
        buckets.collect { bucket -> directoryFromS3Bucket(bucket) }
    }

    AmazonS3Client getS3Client() {
        if (client) {
            return client
        }

        ClientConfiguration configuration = new ClientConfiguration()
        configuration.setUseTcpKeepAlive(keepAlive)
        configuration.setMaxConnections(maxConnections)
        configuration.setProtocol(protocol == 'https' ? com.amazonaws.Protocol.HTTPS : com.amazonaws.Protocol.HTTP)
        configuration.setUseGzip(useGzip)
        configuration.proxyHost = null;
        configuration.proxyPort = 0;
        configuration.proxyDomain = null;
        configuration.proxyUsername = null;
        configuration.proxyPassword = null;

        // If accessKey/secret are specified, then use basic credentials.
        if (!StringUtils.isBlank(accessKey) && !StringUtils.isBlank(secretKey)) {
            println "Using specified accessKey/secret...";
            BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey)
            if (symmetricKey) {
                EncryptionMaterials materials = new EncryptionMaterials(new SecretKeySpec(symmetricKey.bytes, 'AES'))
                CryptoConfiguration cryptoConfig = new CryptoConfiguration().withStorageMode(CryptoStorageMode.ObjectMetadata)
                client = new AmazonS3EncryptionClient(credentials, materials, configuration, cryptoConfig)
            } else {
                client = new AmazonS3Client(credentials, configuration)
            }
        } else {

            // No accessKey/secret? Attempt to use the IAM profile.
            println "Using default (no-args) S3 Client...";

            // InstanceProfileCredentialsProvider gets credentials from a url, and we shouldn't be running through the proxy.
            System.setProperty('http.nonProxyHosts', '169.254.169.254');
            client = new AmazonS3Client(new InstanceProfileCredentialsProvider(), configuration);
        }

        if (region) {
            Region region = RegionUtils.getRegion(region)
            client.region = region
        }

        if (endpoint) {
            client.endpoint = endpoint
        }

        return client
    }

    private S3Directory directoryFromS3Bucket(bucket) {
        new S3Directory(
                name: bucket.name,
                provider: this
        )
    }
}