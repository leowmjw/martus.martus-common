/*

The Martus(tm) free, social justice documentation and
monitoring software. Copyright (C) 2001-2006, Beneficent
Technology, Inc. (Benetech).

Martus is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either
version 2 of the License, or (at your option) any later
version with the additions and exceptions described in the
accompanying Martus license file entitled "license.txt".

It is distributed WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, including warranties of fitness of purpose or
merchantability.  See the accompanying Martus License and
GPL license for more details on the required license terms
for this software.

You should have received a copy of the GNU General Public
License along with this program; if not, write to the Free
Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA 02111-1307, USA.

*/

package org.martus.common.crypto;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.x509.X509V1CertificateGenerator;
import org.martus.common.MartusConstants;
import org.martus.util.Base64;
import org.martus.util.Stopwatch;
import org.martus.util.inputstreamwithseek.ByteArrayInputStreamWithSeek;
import org.martus.util.inputstreamwithseek.InputStreamWithSeek;

public class MartusSecurity extends MartusCrypto
{
	public MartusSecurity() throws CryptoInitializationException
	{
		if(rand == null)
		{
			Stopwatch watch = new Stopwatch();
			rand = new SecureRandom();
			initialize(rand);
			System.out.println("Martus Security constructor took = "+ watch.elapsed());
		}
	}

	private void insertHighestPriorityProvider(Provider provider)
	{
		Security.insertProviderAt(provider, 1);
	}

	synchronized void initialize(SecureRandom randToUse)throws CryptoInitializationException
	{
		insertHighestPriorityProvider(new BouncyCastleProvider());

		try
		{
			pbeCipherEngine = Cipher.getInstance(PBE_ALGORITHM, "BC");
			sessionCipherEngine = Cipher.getInstance(SESSION_ALGORITHM, "BC");
			sessionKeyGenerator = KeyGenerator.getInstance(SESSION_ALGORITHM_NAME, "BC");
			keyFactory = SecretKeyFactory.getInstance(PBE_ALGORITHM, "BC");

			keyPair = new MartusJceKeyPair(randToUse);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			throw new CryptoInitializationException();
		}

		decryptedSessionKeys = new HashMap();
	}

	// begin MartusCrypto interface
	public boolean hasKeyPair()
	{
		return keyPair.hasKeyPair();
	}

	public void clearKeyPair()
	{
		keyPair.clear();
	}

	public void createKeyPair()
	{
		if(bitsInPublicKey != 2048)
			System.out.println("Creating full size public key: " + bitsInPublicKey);
		createKeyPair(bitsInPublicKey);
	}

	public void writeKeyPair(OutputStream outputStream, char[] passPhrase) throws
			Exception
	{
		writeKeyPair(outputStream, passPhrase, keyPair);
	}

	public void readKeyPair(InputStream inputStream, char[] passPhrase) throws
		IOException,
		InvalidKeyPairFileVersionException,
		AuthorizationFailedException
	{
		keyPair.clear();
		byte versionPlaceHolder = (byte)inputStream.read();
		if(versionPlaceHolder != 0)
			throw (new InvalidKeyPairFileVersionException());

		byte[] plain = decryptKeyPair(inputStream, passPhrase);
		setKeyPairFromData(plain);
	}

	public String getPublicKeyString()
	{
		return getKeyPair().getPublicKeyString();
	}

	public String getPrivateKeyString()
	{
		PrivateKey privateKey = getKeyPair().getPrivateKey();
		return(MartusJceKeyPair.getKeyString(privateKey));
	}

	public byte[] createSignatureOfStream(InputStream inputStream) throws
			MartusSignatureException
	{
		try
		{
			SignatureEngine engine = SignatureEngine.createSigner(getKeyPair());
			engine.digest(inputStream);
			return engine.getSignature();
		}
		catch (Exception e)
		{
			//System.out.println("createSignature :" + e);
			throw(new MartusSignatureException());
		}
	}

	public boolean verifySignature(InputStream inputStream, byte[] signature) throws
			MartusSignatureException
	{
		MartusKeyPair r = getKeyPair();
		return isValidSignatureOfStream(r.getPublicKeyString(), inputStream, signature);
	}

	public boolean isValidSignatureOfStream(String publicKeyString, InputStream inputStream, byte[] signature) throws
			MartusSignatureException
	{
		try
		{
			SignatureEngine engine = SignatureEngine.createVerifier(publicKeyString);
			engine.digest(inputStream);
			return engine.isValidSignature(signature);
		}
		catch (Exception e)
		{
			//System.out.println("verifySignature :" + e);
			throw(new MartusSignatureException());
		}
	}
	
	public Vector buildKeyShareBundles() 
	{
		Vector shareBundles = new Vector();
		try 
		{
			SessionKey sessionKey = createSessionKey();
			Vector sessionKeyShares = MartusSecretShare.buildShares(sessionKey.getBytes());
			
			ByteArrayInputStream in = new ByteArrayInputStream(keyPair.getKeyPairData());
			ByteArrayOutputStream encryptedKeypair = new ByteArrayOutputStream();
			encrypt(in,encryptedKeypair,sessionKey);	
			encryptedKeypair.close();
			
			KeyShareBundle bundle = new KeyShareBundle(getPublicKeyString(), encryptedKeypair.toByteArray());
			for(int i = 0; i < sessionKeyShares.size(); ++i)
			{
				String thisSharePiece = (String)(sessionKeyShares.get(i));
				shareBundles.add(bundle.createBundleString(thisSharePiece));
			}			
		} 
		catch (Exception e) 
		{
			e.printStackTrace();
			return null;
		}
		
		return shareBundles;
	}

	public void recoverFromKeyShareBundles(Vector bundles) throws KeyShareException 
	{
		clearKeyPair();
		
		if(bundles == null)
			throw new KeyShareException();
		if(bundles.size() < MartusConstants.minNumberOfFilesNeededToRecreateSecret)
			throw new KeyShareException();
			
		try 
		{
			Vector shares = new Vector();
			shares = MartusSecretShare.getSharesFromBundles(bundles);
			byte[] encryptedKeyPair = MartusSecretShare.getEncryptedKeyPairFromBundles(bundles);
			decryptAndSetKeyPair(shares, encryptedKeyPair);
		}
		catch  (KeyShareException e)
		{
			throw e;
		}
		catch (Exception e) 
		{
			e.printStackTrace();
			throw new KeyShareException();
		}
	}
	
	private void decryptAndSetKeyPair(Vector shares, byte[] keyPairEncrypted) throws
		KeyShareException,
		DecryptionException,
		IOException,
		AuthorizationFailedException 
	{
		SessionKey recoveredSessionKey = new SessionKey(MartusSecretShare.recoverShares(shares));
		ByteArrayInputStreamWithSeek inEncryptedKeyPair = new ByteArrayInputStreamWithSeek(keyPairEncrypted);
		ByteArrayOutputStream outDecryptedKeyPair = new ByteArrayOutputStream();
		decrypt( inEncryptedKeyPair, outDecryptedKeyPair, recoveredSessionKey);
		outDecryptedKeyPair.close();
		inEncryptedKeyPair.close();
		setKeyPairFromData(outDecryptedKeyPair.toByteArray());
	}

	private byte[] encryptBytes(byte[] rawBytes) throws NoKeyPairException, EncryptionException
	{
		ByteArrayOutputStream encryptedResult = new ByteArrayOutputStream();
		encrypt(new ByteArrayInputStream(rawBytes), encryptedResult);
		byte[] encryptedBytes = encryptedResult.toByteArray();
		return encryptedBytes;
	}

	public void encrypt(InputStream plainStream, OutputStream cipherStream) throws
			NoKeyPairException,
			EncryptionException
	{
		encrypt(plainStream, cipherStream, createSessionKey());
	}

	public synchronized void encrypt(InputStream plainStream, OutputStream cipherStream, SessionKey sessionKey) throws
			EncryptionException,
			NoKeyPairException
	{
		encrypt(plainStream, cipherStream, sessionKey, getPublicKeyString());
	}

	public synchronized void encrypt(InputStream plainStream, OutputStream cipherStream, SessionKey sessionKey, String publicKeyString) throws
			EncryptionException,
			NoKeyPairException
	{
		if(publicKeyString == null)
			throw new NoKeyPairException();

		CipherOutputStream cos = createCipherOutputStream(cipherStream, sessionKey, publicKeyString);
		try
		{
			InputStream bufferedPlainStream = new BufferedInputStream(plainStream);

			byte[] buffer = new byte[MartusConstants.streamBufferCopySize];
			int count = 0;
			while( (count = bufferedPlainStream.read(buffer)) >= 0)
			{
				cos.write(buffer, 0, count);
			}

			cos.close();
		}
		catch(Exception e)
		{
			//System.out.println("MartusSecurity.encrypt: " + e);
			throw new EncryptionException();
		}
	}

	public OutputStream createEncryptingOutputStream(OutputStream cipherStream, SessionKey sessionKeyBytes)
		throws EncryptionException
	{
		return createCipherOutputStream(cipherStream, sessionKeyBytes, getPublicKeyString());
	}

	public CipherOutputStream createCipherOutputStream(OutputStream cipherStream, SessionKey sessionKey, String publicKeyString)
		throws EncryptionException
	{
		try
		{
			byte[] ivBytes = new byte[IV_BYTE_COUNT];
			rand.nextBytes(ivBytes);

			byte[] encryptedKeyBytes = encryptSessionKey(sessionKey, publicKeyString).getBytes();

			SecretKey secretSessionKey = new SecretKeySpec(sessionKey.getBytes(), SESSION_ALGORITHM_NAME);
			IvParameterSpec spec = new IvParameterSpec(ivBytes);
			sessionCipherEngine.init(Cipher.ENCRYPT_MODE, secretSessionKey, spec, rand);

			OutputStream bufferedCipherStream = new BufferedOutputStream(cipherStream);
			DataOutputStream output = new DataOutputStream(bufferedCipherStream);
			output.writeInt(encryptedKeyBytes.length);
			output.write(encryptedKeyBytes);
			output.writeInt(ivBytes.length);
			output.write(ivBytes);

			CipherOutputStream cos = new CipherOutputStream(output, sessionCipherEngine);
			return cos;
		}
		catch(Exception e)
		{
			//e.printStackTrace();
			//System.out.println("MartusSecurity.createCipherOutputStream: " + e);
			throw new EncryptionException();
		}
	}

	public byte[] getSessionKeyCache() throws IOException, NoKeyPairException, EncryptionException, MartusSignatureException
	{
		ByteArrayOutputStream rawOut = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(rawOut);
		out.writeInt(CACHE_VERSION);
		out.writeInt(decryptedSessionKeys.size());
		Set keys = decryptedSessionKeys.keySet();
		for (Iterator iter = keys.iterator(); iter.hasNext(); ) 
		{
			SessionKey encryptedSessionKey = (SessionKey) iter.next();
			byte[] encryptedBytes = encryptedSessionKey.getBytes();
			out.writeInt(encryptedBytes.length);
			out.write(encryptedBytes);
			SessionKey decryptedSessionKey = getCachedDecryptedSessionKey(encryptedSessionKey);
			byte[] decryptedBytes = decryptedSessionKey.getBytes();
			out.writeInt(decryptedBytes.length);
			out.write(decryptedBytes);
		}
		out.writeInt(CACHE_VERSION);
		out.close();
		
		byte[] encryptedBytes = encryptBytes(rawOut.toByteArray());
		
		byte[] bundleBytes = createSignedBundle(encryptedBytes);

		return bundleBytes;
	}
	
	public void setSessionKeyCache(byte[] encryptedCacheBundle) throws IOException, NoKeyPairException, DecryptionException, MartusSignatureException, AuthorizationFailedException
	{
		flushSessionKeyCache();

		byte[] encryptedCacheBytes = extractFromSignedBundle(encryptedCacheBundle);
		
		byte[] decryptedBytes = decryptBytes(encryptedCacheBytes);
		
		ByteArrayInputStream rawIn = new ByteArrayInputStream(decryptedBytes);
		DataInputStream in = new DataInputStream(rawIn);
		if(in.readInt() != CACHE_VERSION)
			throw new IOException();
		int numPairs = in.readInt();
		for(int i=0; i < numPairs; ++i)
		{
			int encryptedSize = in.readInt(); 
			byte[] encrypted = new byte[encryptedSize];
			in.read(encrypted);
			int decryptedSize = in.readInt(); 
			byte[] decrypted = new byte[decryptedSize];
			in.read(decrypted);
			decryptedSessionKeys.put(new SessionKey(encrypted), new SessionKey(decrypted));
		}
		if(in.readInt() != CACHE_VERSION)
			throw new IOException();
		
		if(in.available() != 0)
			throw new IOException();
	}
	
	public byte[] createSignedBundle(byte[] dataBytes) throws MartusSignatureException, IOException
	{
		byte[] sig = createSignatureOfStream(new ByteArrayInputStream(dataBytes));
		ByteArrayOutputStream bundleRawOut = new ByteArrayOutputStream();
		DataOutputStream bundleOut = new DataOutputStream(bundleRawOut);
		bundleOut.writeInt(BUNDLE_VERSION);
		bundleOut.writeUTF(getPublicKeyString());
		bundleOut.writeInt(sig.length);
		bundleOut.write(sig);
		bundleOut.writeInt(dataBytes.length);
		bundleOut.write(dataBytes);
		bundleOut.close();
		byte[] bundleBytes = bundleRawOut.toByteArray();
		return bundleBytes;
	}

	public byte[] extractFromSignedBundle(byte[] dataBundle) throws IOException, MartusSignatureException, AuthorizationFailedException
	{
		Vector authorizedKeys = new Vector();
		authorizedKeys.add(getPublicKeyString());
		return extractFromSignedBundle(dataBundle, authorizedKeys);
	}

	public byte[] extractFromSignedBundle(byte[] dataBundle, Vector authorizedKeys) throws IOException, MartusSignatureException, AuthorizationFailedException
	{
		ByteArrayInputStream bundleRawIn = new ByteArrayInputStream(dataBundle);
		DataInputStream bundleIn = new DataInputStream(bundleRawIn);
		if(bundleIn.readInt() != BUNDLE_VERSION)
			throw new IOException();
		String signerPublicKey = bundleIn.readUTF();
		boolean authorized = false;
		for(int i = 0; i < authorizedKeys.size(); ++i)
		{
			String authorizedKey = (String)authorizedKeys.get(i);
			if(signerPublicKey.equals(authorizedKey))
			{
				authorized = true;
				break;
			}
		}
		if(!authorized)
			throw new AuthorizationFailedException();
		byte[] sig = new byte[bundleIn.readInt()];
		bundleIn.read(sig);
		byte[] dataBytes = new byte[bundleIn.readInt()];
		bundleIn.read(dataBytes);
		if(!isValidSignatureOfStream(signerPublicKey, new ByteArrayInputStream(dataBytes), sig))
			throw new MartusSignatureException();
		return dataBytes;
	}

	public synchronized void flushSessionKeyCache()
	{
		Set keys = decryptedSessionKeys.keySet();
		for (Iterator iter = keys.iterator(); iter.hasNext(); ) 
		{
			SessionKey encryptedSessionKey = (SessionKey)iter.next();
			SessionKey decryptedSessionKey = (SessionKey)decryptedSessionKeys.get(encryptedSessionKey);
			decryptedSessionKey.wipe();
		}
		decryptedSessionKeys.clear();
	}

	private void addSessionKeyToCache(SessionKey encryptedSessionKey, SessionKey decryptedSessionKey)
	{
		decryptedSessionKeys.put(encryptedSessionKey, decryptedSessionKey.copy());
	}

	private SessionKey getCachedDecryptedSessionKey(SessionKey encryptedSessionKey)
	{
		return (SessionKey)decryptedSessionKeys.get(encryptedSessionKey);
	}

	public synchronized SessionKey encryptSessionKey(SessionKey sessionKey, String publicKey) throws
		EncryptionException
	{
		try
		{
			byte[] encryptedKeyBytes = keyPair.encryptBytes(sessionKey.getBytes(), publicKey);
			SessionKey encryptedSessionKey = new SessionKey(encryptedKeyBytes);
			addSessionKeyToCache(encryptedSessionKey, sessionKey);
			return encryptedSessionKey;
		}
		catch (Exception e)
		{
			//e.printStackTrace();
			//System.out.println("MartusSecurity.encryptSessionKey: " + e);
			throw new EncryptionException();
		}
	}

	public synchronized SessionKey decryptSessionKey(SessionKey encryptedSessionKey) throws
		DecryptionException
	{
		SessionKey decrypted = getCachedDecryptedSessionKey(encryptedSessionKey);
		if(decrypted != null)
			return decrypted;

		try
		{
			byte[] bytesToDecrypt = encryptedSessionKey.getBytes();
			byte[] sessionKeyBytes = keyPair.decryptBytes(bytesToDecrypt);
			SessionKey decryptedSessionKey = new SessionKey(sessionKeyBytes);
			addSessionKeyToCache(encryptedSessionKey, decryptedSessionKey);
			return decryptedSessionKey;
		}
		catch(Exception e)
		{
			//System.out.println("MartusSecurity.decryptSessionKey: " + e);
			//e.printStackTrace();
			throw new DecryptionException();
		}
	}

	public void decrypt(InputStreamWithSeek cipherStream, OutputStream plainStream) throws
		NoKeyPairException,
		DecryptionException
	{
		if(!hasKeyPair())
			throw new NoKeyPairException();
		
		decrypt(cipherStream, plainStream, null);
	}
	
	SessionKey readSessionKey(DataInputStream dis) throws DecryptionException
	{
		byte[] encryptedKeyBytes = null;
		
		try
		{
			int keyByteCount = dis.readInt();
			if(keyByteCount > ARBITRARY_MAX_SESSION_KEY_LENGTH)
				throw new DecryptionException();
			encryptedKeyBytes = new byte[keyByteCount];
			dis.readFully(encryptedKeyBytes);
		}
		catch(Exception e)
		{
			//System.out.println("MartusSecurity.decrypt: " + e);
			//e.printStackTrace();
			throw new DecryptionException();
		}
		return new SessionKey(encryptedKeyBytes);
	}

	private byte[] decryptBytes(byte[] encryptedBytes) throws NoKeyPairException, DecryptionException
	{
		ByteArrayOutputStream cacheBytes = new ByteArrayOutputStream();
		decrypt(new ByteArrayInputStreamWithSeek(encryptedBytes), cacheBytes);
		byte[] decryptedBytes = cacheBytes.toByteArray();
		return decryptedBytes;
	}

	public synchronized void decrypt(InputStreamWithSeek cipherStream, OutputStream plainStream, SessionKey sessionKey) throws
			DecryptionException
	{
		InputStream cis = createDecryptingInputStream(cipherStream, sessionKey);
		BufferedOutputStream bufferedPlainStream = new BufferedOutputStream(plainStream);
		try
		{
			final int SIZE = MartusConstants.streamBufferCopySize;
			byte[] chunk = new byte[SIZE];
			int count = 0;
			while((count = cis.read(chunk)) != -1)
			{
				bufferedPlainStream.write(chunk, 0, count);
			}
			cis.close();
			bufferedPlainStream.flush();
		}
		catch(Exception e)
		{
			//System.out.println("MartusSecurity.decrypt: " + e);
			throw new DecryptionException();
		}
	}

	public InputStream createDecryptingInputStream(InputStreamWithSeek cipherStream, SessionKey sessionKey)
		throws	DecryptionException
	{
		try
		{	
			DataInputStream dis = new DataInputStream(cipherStream);
			SessionKey storedSessionKey = readSessionKey(dis);
			if(sessionKey == null)
			{
				sessionKey = decryptSessionKey(storedSessionKey);
			}

			int ivByteCount = dis.readInt();
			byte[] iv = new byte[ivByteCount];
			dis.readFully(iv);

			SecretKey secretSessionKey = new SecretKeySpec(sessionKey.getBytes(), SESSION_ALGORITHM_NAME);
			IvParameterSpec spec = new IvParameterSpec(iv);

			sessionCipherEngine.init(Cipher.DECRYPT_MODE, secretSessionKey, spec, rand);
			CipherInputStream cis = new CipherInputStream(dis, sessionCipherEngine);

			return cis;
		}
		catch(Exception e)
		{
			//System.out.println("MartusSecurity.createCipherInputStream: " + e);
			//e.printStackTrace();
			throw new DecryptionException();
		}
	}

	public synchronized SessionKey createSessionKey()
	{
		sessionKeyGenerator.init(bitsInSessionKey, rand);
		return new SessionKey(sessionKeyGenerator.generateKey().getEncoded());
	}

	public SignatureEngine createSignatureVerifier(String signedByPublicKey) throws Exception
	{
		return SignatureEngine.createVerifier(signedByPublicKey);
	}
	
	public synchronized String createSignatureOfVectorOfStrings(Vector dataToSign) throws MartusCrypto.MartusSignatureException 
	{
		try
		{
			SignatureEngine signer = SignatureEngine.createSigner(getKeyPair());
			for(int element = 0; element < dataToSign.size(); ++element)
			{
				String thisElement = dataToSign.get(element).toString();
				byte[] bytesToSign = thisElement.getBytes("UTF-8");
				signer.digest(bytesToSign);
				signer.digest((byte)0);
			}
			return Base64.encode(signer.getSignature());
		}
		catch(Exception e)
		{
			// TODO: Needs tests!
			e.printStackTrace();
			System.out.println("ServerProxy.sign: " + e);
			throw new MartusCrypto.MartusSignatureException();
		}
	}
	public synchronized boolean verifySignatureOfVectorOfStrings(Vector dataToTest, String signedBy, String sig) 
	{
		try
		{
			SignatureEngine verifier = SignatureEngine.createVerifier(signedBy);
			for(int element = 0; element < dataToTest.size(); ++element)
			{
				String thisElement = dataToTest.get(element).toString();
				byte[] bytesToSign = thisElement.getBytes("UTF-8");
				verifier.digest(bytesToSign);
				verifier.digest((byte)0);
			}
			byte[] sigBytes = Base64.decode(sig);
			return verifier.isValidSignature(sigBytes);
		}
		catch(Exception e)
		{
			return false;
		}
	}
		
	
	public static String createRandomToken()
	{
		byte[] token = new byte[TOKEN_BYTE_COUNT];
		rand.nextBytes(token);

		return Base64.encode(token);
	}

	public KeyManager [] createKeyManagers() throws Exception
	{
		String passphrase = "this passphrase is never saved to disk";

		KeyStore keyStore = KeyStore.getInstance("BKS", "BC");
		keyStore.load(null, null );
		KeyPair sunKeyPair = createSunKeyPair(bitsInPublicKey);
		RSAPublicKey sslPublicKey = (RSAPublicKey) sunKeyPair.getPublic();
		RSAPrivateCrtKey sslPrivateKey = (RSAPrivateCrtKey)sunKeyPair.getPrivate();


		RSAPublicKey serverPublicKey = (RSAPublicKey)getKeyPair().getPublicKey();

		RSAPrivateCrtKey serverPrivateKey = (RSAPrivateCrtKey)getKeyPair().getPrivateKey();
		X509Certificate cert0 = createCertificate(sslPublicKey, sslPrivateKey );
		X509Certificate cert1 = createCertificate(sslPublicKey, serverPrivateKey);
		X509Certificate cert2 = createCertificate(serverPublicKey, serverPrivateKey);


		Certificate[] chain = {cert0, cert1, cert2};
		keyStore.setKeyEntry( "cert", sslPrivateKey, passphrase.toCharArray(), chain );

		KeyManagerFactory kmf = KeyManagerFactory.getInstance( "SunX509" );
		kmf.init( keyStore, passphrase.toCharArray() );
		return kmf.getKeyManagers();
	}

	// end interface

	public void writeKeyPair(OutputStream outputStream, char[] passPhrase, MartusKeyPair keyPairToUse) throws
			Exception
	{
		byte[] randomSalt = createRandomSalt();
		byte[] keyPairData = keyPairToUse.getKeyPairData();
		byte[] cipherText = pbeEncrypt(keyPairData, passPhrase, randomSalt);
		if(cipherText == null)
			return;
		byte versionPlaceHolder = 0;
		outputStream.write(versionPlaceHolder);
		outputStream.write(randomSalt);
		outputStream.write(cipherText);
		outputStream.flush();
	}

	public void setKeyPairFromData(byte[] data) throws
		AuthorizationFailedException
	{
		keyPair.clear();
		try
		{
			keyPair.setFromData(data);
		}
		catch(Exception e)
		{
			//e.printStackTrace();
			//System.out.println("setKeyPairFromData: " + e);
			throw (new AuthorizationFailedException());
		}
	}

	public MartusKeyPair getKeyPair()
	{
		return keyPair;
	}
	
	public static byte[] createRandomSalt()
	{
		byte[] salt = new byte[SALT_BYTE_COUNT];
		rand.nextBytes(salt);
		return salt;
	}

	public synchronized boolean isKeyPairValid(KeyPair candidatePair)
	{
		return MartusJceKeyPair.isKeyPairValid(candidatePair);
	}

	public byte[] decryptKeyPair(InputStream inputStream, char[] passPhrase) throws IOException
	{
		byte[] salt = new byte[SALT_BYTE_COUNT];
		inputStream.read(salt);

		byte[] cipherText = new byte[inputStream.available()];
		inputStream.read(cipherText);

		return pbeDecrypt(cipherText, passPhrase, salt);
	}

	public byte[] pbeEncrypt(byte[] inputText, char[] passPhrase, byte[] salt)
	{
		return pbeEncryptDecrypt(Cipher.ENCRYPT_MODE, inputText, passPhrase, salt);
	}

	public byte[] pbeDecrypt(byte[] inputText, char[] passPhrase, byte[] salt)
	{
		return pbeEncryptDecrypt(Cipher.DECRYPT_MODE, inputText, passPhrase, salt);
	}

	private synchronized byte[] pbeEncryptDecrypt(int mode, byte[] inputText, char[] passPhrase, byte[] salt)
	{
		try
		{
			PBEKeySpec keySpec = new PBEKeySpec(passPhrase);
			SecretKey key = keyFactory.generateSecret(keySpec);
			PBEParameterSpec paramSpec = new PBEParameterSpec(salt, ITERATION_COUNT);

			pbeCipherEngine.init(mode, key, paramSpec, rand);
			byte[] outputText = pbeCipherEngine.doFinal(inputText);
			return outputText;
		}
		catch(Exception e)
		{
			//System.out.println("pbeEncryptDecrypt: " + e);
		}

		return null;
	}

	public synchronized void createKeyPair(int publicKeyBits)
	{
		try
		{
			keyPair.clear();
			keyPair.createRSA(publicKeyBits);
		}
		catch(Exception e)
		{
			System.out.println("createKeyPair " + e);
		}

	}

	BigInteger createCertificateSerialNumber()
	{
		return new BigInteger(128, rand);
	}

	synchronized KeyPair createSunKeyPair(int bitsInKey) throws Exception
	{
		KeyPairGenerator sunKeyPairGenerator = KeyPairGenerator.getInstance("RSA");
    	sunKeyPairGenerator.initialize( bitsInKey );
		KeyPair sunKeyPair = sunKeyPairGenerator.genKeyPair();
		return sunKeyPair;
	}

	public X509Certificate createCertificate(RSAPublicKey publicKey, RSAPrivateCrtKey privateKey)
			throws SecurityException, SignatureException, InvalidKeyException
	{
		Hashtable attrs = new Hashtable();

		Vector ord = new Vector();
		Vector values = new Vector();

		ord.addElement(X509Principal.C);
		ord.addElement(X509Principal.O);
		ord.addElement(X509Principal.L);
		ord.addElement(X509Principal.ST);
		ord.addElement(X509Principal.EmailAddress);

		final String certificateCountry = "US";
		final String certificateOrganization = "Benetech";
		final String certificateLocation = "Palo Alto";
		final String certificateState = "CA";
		final String certificateEmail = "martus@benetech.org";

		values.addElement(certificateCountry);
		values.addElement(certificateOrganization);
		values.addElement(certificateLocation);
		values.addElement(certificateState);
		values.addElement(certificateEmail);

		attrs.put(X509Principal.C, certificateCountry);
		attrs.put(X509Principal.O, certificateOrganization);
		attrs.put(X509Principal.L, certificateLocation);
		attrs.put(X509Principal.ST, certificateState);
		attrs.put(X509Principal.EmailAddress, certificateEmail);

		// create a certificate
		X509V1CertificateGenerator  certGen1 = new X509V1CertificateGenerator();

		certGen1.setSerialNumber(createCertificateSerialNumber());
		certGen1.setIssuerDN(new X509Principal(ord, attrs));
		certGen1.setNotBefore(new Date(System.currentTimeMillis() - 50000));
		certGen1.setNotAfter(new Date(System.currentTimeMillis() + 50000));
		certGen1.setSubjectDN(new X509Principal(ord, values));
		certGen1.setPublicKey( publicKey );
		certGen1.setSignatureAlgorithm("MD5WithRSAEncryption");

		// self-sign it
		X509Certificate cert = certGen1.generateX509Certificate( privateKey );
		return cert;
	}

	protected static byte[] createDigest(ByteArrayInputStream in)
		throws IOException, CreateDigestException
	{
		try
		{
			MessageDigest digester = MessageDigest.getInstance(DIGEST_ALGORITHM);
			digester.reset();
			int got;
			byte[] bytes = new byte[MartusConstants.digestBufferSize];
			while( (got=in.read(bytes)) >= 0)
				digester.update(bytes, 0, got);
			return digester.digest();
		}
		catch(NoSuchAlgorithmException e)
		{
			throw new CreateDigestException();
		}
	}

	public byte[] getDigestOfPartOfPrivateKey() throws CreateDigestException
	{
		try
		{
			return getKeyPair().getDigestOfPartOfPrivateKey();
		}
		catch (Exception e)
		{
			throw new CreateDigestException();
		}
	}
	
	static public String geEncryptedFileIdentifier()
	{
		return ENCRYPTED_FILE_VERSION_IDENTIFIER;
	}
	
	public void verifyJars() throws MartusCrypto.InvalidJarException, IOException
	{
		// for bcprov, look for BCKEY.SF (BCKEY.SIG)
		// for bc-jce, look for SSMTSJAR.SF (SSMTSJAR.SIG)
		
 		URL jceJarURL = getJarURL(Cipher.class);
 		if(jceJarURL.toString().indexOf("bc-jce") < 0)
 		{
			String hintsToSolve = "\n\nXbootclasspath might be incorrect; bc-jce.jar might be missing from Martus/lib/ext";
 			throw new InvalidJarException("Didn't load bc-jce.jar" + hintsToSolve);
 		}
		verifySignedKeyFile("bc-jce.jar", jceJarURL, "SSMTSJAR");
		
 		URL bcprovJarURL = getJarURL(RSAEngine.class);
 		String bcprovJarName = BCPROV_JAR_FILE_NAME;
 		if(bcprovJarURL.toString().indexOf(bcprovJarName) < 0)
 		{
 			String hintsToSolve = "\n\nMake sure " + bcprovJarName + " is the only bcprov file in Martus/lib/ext";
 			throw new InvalidJarException("Didn't load " + bcprovJarName + hintsToSolve);
 		}
		verifySignedKeyFile(bcprovJarName, bcprovJarURL, "BCKEY");
	}

	public void verifySignedKeyFile(String jarDescription, Class c, String keyFileNameWithoutExtension) throws MartusCrypto.InvalidJarException, IOException
	{
		URL jarURL = getJarURL(c);
		verifySignedKeyFile(jarDescription, jarURL, keyFileNameWithoutExtension);
	}

	private void verifySignedKeyFile(String jarDescription, URL jarURL, String keyFileNameWithoutExtension) throws IOException, InvalidJarException
	{
		String keyFileOutsideOfMartus = keyFileNameWithoutExtension + SIGNATURE_FILE_EXTENSION;
		String keyFileInMartusJar = keyFileNameWithoutExtension + MARTUS_SIGNATURE_FILE_EXTENSION;
		
		String errorMessageStart = "Verifying " + jarDescription + ": ";
		JarURLConnection jarConnection = (JarURLConnection)jarURL.openConnection();
		JarFile jf = jarConnection.getJarFile();
		JarEntry entry = jf.getJarEntry("META-INF/" + keyFileOutsideOfMartus);
		if(entry == null)
		{
			String basicErrorMessage = "Missing: " + keyFileOutsideOfMartus + " from " + jarURL;
			String hintsToSolve = "\n\nA jar file may be damaged. Try re-installing Martus.";
			throw new MartusCrypto.InvalidJarException(errorMessageStart + basicErrorMessage + hintsToSolve);
		}
		int size = (int)entry.getSize();
		
		InputStream actualKeyFileIn = jf.getInputStream(entry);
		byte[] actual = null;
		try
		{
			actual = readAll(size, actualKeyFileIn);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			String basicErrorMessage = "Error reading Actual Key in File";
			throw new MartusCrypto.InvalidJarException(errorMessageStart + basicErrorMessage);
		}
		
		InputStream referenceKeyFileIn = getClass().getResourceAsStream(keyFileInMartusJar);
		if(referenceKeyFileIn == null)
		{
			String basicErrorMessage = "Couldn't open " + keyFileInMartusJar + " in Martus jar";
			throw new MartusCrypto.InvalidJarException(errorMessageStart + basicErrorMessage);
		}
		byte[] expected = null;
		try
		{
			expected = readAll(size, referenceKeyFileIn);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			String basicErrorMessage = "Error reading Reference Key in File";
			throw new MartusCrypto.InvalidJarException(errorMessageStart + basicErrorMessage);
		}
		
		if(!Arrays.equals(expected, actual))
		{
			String basicErrorMessage = "Unequal contents for: " + keyFileNameWithoutExtension;
			String hintsToSolve = "Might be wrong version of jar (" + jarURL + ")";
			throw new MartusCrypto.InvalidJarException(errorMessageStart + basicErrorMessage + hintsToSolve);
		}
	}
	
	private byte[] readAll(int size, InputStream streamToRead) throws IOException
	{
		byte[] gotBytes = new byte[size];
		int got = 0;
		while(true)
		{
			int thisByte = streamToRead.read();
			if(thisByte < 0)
				break;
			gotBytes[got++] = (byte)thisByte;
		}
		streamToRead.close();
		return gotBytes;
	}
	
	private static URL getJarURL(Class c) throws MartusCrypto.InvalidJarException, MalformedURLException
	{
		String name = c.getName();
		int lastDot = name.lastIndexOf('.');
		String classFileName = name.substring(lastDot + 1) + ".class";
		URL url = c.getResource(classFileName);
		String wholePath = url.toString();
		int bangAt = wholePath.indexOf('!');
		if(bangAt < 0)
			throw new MartusCrypto.InvalidJarException("Couldn't find ! in jar path: " + url);
		
		String jarPart = wholePath.substring(0, bangAt+2);
		URL jarURL = new URL(jarPart);
		return jarURL;
	}

	private static final String BCPROV_JAR_FILE_NAME = "bcprov-jdk14-128.jar";
	private static final String SESSION_ALGORITHM_NAME = "AES";
	private static final String SESSION_ALGORITHM = "AES/CBC/PKCS5Padding";
	private static final String PBE_ALGORITHM = "PBEWithSHAAndTwofish-CBC";
	private static final String DIGEST_ALGORITHM = "SHA1";
	private static final String ENCRYPTED_FILE_VERSION_IDENTIFIER = "Martus Encrypted File Version 001";
	private static final String MARTUS_SIGNATURE_FILE_EXTENSION = ".SIG";
	private static final String SIGNATURE_FILE_EXTENSION = ".SF";

	private static final int CACHE_VERSION = 1;
	private static final int BUNDLE_VERSION = 1;
	
	private static final int ARBITRARY_MAX_SESSION_KEY_LENGTH = 8192;
	private static SecureRandom rand;
	private MartusKeyPair keyPair;
	private Map decryptedSessionKeys;

	private Cipher pbeCipherEngine;
	private Cipher sessionCipherEngine;
	private KeyGenerator sessionKeyGenerator;
	private SecretKeyFactory keyFactory;
}
