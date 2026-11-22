#!/usr/bin/env python3
"""Offline fixture build; explicit local SDK/JDK/keystore and isolated fixture output, no installs."""
import argparse, hashlib, os, pathlib, re, subprocess, tempfile, xml.etree.ElementTree as ET, zipfile

PACKAGE='agency.carpediem.tv.testfixture.catalog'
ANDROID='{http://schemas.android.com/apk/res/android}'
def run(argv):
    return subprocess.run([str(x) for x in argv],check=True,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT).stdout

def main():
    p=argparse.ArgumentParser()
    for name in ('sdk','build-tools','java-home','output','keystore','key-alias','certificate-sha256'):p.add_argument('--'+name,required=True)
    p.add_argument('--platform',default='android-34')
    p.add_argument('--deny-launch', action='store_true', help='Protect the fixture activity with android.permission.DUMP')
    args=p.parse_args()
    if not __debug__:
        p.error('Run without Python optimization; fixture contract checks must stay enabled')
    source=pathlib.Path(__file__).resolve().parent
    manifest=ET.parse(source/'AndroidManifest.xml').getroot()
    assert manifest.attrib=={'package':PACKAGE,ANDROID+'versionCode':'1',ANDROID+'versionName':'1.0'}
    assert [node.tag for node in manifest]==['uses-sdk','application']
    application=manifest.find('application')
    assert application.attrib=={ANDROID+'label':'Carpe QA Калина',ANDROID+'testOnly':'true',ANDROID+'allowBackup':'false',ANDROID+'theme':'@android:style/Theme.Material.NoActionBar'}
    assert [node.tag for node in application]==['activity']
    activity=application.find('activity')
    assert activity.attrib=={ANDROID+'name':PACKAGE+'.CatalogActivity',ANDROID+'exported':'true'}
    assert [node.tag for node in activity]==['intent-filter']
    assert [(node.tag,node.get(ANDROID+'name')) for node in activity[0]]==[
        ('action','android.intent.action.MAIN'),('category','android.intent.category.LAUNCHER'),('category','android.intent.category.LEANBACK_LAUNCHER')]
    assert manifest.find('uses-sdk').attrib=={ANDROID+'minSdkVersion':'26',ANDROID+'targetSdkVersion':'34'}
    output=pathlib.Path(args.output).resolve()
    runtime=(pathlib.Path(__file__).resolve().parents[2].parent / 'carpe-tv-runtime-local').resolve()
    fixtures=runtime/'fixtures'
    assert runtime.is_dir(), 'Approved local runtime root must already exist'
    assert output.is_relative_to(fixtures) and output!=fixtures, 'Use a fresh child of approved local runtime fixtures directory'
    assert output.parent.is_dir(), 'Existing fixture output parent required'
    output.mkdir(exist_ok=False) # Fresh directory: never overwrite another artifact.
    sdk=pathlib.Path(args.sdk).resolve();bt=sdk/'build-tools'/args.build_tools
    java=pathlib.Path(args.java_home).resolve()/'bin';android=sdk/'platforms'/args.platform/'android.jar'
    os.environ['JAVA_HOME']=str(java.parent)
    os.environ['PATH']=str(java)+os.pathsep+os.environ['PATH']
    # Passwords are supplied only through environment references, never command arguments/logs.
    assert os.environ.get('CATALOG_STORE_PASSWORD') and os.environ.get('CATALOG_KEY_PASSWORD'), 'Keystore password env vars required'
    with tempfile.TemporaryDirectory(prefix='work-',dir=output) as work:
        work=pathlib.Path(work);classes=work/'classes';classes.mkdir();dex=work/'dex';dex.mkdir()
        apk=work/'unsigned.apk'
        manifest_path=source/'AndroidManifest.xml'
        if args.deny_launch:
            ET.register_namespace('android', ANDROID[1:-1])
            activity.set(ANDROID+'permission', 'android.permission.DUMP')
            manifest_path=work/'AndroidManifest.xml'
            ET.ElementTree(manifest).write(manifest_path, encoding='utf-8', xml_declaration=True)
        run([bt/'aapt2','link','-o',apk,'--manifest',manifest_path,'-I',android])
        run([java/'javac','-source','8','-target','8','-bootclasspath',android,'-d',classes,source/'src/agency/carpediem/tv/testfixture/catalog/CatalogActivity.java'])
        run([bt/'d8','--min-api','26','--lib',android,'--output',dex,*classes.rglob('*.class')])
        with zipfile.ZipFile(apk,'a',compression=zipfile.ZIP_DEFLATED) as archive:archive.write(dex/'classes.dex','classes.dex')
        aligned=work/'aligned.apk';run([bt/'zipalign','-p','4',apk,aligned])
        signed=work/'catalog-fixture.apk'
        run([bt/'apksigner','sign','--ks',pathlib.Path(args.keystore).resolve(),'--ks-key-alias',args.key_alias,'--ks-pass','env:CATALOG_STORE_PASSWORD','--key-pass','env:CATALOG_KEY_PASSWORD','--out',signed,aligned])
        verification=run([bt/'apksigner','verify','--verbose','--print-certs',signed])
        expected=args.certificate_sha256.replace(':','').lower()
        assert len(expected)==64 and all(c in '0123456789abcdef' for c in expected)
        digests=[line.split(':',1)[1].strip().replace(':','').lower() for line in verification.splitlines() if 'certificate SHA-256 digest:' in line]
        assert digests==[expected], 'Unexpected signing certificate'
        run([bt/'zipalign','-c','-p','4',signed])
        with zipfile.ZipFile(signed) as archive:
            assert archive.testzip() is None, 'APK CRC failure'
            assert 'classes.dex' in archive.namelist() and 'AndroidManifest.xml' in archive.namelist()
        badging=run([bt/'aapt2','dump','badging',signed])
        assert "package: name='"+PACKAGE+"' versionCode='1' versionName='1.0'" in badging
        (output/'badging-diagnostic.txt').write_text(badging,encoding='utf-8')
        labels=re.findall(r"^application: label='([^']*)'(?: |$)",badging,re.MULTILINE)
        assert labels==['Carpe QA Калина'], 'Unexpected manifest application label'
        signed.replace(output/'catalog-fixture.apk')
        signed=output/'catalog-fixture.apk'
        (output/'verification.txt').write_text(verification+badging,encoding='utf-8')
        digest=hashlib.sha256(signed.read_bytes()).hexdigest()
        (output/'sha256.txt').write_text(digest+'  catalog-fixture.apk\n')
        print('Verified fixture: '+str(signed)+' SHA256 '+digest)
if __name__=='__main__':main()
