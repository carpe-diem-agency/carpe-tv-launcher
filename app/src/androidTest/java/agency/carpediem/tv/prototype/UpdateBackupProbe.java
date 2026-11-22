package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.*;
import android.os.Bundle;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Calls backup only, never install/PackageInstaller/update network operations. */
final class UpdateBackupProbe {
    static void run(Instrumentation t,Bundle result)throws Exception {
        require("agency.carpediem.tv.validation.publicpreview".equals(t.getTargetContext().getPackageName()),"Disposable validation package required");
        MainActivity activity=null;File fixture=null;Throwable primary=null;
        Map<SharedPreferences,Map<String,?>> snapshots=new LinkedHashMap<>();
        try {
            SharedPreferences welcome=t.getTargetContext().getSharedPreferences(MainActivity.class.getName(),0);snapshots.put(welcome,new HashMap<>(welcome.getAll()));
            require(welcome.edit().putBoolean("welcome_seen",true).commit(),"Prepare welcome");t.setInTouchMode(false);
            MainActivity screen=activity=(MainActivity)t.startActivitySync(new Intent(t.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SharedPreferences actual=screen.getPreferences(0),shortStore=screen.getSharedPreferences("MainActivity",0),sounds=UiSounds.preferences(screen),updates=UpdateManager.prefs(screen);
            for(SharedPreferences store:new SharedPreferences[]{actual,shortStore,sounds,updates})if(!snapshots.containsKey(store))snapshots.put(store,new HashMap<>(store.getAll()));
            String prefix="backup_probe_"+Long.toHexString(System.nanoTime());
            String key=prefix+"_main",decoy=prefix+"_decoy",sound=prefix+"_sound";
            Set<String> expectedSet=new HashSet<>(Arrays.asList("alpha","beta"));
            require(actual.edit().putString(key,"actual-main-store").putInt(prefix+"_int",37).putBoolean(prefix+"_bool",true).putLong(prefix+"_long",9000000001L).putFloat(prefix+"_float",.25f).putStringSet(prefix+"_set",expectedSet).commit(),"Write actual Main preference sentinels");
            require(shortStore.edit().putString(decoy,"short-store").commit()&&sounds.edit().putString(sound,"sound-store").commit(),"Write separate store evidence sentinels");
            UpdateManager.WORKER.submit(()->{}).get(15,java.util.concurrent.TimeUnit.SECONDS);
            String shared=prefix+"_shared";
            writeTypes(actual,prefix,"actual");writeTypes(shortStore,prefix,"short");writeTypes(sounds,prefix,"sounds");
            require(updates.edit().putBoolean("autoDownload",false).putString(prefix+"_runtime","must-not-export").commit(),"Prepare updater preference boundary");
            fixture=new File(screen.getFilesDir(),prefix);require(fixture.mkdir(),"Create scoped backup directory");final File directory=fixture;
            Context wrapped=new ContextWrapper(screen){@Override public File getFilesDir(){return directory;}};
            Method backup=UpdateManager.class.getDeclaredMethod("backup",Context.class);backup.setAccessible(true);backup.invoke(null,wrapped);
            File rollback=new File(fixture,"update-rollback"),apk=new File(rollback,"previous.apk"),json=new File(rollback,"preferences.json");
            require(apk.isFile()&&json.isFile(),"Backup did not produce scoped APK/JSON");
            require(Arrays.equals(digest(apk),digest(new File(screen.getApplicationInfo().sourceDir))),"Previous APK bytes differ from installed source");
            JSONObject export=new JSONObject(new String(Files.readAllBytes(json.toPath()),java.nio.charset.StandardCharsets.UTF_8));JSONObject values=export.getJSONObject("preferences");
            require(export.getInt("schemaVersion")==2,"Expected typed namespaced backup schema 2");
            String actualName=screen.getLocalClassName();
            require(actualName.equals(export.getString("mainPreferenceStore")),"Backup Main store differs from Activity local preference name");
            require(values.has(key)&&"actual-main-store".equals(values.getString(key)),"Backup omitted actual MainActivity.getPreferences sentinel");
            require(!values.has(decoy)&&!values.has(sound),"Flat compatibility Main export merged another preference store");
            JSONObject stores=export.getJSONObject("preferenceStores"),types=export.getJSONObject("preferenceTypes");
            Set<String> names=new HashSet<>(Arrays.asList(actualName,"MainActivity","ui_sounds","updates"));
            require(keys(stores).equals(names)&&keys(types).equals(names),"Backup store allowlist differs");
            verifyTypes(stores.getJSONObject(actualName),types.getJSONObject(actualName),prefix,"actual");
            verifyTypes(stores.getJSONObject("MainActivity"),types.getJSONObject("MainActivity"),prefix,"short");
            verifyTypes(stores.getJSONObject("ui_sounds"),types.getJSONObject("ui_sounds"),prefix,"sounds");
            require("short-store".equals(stores.getJSONObject("MainActivity").getString(decoy)),"Short-name decoy was not retained in its own namespace");
            require("sound-store".equals(stores.getJSONObject("ui_sounds").getString(sound)),"Sound-store sentinel omitted");
            JSONObject updateStore=stores.getJSONObject("updates"),updateTypes=types.getJSONObject("updates");
            require(updateStore.length()==1&&updateStore.has("autoDownload")&&!updateStore.getBoolean("autoDownload")
                    &&updateTypes.length()==1&&"boolean".equals(updateTypes.getString("autoDownload")),"Backup must preserve updater choice without session/runtime state");
            result.putString("update_backup","PASS: actual Main/short-name/sound stores, all six explicit types, isolated namespaces and updater user-choice-only export; scoped APK byte identity");

        }catch(Exception|Error failure){primary=failure;throw failure;}
        finally {
            Throwable cleanup=null;boolean quiet=activity==null;
            if(activity!=null)try{SettingsStructureProbe.finish(t,activity);SettingsStructureProbe.quiescent(t,activity);UpdateManager.WORKER.submit(()->{}).get(15,java.util.concurrent.TimeUnit.SECONDS);quiet=true;}catch(Exception|Error failure){cleanup=failure;}
            if(quiet)try{for(Map.Entry<SharedPreferences,Map<String,?>> entry:snapshots.entrySet())SettingsStructureProbe.restore(entry.getKey(),entry.getValue());if(fixture!=null)delete(fixture);}catch(Exception|Error failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}
            if(cleanup!=null){if(primary!=null)primary.addSuppressed(cleanup);else if(cleanup instanceof Error)throw(Error)cleanup;else throw(Exception)cleanup;}
        }
    }
    private static Set<String> keys(JSONObject object){Set<String> result=new HashSet<>();java.util.Iterator<String> keys=object.keys();while(keys.hasNext())result.add(keys.next());return result;}
    private static void writeTypes(SharedPreferences prefs,String prefix,String value){
        require(prefs.edit().putString(prefix+"_shared",value).putBoolean(prefix+"_bool",true).putInt(prefix+"_int",37)
                .putLong(prefix+"_long",9000000001L).putFloat(prefix+"_float",.25f)
                .putStringSet(prefix+"_set",new HashSet<>(Arrays.asList("alpha","beta"))).commit(),"Write scoped typed sentinels");
    }
    private static void verifyTypes(JSONObject values,JSONObject types,String prefix,String shared)throws Exception{
        String[] suffix={"_shared","_bool","_int","_long","_float","_set"},expected={"string","boolean","int","long","float","stringSet"};
        for(int i=0;i<suffix.length;i++)require(expected[i].equals(types.getString(prefix+suffix[i])),"Preference type marker mismatch");
        require(shared.equals(values.getString(prefix+"_shared"))&&values.getBoolean(prefix+"_bool")&&values.getInt(prefix+"_int")==37
                &&values.getLong(prefix+"_long")==9000000001L&&Math.abs(values.getDouble(prefix+"_float")-.25)<.00001,"Preference namespace/scalar value mismatch");
        Set<String> set=new HashSet<>();JSONArray array=values.getJSONArray(prefix+"_set");for(int i=0;i<array.length();i++)set.add(array.getString(i));
        require(set.equals(new HashSet<>(Arrays.asList("alpha","beta"))),"StringSet export mismatch");
    }
    private static byte[] digest(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream input=new FileInputStream(file)){byte[] block=new byte[32768];int n;while((n=input.read(block))!=-1)digest.update(block,0,n);}return digest.digest();}
    private static void delete(File root){File[] files=root.listFiles();if(files!=null)for(File child:files)delete(child);require(!root.exists()||root.delete(),"Scoped backup cleanup failed");}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
