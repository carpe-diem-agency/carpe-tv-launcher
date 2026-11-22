package agency.carpediem.tv.prototype;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.CRC32;

/** Bounded structural integrity check before trusting Android's tolerant PNG decoder. */
final class PngCacheIntegrity {
    static final long MAX_BYTES = 4L * 1024 * 1024;
    private static final long SIGNATURE = 0x89504e470d0a1a0aL;
    static boolean valid(File file) {
        long size;
        try {size=file.length();} catch(SecurityException unavailable) {return false;}
        if(size<57 || size>MAX_BYTES) return false;
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if(in.readLong()!=SIGNATURE) return false;
            long remaining=size-8;
            boolean header=false,data=false,dataEnded=false;
            byte[] buffer=new byte[4096];
            while(remaining>=12) {
                long length=Integer.toUnsignedLong(in.readInt());
                int type=in.readInt();remaining-=12;
                if(length>remaining) return false;
                for(int shift=24;shift>=0;shift-=8) {
                    int letter=(type>>>shift)&255;
                    if(!((letter>='A'&&letter<='Z')||(letter>='a'&&letter<='z'))) return false;
                }
                if((type&0x2000)!=0) return false; // PNG's reserved type bit.
                if(!header && type!=0x49484452) return false;
                CRC32 crc=new CRC32();
                crc.update(type>>>24);crc.update(type>>>16);crc.update(type>>>8);crc.update(type);
                if(type==0x49484452) {
                    if(header || length!=13) return false;
                    in.readFully(buffer,0,13);crc.update(buffer,0,13);
                    long width=unsignedInt(buffer,0),height=unsignedInt(buffer,4);
                    int depth=buffer[8]&255,color=buffer[9]&255;
                    boolean depthValid=color==0 ? depth==1||depth==2||depth==4||depth==8||depth==16
                            : color==3 ? depth==1||depth==2||depth==4||depth==8
                            : (color==2||color==4||color==6)&&(depth==8||depth==16);
                    if(width<1||width>512||height<1||height>512||!depthValid
                            ||buffer[10]!=0||buffer[11]!=0||(buffer[12]!=0&&buffer[12]!=1)) return false;
                    header=true;
                } else {
                    if(type==0x49444154) {if(dataEnded) return false;data=true;}
                    else if(data) dataEnded=true;
                    if(type==0x49454e44 && (length!=0||!data)) return false;
                    // Unknown critical chunks cannot be decoded safely. Ancillary chunks are allowed.
                    if((type&0x20000000)==0 && type!=0x49444154&&type!=0x49454e44&&type!=0x504c5445) return false;
                    for(long left=length;left>0;) {
                        int count=(int)Math.min(left,buffer.length);in.readFully(buffer,0,count);
                        crc.update(buffer,0,count);left-=count;
                    }
                }
                if(Integer.toUnsignedLong(in.readInt())!=crc.getValue()) return false;
                remaining-=length;
                if(type==0x49454e44) return remaining==0&&in.read()==-1;
            }
            return false;
        } catch(IOException|SecurityException error) {return false;}
    }
    private static long unsignedInt(byte[] b,int offset) {
        return ((long)(b[offset]&255)<<24)|((long)(b[offset+1]&255)<<16)
                |((long)(b[offset+2]&255)<<8)|(b[offset+3]&255);
    }
    private PngCacheIntegrity() {}
}
