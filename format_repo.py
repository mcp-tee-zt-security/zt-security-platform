from pathlib import Path
import json, re
import yaml
from lxml import etree

ROOT=Path('/tmp/ztfinal')
TARGETS=[ROOT/'infra', ROOT/'dashboard', ROOT/'apps']


def format_json(p):
    try:
        obj=json.loads(p.read_text())
        p.write_text(json.dumps(obj, indent=2, ensure_ascii=False)+"\n")
        return True
    except Exception:
        return False

def format_yaml(p):
    try:
        obj=yaml.safe_load(p.read_text())
        p.write_text(yaml.safe_dump(obj, sort_keys=False, allow_unicode=True, default_flow_style=False, width=100))
        return True
    except Exception:
        return False

def format_xml(p):
    try:
        parser=etree.XMLParser(remove_blank_text=True)
        root=etree.parse(str(p), parser)
        data=etree.tostring(root, pretty_print=True, encoding='unicode')
        p.write_text(data)
        return True
    except Exception:
        return False

def split_code_line(line, maxlen=100, minlen=70):
    if len(line.rstrip('\n')) <= 120:
        return [line]
    s=line.rstrip('\n')
    base=re.match(r'\s*',s).group(0)
    out=[]; start=0; last_break=-1; i=0
    state='normal'; esc=False; block_comment=False
    while i < len(s):
        ch=s[i]; nxt=s[i+1] if i+1<len(s) else ''
        if block_comment:
            if ch=='*' and nxt=='/': block_comment=False; i+=2; continue
            i+=1; continue
        if state=='line_comment': break
        if state in ('string','char'):
            if esc: esc=False
            elif ch=='\\': esc=True
            elif (state=='string' and ch=='"') or (state=='char' and ch=="'"): state='normal'
            i+=1; continue
        if ch=='/' and nxt=='*': block_comment=True; i+=2; continue
        if ch=='/' and nxt=='/': state='line_comment'; i+=2; continue
        if ch=='"': state='string'; i+=1; continue
        if ch=="'": state='char'; i+=1; continue
        if ch in ',;{}':
            last_break=i+1
            if i-start+1 >= minlen:
                seg=s[start:last_break].strip()
                if seg:
                    out.append(base+seg)
                    start=last_break
                    # indentation for nested brace on subsequent segments
                    while start < len(s) and s[start].isspace(): start+=1
        elif ch in '.':
            if i-start >= maxlen: last_break=i
        i+=1
    tail=s[start:].strip()
    if tail: out.append(base+tail)
    if len(out)==1: return [line]
    # Adjust indentation from braces in generated chunks.
    depth=0; fixed=[]
    for seg in out:
        t=seg.strip()
        if t.startswith('}'):
            depth=max(0,depth-1)
        indent=base+'    '*depth
        fixed.append(indent+t)
        depth += t.count('{')-t.count('}')
        depth=max(0,depth)
    return [x+'\n' for x in fixed]

def format_code(p):
    text=p.read_text(errors='ignore')
    lines=text.splitlines(True)
    out=[]
    for line in lines:
        if len(line.rstrip('\n'))>120:
            out.extend(split_code_line(line))
        else:
            out.append(line)
    p.write_text(''.join(out))

def format_css(p):
    s=p.read_text(errors='ignore')
    out=[]; buf=''; indent=0; state='normal'; esc=False; i=0
    def emit(text, extra=0):
        t=text.strip()
        if t: out.append('    '*max(0,indent+extra)+t)
    while i<len(s):
        ch=s[i]; nxt=s[i+1] if i+1<len(s) else ''
        if state=='comment':
            buf+=ch
            if ch=='*' and nxt=='/': buf+=nxt; i+=2; state='normal'; emit(buf); buf=''
            else:i+=1
            continue
        if state=='string':
            buf+=ch
            if esc: esc=False
            elif ch=='\\': esc=True
            elif ch in ('"',"'"): state='normal'
            i+=1; continue
        if ch=='/' and nxt=='*':
            if buf.strip(): emit(buf); buf=''
            buf='/*'; state='comment'; i+=2; continue
        if ch in ('"',"'"): state='string'; buf+=ch; i+=1; continue
        if ch=='{':
            emit(buf+' {'); buf=''; indent+=1
        elif ch=='}':
            if buf.strip(): emit(buf)
            buf=''; indent=max(0,indent-1); emit('}')
        elif ch==';':
            emit(buf+';'); buf=''
        else: buf+=ch
        i+=1
    if buf.strip(): emit(buf)
    p.write_text('\n'.join(out)+'\n')

for root in TARGETS:
    for p in root.rglob('*'):
        if not p.is_file(): continue
        suf=p.suffix.lower()
        try:
            if suf=='.json': format_json(p)
            elif suf in ('.yaml','.yml'): format_yaml(p)
            elif suf=='.xml': format_xml(p)
            elif suf=='.css': format_css(p)
            elif suf in ('.java','.rs','.sql','.ts','.tsx','.js','.jsx'):
                format_code(p)
        except Exception as e:
            print('WARN',p,e)
