#!/usr/bin/env python3
"""Bounded isolated am start -W cold-process diagnostics, not TTFD or release benchmarking."""
import argparse,hashlib,json,os,re,subprocess,io,tarfile,zipfile,time,statistics
from pathlib import Path
from xml.etree import ElementTree
from run_release_gfxinfo import nodes, positive_bounds, rail_proven
from run_update_chain import verify_signer
APP='agency.carpediem.tv.validation.publicpreview'
TEST=APP+'.test';NORMAL='agency.carpediem.tv.publicpreview';COMPONENT=APP+'/agency.carpediem.tv.prototype.MainActivity'
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
class MeasurementUnavailable(RuntimeError):
    pass

def parse_start(output):
    def field(name):
        found=re.findall(r'^'+re.escape(name)+r':\s*([^\r\n]+)',output,re.M)
        return found[0].strip() if len(found)==1 else None
    if field('Status')!='ok' or field('LaunchState')!='COLD' or field('Activity')!=COMPONENT:
        raise MeasurementUnavailable('am start did not confirm exact Activity and COLD launch')
    result={'launch_state':'COLD'}
    for source,target,required in [('TotalTime','total_time_ms',True),('ThisTime','this_time_ms',False),('WaitTime','wait_time_ms',True)]:
        value=field(source)
        if value is None and not required:continue
        if value is None or not re.fullmatch(r'[0-9]+',value):raise MeasurementUnavailable('am start timing unavailable')
        result[target]=int(value)
    return result

def parse_uptime(output, label):
    matches = re.findall(r'^' + re.escape(label) + r' (\d+)\.(\d{2})$', output, re.MULTILINE)
    if len(matches) != 1:
        raise MeasurementUnavailable('Device uptime marker unavailable: ' + label)
    seconds, centiseconds = map(int, matches[0])
    return seconds * 1000 + centiseconds * 10

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--adb',required=True);parser.add_argument('--aapt',required=True);parser.add_argument('--transport-id',type=int,default=1);parser.add_argument('--runtime-root',required=True);parser.add_argument('--expected-version-code',required=True,type=int);parser.add_argument('--label',required=True);parser.add_argument('--verify-only',action='store_true');parser.add_argument('--samples',type=int,choices=(3,5),default=3);parser.add_argument('--build-type',choices=('debug','release'),default='debug')
    parser.add_argument('--check-first-input', action='store_true',
                        help='Dismiss isolated onboarding once; verify one D-pad Down after each cold first display')
    parser.add_argument('--check-library-draw', action='store_true',
                        help='Read the qualifying Home and Apps draws from the isolated release observer')
    parser.add_argument('--java-home', default='/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home')
    args=parser.parse_args()
    if args.check_library_draw and (not args.check_first_input or args.build_type != 'release'):
        parser.error('library draw requires --build-type release and --check-first-input')
    root=Path(args.runtime_root).resolve()
    local_root=Path(__file__).resolve().parents[1].parent / 'carpe-tv-runtime-local'
    ssd_root=Path('/path/to/isolated-carpe-tv-runtime')
    if not ((root==local_root and root.is_dir()) or (root==ssd_root and root.is_mount())):
        parser.error('exact approved local runtime directory or mounted CarpeTVBuild required')
    if args.transport_id<=0 or args.expected_version_code<=0:parser.error('positive transport/version required')
    if not re.fullmatch(r'[A-Za-z0-9-]+',args.label):parser.error('unsafe label')
    logs=root/'logs';logs.mkdir(exist_ok=True)
    summary_path=logs/('cold-start-summary-'+args.label+'.json')
    if summary_path.exists() or list(logs.glob('probe-*-'+args.label+'.log')):parser.error('label already has evidence; choose a fresh label')
    paths={APP:root/('build/app/outputs/apk/'+args.build_type+'/app-'+args.build_type+'.apk')}
    env=dict(os.environ,TMPDIR=str(root/'tmp'))
    summary={'protocol':'isolated-am-start-cold-v1','runtime':'UNRUN','samples':[],'requested_samples':args.samples,'build_type':args.build_type,'expected_version_code':args.expected_version_code,'script_sha256':sha(Path(__file__)),'apks':{}}
    summary['check_first_input'] = args.check_first_input
    summary['check_library_draw'] = args.check_library_draw
    ui_path = '/data/local/tmp/carpe-cold-ui-' + args.label + '.xml'
    ui_owned = False
    launch_args = f'am start -W --user current -a android.intent.action.MAIN -n {COMPONENT}'
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
    def dump_ui():
        adb(['shell','rm','-f',ui_path])
        clock = adb(['shell',f'set -e; uiautomator dump {ui_path}; printf "ui_read %s\\n" "$(cut -d" " -f1 /proc/uptime)"'], 25)
        return ElementTree.fromstring(adb(['shell','cat',ui_path], 10)), parse_uptime(clock, 'ui_read')

    def prepare_input():
        adb(['shell',launch_args], 45)
        tree, _ = dump_ui()
        explore = [node for node in tree.iter('node') if node.get('package') == APP
                   and node.get('text') in ('Explore first', 'Спершу оглянути')
                   and node.get('class') == 'android.widget.Button' and node.get('focused') == 'true'
                   and node.get('enabled') == 'true' and node.get('clickable') == 'true' and positive_bounds(node)]
        if len(explore) != 1 or not any(node.get('package') == APP and node.get('text') in
                                      ('Welcome to Carpe TV', 'Вітаємо в Carpe TV') for node in tree.iter('node')):
            raise MeasurementUnavailable('Focused Explore action in the owned welcome window not proven')
        adb(['shell','input','keyevent','23'], 10)
        tree, _ = dump_ui()
        if not rail_proven(tree, 'home'):
            raise MeasurementUnavailable('Home rail did not receive focus after owned onboarding')

    def launch_with_first_input():
        return adb(['shell',f'''
            set -e
            mark() {{ printf "%s %s\\n" "$1" "$(cut -d" " -f1 /proc/uptime)"; }}
            mark launch_begin
            {launch_args}
            mark first_display
            dumpsys activity activities | grep -F topResumedActivity | grep -Fq " {COMPONENT} "
            mark dp_down_begin
            input keyevent 20
            mark dp_down_end
        '''], 55)

    def measure_first_input(output):
        begin, displayed, key_begin, key_end = (
            parse_uptime(output, label) for label in ('launch_begin', 'first_display', 'dp_down_begin', 'dp_down_end'))
        for _ in range(3):
            tree, observed = dump_ui()
            search = any(node.get('enabled') == 'true' for node in nodes(tree, 'apps_search'))
            tile = any(node.get('package') == APP and node.get('enabled') == 'true'
                       and node.get('focusable') == 'true' and node.get('clickable') == 'true'
                       and node.get('visible-to-user', 'true') != 'false' and positive_bounds(node)
                       and nodes(node, 'app_name')
                       for grid in nodes(tree, 'apps_grid') for node in grid.iter('node'))
            if rail_proven(tree, 'apps') and search and tile:
                if not begin <= displayed <= key_begin <= key_end <= observed:
                    raise MeasurementUnavailable('Cold input timing markers are out of order')
                return {'first_input': 'DPAD_DOWN', 'first_input_page': 'apps',
                        'first_display_return_to_key_start_ms': key_begin - displayed,
                        'key_command_ms': key_end - key_begin,
                        'launch_to_verified_apps_bound_ms': observed - begin}
        raise MeasurementUnavailable('One D-pad Down did not prove focused Apps with a usable catalog')

    def measure_library_draw(output, pid, observed_bound):
        marker_log = adb(['logcat', '-d', '--pid=' + str(pid), '-v', 'raw', '-s', 'CarpeColdDraw:I'], 10)
        draws = re.findall(r'^apps_draw (\d+)$', marker_log, re.MULTILINE)
        if len(draws) != 1:
            raise MeasurementUnavailable('Missing or ambiguous Apps draw marker from validation process')
        draw = int(draws[0])
        start = parse_uptime(output, 'launch_begin')
        key_start = parse_uptime(output, 'dp_down_begin')
        if not key_start <= draw <= start + observed_bound + 10:
            raise MeasurementUnavailable('Apps draw marker is out of order with device uptime and UI evidence')
        homes = re.findall(r'^home_draw (\d+)$', marker_log, re.MULTILINE)
        if len(homes) != 1 or not start <= int(homes[0]) <= key_start + 10:
            raise MeasurementUnavailable('Home draw with a favorite was not verified before D-pad Down')
        return {'launch_to_usable_home_draw_ms': int(homes[0]) - start,
                'launch_to_usable_apps_draw_ms': draw - start,
                'key_command_start_to_usable_apps_draw_ms': draw - key_start}
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
            debuggable=bool(re.search(r'^application-debuggable(?:\s|$)',badging,re.M))
            if args.build_type=='release' and (debuggable or metadata.get('variantName')!='release'):raise RuntimeError('Release measurement requires actual non-debuggable release variant')
            code=int(match[2] or 0)
            if package==APP and code!=args.expected_version_code:raise RuntimeError('Actual application version mismatch')
            elements=metadata.get('elements',[])
            if not elements or any(int(e.get('versionCode',-1))!=code for e in elements):raise RuntimeError('APK/Gradle version metadata mismatch')
            summary['apks'][package]={'sha256':sha(path),'version_code':code,'metadata_sha256':sha(path.with_name('output-metadata.json')),'variant':args.build_type,'debuggable':debuggable}
            if args.check_first_input:
                summary['apks'][package]['certificate_sha256'] = verify_signer(path, args.aapt, args.java_home, env)
            manifest=subprocess.run([args.aapt,'dump','xmltree',str(path),'AndroidManifest.xml'],capture_output=True,text=True,timeout=30,check=True).stdout
            observer = 'agency.carpediem.tv.prototype.ColdDrawProvider' in manifest
            if observer != args.check_library_draw:
                raise RuntimeError('APK observer and requested cold-draw protocol differ')
            summary['apks'][package]['cold_draw_observer'] = observer
    attempted=[];error=None;baseline=None
    try:
        preflight();summary['preflight']='PASS'
        if args.verify_only:
            summary['runtime']='UNRUN';summary['cleanup']='NOT_APPLICABLE';return 0
        for package in (APP,TEST):
            if 'package:' in adb(['shell','pm','path',package],check=False):raise RuntimeError('Refuse existing validation package; inspect interrupted-run leftovers manually')
        baseline=normal_state()
        if baseline['home']!=NORMAL:raise RuntimeError('Home baseline differs from fixed normal launcher')
        summary['baseline']=baseline
        summary['baseline_verified']=True
        if args.check_first_input:
            state = adb(['shell',f'if [ -e {ui_path} ] || [ -L {ui_path} ]; then echo PRESENT; else echo ABSENT; fi']).strip()
            if state != 'ABSENT':
                raise MeasurementUnavailable('Refuse existing or unverified UI dump staging file')
            ui_owned = True
        summary['attempted_installs']=[]
        summary['runtime']='PREFLIGHT_BASELINE_CAPTURED';persist()
        for package,path in paths.items():
            attempted.append(package)
            summary['attempted_installs']=list(attempted);persist()
            if 'Success' not in adb(['install','-t',str(path)],90):raise RuntimeError('Validation install did not confirm success')
        summary['runtime']='RUNNING';persist()
        summary['scope']=args.build_type+' isolated APK; process-cold explicit Activity; am start -W platform display diagnostics only. Retained data/disk caches; no first-usable, TTFD, physical input or release benchmark claim.'
        if args.check_first_input:
            summary['scope'] = ('Process-cold isolated APK after owned onboarding. One D-pad Down after '
                                'am start -W and foreground check. UI dump gives an upper bound including '
                                'observation overhead; not TTFD or physical button-to-display latency.')
            prepare_input()
        if args.check_library_draw:
            summary['scope'] += (' Validation-only first qualifying Home draw with focused rail and actionable '
                                 'favorite, then Apps draw with focused rail, visible search and actionable tile. '
                                 'Clock starts before the device launch or key command; no physical presentation claim.')
        summary['device']={'api':adb(['shell','getprop','ro.build.version.sdk']).strip(),'display_size':adb(['shell','wm','size']).strip(),'density':adb(['shell','wm','density']).strip()};persist()
        previous_pids=set()
        def pid():
            value=adb(['shell','pidof',APP],timeout=5,check=False).strip()
            if not value:return None
            if not re.fullmatch(r'[1-9][0-9]*',value):raise MeasurementUnavailable('Validation PID unavailable or ambiguous')
            return int(value)
        for index in range(args.samples):
            old_pid=pid()
            adb(['shell','am','force-stop','--user','current',APP])
            deadline=time.monotonic()+5
            while pid() is not None and time.monotonic()<deadline:time.sleep(.1)
            if pid() is not None:raise MeasurementUnavailable('Validation process survived own force-stop')
            output = launch_with_first_input() if args.check_first_input else adb(['shell', launch_args],45)
            write_log(logs/('probe-coldstart-'+str(index+1)+'-'+args.label+'.log'),output)
            sample=parse_start(output)
            deadline=time.monotonic()+5;new_pid=pid()
            while new_pid is None and time.monotonic()<deadline:time.sleep(.1);new_pid=pid()
            if new_pid is None or new_pid in previous_pids:raise MeasurementUnavailable('Cold launch lacks a distinct new validation PID')
            previous_pids.add(new_pid)
            sample.update(index=index+1,pre_stop_pid=old_pid,pid=new_pid,previous_pid_absent=True,new_pid_distinct=True)
            if args.check_first_input:
                sample.update(measure_first_input(output))
                if args.check_library_draw:
                    sample.update(measure_library_draw(output, new_pid, sample['launch_to_verified_apps_bound_ms']))
                if pid() != new_pid:
                    raise MeasurementUnavailable('Validation process changed during the first input check')
            summary['samples'].append(sample);persist()
            time.sleep(.3)
        summary['am_total_time_ms']={'min':min(s['total_time_ms'] for s in summary['samples']),'median':statistics.median(s['total_time_ms'] for s in summary['samples']),'max':max(s['total_time_ms'] for s in summary['samples'])}
        if args.check_library_draw:
            for key in ('launch_to_usable_home_draw_ms', 'launch_to_usable_apps_draw_ms',
                        'key_command_start_to_usable_apps_draw_ms'):
                values = [sample[key] for sample in summary['samples']]
                summary[key] = {'min': min(values), 'median': statistics.median(values), 'max': max(values)}
        summary['runtime']='PASS'
    except BaseException as failure:
        error=failure;summary['runtime']='INPUT_UNVERIFIED' if isinstance(failure,MeasurementUnavailable) else 'UNRUN' if not attempted else 'FAIL';summary['failure_type']=type(failure).__name__;summary['failure']=str(failure) if isinstance(failure,RuntimeError) else 'host/preflight operation failed'
    finally:
        cleanup=[]
        if not args.verify_only:
            if ui_owned:
                try:adb(['shell','rm','-f',ui_path])
                except Exception:cleanup.append('Owned UI dump cleanup failed')
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
