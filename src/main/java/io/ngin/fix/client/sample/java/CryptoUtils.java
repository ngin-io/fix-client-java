package io.ngin.fix.client.sample.java;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class CryptoUtils {

	private static final String ALGORITHM = "HmacSHA512";

	public static String signMessage(int seqNum, String messageType, String apiKey, String timestamp, String secretKey) {
		try {
			String msg = seqNum + messageType + apiKey + timestamp;
			Mac mac = Mac.getInstance(ALGORITHM);
			SecretKeySpec secret_spec = new SecretKeySpec(Base64Decode(secretKey), ALGORITHM);
			mac.init(secret_spec);
			return Base64Encode(mac.doFinal(msg.getBytes()));
		} catch (Exception e) {
			throw new RuntimeException("Could not decode Base 64 string", e);
		}
	}

	private static byte[] Base64Decode(String data) {
		return Base64.getDecoder().decode(data);
	}

	private static String Base64Encode(byte[] data) {
		return Base64.getEncoder().encodeToString(data);
	}
}
