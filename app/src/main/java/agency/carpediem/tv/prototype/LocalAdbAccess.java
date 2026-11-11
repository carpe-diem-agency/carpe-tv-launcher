package agency.carpediem.tv.prototype;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;

/** Local-device bootstrap only. No caller-provided host, command, or shell endpoint. */
final class LocalAdbAccess extends AbsAdbConnectionManager {
    private static final String ALIAS="carpe-local-adb-wrap";
    private final PrivateKey privateKey;
    private final java.security.cert.Certificate certificate;
    LocalAdbAccess(Context context) throws Exception {
        setApi(Build.VERSION.SDK_INT); setHostAddress("127.0.0.1");
        setTimeout(12,TimeUnit.SECONDS); setThrowOnUnauthorised(true);
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)) {
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();
        }
        SecretKey wrap=(SecretKey)store.getKey(ALIAS,null);
        AtomicFile file=new AtomicFile(new File(context.getNoBackupFilesDir(),"local-adb.identity"));
        if(file.getBaseFile().exists()) {
            byte[] plain;
            try(DataInputStream in=new DataInputStream(file.openRead())) {
                int n=in.readUnsignedByte();if(n!=12)throw new IOException("Invalid identity");
                byte[] iv=new byte[n];in.readFully(iv);byte[] encrypted=readLimited(in,16384);
                Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,wrap,new GCMParameterSpec(128,iv));plain=cipher.doFinal(encrypted);
            }
            try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(plain))) {
                int n=in.readInt();if(n<1||n>8192)throw new IOException("Invalid identity");byte[] key=new byte[n];in.readFully(key);
                privateKey=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(key));Arrays.fill(key,(byte)0);
                certificate=CertificateFactory.getInstance("X.509").generateCertificate(in);
            } finally {Arrays.fill(plain,(byte)0);}
        } else {
            KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);java.security.KeyPair pair=generator.generateKeyPair();
            privateKey=pair.getPrivate();X500Name name=new X500Name("CN=Carpe TV local access");
            Date from=new Date(System.currentTimeMillis()-86400000L),to=new Date(System.currentTimeMillis()+315360000000L);
            certificate=new JcaX509CertificateConverter().getCertificate(new JcaX509v3CertificateBuilder(name,
                    new BigInteger(128,new SecureRandom()).add(BigInteger.ONE),from,to,name,pair.getPublic())
                    .build(new JcaContentSignerBuilder("SHA256withRSA").build(privateKey)));
            ByteArrayOutputStream out=new ByteArrayOutputStream();DataOutputStream data=new DataOutputStream(out);
            byte[] key=privateKey.getEncoded();data.writeInt(key.length);data.write(key);data.write(certificate.getEncoded());Arrays.fill(key,(byte)0);
            byte[] plain=out.toByteArray();Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,wrap);
            byte[] encrypted=cipher.doFinal(plain);Arrays.fill(plain,(byte)0);
            FileOutputStream target=null;
            try {target=file.startWrite();target.write(cipher.getIV().length);target.write(cipher.getIV());target.write(encrypted);file.finishWrite(target);}
            catch(Exception error){if(target!=null)file.failWrite(target);throw error;}
        }
    }
    static byte[] readLimited(InputStream in,int limit)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[1024];int n;
        while((n=in.read(buf))!=-1){if(out.size()+n>limit)throw new IOException("Output limit");out.write(buf,0,n);}return out.toByteArray();
    }
    @Override protected PrivateKey getPrivateKey(){return privateKey;}
    @Override protected java.security.cert.Certificate getCertificate(){return certificate;}
    @Override protected String getDeviceName(){return "CarpeTV-local";}
    boolean pairLocal(int port,String code)throws Exception {
        if(port<1024||port>65535||!code.matches("[0-9]{6}"))throw new IllegalArgumentException("Invalid pairing input");
        return pair("127.0.0.1",port,code);
    }
    void startShizuku(Context context,int port)throws Exception {
        if(port<1024||port>65535)throw new IllegalArgumentException("Invalid port");
        String dir=context.getPackageManager().getApplicationInfo("moe.shizuku.privileged.api",0).nativeLibraryDir;
        if(dir==null||!dir.startsWith("/data/app/")||dir.contains("..")||dir.contains("'"))throw new IOException("Starter unavailable");
        if(!connect("127.0.0.1",port))throw new IOException("Connection failed");
        java.util.Timer watchdog=new java.util.Timer(true);
        watchdog.schedule(new java.util.TimerTask(){public void run(){try{disconnect();}catch(Exception ignored){}}},20000);
        try(AdbStream stream=openStream("shell:exec '"+dir+"/libshizuku.so'")) {
            String output=new String(readLimited(stream.openInputStream(),65536),java.nio.charset.StandardCharsets.UTF_8);
            if(!output.contains("shizuku")&&!output.contains("Shizuku"))throw new IOException("Starter did not respond");
        } finally {watchdog.cancel();disconnect();}
    }
    static void forget(Context context)throws Exception {
        new AtomicFile(new File(context.getNoBackupFilesDir(),"local-adb.identity")).delete();
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(ALIAS))store.deleteEntry(ALIAS);
        context.getSharedPreferences("system_setup",0).edit().clear().apply();
    }
}
