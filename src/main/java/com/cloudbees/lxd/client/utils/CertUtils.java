/*
 * Copyright (C) 2016 Original Authors
 * Copyright (C) 2016 Fabric8IO
 * Copyright (C) 2016 CloudBees, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cloudbees.lxd.client.utils;

import okio.ByteString;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;

public class CertUtils {

    public static KeyStore createTrustStore(String caCertData) throws IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException {
        return createTrustStore(new ByteArrayInputStream(caCertData.getBytes()));
    }

    public static KeyStore createTrustStore(InputStream pemInputStream) throws IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException {
        CertificateFactory certFactory = CertificateFactory.getInstance("X509");
        X509Certificate cert = (X509Certificate) certFactory.generateCertificate(pemInputStream);

        KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(null);

        String alias = cert.getSubjectX500Principal().getName();
        trustStore.setCertificateEntry(alias, cert);
        return trustStore;
    }


    public static KeyStore createKeyStore(InputStream certInputStream, InputStream keyInputStream, String clientKeyAlgo, char[] clientKeyPassphrase) throws IOException, CertificateException, NoSuchAlgorithmException, InvalidKeySpecException, KeyStoreException {
        CertificateFactory certFactory = CertificateFactory.getInstance("X509");
        X509Certificate cert = (X509Certificate) certFactory.generateCertificate(certInputStream);

        byte[] keyBytes = decodePem(keyInputStream);

        PrivateKey privateKey;

        KeyFactory keyFactory = KeyFactory.getInstance(clientKeyAlgo);
        try {
            // First let's try PKCS8
            privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (InvalidKeySpecException e) {
            // Otherwise try PKCS1
            RSAPrivateCrtKeySpec keySpec = PKCS1Util.decodePKCS1(keyBytes);
            privateKey = keyFactory.generatePrivate(keySpec);
        }

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, clientKeyPassphrase);

        String alias = cert.getSubjectX500Principal().getName();
        keyStore.setKeyEntry(alias, privateKey, clientKeyPassphrase, new Certificate[]{cert});

        return keyStore;
    }

    public static KeyStore createKeyStore(String clientCertData, String clientKeyData, String clientKeyAlgo, char[] clientKeyPassphrase) throws IOException, CertificateException, NoSuchAlgorithmException, InvalidKeySpecException, KeyStoreException {
        return createKeyStore(new ByteArrayInputStream(clientCertData.getBytes()), new ByteArrayInputStream(clientKeyData.getBytes()), clientKeyAlgo, clientKeyPassphrase);
    }

    // This method is inspired and partly taken over from
    // http://oauth.googlecode.com/svn/code/java/
    // All credits to belong to them.
    private static byte[] decodePem(InputStream keyInputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(keyInputStream));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("-----BEGIN ")) {
                    return readBytes(reader, line.trim().replace("BEGIN", "END"));
                }
            }
            throw new IOException("PEM is invalid: no begin marker");
        } finally {
            reader.close();
        }
    }

    private static byte[] readBytes(BufferedReader reader, String endMarker) throws IOException {
        String line;
        StringBuffer buf = new StringBuffer();

        while ((line = reader.readLine()) != null) {
            if (line.indexOf(endMarker) != -1) {
                return ByteString.decodeBase64(buf.toString()).toByteArray();
            }
            buf.append(line.trim());
        }
        throw new IOException("PEM is invalid : No end marker");
    }
}
