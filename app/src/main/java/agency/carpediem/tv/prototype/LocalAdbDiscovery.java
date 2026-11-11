package agency.carpediem.tv.prototype;
import android.content.Context;
import android.net.nsd.*;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.net.*;
import java.util.*;

/** Discovers a port only after checking that its advertised address belongs to this device. */
final class LocalAdbDiscovery {
    private static final String TAG="CarpeLocalAdb";
    interface Result { void found(int port); void failed(); }
    private final NsdManager nsd;
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean finished,resolving;
    private NsdManager.DiscoveryListener discovery;
    LocalAdbDiscovery(Context context){nsd=context.getSystemService(NsdManager.class);}
    void start(boolean pairing,Result result) {
        discovery=new NsdManager.DiscoveryListener(){
            public void onDiscoveryStarted(String t){Log.i(TAG,"Local ADB discovery started");}
            public void onDiscoveryStopped(String t){}
            public void onStopDiscoveryFailed(String t,int c){}
            public void onStartDiscoveryFailed(String t,int c){Log.w(TAG,"Local ADB discovery failed: "+c);main.post(()->fail(result));}
            public void onServiceLost(NsdServiceInfo info){}
            public void onServiceFound(NsdServiceInfo info){Log.i(TAG,"Local ADB service found");main.post(()->{
                if(finished||resolving)return;resolving=true;
                nsd.resolveService(info,new NsdManager.ResolveListener(){
                    public void onResolveFailed(NsdServiceInfo s,int c){Log.w(TAG,"Local ADB resolve failed: "+c);main.post(()->resolving=false);}
                    public void onServiceResolved(NsdServiceInfo s){main.post(()->{
                        resolving=false;if(finished)return;
                        if(!isLocal(s.getHost())){Log.w(TAG,"Resolved ADB service was not local");return;}
                        int port=s.getPort();if(port<1024||port>65535)return;
                        Log.i(TAG,"Local ADB port resolved");
                        stop();result.found(port);
                    });}
                });
            });}
        };
        try {nsd.discoverServices(pairing?"_adb-tls-pairing._tcp.":"_adb-tls-connect._tcp.",NsdManager.PROTOCOL_DNS_SD,discovery);}
        catch(RuntimeException error){fail(result);return;}
        // The user must navigate several OEM settings screens with a TV remote.
        main.postDelayed(()->fail(result),pairing?900000:20000);
    }
    private static boolean isLocal(InetAddress address){
        if(address==null)return false;if(address.isLoopbackAddress())return true;
        try {for(NetworkInterface i:Collections.list(NetworkInterface.getNetworkInterfaces()))
            for(InetAddress local:Collections.list(i.getInetAddresses()))if(local.equals(address))return true;
        }catch(SocketException ignored){}return false;
    }
    private void fail(Result r){if(!finished){stop();r.failed();}}
    void stop(){if(finished)return;finished=true;main.removeCallbacksAndMessages(null);if(discovery!=null)try{nsd.stopServiceDiscovery(discovery);}catch(RuntimeException ignored){}}
}
