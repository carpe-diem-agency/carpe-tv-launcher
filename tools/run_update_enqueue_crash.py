#!/usr/bin/env python3
"""Guarded real updater enqueue/death/recovery host protocol; verify-only performs no ADB."""
import argparse,hashlib,json,os,re,subprocess,io,tarfile,zipfile,time,uuid,xml.etree.ElementTree as ET
from urllib.parse import urlsplit
from pathlib import Path
APP='agency.carpediem.tv.validation.publicpreview'
TEST=APP+'.test';NORMAL='agency.carpediem.tv.publicpreview';RUNNER='agency.carpediem.tv.prototype.NavigationProbe'
def validate_apk_zip(path):
    try:
        with zipfile.ZipFile(path) as archive:
            if archive.testzip() is not None:
                raise ValueError('CRC mismatch')
    except Exception:
        raise RuntimeError('APK ZIP integrity check failed') from None

def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1048576),b''):h.update(block)
    return h.hexdigest()
def pref_values(raw):
    values={}
    for node in ET.fromstring(raw):
        name=node.get('name')
        if node.tag=='string':values[name]=node.text or ''
        elif node.tag=='boolean':values[name]=node.get('value')=='true'
        elif node.tag in ('long','int'):values[name]=int(node.get('value'))
    return values

def validate_witness(witness,durable,nonce):
    operation=witness.get('operation','')
    if type(witness.get('schemaVersion')) is not int or witness.get('schemaVersion')!=1 or witness.get('nonce')!=nonce or witness.get('package')!=APP:raise RuntimeError('Crash witness identity mismatch')
    if not re.fullmatch(r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}',operation):raise RuntimeError('Crash witness operation invalid')
    if type(witness.get('pid')) is not int or witness['pid']<=0 or type(witness.get('downloadId')) is not int or witness['downloadId']<0:raise RuntimeError('Crash witness PID/owned row invalid')
    name='carpe-update-'+operation+'.apk'
    if witness.get('file')!=name or not str(witness.get('uri','')).endswith('#carpe-update-'+operation):raise RuntimeError('Crash witness destination/URI invalid')
    transport=urlsplit(witness['uri'])
    if transport.scheme!='http' or transport.hostname!='127.0.0.1' or transport.username is not None or transport.path!='/enqueue-only' or transport.query or transport.fragment!='carpe-update-'+operation or not transport.port:raise RuntimeError('Crash witness loopback URI invalid')
    expected_uri='http://127.0.0.1:'+str(transport.port)+'/enqueue-only#carpe-update-'+operation
    if witness['uri']!=expected_uri:raise RuntimeError('Crash witness URI is not exact canonical loopback endpoint')
    if durable.get('enqueuePending') is not True or 'downloadId' in durable:raise RuntimeError('Durable pre-publication journal boundary not proven')
    for key,value in [('downloadOperation',operation),('enqueueOperation',operation),('enqueueFile',name),('enqueueUri',witness['uri'])]:
        if durable.get(key)!=value:raise RuntimeError('Durable journal differs from crash witness')

def instrumentation_values(raw):
    values={}
    for line in raw.splitlines():
        if line.startswith('INSTRUMENTATION_RESULT: '):
            key,separator,value=line[len('INSTRUMENTATION_RESULT: '):].partition('=')
            if separator:
                if key in values:raise RuntimeError('Duplicate recovery result field')
                values[key]=value
    return values

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--adb',required=True);parser.add_argument('--aapt',required=True);parser.add_argument('--transport-id',type=int,default=1);parser.add_argument('--runtime-root',required=True);parser.add_argument('--expected-version-code',required=True,type=int);parser.add_argument('--label',required=True);parser.add_argument('--verify-only',action='store_true');args=parser.parse_args()
    root=Path(args.runtime_root).resolve()
    local_root=Path(__file__).resolve().parents[1].parent / 'carpe-tv-runtime-local'
    ssd_root=Path('/path/to/isolated-carpe-tv-runtime')
    if not ((root==local_root and root.is_dir()) or (root==ssd_root and root.is_mount())):
        parser.error('exact approved local runtime directory or mounted CarpeTVBuild required')
    if args.transport_id<=0 or args.expected_version_code<=0:parser.error('positive transport/version required')
    if not re.fullmatch(r'[A-Za-z0-9-]+',args.label):parser.error('unsafe label')
    logs=root/'logs';logs.mkdir(exist_ok=True)
    summary_path=logs/('update-enqueue-crash-summary-'+args.label+'.json')
    if summary_path.exists() or list(logs.glob('probe-*-'+args.label+'.log')):parser.error('label already has evidence; choose a fresh label')
    paths={APP:root/'build/app/outputs/apk/debug/app-debug.apk',TEST:root/'build/app/outputs/apk/androidTest/debug/app-debug-androidTest.apk'}
    env=dict(os.environ,TMPDIR=str(root/'tmp'))
    summary={'protocol':'update-enqueue-crash-v1','runtime':'UNRUN','phase':'preflight','nonce':str(uuid.uuid4()),'row_cleanup_verified':False,'expected_version_code':args.expected_version_code,'script_sha256':sha(Path(__file__)),'apks':{}}
    def persist():
        temporary=summary_path.with_name(summary_path.name+'.tmp')
        fd=os.open(temporary,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
        with os.fdopen(fd,'w') as stream:
            json.dump(summary,stream,indent=2);stream.flush();os.fsync(stream.fileno())
        os.replace(temporary,summary_path)
    def adb(tokens,timeout=45,check=True):
        value=subprocess.run([args.adb,'-t',str(args.transport_id),*tokens],capture_output=True,text=True,timeout=timeout,env=env)
        output=value.stdout+value.stderr
        if check and value.returncode:raise RuntimeError('ADB operation failed: '+tokens[0])
        return output
    def write_log(path,output):
        with path.open('x') as stream:stream.write(output)
        os.chmod(path,0o600)
    def normal_state():
        role=adb(['shell','cmd','role','get-role-holders','android.app.role.HOME']).strip()
        services=adb(['shell','settings','get','secure','enabled_accessibility_services']).strip()
        version=re.findall(r'versionCode=\d+|versionName=[^\s]+',adb(['shell','dumpsys','package',NORMAL]))
        hashes=[]
        for line in adb(['shell','pm','path',NORMAL]).splitlines():
            path=line.removeprefix('package:')
            if not path.startswith('/data/app/') or not re.fullmatch(r'[A-Za-z0-9_./=+~-]+',path):raise RuntimeError('Normal APK path unavailable')
            digest=adb(['shell','sha256sum',path]).split()[0]
            if not re.fullmatch(r'[a-f0-9]{64}',digest):raise RuntimeError('Normal APK hash unavailable')
            hashes.append(digest)
        if not hashes:raise RuntimeError('Normal APK hashes unavailable')
        read=subprocess.run([args.adb,'-t',str(args.transport_id),'exec-out','run-as',NORMAL,'tar','-cf','-','shared_prefs'],capture_output=True,timeout=45,env=env)
        if read.returncode:raise RuntimeError('Normal preference content hash unavailable')
        digest=hashlib.sha256()
        with tarfile.open(fileobj=io.BytesIO(read.stdout),mode='r:*') as archive:
            for entry in sorted(archive.getmembers(),key=lambda item:item.name):
                if entry.isfile():digest.update(entry.name.encode()+b'\0'+hashlib.sha256(archive.extractfile(entry).read()).digest())
        return dict(home=role,services=services,version=version,apk_sha256=sorted(hashes),preferences_sha256=digest.hexdigest())
    def preflight():
        for package,path in paths.items():
            validate_apk_zip(path)
            metadata=json.loads(path.with_name('output-metadata.json').read_text())
            if metadata.get('applicationId')!=package:raise RuntimeError('Gradle metadata package mismatch')
            badging=subprocess.run([args.aapt,'dump','badging',str(path)],capture_output=True,text=True,timeout=30,check=True).stdout
            match=re.search(r"^package: name='([^']+)' versionCode='(\d*)'",badging,re.M)
            if not match or match[1]!=package:raise RuntimeError('Actual APK package mismatch')
            code=int(match[2] or 0)
            if package==TEST and code!=0:raise RuntimeError('Test APK version must be empty/0')
            if package==APP and code!=args.expected_version_code:raise RuntimeError('Actual application version mismatch')
            elements=metadata.get('elements',[])
            if not elements or any(int(e.get('versionCode',-1))!=code for e in elements):raise RuntimeError('APK/Gradle version metadata mismatch')
            if package==TEST:
                xml=subprocess.run([args.aapt,'dump','xmltree',str(path),'AndroidManifest.xml'],capture_output=True,text=True,timeout=30,check=True).stdout
                block=[];inside=False;indent=0
                for line in xml.splitlines():
                    depth=len(line)-len(line.lstrip())
                    if 'E: instrumentation' in line:inside=True;indent=depth;continue
                    if inside and 'E: ' in line and depth<=indent:break
                    if inside:block.append(line)
                joined='\n'.join(block)
                def attribute(name):
                    found=re.search(r'android:'+name+r'[^\n]*?=["\']([^"\']+)["\']',joined)
                    return found[1] if found else None
                if not inside or attribute('targetPackage')!=APP or attribute('name')!=RUNNER:
                    raise RuntimeError('Instrumentation name/target block mismatch')
            summary['apks'][package]={'sha256':sha(path),'version_code':code,'metadata_sha256':sha(path.with_name('output-metadata.json'))}
    attempted=[];error=None;baseline=None;prepare_attempted=False;recovery_ran=False
    try:
        preflight();summary['preflight']='PASS'
        if args.verify_only:
            summary['runtime']='UNRUN';summary['cleanup']='NOT_APPLICABLE';return 0
        for package in paths:
            if 'package:' in adb(['shell','pm','path',package],check=False):raise RuntimeError('Refuse existing validation package; inspect interrupted-run leftovers manually')
        baseline=normal_state()
        if baseline['home']!=NORMAL:raise RuntimeError('Home baseline differs from fixed normal launcher')
        summary['baseline']=baseline
        summary['baseline_verified']=True
        summary['attempted_installs']=[]
        summary['runtime']='PREFLIGHT_BASELINE_CAPTURED';persist()
        for package,path in paths.items():
            attempted.append(package)
            summary['attempted_installs']=list(attempted);persist()
            if 'Success' not in adb(['install','-t',str(path)],90):raise RuntimeError('Validation install did not confirm success')
        summary['runtime']='RUNNING';persist()
        nonce=summary['nonce']
        summary['phase']='prepare_started';prepare_attempted=True;persist()
        command=['shell','am','instrument','-w','-e','updateenqueuecrash','yes','-e','phase','prepare','-e','nonce',nonce,TEST+'/'+RUNNER]
        # The expected app process death may make am instrument/ADB return nonzero.
        prepared=adb(command,120,check=False)
        write_log(logs/('probe-updatecrash-prepare-'+args.label+'.log'),prepared)
        raw=adb(['shell','run-as',APP,'cat','files/update-process-death-fixture/witness.json'])
        write_log(logs/('probe-updatecrash-witness-'+args.label+'.log'),raw)
        witness=json.loads(raw)
        durable=adb(['shell','run-as',APP,'cat','shared_prefs/updates.xml'])
        write_log(logs/('probe-updatecrash-durable-'+args.label+'.log'),durable)
        validate_witness(witness,pref_values(durable),nonce)
        old_pid=witness['pid']
        def pids():
            value=adb(['shell','pidof',APP],5,check=False).strip()
            if not value:return []
            if not re.fullmatch(r'[1-9][0-9]*(?:\s+[1-9][0-9]*)*',value):raise RuntimeError('Validation PID evidence unavailable')
            return [int(pid) for pid in value.split()]
        deadline=time.monotonic()+8
        while old_pid in pids() and time.monotonic()<deadline:time.sleep(.2)
        if old_pid in pids():raise RuntimeError('Old validation PID did not disappear')
        summary['phase']='durable_crash_proven';summary['old_pid_gone']=True;summary['durable_journal_verified']=True;persist()
        summary['phase']='recover_started';persist()
        recovered=adb(['shell','am','instrument','-w','-e','updateenqueuecrash','yes','-e','phase','recover','-e','nonce',nonce,TEST+'/'+RUNNER],120,check=False)
        recovery_ran=True
        write_log(logs/('probe-updatecrash-recover-'+args.label+'.log'),recovered)
        values=instrumentation_values(recovered)
        if 'INSTRUMENTATION_CODE: -1' not in recovered or 'failure' in values or not values.get('updateprocessdeath','').startswith('PASS'):
            raise RuntimeError('Recovery phase did not verify production rollback')
        if values.get('updateprocessdeath_nonce')!=nonce or not re.fullmatch(r'[1-9][0-9]*',values.get('updateprocessdeath_new_pid','')) or int(values['updateprocessdeath_new_pid'])==old_pid:raise RuntimeError('Recovery process/nonce witness mismatch')
        if values.get('updateprocessdeath_old_pid')!=str(old_pid):raise RuntimeError('Recovery old-PID witness mismatch')
        if values.get('updateprocessdeath_row_cleanup_verified')!='true':raise RuntimeError('Recovery did not verify own DM rows cleaned')
        after=adb(['shell','run-as',APP,'cat','shared_prefs/updates.xml'])
        write_log(logs/('probe-updatecrash-after-'+args.label+'.log'),after)
        after_values=pref_values(after)
        if after_values.get('enqueuePending',False) or after_values.get('downloadId',-1)>=0:raise RuntimeError('Recovery durable cleanup not verified')
        summary['new_pid_distinct']=True;summary['row_cleanup_verified']=True;summary['phase']='recovered';persist()
        summary['runtime']='PASS'
        summary['scope']='Actual UpdateManager.download/DM enqueue then test-only pre-publication process kill; durable journal/new-PID production recovery. No GET completion, APK authenticity/install, spontaneous crash-rate or Home integration claim.'
    except BaseException as failure:
        error=failure;summary['runtime']='UNRUN' if not attempted else 'FAIL';summary['failure_type']=type(failure).__name__;summary['failure']=str(failure) if isinstance(failure,RuntimeError) else 'host/preflight operation failed'
    finally:
        cleanup=[]
        if not args.verify_only:
            if prepare_attempted and not summary.get('row_cleanup_verified'):cleanup.append('Owned DM row cleanup unverified; validation uninstall is not a row-query witness')
            for package in reversed(attempted):
                try:
                    if 'package:' in adb(['shell','pm','path',package],check=False):
                        adb(['shell','am','force-stop',package],check=False)
                        if 'Success' not in adb(['uninstall',package]):raise RuntimeError('Validation uninstall did not confirm success')
                except Exception:cleanup.append('Owned validation package cleanup failed')
            if attempted:
                try:adb(['shell','am','start','-n',NORMAL+'/agency.carpediem.tv.prototype.MainActivity'])
                except Exception:cleanup.append('Normal launcher resume failed')
            if baseline is not None:
                try:
                    after=normal_state();summary['after_baseline']=after
                    if after!=baseline:cleanup.append('Observed normal APK/preferences/version/Home/services readbacks changed; not a product-defect conclusion')
                    if attempted and 'package:' in adb(['shell','pm','list','packages',APP]):cleanup.append('Validation package remains installed')
                except Exception:cleanup.append('Final baseline readback failed')
            summary['cleanup']='FAIL' if cleanup else 'PASS' if attempted else 'NOT_ATTEMPTED'
        summary['cleanup_errors']=cleanup
        if cleanup and error is None:error=RuntimeError('Cleanup failed')
        persist()
        print(json.dumps(summary),flush=True)
    return 2 if error else 0
if __name__=='__main__':raise SystemExit(main())
