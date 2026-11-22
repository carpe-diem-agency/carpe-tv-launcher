#!/usr/bin/env python3
"""Two bounded matched isolated release rail/gfxinfo samples; no system settings writes."""
import argparse,hashlib,json,os,re,subprocess,io,tarfile,zipfile,time,statistics,xml.etree.ElementTree as ET
import csv
import shlex
from run_update_chain import verify_signer
from pathlib import Path
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

def nodes(tree,resource):
    return [n for n in tree.iter('node')if n.get('package')==APP and n.get('resource-id','').endswith(':id/'+resource) and n.get('visible-to-user','true')!='false' and positive_bounds(n)]
def positive_bounds(node):
    rect=node_bounds(node)
    return bool(rect and rect[2]>rect[0] and rect[3]>rect[1])


def node_bounds(node):
    match=re.fullmatch(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]',node.get('bounds',''))
    return tuple(map(int, match.groups())) if match else None
def rail_proven(tree,page):
    target=nodes(tree,'nav_'+page)
    token={'home':'home_time','apps':'apps_title','settings':'settings_heading'}[page]
    focused=[n for n in tree.iter('node')if n.get('focused')=='true']
    return len(target)==1 and target[0].get('selected')=='true' and target[0].get('focused')=='true' and target[0].get('enabled')=='true' and len(focused)==1 and bool(nodes(tree,token))
def parse_gfxinfo(raw,pid):
    headers=list(re.finditer(r'^.*Graphics info for pid (\d+) \[([^\]]+)\].*$',raw,re.M))
    owners=[(index,header) for index,header in enumerate(headers) if header.group(1)==str(pid) and header.group(2)==APP]
    if len(owners)!=1:raise MeasurementUnavailable('gfxinfo PID/package witness mismatch')
    index,header=owners[0]
    end=headers[index+1].start() if index+1<len(headers) else len(raw)
    # Some builds repeat counters in per-window sections. Use only the process aggregate.
    process=raw[header.end():end]
    aggregate=re.split(r'^[ \t]*Window:',process,maxsplit=1,flags=re.M)[0]
    def number(pattern,required=True):
        match=re.findall(pattern,aggregate,re.M)
        if len(match)!=1:
            if not required:return None
            raise MeasurementUnavailable('gfxinfo denominator unavailable/ambiguous')
        return int(match[0])
    total=number(r'^\s*Total frames rendered:\s*(\d+)\s*$')
    janky=number(r'^\s*Janky frames:\s*(\d+)\s*\(')
    legacy=number(r'^\s*Janky frames \(legacy\):\s*(\d+)\s*\(',False)
    if total<=0 or janky>total or (legacy is not None and legacy>total):raise MeasurementUnavailable('gfxinfo frame population invalid')
    values={'total_frames':total,'janky_frames':janky,'janky_fraction':janky/total,'legacy_janky_frames':legacy}
    for percentile in (50,90,95,99):values['p'+str(percentile)+'_ms']=number(r'^\s*'+str(percentile)+r'th percentile:\s*(\d+)ms\s*$',False)
    values['rolling_window']=parse_frame_profile(process)
    return values


def parse_frame_profile(process):
    sections = process.split('---PROFILEDATA---')
    if len(sections) != 3:
        return None
    reader = csv.DictReader(sections[1].strip().splitlines())
    fields = ('Flags', 'IntendedVsync', 'FrameDeadline', 'GpuCompleted', 'DisplayPresentTime',
              'DequeueBufferDuration', 'SyncQueued', 'SyncStart', 'SwapBuffersCompleted')
    if not reader.fieldnames or any(reader.fieldnames.count(name) != 1 for name in fields):
        return None
    try:
        rows = [{name: int(row[name]) for name in fields} for row in reader]
    except (ValueError, TypeError):
        return None
    frames = [row for row in rows if row['Flags'] == 0
              and 0 < row['IntendedVsync'] <= row['GpuCompleted'] < 2**63 - 1
              and row['IntendedVsync'] < row['FrameDeadline'] < 2**63 - 1]
    if not frames:
        return None
    presented = [row for row in frames if row['GpuCompleted'] <= row['DisplayPresentTime'] < 2**63 - 1]
    times = sorted({row['DisplayPresentTime'] for row in presented})
    present_gaps = [after - before for before, after in zip(times, times[1:])]
    swap_durations = [
        row['SwapBuffersCompleted'] - row['IntendedVsync'] - (row['SyncStart'] - row['SyncQueued'])
        for row in frames if row['IntendedVsync'] <= row['SyncQueued'] <= row['SyncStart']
        <= row['SwapBuffersCompleted'] <= row['GpuCompleted']
    ]

    def median_ms(ns):
        return round(statistics.median(ns) / 1e6, 2) if ns else None

    return {
        'rows': len(rows),
        'eligible_frames': len(frames),
        'frames_at_or_past_gpu_deadline': sum(row['GpuCompleted'] >= row['FrameDeadline'] for row in frames),
        'median_gpu_completion_from_vsync_ms': median_ms(
            [row['GpuCompleted'] - row['IntendedVsync'] for row in frames]),
        'median_deadline_from_vsync_ms': median_ms(
            [row['FrameDeadline'] - row['IntendedVsync'] for row in frames]),
        'median_dequeue_wait_ms': median_ms([row['DequeueBufferDuration'] for row in frames
                                             if 0 <= row['DequeueBufferDuration'] < 2**63 - 1]),
        'median_swap_completion_excluding_sync_wait_ms': median_ms(swap_durations),
        'frames_with_present_time': len(presented),
        'distinct_present_timestamps': len(times),
        'median_present_from_vsync_ms': median_ms(
            [row['DisplayPresentTime'] - row['IntendedVsync'] for row in presented]),
        'median_present_gap_ms': median_ms(present_gaps),
        'max_present_gap_ms': round(max(present_gaps) / 1e6, 2) if present_gaps else None,
    }


RAIL_KEYS = (20, 20, 19, 19)
RAIL_CYCLES = 20
WALLPAPERS = ('alpine', 'aurora', 'como', 'blue', 'plain')


def selected_wallpaper(tree):
    matches = [key for key in WALLPAPERS
               if any(node.get('selected') == 'true' for node in nodes(tree, 'wallpaper_' + key))]
    return matches[0] if len(matches) == 1 else None


def device_key_script(minimum_gap_ms):
    gap_cs = minimum_gap_ms // 10  # /proc/uptime reports centiseconds on Android.
    keys = ' '.join(str(key) for key in RAIL_KEYS * RAIL_CYCLES)
    return f"""
        set -e
        read_clock() {{
            read -r uptime idle < /proc/uptime
            seconds=${{uptime%.*}}
            fraction=${{uptime#*.}}
            stamp=$((seconds * 100 + ${{fraction#0}}))
        }}
        for code in {keys}; do
            read_clock
            start="$stamp"
            input keyevent "$code"
            read_clock
            end="$stamp"
            printf '%s %s %s\\n' "$code" "$start" "$end"
            remaining=$(({gap_cs} - (end - start)))
            if [ "$remaining" -gt 0 ]; then
                sleep "$(printf '0.%02d' "$remaining")"
            fi
        done
    """


def parse_device_key_times(output):
    rows = output.splitlines()
    keys = RAIL_KEYS * RAIL_CYCLES
    if len(rows) != len(keys):
        raise MeasurementUnavailable('Device input count did not match the requested rail sequence')
    timing = []
    previous_end = 0
    for index, (row, expected) in enumerate(zip(rows, keys)):
        if not re.fullmatch(r'\d+ \d+ \d+', row):
            raise MeasurementUnavailable('Device input timing row is invalid')
        key, start, end = map(int, row.split())
        if key != expected or start < previous_end or end < start:
            raise MeasurementUnavailable('Device input order or monotonic clock is invalid')
        timing.append(dict(code=key, start_ns=start * 10_000_000, end_ns=end * 10_000_000,
                           cycle=index // len(RAIL_KEYS) + 1))
        previous_end = end
    return timing


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--adb',required=True);parser.add_argument('--aapt',required=True);parser.add_argument('--transport-id',type=int,default=1);parser.add_argument('--runtime-root',required=True);parser.add_argument('--expected-version-code',required=True,type=int);parser.add_argument('--label',required=True);parser.add_argument('--verify-only',action='store_true');parser.add_argument('--java-home',default='/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home')
    parser.add_argument('--cadence', choices=('paced', 'rapid'), default='paced')
    parser.add_argument('--motion', choices=('default', 'on', 'off'), default='default',
                        help='Verify or change the animation switch inside the disposable app before warmup')
    parser.add_argument('--wallpaper', choices=('default', 'blue', 'alpine', 'plain'), default='default',
                        help='Select and verify a wallpaper in the disposable app before warmup')
    parser.add_argument('--device-paced-input', action='store_true',
                        help='Run the measured key sequence on the device clock in one bounded ADB shell')
    parser.add_argument('--artifact-root', type=Path,
                        help='Gradle output root below the approved local runtime or mounted CarpeTVOverflow')
    args=parser.parse_args()
    measured_gap_ms = {'paced': 300, 'rapid': 100}[args.cadence]
    root=Path(args.runtime_root).resolve()
    local_root=Path(__file__).resolve().parents[1].parent / 'carpe-tv-runtime-local'
    ssd_root=Path('/path/to/isolated-carpe-tv-runtime')
    if not ((root==local_root and root.is_dir()) or (root==ssd_root and root.is_mount())):
        parser.error('exact approved local runtime directory or mounted CarpeTVBuild required')
    if args.transport_id<=0 or args.expected_version_code<=0:parser.error('positive transport/version required')
    if not re.fullmatch(r'[A-Za-z0-9-]+',args.label):parser.error('unsafe label')
    logs=root/'logs';logs.mkdir(exist_ok=True)
    summary_path=logs/('release-gfxinfo-summary-'+args.label+'.json')
    if summary_path.exists() or list(logs.glob('probe-*-'+args.label+'.log')):parser.error('label already has evidence; choose a fresh label')
    artifact_root = root / 'build'
    if args.artifact_root is not None:
        overflow = Path('/Volumes/CarpeTVOverflow')
        artifact_root = args.artifact_root.resolve()
        local_artifacts = root == local_root and artifact_root != local_root and artifact_root.is_relative_to(local_root)
        overflow_artifacts = root == ssd_root and artifact_root != overflow and artifact_root.is_relative_to(overflow) and overflow.is_mount()
        if not artifact_root.is_dir() or not (local_artifacts or overflow_artifacts):
            parser.error('artifact root must be below the approved local runtime or mounted CarpeTVOverflow')
    apk_path = artifact_root / 'app/outputs/apk/release/app-release.apk'
    if args.artifact_root is not None and (apk_path.resolve() != apk_path or
            apk_path.with_name('output-metadata.json').is_symlink()):
        parser.error('APK and metadata must be direct files at their Gradle paths')
    paths={APP:apk_path}
    env=dict(os.environ,TMPDIR=str(root/'tmp'))
    summary={'protocol':'isolated-release-rail-gfxinfo-v1','cadence':args.cadence,'runtime':'UNRUN','samples':[],'requested_samples':2,'cycles_per_sample':20,'minimum_key_start_gap_ms':measured_gap_ms,'build_type':'release','expected_version_code':args.expected_version_code,'script_sha256':sha(Path(__file__)),'guard_helper_sha256':sha(Path(__file__).with_name('run_update_chain.py')),'apks':{}}
    summary['requested_motion'] = args.motion
    summary['requested_wallpaper'] = args.wallpaper
    summary['input_clock'] = 'device-uptime-10ms' if args.device_paced_input else 'host-monotonic'
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
            debuggable=bool(re.search(r'^application-debuggable(?:\s|$)',badging,re.M))
            if debuggable or metadata.get('variantName')!='release':raise RuntimeError('Release measurement requires actual non-debuggable release variant')
            code=int(match[2] or 0)
            if package==APP and code!=args.expected_version_code:raise RuntimeError('Actual application version mismatch')
            elements=metadata.get('elements',[])
            if not elements or any(int(e.get('versionCode',-1))!=code for e in elements):raise RuntimeError('APK/Gradle version metadata mismatch')
            summary['apks'][package]={'sha256':sha(path),'version_code':code,'metadata_sha256':sha(path.with_name('output-metadata.json')),'variant':'release','debuggable':debuggable,'certificate_sha256':verify_signer(path,args.aapt,args.java_home,env)}
    attempted=[];error=None;baseline=None;ui_owned=False
    ui_path='/data/local/tmp/carpe-pf2-'+args.label+'.xml'
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
        summary['attempted_installs']=[]
        summary['runtime']='PREFLIGHT_BASELINE_CAPTURED';persist()
        for package,path in paths.items():
            attempted.append(package)
            summary['attempted_installs']=list(attempted);persist()
            if 'Success' not in adb(['install','-t',str(path)],90):raise RuntimeError('Validation install did not confirm success')
        summary['runtime']='RUNNING';persist()
        summary['scope']='Same non-debuggable test-signed isolated release, UI-warmed single process, two matched rail workloads. UI route verified in warmup and at sample boundaries; not per-event page proof, physical remote, stable60fps or perceived smoothness.'
        summary['device']={'api':adb(['shell','getprop','ro.build.version.sdk']).strip(),'display_size':adb(['shell','wm','size']).strip(),'density':adb(['shell','wm','density']).strip(),'system_animation_scales':{name:adb(['shell','settings','get','global',name]).strip()for name in ('window_animation_scale','transition_animation_scale','animator_duration_scale')}}
        write_log(logs/('probe-pf2-display-'+args.label+'.log'),adb(['shell','dumpsys','display']))
        state=adb(['shell','if [ -e '+ui_path+' ] || [ -L '+ui_path+' ]; then echo PRESENT; else echo ABSENT; fi']).strip()
        if state!='ABSENT':raise MeasurementUnavailable('Refuse existing or unverified UI dump staging file')
        ui_owned=True;summary['ui_dump_owned']=True;persist()
        counter=0
        def ui(stage):
            nonlocal counter
            counter+=1
            try:
                adb(['shell','rm','-f',ui_path])
                adb(['shell','uiautomator','dump',ui_path],25)
                raw=adb(['shell','cat',ui_path],10)
            except Exception as error:raise MeasurementUnavailable('Fresh owned UI dump unavailable: '+stage)from error
            write_log(logs/('probe-pf2-ui-'+str(counter)+'-'+stage+'-'+args.label+'.log'),raw)
            return ET.fromstring(raw)
        def await_rail(page,stage):
            deadline=time.monotonic()+8
            while True:
                tree=ui(stage)
                if rail_proven(tree,page):return tree
                if time.monotonic()>=deadline:raise MeasurementUnavailable('Actual foreground rail/page not proven: '+stage)
                time.sleep(.2)
        def key(code, minimum_gap_ms=300):
            start = time.monotonic_ns()
            adb(['shell', 'input', 'keyevent', str(code)], 10)
            end = time.monotonic_ns()
            time.sleep(max(0, minimum_gap_ms / 1000 - (end - start) / 1e9))
            return {'code': code, 'start_ns': start, 'end_ns': end}
        def tap_owned(node):
            left, top, right, bottom = node_bounds(node)
            if min(left, top) < 0:
                raise MeasurementUnavailable('Owned control bounds unavailable')
            adb(['shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2)], 10)

        def motion_switch(tree):
            switches = nodes(tree, 'animations')
            if not switches:
                return None
            if len(switches) != 1:
                raise MeasurementUnavailable('Animation switch is ambiguous')
            switch = switches[0]
            if (switch.get('class') != 'android.widget.Switch' or switch.get('checkable') != 'true'
                    or switch.get('clickable') != 'true' or switch.get('enabled') != 'true'
                    or switch.get('checked') not in ('true', 'false')):
                raise MeasurementUnavailable('Exact owned animation switch not proven')
            return switch

        def configure_motion(tree):
            switch = motion_switch(tree)
            if switch is None:
                scroll = nodes(tree, 'settings_scroll')
                if len(scroll) != 1 or scroll[0].get('scrollable') != 'true':
                    raise MeasurementUnavailable('Owned Appearance scroll view is unavailable')
                left, top, right, bottom = node_bounds(scroll[0])
                x = str((left + right) // 2)
                adb(['shell', 'input', 'swipe', x, str(bottom - (bottom-top)//8),
                     x, str(top + (bottom-top)//4), '350'], 10)
                tree = ui('motion-scrolled')
                switch = motion_switch(tree)
            if switch is None:
                raise MeasurementUnavailable('Animation switch not visible after owned scroll')
            desired = 'true' if args.motion == 'on' else 'false'
            if switch.get('checked') != desired:
                tap_owned(switch)
                tree = ui('motion-switched')
                switch = motion_switch(tree)
            if switch is None or switch.get('checked') != desired:
                raise MeasurementUnavailable('Animation switch state could not be verified')
            summary['motion_switch_checked'] = desired == 'true'
            return tree

        def prepare_appearance():
            for page in ('apps', 'settings'):
                key(20)
                tree = await_rail(page, 'appearance-' + page)
            if len(nodes(tree, 'appearance_heading')) != 1:
                raise MeasurementUnavailable('Owned Appearance page is unavailable')
            if args.wallpaper != 'default' and selected_wallpaper(tree) != args.wallpaper:
                key(22)
                key(22)
                tree = ui('appearance-gallery-entered')
                focused = [index for index, name in enumerate(WALLPAPERS)
                           if any(node.get('focused') == 'true' for node in nodes(tree, 'wallpaper_' + name))]
                if len(focused) != 1:
                    raise MeasurementUnavailable('Exact owned wallpaper entry not focused')
                current = focused[0]
                target = WALLPAPERS.index(args.wallpaper)
                step = 1 if target > current else -1
                for index in range(current, target + step, step):
                    if index != current:
                        key(22 if step > 0 else 21)
                        tree = ui('appearance-wallpaper-' + WALLPAPERS[index])
                    items = nodes(tree, 'wallpaper_' + WALLPAPERS[index])
                    if (len(items) != 1 or items[0].get('focused') != 'true'
                            or items[0].get('enabled') != 'true' or items[0].get('clickable') != 'true'):
                        raise MeasurementUnavailable('Exact owned wallpaper tile not focused: ' + WALLPAPERS[index])
                key(23)
                tree = ui('appearance-' + args.wallpaper + '-selected')
            if args.wallpaper != 'default':
                if selected_wallpaper(tree) != args.wallpaper:
                    raise MeasurementUnavailable('Requested wallpaper selection not proven: ' + args.wallpaper)
                summary['wallpaper_selection_verified'] = args.wallpaper
            if args.motion != 'default':
                tree = configure_motion(tree)
            home = nodes(tree, 'nav_home')
            if len(home) != 1 or home[0].get('clickable') != 'true' or home[0].get('enabled') != 'true':
                raise MeasurementUnavailable('Owned Home rail control unavailable')
            tap_owned(home[0])
            key(21)
            await_rail('home', 'appearance-home')
        launch=adb(['shell','am','start','-W','--user','current','-a','android.intent.action.MAIN','-n',COMPONENT],45)
        write_log(logs/('probe-pf2-launch-'+args.label+'.log'),launch)
        tree=ui('initial')
        explore=[n for n in tree.iter('node')if n.get('package')==APP and n.get('text')in('Explore first','Спершу оглянути') and n.get('class')=='android.widget.Button' and n.get('clickable')=='true' and n.get('enabled')=='true' and positive_bounds(n)]
        if explore:
            title=any(n.get('package')==APP and n.get('text')in('Welcome to Carpe TV','Вітаємо в Carpe TV')for n in tree.iter('node'))
            if len(explore)!=1 or not title:raise MeasurementUnavailable('Exact owned Explore welcome not proven')
            if explore[0].get('focused')=='true':key(23)
            else:
                rect=list(map(int,re.findall(r'-?\d+',explore[0].get('bounds'))))
                if min(rect)<0:raise MeasurementUnavailable('Explore visible bounds unavailable')
                adb(['shell','input','tap',str((rect[0]+rect[2])//2),str((rect[1]+rect[3])//2)],10)
            tree=ui('explore-dismissed')
        # Left moves within Home content/toolbar toward its rail; never activates any app/control.
        for attempt in range(7):
            if rail_proven(tree,'home'):break
            if not nodes(tree,'home_time')or not nodes(tree,'nav_home')or nodes(tree,'nav_home')[0].get('selected')!='true':raise MeasurementUnavailable('Home not visible before bounded rail focus setup')
            if attempt==6:raise MeasurementUnavailable('Cannot establish foreground Home rail')
            key(21);tree=ui('home-left-'+str(attempt))
        if args.motion != 'default' or args.wallpaper != 'default':
            prepare_appearance()
        observed_wallpaper = None
        for warm in range(2):
            for code,page in ((20,'apps'),(20,'settings'),(19,'apps'),(19,'home')):
                key(code)
                tree = await_rail(page,'warm-'+str(warm)+'-'+page)
                if page == 'settings':
                    observed_wallpaper = selected_wallpaper(tree) or observed_wallpaper
        time.sleep(2)
        value=adb(['shell','pidof',APP],5).strip()
        if not re.fullmatch(r'[1-9][0-9]*',value):raise MeasurementUnavailable('Single validation PID not proven')
        pid=int(value);summary['pid']=pid;summary['defaults']={'wallpaper':observed_wallpaper,'rotation':False,'motion':True,'sounds':True,'sound_volume':70,'source':'fresh app defaults; wallpaper read from the gallery'}
        if args.motion != 'default':
            summary['defaults']['motion'] = summary['motion_switch_checked']
            summary['defaults']['source'] = 'fresh app defaults with animation switch verified in the isolated UI'
        if args.wallpaper != 'default':
            summary['defaults']['wallpaper'] = summary['wallpaper_selection_verified']
            summary['defaults']['source'] = 'fresh app defaults with requested Appearance settings verified in the isolated UI'
        persist()
        for sample in range(2):
            await_rail('home','sample-'+str(sample+1)+'-before')
            write_log(logs/('probe-pf2-reset-'+str(sample+1)+'-'+args.label+'.log'),adb(['shell','dumpsys','gfxinfo',APP,'reset'],20))
            if args.device_paced_input:
                command = 'toybox timeout -k 2 60 sh -c ' + shlex.quote(device_key_script(measured_gap_ms))
                output = adb(['shell', command], 75)
                timing = parse_device_key_times(output)
            else:
                timing=[]
                for cycle in range(RAIL_CYCLES):
                    for code in RAIL_KEYS:
                        event=key(code, measured_gap_ms);event.update(cycle=cycle+1);timing.append(event)
            time.sleep(.7)
            raw=adb(['shell','dumpsys','gfxinfo',APP,'framestats'],20)
            write_log(logs/('probe-pf2-frames-'+str(sample+1)+'-'+args.label+'.log'),raw)
            write_log(logs/('probe-pf2-key-times-'+str(sample+1)+'-'+args.label+'.log'),json.dumps(timing))
            values=parse_gfxinfo(raw,pid)
            await_rail('home','sample-'+str(sample+1)+'-after')
            if adb(['shell','pidof',APP],5).strip()!=str(pid):raise MeasurementUnavailable('Validation PID changed during matched samples')
            gaps=[(timing[i]['start_ns']-timing[i-1]['start_ns'])/1e6 for i in range(1,len(timing))]
            values.update(sample=sample+1,key_count=len(timing),cycles=RAIL_CYCLES,actual_key_gap_ms={'min':min(gaps),'median':statistics.median(gaps),'max':max(gaps)},key_span_ms=(timing[-1]['end_ns']-timing[0]['start_ns'])/1e6)
            summary['samples'].append(values);persist()
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
