#!/usr/bin/env python3
"""Guarded real update feed/download/default-validation-to-ready protocol; no installer."""
import argparse,hashlib,json,os,re,subprocess,io,tarfile,zipfile,shlex
from pathlib import Path
APP='agency.carpediem.tv.validation.publicpreview'
TEST=APP+'.test';NORMAL='agency.carpediem.tv.publicpreview';RUNNER='agency.carpediem.tv.prototype.NavigationProbe'
CERT='4434b55f94de7a3470a4c623b5f0bff9f015fc4e0f975a9cfd15f62e5b86af8a'
STAGE_DIR='files/update-feed-fixture';STAGE=STAGE_DIR+'/newer.apk'
WRONG_SIGNER_STAGE=STAGE_DIR+'/wrong-signer.apk'
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
def instrumentation_values(raw):
    values={}
    for line in raw.splitlines():
        if line.startswith('INSTRUMENTATION_RESULT: '):
            key,separator,value=line[len('INSTRUMENTATION_RESULT: '):].partition('=')
            if separator:
                if key in values:raise RuntimeError('Duplicate instrumentation result field')
                values[key]=value
    return values

def apk_signer(path,aapt,java_home,env):
    signing_env=dict(env,JAVA_HOME=java_home)
    output=subprocess.run([str(Path(aapt).resolve().parent/'apksigner'),'verify','--verbose','--print-certs',str(path)],capture_output=True,text=True,timeout=45,check=True,env=signing_env).stdout
    values=re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9:]+)$',output,re.M)
    if len(values)!=1:raise RuntimeError('Expected exactly one APK signer')
    return values[0].replace(':','').lower()

def verify_signer(path,aapt,java_home,env):
    certificate=apk_signer(path,aapt,java_home,env)
    if certificate!=CERT:raise RuntimeError('APK test signer mismatch')
    return certificate

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--adb',required=True);parser.add_argument('--aapt',required=True);parser.add_argument('--transport-id',type=int,default=1);parser.add_argument('--runtime-root',required=True);parser.add_argument('--expected-version-code',required=True,type=int);parser.add_argument('--label',required=True);parser.add_argument('--verify-only',action='store_true');parser.add_argument('--candidate-apk',required=True);parser.add_argument('--candidate-version-code',required=True,type=int);parser.add_argument('--candidate-sha256',required=True);parser.add_argument('--java-home',default='/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home')
    parser.add_argument('--artifact-root', type=Path,
                        help='Separate Gradle output root under the local runtime or mounted CarpeTVOverflow')
    parser.add_argument('--require-download-retry', action='store_true')
    parser.add_argument('--require-feed-timeout', action='store_true',
                        help='Require an actual feed response timeout and successful D-pad retry')
    parser.add_argument('--require-dpad-retry', action='store_true',
                        help='require the retry through the focused update-screen buttons')
    parser.add_argument('--wrong-signer-apk', type=Path,
                        help='Optional same-version validation APK with a different valid signer, staged only')
    parser.add_argument('--wrong-signer-sha256', help='Exact SHA-256 of the optional APK')
    args=parser.parse_args()
    root=Path(args.runtime_root).resolve()
    local_root=Path(__file__).resolve().parents[1].parent / 'carpe-tv-runtime-local'
    ssd_root=Path('/path/to/isolated-carpe-tv-runtime')
    if not ((root==local_root and root.is_dir()) or (root==ssd_root and root.is_mount())):
        parser.error('exact approved local runtime directory or mounted CarpeTVBuild required')
    if args.transport_id<=0 or args.expected_version_code<=0:parser.error('positive transport/version required')
    if not re.fullmatch(r'[A-Za-z0-9-]+',args.label):parser.error('unsafe label')
    artifact_root = root / 'build'
    allowed_candidate_roots = [root]
    if args.artifact_root is not None:
        overflow = Path('/Volumes/CarpeTVOverflow')
        artifact_root = args.artifact_root.resolve()
        local_build_roots = [local_root / name for name in ('tmp', 'fixtures')]
        local_artifacts = root == local_root and any(artifact_root != folder and artifact_root.is_relative_to(folder)
                                                     for folder in local_build_roots)
        overflow_artifacts = artifact_root != overflow and artifact_root.is_relative_to(overflow) and overflow.is_mount()
        if overflow_artifacts:
            allowed_candidate_roots.append(overflow)
        elif not local_artifacts:
            parser.error('artifact root must be inside the local runtime tmp/fixtures or mounted CarpeTVOverflow')
    candidate=Path(args.candidate_apk).resolve()
    if not any(candidate.is_relative_to(path) for path in allowed_candidate_roots) or not candidate.is_file():
        parser.error('candidate must be a sealed file beneath the approved runtime or selected overflow volume')
    if candidate.with_name('output-metadata.json').is_symlink():
        parser.error('candidate metadata must be a direct sibling file')
    if args.candidate_version_code<=args.expected_version_code or not re.fullmatch(r'[a-f0-9]{64}',args.candidate_sha256):parser.error('strictly newer candidate version and exact lowerhex SHA required')
    wrong_signer = args.wrong_signer_apk.resolve() if args.wrong_signer_apk else None
    if bool(wrong_signer) != bool(args.wrong_signer_sha256):
        parser.error('wrong-signer APK requires its exact SHA-256')
    if wrong_signer and (wrong_signer == candidate or not wrong_signer.is_file()
                         or not any(wrong_signer.is_relative_to(path) for path in allowed_candidate_roots)
                         or not re.fullmatch(r'[a-f0-9]{64}', args.wrong_signer_sha256)):
        parser.error('wrong-signer APK must be a distinct sealed file beneath the approved runtime')
    logs=root/'logs';logs.mkdir(exist_ok=True)
    summary_path=logs/('update-chain-summary-'+args.label+'.json')
    if summary_path.exists() or list(logs.glob('probe-*-'+args.label+'.log')):parser.error('label already has evidence; choose a fresh label')
    outputs = artifact_root / 'app/outputs/apk'
    paths = {APP: outputs / 'debug/app-debug.apk',
             TEST: outputs / 'androidTest/debug/app-debug-androidTest.apk'}
    if args.artifact_root is not None and any(
            path.resolve() != path or path.with_name('output-metadata.json').is_symlink()
            for path in paths.values()):
        parser.error('APKs and metadata must be direct files at their Gradle paths')
    env=dict(os.environ,TMPDIR=str(root/'tmp'))
    summary={'protocol':'update-feed-chain-v1','runtime':'UNRUN','phase':'preflight','row_cleanup_verified':False,'expected_version_code':args.expected_version_code,'script_sha256':sha(Path(__file__)),'apks':{}}
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
    def stage_apk(path, remote_path, expected_sha, log_name):
        with path.open('rb') as stream:
            # A non-PTY shell preserves the APK bytes and reports the remote command status.
            transfer=subprocess.run([args.adb,'-t',str(args.transport_id),'shell','-T','run-as',APP,'sh','-c',shlex.quote('cat > '+remote_path)],stdin=stream,capture_output=True,timeout=90,env=env)
        write_log(logs/(log_name+'-'+args.label+'.log'),(transfer.stdout+transfer.stderr)[:4096].decode(errors='replace'))
        if transfer.returncode:raise RuntimeError('Private APK staging failed')
        remote=adb(['shell','run-as',APP,'sha256sum',remote_path]).split()
        if not remote or remote[0]!=expected_sha or sha(path)!=expected_sha:raise RuntimeError('Private staged APK SHA mismatch')
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
        java_version=subprocess.run([str(Path(args.java_home)/'bin/java'),'-version'],capture_output=True,text=True,timeout=15,check=True)
        if not re.search(r'version "17(?:\.|")',java_version.stdout+java_version.stderr):raise RuntimeError('APK verification requires Java17')
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
            signer=verify_signer(path,args.aapt,args.java_home,env)
            summary['apks'][package]={'certificate_sha256':signer,'sha256':sha(path),'version_code':code,'metadata_sha256':sha(path.with_name('output-metadata.json'))}
        validate_apk_zip(candidate)
        if not 1024<=candidate.stat().st_size<=50*1024*1024:raise RuntimeError('Candidate size outside production bounds')
        if sha(candidate)!=args.candidate_sha256:raise RuntimeError('Candidate sealed SHA mismatch')
        metadata_path=candidate.with_name('output-metadata.json');metadata=json.loads(metadata_path.read_text())
        if metadata.get('applicationId')!=APP or not metadata.get('elements') or any(int(e.get('versionCode',-1))!=args.candidate_version_code for e in metadata['elements']):raise RuntimeError('Candidate Gradle package/version metadata mismatch')
        badging=subprocess.run([args.aapt,'dump','badging',str(candidate)],capture_output=True,text=True,timeout=30,check=True).stdout
        pattern=r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'"
        package=re.search(pattern,badging,re.M)
        if not package or package[1]!=APP or int(package[2])!=args.candidate_version_code:raise RuntimeError('Candidate actual package/version mismatch')
        signer=verify_signer(candidate,args.aapt,args.java_home,env)
        summary['candidate']={'path':str(candidate),'sha256':args.candidate_sha256,'version_code':args.candidate_version_code,'byte_size':candidate.stat().st_size,'metadata_sha256':sha(metadata_path),'certificate_sha256':signer}
        if wrong_signer:
            validate_apk_zip(wrong_signer)
            if not 1024 <= wrong_signer.stat().st_size <= 50*1024*1024 or sha(wrong_signer) != args.wrong_signer_sha256:
                raise RuntimeError('Wrong-signer APK size or sealed SHA mismatch')
            badging = subprocess.run([args.aapt,'dump','badging',str(wrong_signer)],capture_output=True,text=True,timeout=30,check=True).stdout
            alternate = re.search(pattern,badging,re.M)
            if not alternate or alternate.groups() != package.groups():
                raise RuntimeError('Wrong-signer APK package and version must match the valid candidate')
            alternate_pin = apk_signer(wrong_signer,args.aapt,args.java_home,env)
            if alternate_pin == signer:raise RuntimeError('Wrong-signer APK uses the normal test certificate')
            summary['wrong_signer_candidate'] = {'sha256':args.wrong_signer_sha256, 'certificate_sha256':alternate_pin}
    attempted=[];error=None;baseline=None;stage_attempted=False;probe_attempted=False
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
        state=adb(['shell','run-as',APP,'sh','-c',shlex.quote('if [ -e '+STAGE_DIR+' ] || [ -L '+STAGE_DIR+' ]; then echo PRESENT; else echo ABSENT; fi')]).strip()
        if state!='ABSENT':raise RuntimeError('Refuse existing or unverified private staging directory')
        summary['phase']='staging';stage_attempted=True;persist()
        # A fresh install has an app data directory but may not have created files/ yet.
        adb(['shell','run-as',APP,'mkdir','-p','files'])
        adb(['shell','run-as',APP,'mkdir',STAGE_DIR])
        summary['private_stage_dir_created']=True;persist()
        stage_apk(candidate,STAGE,args.candidate_sha256,'probe-updatechain-stage')
        extra = []
        if wrong_signer:
            stage_apk(wrong_signer,WRONG_SIGNER_STAGE,args.wrong_signer_sha256,'probe-updatechain-wrong-signer-stage')
            extra = ['-e','wrongSignerSha256',args.wrong_signer_sha256]
        summary['staging_sha_verified']=True;summary['phase']='instrumentation';probe_attempted=True;persist()
        probe_timeout = 540 if args.require_download_retry or args.require_dpad_retry else 330
        output=adb(['shell','am','instrument','-w','-e','updatechain','yes','-e','candidateSha256',args.candidate_sha256,'-e','candidateVersionCode',str(args.candidate_version_code),*extra,TEST+'/'+RUNNER],probe_timeout+(90 if wrong_signer else 0),check=False)
        write_log(logs/('probe-updatechain-'+args.label+'.log'),output)
        values=instrumentation_values(output)
        summary['row_cleanup_verified'] = values.get('updatechain_cleanup_verified') == 'true'
        if 'INSTRUMENTATION_CODE: -1' not in output or 'failure' in values or not values.get('updatechain','').startswith('PASS') or not values.get('updatechain_wrong_sha','').startswith('PASS'):raise RuntimeError('Actual feed/download/default-validator chain or wrong-SHA rejection unverified')
        if not summary['row_cleanup_verified']:raise RuntimeError('App-UID owned DM row cleanup unverified')
        if wrong_signer:
            rejection = values.get('updatechain_wrong_signer', '')
            if not rejection.startswith('PASS:'):raise RuntimeError('Actual wrong-signer download rejection and UI unverified')
            if values.get('updatechain_wrong_signer_cleanup_verified') != 'true':
                raise RuntimeError('Production wrong-signer file and DownloadManager row cleanup unverified')
            summary['wrong_signer_rejection'] = rejection
            summary['wrong_signer_cleanup_verified'] = True
        retry = values.get('updatechain_get404_retry', '')
        if args.require_download_retry and not retry.startswith('PASS'):
            raise RuntimeError('Actual APK GET failure/retry unverified')
        if retry.startswith('PASS'):
            summary['download_retry'] = retry
        dpad_retry = values.get('updatechain_get404_dpad', '')
        if args.require_dpad_retry and not dpad_retry.startswith('PASS'):
            raise RuntimeError('APK GET recovery through the D-pad controls unverified')
        if dpad_retry.startswith('PASS'):
            summary['dpad_retry'] = dpad_retry
        feed_timeout = values.get('updatechain_feed_timeout', '')
        if args.require_feed_timeout and not feed_timeout.startswith('PASS'):
            raise RuntimeError('Actual feed timeout recovery through D-pad Check unverified')
        if feed_timeout.startswith('PASS'):
            summary['feed_timeout_retry'] = feed_timeout
            waited = values.get('updatechain_feed_timeout_ms', '')
            if waited.isdecimal():
                summary['feed_timeout_ms'] = int(waited)
        summary['phase']='verified';persist()
        summary['runtime']='PASS'
        summary['scope']='Actual local feed/check/HEAD/GET/DownloadManager/default APK validator to ready plus wrong SHA rejection. Higher-version same-test-signed isolated APK is NOT installed; no release distribution/authenticity trust or system settings claim.'
    except BaseException as failure:
        error=failure;summary['runtime']='UNRUN' if not attempted else 'FAIL';summary['failure_type']=type(failure).__name__;summary['failure']=str(failure) if isinstance(failure,RuntimeError) else 'host/preflight operation failed'
    finally:
        cleanup=[]
        if not args.verify_only:
            if probe_attempted and not summary.get('row_cleanup_verified'):cleanup.append('Owned DM row cleanup unverified; validation uninstall is not a row-query witness')
            if stage_attempted:
                try:
                    adb(['shell','am','force-stop',APP],check=False)
                    adb(['shell','run-as',APP,'rm','-f',STAGE,WRONG_SIGNER_STAGE])
                    remove='if [ -d '+STAGE_DIR+' ]; then rmdir '+STAGE_DIR+'; fi'
                    adb(['shell','run-as',APP,'sh','-c',shlex.quote(remove)])
                    summary['own_staging_removed']=True
                except Exception:cleanup.append('Owned private candidate staging cleanup failed')
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
