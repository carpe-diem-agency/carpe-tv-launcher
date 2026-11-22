package agency.carpediem.tv.prototype;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Test-only preference adapter: failed commit publishes memory but skips the durable delegate write. */
final class FailingRecoveryPreferences implements SharedPreferences {
    private final SharedPreferences durable;
    private Map<String,Object> memory=new HashMap<>();
    int failures;
    FailingRecoveryPreferences(SharedPreferences durable){this.durable=durable;memory.putAll(durable.getAll());}
    @Override public Map<String,?> getAll(){return new HashMap<>(memory);}
    @Override public String getString(String key,String fallback){return (String)memory.getOrDefault(key,fallback);}
    @SuppressWarnings("unchecked") @Override public Set<String> getStringSet(String key,Set<String> fallback){Set<String> value=(Set<String>)memory.get(key);return value==null?fallback:new HashSet<>(value);}
    @Override public int getInt(String key,int fallback){return (Integer)memory.getOrDefault(key,fallback);}
    @Override public long getLong(String key,long fallback){return (Long)memory.getOrDefault(key,fallback);}
    @Override public float getFloat(String key,float fallback){return (Float)memory.getOrDefault(key,fallback);}
    @Override public boolean getBoolean(String key,boolean fallback){return (Boolean)memory.getOrDefault(key,fallback);}
    @Override public boolean contains(String key){return memory.containsKey(key);}
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener){throw new UnsupportedOperationException("No listener use in recovery fixture");}
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener){}
    @Override public Editor edit(){return new Edit();}
    private final class Edit implements Editor {
        final Map<String,Object> values=new HashMap<>();final Set<String> removals=new HashSet<>();boolean clear;
        private Editor put(String key,Object value){if(value==null)return remove(key);removals.remove(key);values.put(key,value);return this;}
        @Override public Editor putString(String key,String value){return put(key,value);}
        @Override public Editor putStringSet(String key,Set<String> value){return put(key,value==null?null:new HashSet<>(value));}
        @Override public Editor putInt(String key,int value){return put(key,value);}
        @Override public Editor putLong(String key,long value){return put(key,value);}
        @Override public Editor putFloat(String key,float value){return put(key,value);}
        @Override public Editor putBoolean(String key,boolean value){return put(key,value);}
        @Override public Editor remove(String key){values.remove(key);removals.add(key);return this;}
        @Override public Editor clear(){clear=true;return this;}
        @Override public boolean commit(){
            Map<String,Object> next=clear?new HashMap<>():new HashMap<>(memory);
            for(String key:removals)next.remove(key);next.putAll(values);memory=next;
            if(failures==0&&removals.contains("enqueuePending")) {
                if(memory.containsKey("enqueuePending")||!durable.getBoolean("enqueuePending",false))throw new AssertionError("Failure seam did not model memory/disk divergence");
                failures++;return false;
            }
            SharedPreferences.Editor write=durable.edit().clear();
            for(Map.Entry<String,Object> entry:memory.entrySet())copy(write,entry.getKey(),entry.getValue());
            return write.commit();
        }
        @Override public void apply(){commit();}
    }
    @SuppressWarnings("unchecked") private static void copy(Editor editor,String key,Object value){
        if(value instanceof String)editor.putString(key,(String)value);
        else if(value instanceof Integer)editor.putInt(key,(Integer)value);
        else if(value instanceof Long)editor.putLong(key,(Long)value);
        else if(value instanceof Float)editor.putFloat(key,(Float)value);
        else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
        else if(value instanceof Set)editor.putStringSet(key,(Set<String>)value);
        else throw new AssertionError("Unexpected preference type");
    }
}
