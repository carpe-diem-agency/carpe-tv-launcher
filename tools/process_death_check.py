#!/usr/bin/env python3
"""Private host-side check of an isolated package after stopped-task process death."""
import argparse, hashlib, json, pathlib, re, subprocess, time, xml.etree.ElementTree as ET
import io, tarfile, os, uuid
from run_cold_start import MeasurementUnavailable, parse_start
from run_release_gfxinfo import positive_bounds
from run_validation_probes import FIXTURE, validate_catalog_fixture
PKG='agency.carpediem.tv.validation.publicpreview'
COMP=PKG+'/agency.carpediem.tv.prototype.MainActivity'
class Unverified(RuntimeError): pass
def parse_task(text):
    # Android 14 ActivityRecord embeds t<TASK_ID> on the component line.
    ids=re.findall(r'ActivityRecord\{[^\n]*'+re.escape(PKG)+r'/[^\n]*?\bt(\d+)\b',text)
    if not ids: raise Unverified('validation task ID unavailable')
    if len(set(ids))!=1: raise Unverified('ambiguous validation task IDs')
    return ids[0]
def main():
    p=argparse.ArgumentParser();p.add_argument('--adb',required=True);p.add_argument('--transport-id',type=int,default=1);p.add_argument('--aapt',required=True);p.add_argument('--apk',required=True);p.add_argument('--expected-version-code',type=int,required=True);p.add_argument('--artifacts',required=True);p.add_argument('--label',required=True);p.add_argument('--case',choices=['apps','apps-scroll','apps-scroll-rail','apps-query','apps-query-editor','apps-query-scroll','apps-external-back','authors','authors-modal','device-diagnostics','home-favorite','home-rail','home-header'],default='apps');p.add_argument('--query');p.add_argument('--main-component',required=True)
    p.add_argument('--catalog-fixture', type=pathlib.Path)
    p.add_argument('--java-home', default='/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home')
    a=p.parse_args()
    external_back = a.case == 'apps-external-back'
    if external_back != bool(a.catalog_fixture):
        p.error('apps-external-back requires --catalog-fixture; other cases do not use it')
    query_scroll = a.case == 'apps-query-scroll'
    query_case = a.case in ('apps-query', 'apps-query-editor') or query_scroll
    scroll_rail = a.case == 'apps-scroll-rail'
    scroll_case = a.case in ('apps-scroll', 'apps-scroll-rail') or query_scroll
    apps_case = a.case == 'apps' or scroll_case or query_case or external_back
    home_case = a.case.startswith('home-')
    device_case = a.case == 'device-diagnostics'
    root=pathlib.Path(a.artifacts).resolve()
    if a.transport_id<=0:p.error('transport ID must be positive')
    if a.expected_version_code<=0:p.error('expected version code must be positive')
    volumes=[pathlib.Path('/path/to/isolated-carpe-tv-runtime'),pathlib.Path('/Volumes/CarpeTVOverflow')]
    local_runtime = pathlib.Path(__file__).resolve().parents[1].parent / 'carpe-tv-runtime-local'
    local_logs = local_runtime / 'logs' / 'process-death-checks'
    local_allowed = local_runtime.is_dir() and root == local_logs
    if not local_allowed and not any(volume.is_mount()and root.is_relative_to(volume)for volume in volumes):
        p.error('artifacts require the approved local process-death log directory or a mounted SSD build volume')
    if a.query is not None and (not query_case or not re.fullmatch(r'[A-Za-z]{1,12}',a.query)):p.error('--query requires a query case and 1–12 ASCII letters')
    if a.label in ('.','..') or not re.fullmatch(r'[A-Za-z0-9_.-]+',a.label):p.error('unsafe label')
    mainpkg=a.main_component.split('/')[0]
    if mainpkg==PKG or not re.fullmatch(r'agency\.carpediem\.tv(?:\.[a-z]+)?',mainpkg):p.error('invalid baseline package')
    if a.main_component!=mainpkg+'/agency.carpediem.tv.prototype.MainActivity':p.error('baseline component must be exact MainActivity')
    root=root/a.label;root.mkdir(parents=True,exist_ok=False,mode=0o700);os.chmod(root,0o700)
    def adb(*args,binary=False,ok=True):
        r=subprocess.run([a.adb,'-t',str(a.transport_id),*args],capture_output=True,timeout=90 if args and args[0]=='install' else 25)
        if ok and r.returncode:raise Unverified('ADB command failed: '+args[0])
        return r.stdout if binary else r.stdout.decode(errors='replace')
    def shell(command,**kw):return adb('shell',command,**kw)
    def dump(tag):
        text=shell('dumpsys activity activities');(root/(tag+'.activities.txt')).write_text(text);return text
    def pid():
        result=subprocess.run([a.adb,'-t',str(a.transport_id),'shell','pidof',PKG],capture_output=True,timeout=25)
        output=result.stdout.decode(errors='replace').strip()
        if result.returncode==1 and not output and not result.stderr.strip():return ''
        if result.returncode!=0 or not re.fullmatch(r'[1-9][0-9]*',output):raise Unverified('validation PID observation failed or ambiguous')
        return output
    def wait(check,seconds=12):
        end=time.monotonic()+seconds
        while time.monotonic()<end:
            value=check()
            if value:return value
            time.sleep(.25)
        raise Unverified('bounded observation timed out')
    ui_path='/data/local/tmp/carpe-death-ui-'+uuid.uuid4().hex+'.xml';ui_owned=False
    def ui(tag):
        nonlocal ui_owned
        if not ui_owned:
            state=shell('if [ -e '+ui_path+' ] || [ -L '+ui_path+' ]; then echo PRESENT; else echo ABSENT; fi').strip()
            if state!='ABSENT':raise Unverified('refuse existing or unverified UI staging path')
            ui_owned=True
        # Only this invocation's fresh path is ever removed. Clear it first to reject stale dumps.
        shell('rm -f '+ui_path);shell('uiautomator dump '+ui_path)
        raw=shell('cat '+ui_path)
        target=root/(tag+'.xml');target.write_text(raw);os.chmod(target,0o600)
        return ET.fromstring(raw)
    def tap_node(node):
        nums=list(map(int,re.findall(r'\d+',node.attrib.get('bounds',''))))
        if (node.get('package')!=PKG or node.get('enabled')!='true' or len(nums)!=4
                or nums[2]<=nums[0] or nums[3]<=nums[1]):
            raise Unverified('validation tap target or its bounds unavailable')
        shell('input tap %d %d'%((nums[0]+nums[2])//2,(nums[1]+nums[3])//2));time.sleep(.4)
    def resource(tree,name):return next((n for n in tree.iter('node') if n.get('package')==PKG and n.get('resource-id','').endswith(':id/'+name)),None)
    def fingerprint(tree):
        if resource(tree,'apps_search') is None:raise Unverified('Apps page not visible')
        focused=[n for n in tree.iter('node') if n.get('focused')=='true']
        if len(focused)!=1:raise Unverified('focused app evidence unavailable')
        n=focused[0]
        focus_area = 'tile'
        if a.case=='apps-query' or scroll_case or external_back:
            nav=resource(tree,'nav_apps')
            if nav is None or nav.get('selected')!='true' or n.get('package')!=PKG:raise Unverified('query Apps/focus owner not proven')
            labels=[label for label in n.iter('node')if label.get('resource-id','').endswith(':id/app_name')]
            if scroll_rail and n is nav:
                focus_area = 'rail'
            elif len(labels) != 1:
                raise Unverified('focused app tile not proven')
        focus_identity = n.get('content-desc')
        if a.case == 'apps-query-editor':
            if n.get('package') != PKG:
                raise Unverified('search focus owner not proven')
            focus_identity = n.get('resource-id') or focus_identity
        if not focus_identity:raise Unverified('focused UI identity unavailable')
        # Hash identity, preserve private raw XML for precise investigation.
        parents={child:parent for parent in tree.iter() for child in parent}
        visible=[]
        for label in tree.iter('node'):
            if label.get('resource-id','').endswith(':id/app_name'):
                card=parents.get(label)
                if card is not None:
                    rect=list(map(int,re.findall(r'\d+',card.get('bounds',''))))
                    if len(rect)==4 and rect[2]>rect[0] and rect[3]>rect[1]:visible.append((hashlib.sha256(label.get('text','').encode()).hexdigest(),card.get('bounds')))
        value = dict(query_sha256=hashlib.sha256(resource(tree,'apps_search').get('text','').encode()).hexdigest(),focus=hashlib.sha256(focus_identity.encode()).hexdigest(),bounds=n.get('bounds'),query_length=len(resource(tree,'apps_search').get('text','')),visible_tiles=visible)
        if scroll_case:
            scroll = resource(tree, 'apps_scroll')
            observed = re.fullmatch(r'carpe-test-scroll-y:(0|[1-9][0-9]{0,5})',
                                    scroll.get('content-desc', '') if scroll is not None else '')
            if observed is None:
                raise Unverified('test-only scroll offset observer unavailable')
            value['scroll_y_px'] = int(observed[1])
            if scroll_rail:
                value['focus_area'] = focus_area
            if query_scroll:
                viewport = list(map(int, re.findall(r'\d+', scroll.get('bounds', ''))))
                if len(viewport) != 4 or viewport[3] <= viewport[1]:
                    raise Unverified('filtered scroll viewport height unavailable')
                value['viewport_height_px'] = viewport[3] - viewport[1]
        return value
    def stable_apps(tag,query=None):
        previous=[None];count=[0]
        def observe():
            count[0]+=1
            try:
                tree=ui(tag+'-'+str(count[0]));value=fingerprint(tree)
                if query is not None and (resource(tree,'apps_search').get('text','')!=query or len(value['visible_tiles'])<2):return False
            except Unverified:return False
            if previous[0]==value:return value
            previous[0]=value;return False
        return wait(observe,20)

    fixture_component = FIXTURE + '/.CatalogActivity'

    def resumed_task(text, component):
        # Match the resumed record only, never a historical entry for this component.
        match = re.search(r'^.*\b(?:topResumedActivity|ResumedActivity):[^\n]* '
                          + re.escape(component) + r' [^\n]*?\bt(\d+)\b', text, re.M)
        return match[1] if match else None

    def external_setup():
        fixture_query = 'Carpe QA'
        shell('input dpad keyevent 22')
        stable_apps('external-first-tile')
        shell('input dpad keyevent 19')
        editor = resource(ui('external-query-editor'), 'apps_search')
        if editor is None or editor.get('focused') != 'true':
            raise Unverified('search editor focus unavailable')
        shell('input text ' + fixture_query.replace(' ', '%s'))
        typed = resource(ui('external-query-typed'), 'apps_search')
        if typed is None or typed.get('text') != fixture_query:
            raise Unverified('external fixture search query was not entered')
        ime_pattern = r'^\s*mInputShown=(true|false)\s*$'
        ime = re.findall(ime_pattern, shell('dumpsys input_method'), re.M)
        if ime == ['true']:
            shell('input dpad keyevent 4')
        wait(lambda: re.findall(ime_pattern, shell('dumpsys input_method'), re.M) == ['false'])
        shell('input dpad keyevent 20')
        def selected_fixture():
            tree = ui('external-tile-selected')
            search = resource(tree, 'apps_search')
            labels = [label.get('text') for node in tree.iter('node')
                      if node.get('package') == PKG and node.get('focused') == 'true'
                      for label in node.iter('node')
                      if label.get('resource-id','').endswith(':id/app_name')]
            if search is not None and search.get('text') == fixture_query and labels == ['Carpe QA Калина']:
                return fingerprint(tree)
            return False
        return wait(selected_fixture)
    def query_setup(tree):
        query=a.query
        if query is None:
            labels=[n.get('text','').lower()for n in tree.iter('node')if n.get('resource-id','').endswith(':id/app_name')]
            query=next((part for part in ('a','e','o','i','s','t','tv','an')if sum(part in label for label in labels)>=2),None)
            if query is None:raise Unverified('no allowlisted query with two visible baseline matches')
        if resource(tree,'apps_search') is None:raise Unverified('search editor unavailable')
        # Up from the first result focuses the editor without clicking its IME opener.
        shell('input keyevent 22')
        stable_apps('query-entry-tile')
        shell('input keyevent 19')
        check=ui('query-editor')
        if resource(check,'apps_search')is None or resource(check,'apps_search').get('focused')!='true':raise Unverified('search editor focus not proven before input')
        shell('input text '+query)
        seen=[0]
        def filtered():
            seen[0]+=1;tree=ui('query-input-'+str(seen[0]));search=resource(tree,'apps_search');nav=resource(tree,'nav_apps')
            labels=[n for n in tree.iter('node')if n.get('package')==PKG and n.get('resource-id','').endswith(':id/app_name')]
            return (search is not None and search.get('text','')==query
                    and nav is not None and nav.get('selected')=='true' and len(labels)>=2
                    and all(query.lower() in n.get('text','').lower() for n in labels))
        wait(filtered,20)
        ime = re.findall(r'^\s*mInputShown=(true|false)\s*$', shell('dumpsys input_method'), re.M)
        summary['ime_after_query'] = ime[0] if len(ime) == 1 else 'unverified'
        if ime == ['true']:
            shell('input keyevent 4')
            editor = resource(ui('query-ime-dismissed'),'apps_search')
            if editor is None or editor.get('focused')!='true' or editor.get('text')!=query:
                raise Unverified('Back did not close the editor keyboard while retaining the query')
        if a.case == 'apps-query-editor':
            hidden = re.findall(r'^\s*mInputShown=(true|false)\s*$', shell('dumpsys input_method'), re.M)
            editor = resource(ui('query-editor-held'), 'apps_search')
            if hidden != ['false'] or editor is None or editor.get('focused') != 'true':
                raise Unverified('Apps editor focus with keyboard closed not proven')
            before = stable_apps('query-editor-stable', query)
            editor_identity = hashlib.sha256(editor.get('resource-id').encode()).hexdigest()
            if before['focus'] != editor_identity:
                raise Unverified('Apps editor lost focus before process-death setup')
            summary['query_length'] = len(query)
            summary['visible_result_count'] = len(before['visible_tiles'])
            summary['editor_focus_before'] = True
            return before
        shell('input dpad keyevent 20')
        exit_tree=ui('query-editor-exit')
        search=resource(exit_tree,'apps_search')
        nav=resource(exit_tree,'nav_apps')
        if search is None or search.get('text')!=query or nav is None or nav.get('selected')!='true':
            raise Unverified('filtered Apps disappeared after Down')
        summary['editor_exit']='dpad-down'
        if search.get('focused')=='true':
            # Re-enter filtered results through the selected, verified Apps rail.
            tap_node(nav)
            rail=resource(ui('query-rail-reentry'),'nav_apps')
            if rail is not None and rail.get('focused')=='true':
                shell('input dpad keyevent 22')
            summary['editor_exit']='apps-rail-reentry'
        first=stable_apps('query-first',query)
        if query_scroll:
            if first['scroll_y_px'] != 0:
                raise Unverified('new query viewport did not start at zero')
            for _ in range(4):
                shell('input dpad keyevent 20')
                time.sleep(.2)
            before = stable_apps('query-scrolled-before', query)
            if (before['scroll_y_px'] <= 0 or before['focus'] == first['focus']
                    or before['visible_tiles'] == first['visible_tiles']):
                raise Unverified('distinct focused result and positive query scroll not proven')
            summary['scroll_y_before_px'] = before['scroll_y_px']
            summary['viewport_height_px'] = before['viewport_height_px']
        else:
            shell('input dpad keyevent 22');before=stable_apps('query-selected-second',query)
            if before['focus']==first['focus'] or before['bounds']==first['bounds']:raise Unverified('distinct second actual query result not proven')
        summary['query_length']=len(query);summary['query_sha256']=hashlib.sha256(query.encode()).hexdigest();summary['visible_result_count']=len(before['visible_tiles'])
        return before
    def authors_fingerprint(tree):
        settings=resource(tree,'nav_settings')
        back=resource(tree,'authors_back')
        if settings is None or settings.get('selected')!='true' or back is None:raise Unverified('Authors inline context unavailable')
        controls=[n for n in tree.iter('node') if ':id/authors_' in n.get('resource-id','')]
        focus=[n for n in controls if n.get('focused')=='true']
        if len(focus)!=1:raise Unverified('Authors child focus unavailable')
        return dict(focused_id=focus[0].get('resource-id').split(':id/')[-1],bounds=focus[0].get('bounds'),
                    controls=[(n.get('resource-id').split(':id/')[-1],n.get('bounds')) for n in controls])
    def home_fingerprint(tree):
        rail = resource(tree, 'nav_home')
        if rail is None or rail.get('selected') != 'true' or resource(tree, 'home_time') is None:
            raise Unverified('Owned Home is not visible')
        parents = {child: parent for parent in tree.iter('node') for child in parent}
        favorites = [(label, parents.get(label)) for label in tree.iter('node')
                     if label.get('package') == PKG and label.get('resource-id', '').endswith(':id/app_name')]
        favorites = [(label, card) for label, card in favorites if card is not None and card.get('clickable') == 'true'
                     and card.get('enabled') == 'true' and positive_bounds(card)]
        if len(favorites) < 2:
            raise Unverified('At least two visible favorites are required')
        focus = [node for node in tree.iter('node') if node.get('package') == PKG and node.get('focused') == 'true']
        if len(focus) != 1:
            raise Unverified('Owned Home focus is not observable')
        node = focus[0]
        index = next((i for i, (_, card) in enumerate(favorites) if card == node), None)
        identity = node.get('content-desc') or node.get('resource-id')
        if not identity:
            raise Unverified('Home focus identity is missing')
        control = node.get('resource-id', '').rsplit(':id/', 1)[-1]
        if index is not None:
            focus_area = 'favorite'
        elif control == 'nav_home':
            focus_area = 'rail'
        elif control in ('home_wifi', 'home_bluetooth', 'home_timer', 'home_power'):
            focus_area = 'header'
        else:
            focus_area = 'other'
        return dict(favorite_index=index, focus_area=focus_area, focus=hashlib.sha256(identity.encode()).hexdigest(),
                    bounds=node.get('bounds'), favorites=[(hashlib.sha256(label.get('text', '').encode()).hexdigest(),
                                                         card.get('bounds')) for label, card in favorites])

    def home_setup():
        home_fingerprint(ui('home-initial'))
        shell('input keyevent 4')
        rail = resource(ui('home-rail'), 'nav_home')
        if rail is None or rail.get('focused') != 'true':
            raise Unverified('Back did not reach the owned Home rail')
        shell('input dpad keyevent 22')
        first = home_fingerprint(ui('home-first-favorite'))
        shell('input dpad keyevent 22')
        second = home_fingerprint(ui('home-second-favorite'))
        if first['favorite_index'] != 0 or second['favorite_index'] != 1 or first['focus'] == second['focus']:
            raise Unverified('First and second favorite did not receive real D-pad focus')
        summary['visible_favorite_count'] = len(second['favorites'])
        remembered = second['favorite_index']
        if a.case == 'home-favorite':
            before = second
        else:
            shell('input dpad keyevent ' + ('4' if a.case == 'home-rail' else '19'))
            before = home_fingerprint(ui('home-origin-control'))
            if before['focus_area'] != a.case.removeprefix('home-'):
                raise Unverified('Requested Home control did not receive focus before process death')
        if home_fingerprint(ui('home-before-confirmed')) != before:
            raise Unverified('Home fingerprint did not settle before process death')
        summary['remembered_favorite_index'] = remembered
        summary['favorite_index_before'] = before['favorite_index']
        summary['home_focus_before'] = before['focus_area']
        return before

    def authors_setup():
        tree=ui('authors-setup-start');nav=resource(tree,'nav_settings')
        if nav is None:raise Unverified('Settings rail unavailable')
        tap_node(nav);tree=ui('settings-selected')
        about=next((n for n in tree.iter('node') if n.get('text') in ('About','Про лаунчер') and n.get('clickable')=='true'),None)
        if about is None:raise Unverified('Localized About category unavailable')
        tap_node(about);tree=ui('about-selected');entry=resource(tree,'about_authors')
        if entry is None:raise Unverified('Authors action unavailable')
        tap_node(entry)
        touch_tree=ui('authors-open-touch')
        if resource(touch_tree,'authors_back') is None:raise Unverified('Authors child did not open after touch')
        # A real direction key leaves touch mode; tap alone is not focus evidence.
        shell('input keyevent 22');tree=ui('authors-open')
        if resource(tree,'authors_back') is None:raise Unverified('Authors child lost on first D-pad after touch')
        target='authors_code_license' if a.case=='authors-modal' else 'authors_back'
        for attempt in range(12):
            if resource(tree,'authors_back') is None:raise Unverified('Authors child lost during D-pad focus setup')
            node=resource(tree,target)
            if node is not None and node.get('focused')=='true':break
            focused=[n for n in tree.iter('node') if n.get('focused')=='true' and ':id/authors_' in n.get('resource-id','')]
            key=20 if focused and focused[0].get('resource-id','').endswith(':id/authors_back') and target!='authors_back' else 19 if focused else 22
            shell('input keyevent '+str(key));tree=ui('authors-focus-'+str(attempt))
        else:raise Unverified('Cannot establish Authors hardware focus')
        before=authors_fingerprint(tree)
        if a.case=='authors-modal':
            shell('input keyevent 23');tree=ui('authors-modal-open')
            if not any('Apache License' in n.get('text','') for n in tree.iter('node')):raise Unverified('Safe code-license modal not confirmed')
        return before

    def device_fingerprint(tree):
        settings = resource(tree, 'nav_settings')
        diagnostic = resource(tree, 'device_diagnostics')
        if settings is None or settings.get('selected') != 'true' or diagnostic is None:
            raise Unverified('Device category unavailable')
        controls = ('device_specs', 'device_ram_title', 'device_storage_title')
        if any(resource(tree, name) is None for name in controls):
            raise Unverified('Device controls unavailable')
        focused = [node for node in tree.iter('node') if node.get('focused') == 'true']
        if len(focused) != 1 or focused[0].get('package') != PKG:
            raise Unverified('Device focus unavailable')
        return dict(focus=focused[0].get('resource-id', '').split(':id/')[-1],
                    bounds=diagnostic.get('bounds'))

    def open_diagnostic_report(tag):
        shell('input dpad keyevent 23')
        attempt = 0
        def visible_report():
            nonlocal attempt
            attempt += 1
            tree = ui(f'{tag}-{attempt}')
            qr = resource(tree, 'diagnostic_qr')
            text = resource(tree, 'diagnostic_text')
            if qr is None or text is None or not text.get('text'):
                return False
            return hashlib.sha256(text.get('text').encode()).hexdigest()
        return wait(visible_report, 12)

    def device_setup():
        tree = ui('device-setup-start')
        settings = resource(tree, 'nav_settings')
        if settings is None:
            raise Unverified('Settings rail unavailable')
        tap_node(settings)
        tree = ui('device-settings-selected')
        choices = [node for node in tree.iter('node') if node.get('package') == PKG
                   and node.get('text') in ('Device', 'Пристрій') and node.get('clickable') == 'true']
        if len(choices) != 1:
            raise Unverified('Localized Device category unavailable')
        tap_node(choices[0])
        tree = ui('device-selected')
        target = resource(tree, 'device_diagnostics')
        if target is not None and target.get('focused') != 'true':
            shell('input dpad keyevent 22')
            tree = ui('device-action-selected')
        before = device_fingerprint(tree)
        if before['focus'] != 'device_diagnostics':
            raise Unverified('Device diagnostics action does not hold focus')
        return before, open_diagnostic_report('diagnostics-open-before')
    def baseline():
        read=subprocess.run([a.adb,'-t',str(a.transport_id),'exec-out','run-as',mainpkg,'tar','-cf','-','shared_prefs'],capture_output=True,timeout=25)
        if read.returncode:raise Unverified('baseline preference hash unavailable')
        pref=read.stdout
        if not pref or b'not debuggable' in pref or b'unknown package' in pref:raise Unverified('baseline preference hash unavailable')
        digest=hashlib.sha256()
        with tarfile.open(fileobj=io.BytesIO(pref),mode='r:*') as archive:
            for member in sorted(archive.getmembers(),key=lambda m:m.name):
                if member.isfile():
                    content=archive.extractfile(member).read();digest.update(member.name.encode()+b'\0'+hashlib.sha256(content).digest())
        paths=shell('pm path '+mainpkg).splitlines();apkhash=[]
        for line in paths:
            path=line.removeprefix('package:')
            if not path.startswith('/data/app/') or not re.fullmatch(r'[A-Za-z0-9_./=+~-]+',path):raise Unverified('installed main APK path unavailable')
            value=shell('sha256sum '+path).split()[0]
            if not re.fullmatch(r'[a-f0-9]{64}',value):raise Unverified('installed main APK hash unavailable')
            apkhash.append(value)
        if not apkhash:raise Unverified('main APK hashes unavailable')
        version=shell('dumpsys package '+mainpkg)
        return dict(home=shell('cmd role get-role-holders android.app.role.HOME').strip(),services=shell('settings get secure enabled_accessibility_services').strip(),version=re.findall(r'versionCode=\d+|versionName=[^\s]+',version),prefs_sha256=digest.hexdigest(),apk_sha256=sorted(apkhash))
    summary=dict(case=a.case,phase='preflight',process_death_attempted=False,scope=('Apps UIA query-attribute hash, tile/bounds/order; not exact scrollY' if apps_case else 'Authors inline control IDs/focus/bounds; modal case expects safely closed license on restore'),status='INPUT_UNVERIFIED')
    if scroll_case:
        summary['scope'] = 'Exact live ScrollView Y from a validation-only accessibility observer, focused app and visible tile bounds/order after same-task process death'
    if scroll_rail:
        summary['scope'] = 'Exact live ScrollView Y and active Apps rail, then Right to the last viewed tile after same-task process death'
    if a.case=='apps-query':summary['scope']='Actual nonempty ASCII input, at least two visible results, second result focus and stable UIA query hash/tile/bounds/order; no exact scrollY or soft composition claim'
    if query_scroll:
        summary['scope'] = 'Nonempty ASCII input, exact positive live ScrollView Y, focused result and visible result bounds/order after same-task process death'
    if a.case=='apps-query-editor':summary['scope']='Nonempty query, editor focus with keyboard closed, visible matches and bounds before and after process death'
    if home_case:summary['scope']='Owned Home with remembered second favorite; active focus, favorite order and visible bounds before and after stopped-task process death'
    if device_case:
        summary['scope'] = 'Device diagnostics dialog dismissed after same-task process death, Device action focus restored and report reopened with matching visible text and QR'
    if external_back:
        summary['scope'] = 'D-pad OK launches the owned fixture. With that activity foreground, kill the stopped validation process. D-pad Back alone restores Apps query, tile focus and visible tile bounds in a new PID on the same task.'
    witness={}
    installed=False;own_install_attempted=False;initial=None
    fixture_attempted = False
    try:
        summary['validation_apk_sha256']=hashlib.sha256(pathlib.Path(a.apk).read_bytes()).hexdigest()
        summary['host_script_sha256']=hashlib.sha256(pathlib.Path(__file__).read_bytes()).hexdigest()
        summary['expected_version_code']=a.expected_version_code
        if 'package:'in shell('pm path '+PKG,ok=False) or 'package:'in shell('pm path '+PKG+'.test',ok=False):raise Unverified('refuse existing installed validation or companion test package')
        meta=subprocess.run([a.aapt,'dump','badging',a.apk],capture_output=True,text=True,timeout=25,check=True).stdout
        if "name='"+PKG+"'" not in meta or ("versionCode='"+str(a.expected_version_code)+"'") not in meta:raise Unverified('APK package/version code mismatch')
        if (scroll_case or external_back) and re.search(r'^application-debuggable', meta, re.M):
            raise Unverified('this case requires a non-debuggable validation APK')
        initial=baseline()
        if initial['home']!=mainpkg:raise Unverified('baseline Home role differs from designated main package')
        (root/'baseline.json').write_text(json.dumps(initial))
        summary['phase']='install'
        if external_back:
            if shell('pm path '+FIXTURE, ok=False).strip():
                raise Unverified('refuse existing installed external fixture')
            summary['catalog_fixture'] = validate_catalog_fixture(
                a.catalog_fixture.resolve(strict=True), a.aapt, a.java_home, os.environ, deny_launch=False)
            fixture_attempted = True
            if 'Success' not in adb('install', '-t', str(a.catalog_fixture)):
                raise Unverified('external fixture install did not report success')
        own_install_attempted=True
        out=adb('install','-t',a.apk)
        if 'Success' not in out:raise Unverified('validation install did not report success')
        installed=True;summary['phase']='cold_launch';shell('am start -n '+COMP);time.sleep(1)
        tree=ui('cold')
        welcome=next((n for n in tree.iter('node') if n.get('package')==PKG
                      and n.get('text') in ('Explore first','Спершу оглянути')
                      and n.get('clickable')=='true'),None)
        if welcome is not None:tap_node(welcome);tree=ui('explored')
        if apps_case:
            nav=resource(tree,'nav_apps')
            if nav is None:raise Unverified('Apps rail missing')
            summary['phase']='apps_touch_to_dpad'
            tap_node(nav);tree=ui('apps-selected')
            if resource(tree,'apps_search') is None or resource(tree,'nav_apps').get('selected')!='true':raise Unverified('Apps page not confirmed before Right')
            if external_back:
                summary['phase'] = 'external_tile_setup'
                before = external_setup()
            elif query_case:
                summary['phase']='apps_query_input';before=query_setup(tree)
            elif scroll_case:
                summary['phase'] = 'apps_scroll_setup'
                shell('input dpad keyevent 22')
                first = stable_apps('apps-scroll-origin')
                if first['scroll_y_px'] != 0:
                    raise Unverified('fresh Apps viewport did not start at zero')
                for _ in range(4):
                    shell('input dpad keyevent 20')
                    time.sleep(.2)
                deepest = stable_apps('apps-scroll-deep')
                shell('input dpad keyevent 19')
                before = stable_apps('apps-scroll-before')
                if (before['scroll_y_px'] <= 0 or before['focus'] == deepest['focus']
                        or before['focus'] == first['focus'] or before['visible_tiles'] == first['visible_tiles']):
                    raise Unverified('nonzero scrolled viewport and distinct focused tile not proven')
                if scroll_rail:
                    for step in range(1, 6):
                        remembered_tile = before
                        shell('input dpad keyevent 21')
                        before = stable_apps(f'apps-scroll-left-{step}')
                        if (before['scroll_y_px'] != remembered_tile['scroll_y_px']
                                or before['visible_tiles'] != remembered_tile['visible_tiles']):
                            raise Unverified('Left changed the scrolled viewport before entering the Apps rail')
                        if before['focus_area'] == 'rail':
                            break
                    else:
                        raise Unverified('Left did not reach the Apps rail from the five-column grid')
                    summary['focus_area_before'] = before['focus_area']
                summary['scroll_y_before_px'] = before['scroll_y_px']
                summary['scroll_y_deepest_px'] = deepest['scroll_y_px']
            else:
                shell('input keyevent 22');time.sleep(.5)
                first=fingerprint(ui('apps-first'))
                for _ in range(3):shell('input keyevent 20');time.sleep(.15)
                before=fingerprint(ui('apps-scrolled'))
                if before['focus']==first['focus'] or before['visible_tiles']==first['visible_tiles']:raise Unverified('changed focused selection and visible viewport not both proven')
        elif home_case:
            summary['phase']='home_favorite_setup';before=home_setup()
        elif device_case:
            summary['phase']='device_diagnostics_setup';before, diagnostic_text = device_setup()
        else:
            summary['phase']='authors_setup';before=authors_setup()
        oldpid=pid()
        if not oldpid:raise Unverified('validation PID absent before background route')
        task=parse_task(dump('before-background'))
        witness.update(old_pid=oldpid,old_task_id=task)
        (root/'process-witness.json').write_text(json.dumps(witness,indent=2))
        summary['phase']='background_saved_task'
        if external_back:
            shell('input dpad keyevent 23')
            fixture_task = wait(lambda: resumed_task(dump('external-foreground'), fixture_component))
            fixture_pid = shell('pidof ' + FIXTURE).strip()
            if not re.fullmatch(r'[1-9][0-9]*', fixture_pid):
                raise Unverified('external fixture PID unavailable')
            witness.update(external_pid=fixture_pid, external_task_id=fixture_task)
        else:
            shell('am start -n '+a.main_component)
        def stopped():
            text=dump('stopped')
            blocks=re.split(r'(?=\s*\* Hist\s+#\d+: ActivityRecord\{)',text)
            for block in blocks:
                block=block.strip()
                first=block.splitlines()[0:1]
                if any(PKG+'/' in line for line in first):
                    # Stop at any further ActivityRecord boundary before testing state.
                    detail=re.split(r'\n[^\n]*ActivityRecord\{',block,maxsplit=1)[0]
                    if re.search(r'\b(?:mState|state)=STOPPED\b',detail):return True
            return False
        wait(stopped)
        summary['phase']='process_death';summary['process_death_attempted']=True
        shell('am kill --user current '+PKG);wait(lambda:not pid())
        dead_task = dump('dead-task')
        require_task = parse_task(dead_task)
        witness.update(dead_pid_empty=not bool(pid()),retained_task_id=require_task)
        (root/'process-witness.json').write_text(json.dumps(witness,indent=2))
        if require_task!=task:raise Unverified('task not retained after process death')
        summary['phase']='same_task_restore'
        relaunch_at = time.monotonic()
        if external_back:
            if (resumed_task(dead_task, fixture_component) != fixture_task
                    or shell('pidof ' + FIXTURE).strip() != fixture_pid):
                raise Unverified('same external fixture task and process did not retain foreground before Back')
            shell('input dpad keyevent 4')
            summary['restore_action'] = 'one D-pad Back from the foreground external fixture'
        else:
            relaunch = shell('am start -W -n '+COMP)
            (root/'restored-launch.txt').write_text(relaunch)
            try:
                summary['restore_first_display'] = parse_start(relaunch)
            except MeasurementUnavailable:
                summary['restore_first_display'] = None
        newpid=wait(pid);time.sleep(1)
        restored_activities=dump('restored-task')
        restored_task=parse_task(restored_activities);witness.update(new_pid=newpid,new_task_id=restored_task)
        (root/'process-witness.json').write_text(json.dumps(witness,indent=2))
        if newpid==oldpid or restored_task!=task:raise Unverified('new PID/same task restoration not proven')
        if external_back and resumed_task(restored_activities, COMP) != task:
            raise Unverified('retained validation task not resumed after Back')
        observed=[None];counter=[0]
        def stable_restore():
            counter[0]+=1
            try:
                tree=ui('restored-ui-'+str(counter[0]))
                if a.case=='authors-modal' and any('Apache License' in n.get('text','') for n in tree.iter('node')):raise AssertionError('License modal unexpectedly reopened after process death')
                if device_case:
                    if resource(tree, 'diagnostic_text') is not None:
                        raise AssertionError('Diagnostics modal unexpectedly reopened after process death')
                    value = device_fingerprint(tree)
                else:
                    value=fingerprint(tree) if apps_case else home_fingerprint(tree) if home_case else authors_fingerprint(tree)
            except Unverified:return False
            if value==observed[0]:return value
            observed[0]=value;return False
        after=wait(stable_restore,20)
        restore_observed_ms = round((time.monotonic() - relaunch_at) * 1000)
        if home_case:
            summary['favorite_index_after'] = after['favorite_index']
            summary['home_focus_after'] = after['focus_area']
            summary['favorite_order_and_bounds_equal'] = after['favorites'] == before['favorites']
        if a.case == 'apps-query-editor':
            summary['editor_focus_after'] = after['focus'] == before['focus']
            ime_after = re.findall(r'^\s*mInputShown=(true|false)\s*$', shell('dumpsys input_method'), re.M)
            if ime_after not in (['true'], ['false']):
                raise Unverified('Restored keyboard visibility unavailable')
            summary['editor_keyboard_after'] = ime_after == ['true']
            if summary['editor_keyboard_after']:
                raise AssertionError('Keyboard unexpectedly reopened after process death')
        if scroll_case:
            summary['scroll_y_after_px'] = after['scroll_y_px']
            summary['focused_app_equal'] = after['focus'] == before['focus']
            summary['visible_tiles_equal'] = after['visible_tiles'] == before['visible_tiles']
        if query_scroll:
            summary['query_equal'] = after['query_sha256'] == before['query_sha256']
        if scroll_rail:
            summary['focus_area_after'] = after['focus_area']
        if after!=before:raise AssertionError('observable context changed after real process death')
        if device_case:
            summary['device_origin_focus_restored'] = True
            reopened = open_diagnostic_report('diagnostics-open-after')
            if reopened != diagnostic_text:
                raise AssertionError('Reopened diagnostics report changed')
            summary['report_reopened_with_qr_and_matching_text'] = True
            shell('input dpad keyevent 4')
            if device_fingerprint(ui('diagnostics-back-after')) != before:
                raise AssertionError('Back from reopened diagnostics did not return to its original action')
            summary['back_restored_origin_focus'] = True
        if scroll_rail:
            shell('input dpad keyevent 22')
            reentered = stable_apps('apps-scroll-rail-return')
            summary['remembered_app_equal'] = reentered['focus'] == remembered_tile['focus']
            summary['scroll_y_after_reenter_px'] = reentered['scroll_y_px']
            if reentered != remembered_tile:
                raise AssertionError('Right from restored Apps rail did not return to the same visible tile and scroll')
        if a.case in ('home-rail','home-header'):
            shell('input dpad keyevent ' + ('22' if a.case == 'home-rail' else '20'))
            entry = home_fingerprint(ui('home-remembered-favorite'))
            if entry['favorite_index'] != summary['remembered_favorite_index']:
                raise AssertionError('Restored control did not lead back to the remembered favorite')
            summary['restored_entry_index'] = entry['favorite_index']
        if a.case == 'apps-query-editor':
            shell('input keyevent 23')
            wait(lambda: bool(re.search(r'^\s*mInputShown=true\s*$', shell('dumpsys input_method'), re.M)))
            shell('input keyevent 4')
            wait(lambda: bool(re.search(r'^\s*mInputShown=false\s*$', shell('dumpsys input_method'), re.M)))
            if stable_apps('query-editor-after-keyboard') != before:
                raise AssertionError('Explicit keyboard round trip changed the restored editor context')
            summary['editor_keyboard_round_trip'] = True
        summary.update(status='PASS',phase='verified',new_process=True,same_task=True,observable_context_equal=True,
                       launch_to_verified_restore_bound_ms=restore_observed_ms)
    except Exception as error:
        if isinstance(error,AssertionError):summary['status']='FAIL'
        summary['failure_type']=type(error).__name__;summary['failure']=str(error) if isinstance(error,(Unverified,AssertionError)) else 'host operation failed'
    finally:
        ui_cleanup_error=None
        if ui_owned:
            try:shell('rm -f '+ui_path)
            except Exception as error:ui_cleanup_error=error
        fixture_cleanup_error = None
        if fixture_attempted:
            try:
                if shell('pm path '+FIXTURE, ok=False).strip():
                    if 'Success' not in adb('uninstall', FIXTURE):
                        raise Unverified('external fixture cleanup failed')
                if shell('pm path '+FIXTURE, ok=False).strip():
                    raise Unverified('external fixture remains installed')
            except Exception as error:
                fixture_cleanup_error = error
        try:
            if own_install_attempted and shell('pm path '+PKG,ok=False).strip():
                if 'Success' not in adb('uninstall',PKG):raise Unverified('validation cleanup failed')
            if own_install_attempted or initial is not None:shell('am start -n '+a.main_component)
            if initial is not None and baseline()!=initial:raise AssertionError('baseline readbacks changed')
            if ui_cleanup_error is not None:raise Unverified('owned UI staging cleanup failed')
            if fixture_cleanup_error is not None:raise Unverified('owned external fixture cleanup failed')
            summary['cleanup']='PASS'
        except Exception as error:
            summary['cleanup']='FAIL';summary['cleanup_failure_type']=type(error).__name__
            summary['cleanup_failure']=str(error) if isinstance(error,(Unverified,AssertionError)) else 'host cleanup operation failed'
        (root/'summary.json').write_text(json.dumps(summary,indent=2));print(json.dumps(summary))
    return 0 if summary['status']=='PASS' and summary.get('cleanup')=='PASS' else 2
if __name__=='__main__':raise SystemExit(main())
