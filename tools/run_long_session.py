#!/usr/bin/env python3
"""Single-process long-session runner; same baseline guards, independent long deadline."""
import argparse,hashlib,json,os,re,subprocess,io,tarfile,zipfile
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
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--adb',required=True);parser.add_argument('--aapt',required=True);parser.add_argument('--transport-id',type=int,default=1);parser.add_argument('--runtime-root',required=True);parser.add_argument('--expected-version-code',required=True,type=int);parser.add_argument('--label',required=True);parser.add_argument('--verify-only',action='store_true');parser.add_argument('--blocks',type=int,choices=(2,24),default=2);args=parser.parse_args()
    root=Path(args.runtime_root).resolve()
    local_root=Path(__file__).resolve().parents[1].parent / 'carpe-tv-runtime-local'
    ssd_root=Path('/path/to/isolated-carpe-tv-runtime')
    if not ((root==local_root and root.is_dir()) or (root==ssd_root and root.is_mount())):
        parser.error('exact approved local runtime directory or mounted CarpeTVBuild required')
    if args.transport_id<=0 or args.expected_version_code<=0:parser.error('positive transport/version required')
    if not re.fullmatch(r'[A-Za-z0-9-]+',args.label):parser.error('unsafe label')
    flags=['longsession']
    # Only the dedicated longsession dispatch is accepted.
    logs=root/'logs';logs.mkdir(exist_ok=True)
    summary_path=logs/('long-session-summary-'+args.label+'.json')
    if summary_path.exists() or list(logs.glob('probe-*-'+args.label+'.log')):parser.error('label already has evidence; choose a fresh label')
    paths={APP:root/'build/app/outputs/apk/debug/app-debug.apk',TEST:root/'build/app/outputs/apk/androidTest/debug/app-debug-androidTest.apk'}
    env=dict(os.environ,TMPDIR=str(root/'tmp'))
    summary={'protocol':'long-session-v1','runtime':'UNRUN','tests':{},'blocks':args.blocks,'expected_version_code':args.expected_version_code,'script_sha256':sha(Path(__file__)),'apks':{}}
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
    attempted=[];error=None;baseline=None
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
        for name in flags:
            log=logs/('probe-'+name+'-'+args.label+'.log')
            command=[args.adb,'-t',str(args.transport_id),'shell','am','instrument','-w','-e','longsession','yes','-e','blocks',str(args.blocks),TEST+'/'+RUNNER]
            fd=os.open(log,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
            with os.fdopen(fd,'w') as stream:
                process=subprocess.Popen(command,stdout=stream,stderr=subprocess.STDOUT,text=True,env=env)
                summary['launched_adb_pid']=process.pid;persist()
                # Each fourth block additionally recreates and rewarms three workloads.
                try:process.wait(timeout=args.blocks*300+300+(args.blocks//4)*90)
                except BaseException:
                    process.terminate()
                    try:process.wait(timeout=5)
                    except subprocess.TimeoutExpired:process.kill();process.wait(timeout=5)
                    summary['tests'][name]='TIMEOUT_OR_INTERRUPTED';raise
            output=log.read_text()
            checkpoints=[]
            for line in output.splitlines():
                if line.startswith('INSTRUMENTATION_STATUS: longsession_checkpoint='):
                    checkpoints.append(json.loads(line.split('=',1)[1]))
            summary['checkpoints']=checkpoints
            passed='INSTRUMENTATION_CODE: -1' in output and 'INSTRUMENTATION_RESULT: failure=' not in output
            summary['tests'][name]='PASS' if passed else 'FAIL';print(name+': '+summary['tests'][name],flush=True)
            if not passed:raise RuntimeError('Probe failed: '+name)
            if len(checkpoints)!=args.blocks+1:raise RuntimeError('Incomplete checkpoint population')
            if len({point['pid'] for point in checkpoints})!=1:raise RuntimeError('Checkpoint process identity changed')
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
