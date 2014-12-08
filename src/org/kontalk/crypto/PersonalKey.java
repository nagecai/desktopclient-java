/*
 * Kontalk Java client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Iterator;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.operator.KeyFingerPrintCalculator;
import org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.bouncycastle.openpgp.operator.PGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.kontalk.crypto.PGP.PGPDecryptedKeyPairRing;
import org.kontalk.crypto.PGP.PGPKeyPairRing;
import org.kontalk.util.MessageUtils;

/** Personal asymmetric encryption key. */
public final class PersonalKey {

    /** Decrypted key pair (for direct usage). */
    private final PGPDecryptedKeyPairRing mPair;
    /** X.509 bridge certificate. */
    private X509Certificate mBridgeCert;

    private PersonalKey(PGPDecryptedKeyPairRing keyPair, X509Certificate bridgeCert) {
        mPair = keyPair;
        mBridgeCert = bridgeCert;
    }

    private PersonalKey(PGPKeyPair signKp, PGPKeyPair encryptKp, X509Certificate bridgeCert) {
        this(new PGPDecryptedKeyPairRing(signKp, encryptKp), bridgeCert);
    }

//    private PersonalKey(Parcel in) throws PGPException {
//        mPair = PGP.fromParcel(in);
//        mBridgeCert = X509Bridge.fromParcel(in);
//    }

    public PGPKeyPair getEncryptKeyPair() {
        return mPair.encryptKey;
    }

    public PGPKeyPair getSignKeyPair() {
        return mPair.signKey;
    }

    public X509Certificate getBridgeCertificate() {
        return mBridgeCert;
    }

    public PrivateKey getBridgePrivateKey() throws PGPException {
    	return PGP.convertPrivateKey(mPair.signKey.getPrivateKey());
    }

    public PGPPublicKeyRing getPublicKeyRing() throws IOException {
    	return new PGPPublicKeyRing(getEncodedPublicKeyRing(), new BcKeyFingerprintCalculator());
    }

    public byte[] getEncodedPublicKeyRing() throws IOException {
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	mPair.signKey.getPublicKey().encode(out);
    	mPair.encryptKey.getPublicKey().encode(out);
    	return out.toByteArray();
    }

    /** Returns the first user ID on the key that matches the given network. */
    public String getUserId(String network) {
        return PGP.getUserId(mPair.signKey.getPublicKey(), network);
    }

    public String getFingerprint() {
    	return MessageUtils.bytesToHex(mPair.signKey.getPublicKey().getFingerprint());
    }

    public PGPKeyPairRing store(String name, String email, String comment, String passphrase) throws PGPException {
        // name[ (comment)] <[email]>
        StringBuilder userid = new StringBuilder(name);

        if (comment != null) userid
            .append(" (")
            .append(comment)
            .append(')');

        userid.append(" <");
        if (email != null)
            userid.append(email);
        userid.append('>');

        return PGP.store(mPair, userid.toString(), passphrase);
    }

    /** Creates a {@link PersonalKey} from private and public key byte buffers. */
    @SuppressWarnings("unchecked")
    public static PersonalKey load(InputStream privateKeyData, InputStream publicKeyData, String passphrase, byte[] bridgeCertData)
            throws PGPException, IOException, CertificateException, NoSuchProviderException {

        KeyFingerPrintCalculator fpr = new BcKeyFingerprintCalculator();
        PGPSecretKeyRing secRing = new PGPSecretKeyRing(privateKeyData, fpr);
        PGPPublicKeyRing pubRing = new PGPPublicKeyRing(publicKeyData, fpr);

        PGPDigestCalculatorProvider sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build();
        PBESecretKeyDecryptor decryptor = new JcePBESecretKeyDecryptorBuilder(sha1Calc)
            .setProvider(PGP.PROVIDER)
            .build(passphrase.toCharArray());

        PGPKeyPair signKp, encryptKp;

        PGPPublicKey  signPub = null;
        PGPPrivateKey signPriv = null;
        PGPPublicKey   encPub = null;
        PGPPrivateKey  encPriv = null;

        // public keys
        Iterator<PGPPublicKey> pkeys = pubRing.getPublicKeys();
        while (pkeys.hasNext()) {
            PGPPublicKey key = pkeys.next();
            if (key.isMasterKey()) {
                // master (signing) key
                signPub = key;
            }
            else {
                // sub (encryption) key
                encPub = key;
            }
        }

        // secret keys
        Iterator<PGPSecretKey> skeys = secRing.getSecretKeys();
        while (skeys.hasNext()) {
            PGPSecretKey key = skeys.next();
            PGPSecretKey sec = secRing.getSecretKey();
            if (key.isMasterKey()) {
                // master (signing) key
                signPriv = sec.extractPrivateKey(decryptor);
            }
            else {
                // sub (encryption) key
                encPriv = sec.extractPrivateKey(decryptor);
            }
        }

        // X.509 bridge certificate
        X509Certificate bridgeCert = X509Bridge.load(bridgeCertData);

        if (encPriv != null && encPub != null && signPriv != null && signPub != null && bridgeCert != null) {
            signKp = new PGPKeyPair(signPub, signPriv);
            encryptKp = new PGPKeyPair(encPub, encPriv);
            return new PersonalKey(signKp, encryptKp, bridgeCert);
        }

        throw new PGPException("invalid key data");
    }

    /**
     * Signs the given public key uid using our master (signing) key.<br>
     * WARNING use this method along with {@link PGPPublicKeyRing#insertPublicKey()}
     * to make this effective, otherwise GnuPG will not accept the new signature.
     * @see PGPPublicKeyRing#insertPublicKey(PGPPublicKeyRing, PGPPublicKey)
     * @see #signPublicKey(byte[], String)
     */
    public PGPPublicKey signPublicKey(PGPPublicKey keyToBeSigned, String id)
            throws PGPException, IOException, SignatureException {

        return PGP.signPublicKey(mPair.signKey, keyToBeSigned, id);
    }

    /**
     * Revokes the whole key pair using the master (signing) key.
     * @param store true to store the key in this object
     * @return the revoked master public key
     */
	public PGPPublicKey revoke(boolean store)
			throws PGPException, IOException, SignatureException {

		PGPPublicKey revoked = PGP.revokeKey(mPair.signKey);

		if (store)
			mPair.signKey = new PGPKeyPair(revoked, mPair.signKey.getPrivateKey());

		return revoked;
	}

}
