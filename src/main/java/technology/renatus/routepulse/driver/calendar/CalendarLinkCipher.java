package technology.renatus.routepulse.driver.calendar;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CalendarLinkCipher {
    private final SecretKeySpec key;
    public CalendarLinkCipher(@Value("${app.calendar.encryption-key:${app.security.remember-me.key}}") String secret) {
        try {
            var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            key = new SecretKeySpec(mac.doFinal("shuttle:ukg-calendar:v1".getBytes(StandardCharsets.UTF_8)),"AES");
        } catch (Exception e) { throw new IllegalStateException("Calendar encryption could not be initialized."); }
    }
    public String encrypt(Long org, Long driver, String text) {
        try {
            byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            var cipher = cipher(Cipher.ENCRYPT_MODE, org, driver, iv);
            return "v1." + Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("The calendar link could not be saved securely."); }
    }
    public String fingerprint(String link) {
        try {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(link.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException("The calendar link could not be checked.");}
    }
    public String decrypt(Long org, Long driver, String value) {
        try {
            String[] parts = value.split("\\."); if (parts.length != 3 || !parts[0].equals("v1")) throw new IllegalArgumentException();
            var cipher = cipher(Cipher.DECRYPT_MODE, org, driver, Base64.getDecoder().decode(parts[1]));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Reconnect the UKG calendar to refresh its access."); }
    }
    private Cipher cipher(int mode, Long org, Long driver, byte[] iv) throws Exception {
        var cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(mode,key,new GCMParameterSpec(128,iv));
        cipher.updateAAD((org+":"+driver).getBytes(StandardCharsets.UTF_8)); return cipher;
    }
}
