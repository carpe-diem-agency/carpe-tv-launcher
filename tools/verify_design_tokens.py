#!/usr/bin/env python3
"""Strict offline equivalence of token extraction; not a build or visual approval."""
import argparse,hashlib,json,re,sys
from pathlib import Path
import xml.etree.ElementTree as ET
TOKEN=Path('app/src/main/res/values/design_tokens.xml')
REF=re.compile(r'@(color|dimen)/([A-Za-z0-9_]+)\Z')
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def tokens(path):
    root=ET.parse(path).getroot();result={}
    for node in root:
        if node.tag not in ('color','dimen'):raise ValueError('unsupported token type')
        key=(node.tag,node.attrib['name'])
        if key in result:raise ValueError('duplicate token')
        if list(node):raise ValueError('non-scalar token')
        result[key]=node.text or ''
    return result
def resolve(value,mapping,trail=(),expected=None):
    match=REF.fullmatch(value)
    if not match:return value
    key=match.groups()
    if expected and key[0]!=expected:raise ValueError('wrong resource type')
    if key not in mapping:raise ValueError('missing token: '+value)
    if key in trail:raise ValueError('cyclic token: '+value)
    return resolve(mapping[key],mapping,trail+(key,),key[0])
def color(value):
    if not re.fullmatch(r'#[0-9a-fA-F]{6}|#[0-9a-fA-F]{8}',value):raise ValueError('unsupported color literal')
    return '#'+('ff' if len(value)==7 else '')+value[1:].lower()
def validate(mapping):
    for key in mapping:
        value=resolve('@'+key[0]+'/'+key[1],mapping)
        if key[0]=='color':color(value)
        elif not re.fullmatch(r'-?(?:\d+(?:\.\d+)?|\.\d+)(?:dp|dip|sp|px|pt|in|mm)',value):raise ValueError('invalid dimension token')
def java(text,mapping):
    # Never substitute inside comments, quoted user text, or character literals.
    parts=re.split(r'("(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/)',text)
    call=re.compile(r'(?<![\w.])getColor\(R\.color\.(cd_[A-Za-z0-9_]+)\)')
    for index in range(0,len(parts),2):
        def replacement(match):return '0x'+color(resolve('@color/'+match[1],mapping))[1:]
        code=call.sub(replacement,parts[index])
        parts[index]=re.sub(r'0[xX][0-9a-fA-F]+',lambda match:match[0].lower(),code)
    return ''.join(parts)
def xml(path,mapping):
    def walk(node):
        attrs=[]
        for key,value in node.attrib.items():
            if REF.fullmatch(value):value=resolve(value,mapping)
            if re.fullmatch(r'#[0-9a-fA-F]{6}|#[0-9a-fA-F]{8}',value):value=color(value)
            attrs.append((key,value))
        text=node.text if node.text and not node.text.isspace() else None
        tail=node.tail if node.tail and not node.tail.isspace() else None
        return(node.tag,sorted(attrs),text,tail,[walk(child) for child in node])
    return walk(ET.parse(path).getroot())
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--before',required=True,type=Path);parser.add_argument('--repo',required=True,type=Path);parser.add_argument('--report',required=True,type=Path);args=parser.parse_args()
    if not args.report.resolve().is_relative_to(Path('/path/to/isolated-carpe-tv-runtime')):parser.error('report must be on CarpeTVBuild SSD')
    report={'status':'FAIL','scope':'strict Java source and resolved XML token equivalence only; build/runtime/visual checks separate','checks':{}}
    try:
        before=args.before.resolve();repo=args.repo.resolve();destination=args.report.resolve()
        if not destination.is_relative_to(Path('/path/to/isolated-carpe-tv-runtime')):raise ValueError('report must be on CarpeTVBuild SSD')
        files=sorted(p.relative_to(before) for p in before.rglob('*') if p.is_file() and p.suffix in ('.java','.xml'))
        if len(files)!=7 or TOKEN not in files:raise ValueError('expected exactly seven baseline source files')
        manifest=before.parent/'before-manifest.json'
        if manifest.exists():
            expected=json.loads(manifest.read_text())
            if set(expected)!=set(map(str,files)):raise ValueError('baseline manifest file set mismatch')
            for path in files:
                if digest(before/path)!=expected[str(path)]:raise ValueError('baseline manifest hash mismatch: '+str(path))
            report['baseline_manifest']='PASS'
        else:report['baseline_manifest']='NOT_PROVIDED'
        old=tokens(before/TOKEN);new=tokens(repo/TOKEN);validate(old);validate(new)
        for key,value in old.items():
            if key not in new or resolve(value,old,expected=key[0])!=resolve(new[key],new,expected=key[0]):raise ValueError('existing token changed: '+str(key))
        report['checks'][str(TOKEN)]='PASS: existing token subset unchanged, all references valid'
        for path in files:
            if path==TOKEN:continue
            if path.suffix=='.java':same=java((before/path).read_text(),old)==java((repo/path).read_text(),new)
            else:same=xml(before/path,old)==xml(repo/path,new)
            if not same:raise ValueError('normalized full source differs: '+str(path))
            report['checks'][str(path)]='PASS'
        report['status']='PASS'
    except Exception as error:report['failure']=str(error)
    args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2));print(json.dumps(report));return 0 if report['status']=='PASS' else 2
if __name__=='__main__':sys.exit(main())
