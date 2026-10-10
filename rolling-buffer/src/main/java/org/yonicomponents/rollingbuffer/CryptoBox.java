package org.yonicomponents.rollingbuffer;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/** An independently authenticated encrypted object. The file name is authenticated too. */
public final class CryptoBox {
    private final SecretKey key;
    public CryptoBox(SecretKey key) { this.key=key; }
    public byte[] seal(byte[] clear, String name) throws IOException {
        try {
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE,key);
            byte[] iv=c.getIV();
            if (iv.length!=12) throw new IOException("Unsupported GCM nonce length");
            c.updateAAD(("RollingAudio/v1/"+name).getBytes(StandardCharsets.UTF_8));
            byte[] cipher=c.doFinal(clear), out=new byte[13+cipher.length];
            out[0]=1; System.arraycopy(iv,0,out,1,12); System.arraycopy(cipher,0,out,13,cipher.length);
            return out;
        } catch (GeneralSecurityException e) { throw new IOException("Encryption failed",e); }
    }
    public byte[] open(byte[] data,String name) throws IOException {
        if(data.length<29 || data[0]!=1) throw new IOException("Invalid encrypted object");
        try {
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOfRange(data,1,13)));
            c.updateAAD(("RollingAudio/v1/"+name).getBytes(StandardCharsets.UTF_8));
            return c.doFinal(data,13,data.length-13);
        } catch (GeneralSecurityException e) { throw new IOException("Authentication failed; original files preserved",e); }
    }
}

