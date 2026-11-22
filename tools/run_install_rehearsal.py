#!/usr/bin/env python3
"""Opt-in retained isolated rehearsal: prepare / read-only inspect / owned cleanup; no installer CTA."""
import argparse,hashlib,io,json,os,re,shlex,subprocess,tarfile,uuid
from pathlib import Path
from run_update_chain import validate_apk_zip,sha,verify_signer,instrumentation_values
APP='agency.carpediem.tv.validation.publicpreview';TEST=APP+'.test'
NORMAL='agency.carpediem.tv.publicpreview';RUNNER='agency.carpediem.tv.prototype.NavigationProbe'
STAGE_DIR='files/update-feed-fixture';STAGE=STAGE_DIR+'/newer.apk'
WITNESS='files/update-install-rehearsal/witness.json'
PAIRS = {
    '36-to-37': (36, 37, '0.2.30-updater-fixture'),
    '45-to-46': (45, 46, '0.2.39-updater-fixture'),
    '47-to-48': (47, 48, '0.2.41-updater-fixture'),
    '59-to-60': (59, 60, '0.2.53'),
    '62-to-63': (62, 63, '0.2.56'),
}


def atomic(path,value):
    temporary=path.with_suffix(path.suffix+'.tmp')
    fd=os.open(temporary,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
    with os.fdopen(fd,'w') as stream:json.dump(value,stream,indent=2);stream.flush();os.fsync(stream.fileno())
    os.replace(temporary,path)


def validate_witness(witness,manifest):
    if witness.get('schemaVersion')!=1 or witness.get('nonce')!=manifest['nonce'] or witness.get('package')!=APP:raise RuntimeError('Rehearsal witness identity mismatch')
    old, new = manifest['artifacts']['old'], manifest['artifacts']['new']
    if (witness.get('oldVersion') != old['version_code'] or witness.get('newVersion') != new['version_code']
            or witness.get('oldApkSha256') != old['sha256'] or witness.get('candidateSha256') != new['sha256']):
        raise RuntimeError('Rehearsal sealed version/hash mismatch')
    operation=witness.get('downloadOperation','')
    if not re.fullmatch(r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}',operation) or witness.get('downloadFile')!='carpe-update-'+operation+'.apk':raise RuntimeError('Rehearsal UUID destination mismatch')
    if type(witness.get('downloadId')) is not int or witness['downloadId']<0:raise RuntimeError('Rehearsal owned row missing')
    if not isinstance(witness.get('downloadUri'),str) or not witness['downloadUri']:raise RuntimeError('Rehearsal transport witness missing')


def require_installable_candidate(badging,xml,version_code,version_name):
    package=re.search(r"^package: name='([^']+)' versionCode='([^']*)' versionName='([^']*)'",badging,re.M)
    if not package or package.groups() != (APP, str(version_code), version_name):
        raise RuntimeError('Rehearsal candidate versionName/code mismatch')
    application=re.search(r'^\s*E: application\b[^\n]*\n(.*?)(?=^\s*E:|\Z)',xml,re.M|re.S)
    if not application:raise RuntimeError('Rehearsal candidate application manifest unavailable')
    flag=re.search(r'android:testOnly[^=\n]*=([^\n]+)',application[1])
    if re.search(r'^application-testOnly(?:\s|$)',badging,re.M)or(flag and flag[1].strip()not in ('false','(type 0x12)0x0','0x0')):raise RuntimeError('PackageInstaller rehearsal candidate must not be testOnly')


def main():
    p=argparse.ArgumentParser();p.add_argument('phase',choices=('prepare','inspect','cleanup'));p.add_argument('--adb',required=True);p.add_argument('--aapt',required=True);p.add_argument('--runtime-root',required=True);p.add_argument('--transport-id',type=int,default=1);p.add_argument('--label',required=True);p.add_argument('--test-apk');p.add_argument('--test-sha256');p.add_argument('--java-home',default='/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home');p.add_argument('--execute',action='store_true');p.add_argument('--owner-approved',action='store_true');p.add_argument('--approval-reference')
    p.add_argument('--pair', choices=PAIRS, default='36-to-37', help='Sealed observed update pair for prepare')
    a=p.parse_args()
    root=Path(a.runtime_root).resolve();local=Path(__file__).resolve().parents[1].parent / 'carpe-tv-runtime-local';ssd=Path('/path/to/isolated-carpe-tv-runtime')
    if not ((root==local and root.is_dir())or(root==ssd and root.is_mount())):p.error('exact approved runtime required')
    if a.transport_id<=0 or not re.fullmatch(r'[A-Za-z0-9-]+',a.label):p.error('positive transport and safe label required')
    home=root/'install-rehearsals';home.mkdir(exist_ok=True,mode=0o700);os.chmod(home,0o700)
    directory=home/a.label;manifest_path=directory/'manifest.json';env=dict(os.environ,TMPDIR=str(root/'tmp'))
    def persist():atomic(manifest_path,m)
    def adb(tokens,timeout=45,check=True):
        value=subprocess.run([a.adb,'-t',str(a.transport_id),*tokens],capture_output=True,text=True,timeout=timeout,env=env)
        if check and value.returncode:raise RuntimeError('ADB operation failed: '+tokens[0])
        return value.stdout+value.stderr
    def private_log(name,text):
        target=directory/name
        fd=os.open(target,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        with os.fdopen(fd,'w')as stream:stream.write(text)
    def installed(package):
        output=adb(['shell','pm','path',package],check=False)
        return 'package:' in output
    def installed_hash(package):
        lines=adb(['shell','pm','path',package]).splitlines();values=[]
        for line in lines:
            path=line.removeprefix('package:')
            if not path.startswith('/data/app/')or not re.fullmatch(r'[A-Za-z0-9_./=+~-]+',path):raise RuntimeError('Installed APK path unavailable')
            value=adb(['shell','sha256sum',path]).split()[0]
            if not re.fullmatch(r'[a-f0-9]{64}',value):raise RuntimeError('Installed APK SHA unavailable')
            values.append(value)
        if not values:raise RuntimeError('Installed APK identity missing')
        return sorted(values)
    def baseline():
        read=subprocess.run([a.adb,'-t',str(a.transport_id),'exec-out','run-as',NORMAL,'tar','-cf','-','shared_prefs'],capture_output=True,timeout=45,env=env)
        if read.returncode:raise RuntimeError('Normal preference hash unavailable')
        digest=hashlib.sha256()
        with tarfile.open(fileobj=io.BytesIO(read.stdout),mode='r:*')as archive:
            for entry in sorted(archive.getmembers(),key=lambda item:item.name):
                if entry.isfile():digest.update(entry.name.encode()+b'\0'+hashlib.sha256(archive.extractfile(entry).read()).digest())
        return dict(home=adb(['shell','cmd','role','get-role-holders','android.app.role.HOME']).strip(),services=adb(['shell','settings','get','secure','enabled_accessibility_services']).strip(),version=re.findall(r'versionCode=\d+|versionName=[^\s]+',adb(['shell','dumpsys','package',NORMAL])),apk_sha256=installed_hash(NORMAL),preferences_sha256=digest.hexdigest())
    def artifact(path,package,code,installable_version_name=None):
        validate_apk_zip(path);meta=path.with_name('output-metadata.json');data=json.loads(meta.read_text())
        if data.get('applicationId')!=package or not data.get('elements')or any(int(e.get('versionCode',-1))!=code for e in data['elements']):raise RuntimeError('APK Gradle metadata mismatch')
        badging=subprocess.run([a.aapt,'dump','badging',str(path)],capture_output=True,text=True,timeout=30,check=True).stdout
        match=re.search(r"^package: name='([^']+)' versionCode='(\d*)'",badging,re.M)
        if not match or match[1]!=package or int(match[2]or'0')!=code:raise RuntimeError('Actual APK package/code mismatch')
        if installable_version_name is not None:
            xml=subprocess.run([a.aapt,'dump','xmltree',str(path),'AndroidManifest.xml'],capture_output=True,text=True,timeout=30,check=True).stdout
            require_installable_candidate(badging,xml,code,installable_version_name)
        return dict(path=str(path),sha256=sha(path),metadata_sha256=sha(meta),version_code=code,certificate_sha256=verify_signer(path,a.aapt,a.java_home,env))
    def run_phase(phase,extra=()):
        output=adb(['shell','am','instrument','-w','-e','updaterehearsal','yes','-e','phase',phase,'-e','nonce',m['nonce'],*extra,TEST+'/'+RUNNER],180,check=False)
        private_log(phase+'-'+uuid.uuid4().hex+'.log',output);values=instrumentation_values(output)
        if 'INSTRUMENTATION_CODE: -1'not in output or 'failure'in values or not values.get('updaterehearsal_'+phase,'').startswith('PASS'):raise RuntimeError('Rehearsal instrumentation phase failed: '+phase)
        return values
    def verify_installed_identity():
        if installed_hash(TEST)!=[m['artifacts']['test']['sha256']]:raise RuntimeError('Installed test APK no longer owned sealed instrumentation')
        if installed_hash(APP)not in ([m['artifacts']['old']['sha256']],[m['artifacts']['new']['sha256']]):raise RuntimeError('Installed validation APK differs from owned old/new pair')
    if a.phase=='prepare':
        if manifest_path.exists():p.error('retained manifest exists; inspect/cleanup, never overwrite')
        if not a.test_apk or not a.test_sha256 or not re.fullmatch(r'[a-f0-9]{64}',a.test_sha256):p.error('new opt-in instrumentation path/explicit SHA required')
        test=Path(a.test_apk).resolve()
        if not test.is_relative_to(root):p.error('test APK must be sealed beneath approved runtime')
        old_code, new_code, new_name = PAIRS[a.pair]
        pair_root = root / 'fixtures' / ('installer-rehearsal-' + a.pair)
        proof=pair_root/'proof.json';evidence=json.loads(proof.read_text())
        if evidence.get('runtime')!='PASS'or evidence.get('cleanup')!='PASS'or evidence.get('expected_version_code')!=old_code or evidence.get('candidate',{}).get('version_code')!=new_code:raise RuntimeError('Confirmed '+a.pair+' ordinary chain proof required')
        old=artifact(pair_root/'old/app-debug.apk',APP,old_code);new=artifact(pair_root/'new/app-debug.apk',APP,new_code,new_name);instrumentation=artifact(test,TEST,0)
        if old['sha256']!=evidence['apks'][APP]['sha256']or new['sha256']!=evidence['candidate']['sha256']or instrumentation['sha256']!=a.test_sha256:raise RuntimeError('Sealed proof/test hash mismatch')
        xml=subprocess.run([a.aapt,'dump','xmltree',str(test),'AndroidManifest.xml'],capture_output=True,text=True,timeout=30,check=True).stdout
        block=re.search(r'E: instrumentation\b(.*?)(?=\n\s*E:|\Z)',xml,re.S)
        if not block or not re.search(r'android:targetPackage[^\n]*="'+re.escape(APP)+'"',block[1])or not re.search(r'android:name[^\n]*="'+re.escape(RUNNER)+'"',block[1]):raise RuntimeError('Opt-in instrumentation target/name mismatch')
        m=dict(schemaVersion=1,package=APP,nonce=str(uuid.uuid4()),label=a.label,phase='verified_plan_only',artifacts=dict(old=old,new=new,test=instrumentation),proof_sha256=sha(proof),script_sha256=sha(Path(__file__)),helper_sha256=sha(Path(__file__).with_name('run_update_chain.py')),attempted_installs=[],staging_attempted=False)
        if not a.execute:print(json.dumps(m,indent=2));return 0
        if not a.owner_approved or not a.approval_reference or len(a.approval_reference)>512:p.error('execution requires explicit prior owner approval/reference; no ADB performed')
        directory.mkdir(mode=0o700);m['approval_reference']=a.approval_reference;persist()
    else:
        m=json.loads(manifest_path.read_text())
        if m.get('schemaVersion')!=1 or m.get('package')!=APP or m.get('label')!=a.label or not m.get('approval_reference')or str(uuid.UUID(m['nonce']))!=m['nonce']:raise RuntimeError('Owned approved rehearsal manifest required')
        if not a.execute:print(json.dumps(m,indent=2));return 0
        if m.get('script_sha256')!=sha(Path(__file__))or m.get('helper_sha256')!=sha(Path(__file__).with_name('run_update_chain.py')):raise RuntimeError('Rehearsal tool changed; root review required before resume')
    try:
        if a.phase=='prepare':
            if installed(APP)or installed(TEST):raise RuntimeError('Refuse pre-existing validation installation')
            m['baseline']=baseline()
            if m['baseline']['home']!=NORMAL:raise RuntimeError('Normal Home baseline mismatch')
            m['phase']='installing';persist()
            for package,kind in [(APP,'old'),(TEST,'test')]:
                m['attempted_installs'].append(package);persist()
                path=Path(m['artifacts'][kind]['path'])
                if sha(path)!=m['artifacts'][kind]['sha256']:raise RuntimeError('Sealed APK changed before install')
                if 'Success'not in adb(['install','-t',str(path)],90):raise RuntimeError('Own validation installation failed')
            state=adb(['shell','run-as',APP,'sh','-c',shlex.quote('if [ -e '+STAGE_DIR+' ] || [ -L '+STAGE_DIR+' ]; then echo PRESENT; else echo ABSENT; fi')]).strip()
            if state!='ABSENT':raise RuntimeError('Refuse pre-existing private staging')
            m['staging_attempted']=True;m['phase']='staging';persist()
            # A fresh install may not have created its private files directory yet.
            adb(['shell','run-as',APP,'mkdir','-p','files'])
            adb(['shell','run-as',APP,'mkdir',STAGE_DIR])
            with Path(new['path']).open('rb')as source:
                transfer=subprocess.run([a.adb,'-t',str(a.transport_id),'shell','-T','run-as',APP,'sh','-c',shlex.quote('cat > '+STAGE)],stdin=source,capture_output=True,timeout=90,env=env)
            if transfer.returncode or adb(['shell','run-as',APP,'sha256sum',STAGE]).split()[0]!=new['sha256']:raise RuntimeError('Own staging transfer/hash failed')
            m['phase']='preparing_ready';persist();run_phase('prepare',['-e','candidateSha256',new['sha256'],'-e','candidateVersionCode',str(new['version_code'])])
            raw=adb(['shell','run-as',APP,'cat',WITNESS]);private_log('ready-witness.json',raw);witness=json.loads(raw);validate_witness(witness,m)
            m['witness']=witness;m['last_inspection']=run_phase('inspect');m['phase']='ready_retained';persist()
            m['settings_gate']='ALREADY_ALLOWED'if m['last_inspection'].get('updaterehearsal_can_request_package_installs')=='true'else'BLOCKED_SETTINGS_CONSENT';persist()
        elif a.phase=='inspect':
            verify_installed_identity();values=run_phase('inspect');m['last_inspection']=values;m['phase']='inspected';persist()
            after=baseline()
            m['last_normal_readback']=after;m['normal_preferences_match_original']=after['preferences_sha256']==m['baseline']['preferences_sha256'];persist()
            if any(after[key]!=m['baseline'][key]for key in ('home','services','version','apk_sha256')):raise RuntimeError('Observed normal APK/version/Home/services changed; inspect manually')
        else:
            if m.get('phase')=='cleaned':raise RuntimeError('Rehearsal already cleaned')
            verify_installed_identity()
            inspection=run_phase('inspect');m['last_inspection']=inspection;persist()
            if inspection.get('updaterehearsal_session_present')!='false' or inspection.get('updaterehearsal_state')in('installing','confirming'):raise RuntimeError('Pending or unverified installer session; owner resolution required before cleanup')
            values=run_phase('cleanup')
            if values.get('updaterehearsal_cleanup_verified')!='true':raise RuntimeError('Own row/snapshot cleanup unverified')
            m['app_cleanup_verified']=True;persist()
            adb(['shell','am','force-stop',APP])
            if m['staging_attempted']:adb(['shell','run-as',APP,'rm','-f',STAGE]);adb(['shell','run-as',APP,'rmdir',STAGE_DIR])
            for package in reversed(m['attempted_installs']):
                if package not in (APP,TEST):raise RuntimeError('Refuse foreign cleanup package')
                adb(['shell','am','force-stop',package],check=False)
                if 'Success'not in adb(['uninstall',package],90):raise RuntimeError('Owned uninstall failed')
            adb(['shell','am','start','-n',NORMAL+'/agency.carpediem.tv.prototype.MainActivity'])
            if baseline()!=m['baseline']:raise RuntimeError('Observed normal baseline differs after cleanup')
            if installed(APP)or installed(TEST):raise RuntimeError('Owned validation packages remain')
            m['phase']='cleaned';persist()
        print(json.dumps({'phase':m['phase'],'manifest':str(manifest_path),'settings_gate':m.get('settings_gate'),'installer_or_settings_activated':False}));return 0
    except BaseException as error:
        m['interrupted_phase']=m['phase'];m['phase']='needs_inspection';m['failure_type']=type(error).__name__;m['failure']=str(error)if isinstance(error,RuntimeError)else'host operation interrupted';persist()
        print(json.dumps({'phase':m['phase'],'manifest':str(manifest_path),'automatic_uninstall':False}));return 2

if __name__=='__main__':raise SystemExit(main())
