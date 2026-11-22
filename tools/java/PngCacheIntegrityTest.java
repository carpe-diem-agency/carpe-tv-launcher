package agency.carpediem.tv.prototype;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/** Standalone JDK test. Fixtures and outputs belong on the supplied SSD directory. */
public final class PngCacheIntegrityTest {
    public static void main(String[] args) throws Exception {
        if(args.length!=1) throw new IllegalArgumentException("Supply existing SSD fixture parent");
        Path dir=Files.createTempDirectory(Path.of(args[0]),"png-integrity-");
        Path file=dir.resolve("cache.png");
        try {
            byte[] valid=png(1,1,false);
            check(file,valid,true,"valid RGBA PNG");
            check(file,png(512,512,false),true,"dimension boundary structure");
            check(file,png(513,1,false),false,"oversized dimension");
            check(file,png(0,1,false),false,"zero dimension");
            check(file,png(1,1,true),true,"unknown ancillary with valid CRC");
            for(int length=0;length<valid.length;length++) check(file,Arrays.copyOf(valid,length),false,"truncation "+length);
            byte[] changed=valid.clone();changed[45]^=1;check(file,changed,false,"interior data CRC");
            changed=valid.clone();changed[8]=0x7f;check(file,changed,false,"chunk length beyond file");
            changed=valid.clone();changed[changed.length-1]^=1;check(file,changed,false,"bad IEND CRC");
            check(file,Arrays.copyOf(valid,valid.length+1),false,"trailing byte");
            changed=valid.clone();changed[0]=0;check(file,changed,false,"signature");
            check(file,new byte[(int)PngCacheIntegrity.MAX_BYTES+1],false,"file size cap");
            Files.delete(file);
            if(PngCacheIntegrity.valid(file.toFile())) throw new AssertionError("missing accepted");
            System.out.println("PASS: PNG structure, bounds, CRC, ancillary, truncation and cleanup");
        } finally {Files.deleteIfExists(file);Files.delete(dir);}
    }
    private static void check(Path file,byte[] bytes,boolean expected,String label) throws Exception {
        Files.write(file,bytes);
        if(PngCacheIntegrity.valid(file.toFile())!=expected) throw new AssertionError(label);
    }
    private static byte[] png(int width,int height,boolean ancillary) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeLong(0x89504e470d0a1a0aL);
        ByteArrayOutputStream header=new ByteArrayOutputStream();DataOutputStream h=new DataOutputStream(header);
        h.writeInt(width);h.writeInt(height);h.write(new byte[]{8,6,0,0,0});chunk(out,"IHDR",header.toByteArray());
        if(ancillary) chunk(out,"vpAg",new byte[]{1,2,3});
        ByteArrayOutputStream compressed=new ByteArrayOutputStream();
        try(DeflaterOutputStream z=new DeflaterOutputStream(compressed)) {
            for(int row=0;row<height;row++) {z.write(0);for(int col=0;col<width;col++) z.write(new byte[]{10,20,30,(byte)255});}
        }
        chunk(out,"IDAT",compressed.toByteArray());chunk(out,"IEND",new byte[0]);return bytes.toByteArray();
    }
    private static void chunk(DataOutputStream out,String name,byte[] data) throws Exception {
        byte[] type=name.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        CRC32 crc=new CRC32();crc.update(type);crc.update(data);
        out.writeInt(data.length);out.write(type);out.write(data);out.writeInt((int)crc.getValue());
    }
}
