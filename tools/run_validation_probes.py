#!/usr/bin/env python3
"""Repository-owned version of the audited SSD probe protocol; verify-only never calls ADB."""
import argparse,hashlib,json,os,re,subprocess,io,tarfile,zipfile,time,uuid
from pathlib import Path
APP='agency.carpediem.tv.validation.publicpreview'
TEST=APP+'.test';NORMAL='agency.carpediem.tv.publicpreview';RUNNER='agency.carpediem.tv.prototype.NavigationProbe'
FIXTURE='agency.carpediem.tv.testfixture.catalog'
FIXTURE_STAGE='/data/local/tmp/carpe-catalog-fixture.apk'
FIXTURE_CERT='4434b55f94de7a3470a4c623b5f0bff9f015fc4e0f975a9cfd15f62e5b86af8a'
FLAGS={'libraryfocus','librarystate','catalogstate','catalogscanerror','wallpapers','settingsfocus','settingscompact','settingscontrast','homecontrast','focusring','cachepolicy','modalfocus','uisounds','audiocoexistence','audioexternal','settingsstructure','feedback','feedbackbounded','diagnosticscompact','firstrun','appaudit','appauditroute','headerconnectivity','headeredge','navigationaccessibility','modaltouch','corruptsaverphoto','savervideolifecycle','saversourcefailure','screensaver','languagestate','libraryime','librarysoftkeys','authorsstate','inactivelibrary','libraryperf','navigationsoak','librarypool','libraryartwork','libraryinputperf','pendinglibrary','wallpapercache','updatebackup','updatecancel','updatecallback','librarycomposition','launcherfont','largefont','startupdraw','catalogbroadcast','cataloglaunchdenied','updateorphan','updateorphanpending','reorder'}
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
def catalog_manifest(xml, deny_launch):
    nodes=[];stack=[]
    for line in xml.splitlines():
        depth=len(line)-len(line.lstrip())
        element=re.match(r'\s*E: ([^\s(]+)',line)
        if element:
            while stack and stack[-1]['depth']>=depth:stack.pop()
            node={'tag':element[1],'attrs':{},'depth':depth,'parent':stack[-1] if stack else None}
            nodes.append(node);stack.append(node)
        elif stack:
            attribute=re.match(r'\s*A: (?:android:)?([^\s(=]+)[^=]*=(.*)',line)
            if attribute:
                raw=attribute[2].strip();quoted=re.match(r'"([^"\n]*)"',raw)
                stack[-1]['attrs'][attribute[1]]=quoted[1] if quoted else raw
    def truth(value):return value in ('true','(type 0x12)0xffffffff','0xffffffff')
    apps=[n for n in nodes if n['tag']=='application']
    activities=[n for n in nodes if n['tag']=='activity']
    if len(apps)!=1 or not truth(apps[0]['attrs'].get('testOnly')):raise RuntimeError('Fixture must be testOnly')
    if any(n['tag'].startswith('uses-permission') or n['tag'] in ('permission','permission-tree','permission-group','service','receiver','provider','activity-alias','instrumentation') for n in nodes):raise RuntimeError('Fixture permissions/components rejected')
    if len(activities)!=1 or activities[0]['parent'] is not apps[0] or activities[0]['attrs'].get('name') not in ('.CatalogActivity',FIXTURE+'.CatalogActivity') or not truth(activities[0]['attrs'].get('exported')):raise RuntimeError('Fixture must have exactly one exported CatalogActivity')
    expected_permission = 'android.permission.DUMP' if deny_launch else None
    if apps[0]['attrs'].get('permission') is not None or activities[0]['attrs'].get('permission') != expected_permission:
        raise RuntimeError('Unexpected fixture activity launch permission')
    filters=[n for n in nodes if n['tag']=='intent-filter']
    if len(filters)!=1 or filters[0]['parent'] is not activities[0]:raise RuntimeError('Fixture intent-filter hierarchy rejected')
    if any(n['parent'] is not filters[0] for n in nodes if n['tag'] in ('action','category')):raise RuntimeError('Fixture launcher hierarchy rejected')
    actions=[n['attrs'].get('name') for n in nodes if n['tag']=='action']
    categories=[n['attrs'].get('name') for n in nodes if n['tag']=='category']
    if actions!=['android.intent.action.MAIN'] or set(categories)!={'android.intent.category.LAUNCHER','android.intent.category.LEANBACK_LAUNCHER'} or len(categories)!=2:raise RuntimeError('Fixture launcher filters rejected')
    if apps[0]['attrs'].get('name') is not None:raise RuntimeError('Fixture custom Application rejected')

def validate_catalog_fixture(path,aapt,java_home,env,deny_launch):
    validate_apk_zip(path)
    tools=Path(aapt).resolve().parent
    java=Path(java_home)/'bin/java'
    version=subprocess.run([str(java),'-version'],capture_output=True,text=True,timeout=15,check=True)
    if not re.search(r'version "17(?:[.\"])',version.stdout+version.stderr):raise RuntimeError('Fixture verification requires Java17')
    signing_env=dict(env,JAVA_HOME=str(Path(java_home)))
    signed=subprocess.run([str(tools/'apksigner'),'verify','--verbose','--print-certs',str(path)],capture_output=True,text=True,timeout=30,check=True,env=signing_env).stdout
    certificates=re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9:]+)$',signed,re.M)
    if len(certificates)!=1 or certificates[0].replace(':','').lower()!=FIXTURE_CERT:raise RuntimeError('Fixture signer certificate mismatch')
    badging=subprocess.run([aapt,'dump','badging',str(path)],capture_output=True,text=True,timeout=30,check=True).stdout
    package=re.search(r"^package: name='([^']+)' versionCode='(\d+)'",badging,re.M)
    if not package or package[1]!=FIXTURE or package[2]!='1':raise RuntimeError('Fixture package/version mismatch')
    xml=subprocess.run([aapt,'dump','xmltree',str(path),'AndroidManifest.xml'],capture_output=True,text=True,timeout=30,check=True).stdout
    catalog_manifest(xml, deny_launch)
    return {'sha256':sha(path),'package':FIXTURE,'version_code':1,'certificate_sha256':FIXTURE_CERT}

def verify_external_audio(raw, token, player_uid, validation_uid):
    if player_uid == validation_uid:
        raise RuntimeError('External audio witness requires separate UIDs')
    phases = {}
    samples = []
    for index, line in enumerate(raw.splitlines()):
        if not line.startswith(token+' '):
            continue
        words = line.split()
        if len(words) == 6 and words[1] == 'player' and all(word.isdecimal() for word in words[2:]):
            uid, head, loss, gain = map(int, words[2:])
            if uid != player_uid:
                raise RuntimeError('Unexpected playback witness UID')
            samples.append((index, head, loss, gain))
        elif len(words) == 4 and words[1:3] == ['phase', str(validation_uid)] and words[3] not in phases:
            phases[words[3]] = index
        else:
            raise RuntimeError('Malformed or duplicate external audio witness')
    expected = ['control_begin','control_release','sounds_begin','sounds_end','video_begin','video_end','back_end']
    if list(phases) != expected:
        raise RuntimeError('External audio phase markers missing or out of order')
    def between(start, end):
        return [row for row in samples if phases.get(start, -1) < row[0] < phases[end]]
    def advanced(rows):
        return len(rows) >= 2 and rows[-1][1] > rows[0][1]
    initial = between(None, 'control_begin')
    challenged = between('control_begin', 'control_release')
    recovered = between('control_release', 'sounds_begin')
    if (not advanced(initial) or not challenged or not recovered or challenged[-1][2] <= initial[-1][2]
            or recovered[-1][3] <= challenged[-1][3]):
        raise RuntimeError('External audio positive focus loss/gain control not verified')
    summary = {'separate_uids':True,'focus_loss_and_gain_control':'PASS'}
    for stage, start, end in [('interface','sounds_begin','sounds_end'), ('video','video_begin','video_end'),
                              ('back','video_end','back_end')]:
        rows = between(start,end)
        if not advanced(rows):
            raise RuntimeError(f'External playback advancement not observed during {stage}')
        summary[stage] = {'samples':len(rows), 'playback_frames_advanced':rows[-1][1]-rows[0][1]}
    if any(row[2:] != recovered[-1][2:] for row in between('sounds_begin','back_end')):
        raise RuntimeError('External media focus changed during Carpe TV playback')
    return summary

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--adb',required=True);parser.add_argument('--aapt',required=True);parser.add_argument('--transport-id',type=int,default=1);parser.add_argument('--runtime-root',required=True);parser.add_argument('--expected-version-code',required=True,type=int);parser.add_argument('--label',required=True);parser.add_argument('--verify-only',action='store_true');parser.add_argument('--catalog-fixture');parser.add_argument('--java-home',default='/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home')
    parser.add_argument('--artifact-root', type=Path,
                        help='Gradle output directory inside the local runtime or mounted CarpeTVOverflow')
    parser.add_argument('flags',nargs='*');args=parser.parse_args()
    root=Path(args.runtime_root).resolve()
    local_root=Path(__file__).resolve().parents[1].parent / 'carpe-tv-runtime-local'
    ssd_root=Path('/path/to/isolated-carpe-tv-runtime')
    if not ((root==local_root and root.is_dir()) or (root==ssd_root and root.is_mount())):
        parser.error('exact approved local runtime directory or mounted CarpeTVBuild required')
    if args.transport_id<=0 or args.expected_version_code<=0:parser.error('positive transport/version required')
    if not re.fullmatch(r'[A-Za-z0-9-]+',args.label):parser.error('unsafe label')
    flags=args.flags or ['libraryfocus','librarystate','catalogstate','wallpapers','settingsfocus']
    if any(name not in FLAGS for name in flags):parser.error('probe outside approved whitelist')
    fixture_modes = {'catalogbroadcast', 'cataloglaunchdenied', 'audioexternal'}.intersection(flags)
    if len(fixture_modes) > 1 or bool(fixture_modes) != bool(args.catalog_fixture):
        parser.error('Choose one catalog fixture mode with --catalog-fixture')
    fixture_path=Path(args.catalog_fixture).resolve() if args.catalog_fixture else None
    logs=root/'logs';logs.mkdir(exist_ok=True)
    summary_path=logs/('validation-probes-summary-'+args.label+'.json')
    if summary_path.exists() or list(logs.glob('probe-*-'+args.label+'.log')):parser.error('label already has evidence; choose a fresh label')
    artifact_root = root / 'build'
    if args.artifact_root is not None:
        overflow = Path('/Volumes/CarpeTVOverflow')
        artifact_root = args.artifact_root.resolve()
        local = root == local_root and artifact_root.is_relative_to(root) and artifact_root != root
        external = overflow.is_mount() and artifact_root.is_relative_to(overflow) and artifact_root != overflow
        if not artifact_root.is_dir() or not (local or external):
            parser.error('artifact root must be a directory below the local runtime or mounted CarpeTVOverflow')
    outputs = artifact_root / 'app/outputs/apk'
    paths = {APP: outputs / 'debug/app-debug.apk',
             TEST: outputs / 'androidTest/debug/app-debug-androidTest.apk'}
    if args.artifact_root is not None and any(
            path.resolve() != path or path.with_name('output-metadata.json').is_symlink()
            for path in paths.values()):
        parser.error('alternate APKs and metadata must be direct files at their Gradle paths')
    env=dict(os.environ,TMPDIR=str(root/'tmp'))
    summary={'protocol':'repository-validation-probes-v1','runtime':'UNRUN','tests':{},'expected_version_code':args.expected_version_code,'script_sha256':sha(Path(__file__)),'apks':{}}
    def adb(tokens,timeout=45,check=True):
        value=subprocess.run([args.adb,'-t',str(args.transport_id),*tokens],capture_output=True,text=True,timeout=timeout,env=env)
        output=value.stdout+value.stderr
        if check and value.returncode:raise RuntimeError('ADB operation failed: '+tokens[0])
        return output
    def staged_exists():
        state=adb(['shell','if [ -e '+FIXTURE_STAGE+' ] || [ -L '+FIXTURE_STAGE+' ]; then echo PRESENT; else echo ABSENT; fi']).strip()
        if state not in ('PRESENT','ABSENT'):raise RuntimeError('Fixture staging presence unavailable')
        return state=='PRESENT'
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
    attempted=[];error=None;baseline=None;fixture_staging_attempted=False;catalog_attempted=False
    try:
        preflight()
        if fixture_path:summary['catalog_fixture']=validate_catalog_fixture(
            fixture_path,args.aapt,args.java_home,env,'cataloglaunchdenied' in fixture_modes)
        summary['preflight']='PASS'
        if args.verify_only:
            summary['runtime']='UNRUN';summary['cleanup']='NOT_APPLICABLE';return 0
        for package in paths:
            if 'package:' in adb(['shell','pm','path',package],check=False):raise RuntimeError('Refuse existing validation package; inspect interrupted-run leftovers manually')
        if fixture_path:
            if 'package:' in adb(['shell','pm','path',FIXTURE],check=False):raise RuntimeError('Refuse existing catalog fixture package')
            if staged_exists():raise RuntimeError('Refuse existing catalog fixture staging path')
        baseline=normal_state()
        if baseline['home']!=NORMAL:raise RuntimeError('Home baseline differs from fixed normal launcher')
        summary['baseline']=baseline
        summary['baseline_verified']=True
        for package,path in paths.items():
            attempted.append(package)
            if 'Success' not in adb(['install','-t',str(path)],90):raise RuntimeError('Validation install did not confirm success')
        if fixture_path:
            fixture_staging_attempted=True
            adb(['push',str(fixture_path),FIXTURE_STAGE],90)
            remote=adb(['shell','sha256sum',FIXTURE_STAGE]).split()
            if not remote or remote[0]!=summary['catalog_fixture']['sha256']:raise RuntimeError('Pushed fixture SHA mismatch')
        summary['runtime']='RUNNING'
        for name in flags:
            if name in fixture_modes:catalog_attempted=True
            extra=['-e','photoWallpaper','yes'] if name=='navigationsoak' else []
            if name=='audioexternal':
                audio = adb(['shell','dumpsys','audio'])
                focus_stack = re.search(r'^Audio Focus stack entries \(last is top of stack\):\n(.*?)^No external focus policy\s*$',
                                        audio,re.M|re.S)
                players = re.search(r'^  players:\n(.*?)^  ducked players piids:',audio,re.M|re.S)
                if not focus_stack or focus_stack[1].strip() or not players or 'state:started' in players[1]:
                    raise RuntimeError('Audio focus and playback must be idle before the external audio probe')
                token = uuid.uuid4().hex
                if 'Success' not in adb(['shell','pm','install','-t',FIXTURE_STAGE],90):
                    raise RuntimeError('External audio fixture installation did not confirm success')
                def package_uid(package):
                    info = adb(['shell','pm','list','packages','--user','current','-U',package])
                    match = re.search(r'^package:'+re.escape(package)+r' uid:(\d+)\s*$',info,re.M)
                    if not match:
                        raise RuntimeError('Audio package UID unavailable')
                    return int(match[1])
                player_uid = package_uid(FIXTURE)
                validation_uid = package_uid(APP)
                started = adb(['shell','am','start','-W','-n',FIXTURE+'/.CatalogActivity',
                               '--es','carpe_audio_witness',token])
                if 'Status: ok' not in started:
                    raise RuntimeError('External audio fixture did not start')
                time.sleep(.75)
                extra = ['-e','audiowitness',token]
            try:output=adb(['shell','am','instrument','-w','-e',name,'yes',*extra,TEST+'/'+RUNNER],180)
            except subprocess.TimeoutExpired as failure:
                output=(failure.stdout or b'');output=output.decode(errors='replace') if isinstance(output,bytes) else output
                write_log(logs/('probe-'+name+'-'+args.label+'.log'),output);summary['tests'][name]='TIMEOUT';raise
            log=logs/('probe-'+name+'-'+args.label+'.log');write_log(log,output)
            passed='INSTRUMENTATION_CODE: -1' in output and 'INSTRUMENTATION_RESULT: failure=' not in output
            if name in {'audioexternal','cataloglaunchdenied','feedbackbounded','headeredge','libraryartwork','navigationaccessibility','reorder','savervideolifecycle','saversourcefailure','launcherfont'}:
                passed = passed and f'INSTRUMENTATION_RESULT: {name}=PASS:' in output
            if passed and name == 'audioexternal':
                recent = adb(['logcat','-d','-t','3500','-v','raw','-s','CarpeAudioProbe:I'])
                summary['external_audio'] = verify_external_audio(recent, token, player_uid, validation_uid)
            if passed and name == 'cataloglaunchdenied':
                caller = re.search(r'^INSTRUMENTATION_RESULT: catalog_launch_caller=(\d+):(\d+)\s*$', output, re.M)
                recent = adb(['logcat','-d','-t','1450','-s','ActivityTaskManager'])
                passed = bool(caller and any(
                    'Permission Denial: starting Intent' in line
                    and f'cmp={FIXTURE}/.CatalogActivity' in line
                    and f':{APP}/' in line
                    and f'(pid={caller[1]}, uid={caller[2]}) requires android.permission.DUMP' in line
                    for line in recent.splitlines()
                ))
                summary['catalog_launch_android_denial'] = 'OBSERVED' if passed else 'MISSING'
            if name in {'settingscontrast','homecontrast','focusring'}:
                passed = passed and f'INSTRUMENTATION_RESULT: {name}=minimum ' in output
            summary['tests'][name]='PASS' if passed else 'FAIL';print(name+': '+summary['tests'][name],flush=True)
            if not passed:raise RuntimeError('Probe failed: '+name)
        summary['runtime']='PASS'
    except BaseException as failure:
        error=failure;summary['runtime']='UNRUN' if not attempted else 'FAIL';summary['failure_type']=type(failure).__name__;summary['failure']=str(failure) if isinstance(failure,RuntimeError) else 'host/preflight operation failed'
    finally:
        cleanup=[]
        if not args.verify_only:
            for package in reversed(attempted):
                try:
                    if 'package:' in adb(['shell','pm','path',package],check=False):
                        adb(['shell','am','force-stop',package],check=False)
                        if 'Success' not in adb(['uninstall',package]):raise RuntimeError('Validation uninstall did not confirm success')
                except Exception:cleanup.append('Owned validation package cleanup failed')
            if catalog_attempted:
                try:
                    if 'package:' in adb(['shell','pm','path',FIXTURE],check=False):
                        if 'Success' not in adb(['uninstall',FIXTURE],90):raise RuntimeError('Owned fixture uninstall failed')
                    if 'package:' in adb(['shell','pm','path',FIXTURE],check=False):raise RuntimeError('Owned fixture remains')
                except Exception:cleanup.append('Owned catalog fixture cleanup failed')
            if fixture_staging_attempted:
                try:
                    adb(['shell','rm','-f',FIXTURE_STAGE])
                    if staged_exists():raise RuntimeError('Owned fixture staging remains')
                except Exception:cleanup.append('Owned catalog staging cleanup failed')
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
        summary_path.write_text(json.dumps(summary,indent=2));os.chmod(summary_path,0o600)
        print(json.dumps(summary),flush=True)
    return 2 if error else 0
if __name__=='__main__':raise SystemExit(main())
