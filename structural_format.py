from pathlib import Path
import re
ROOT=Path('/tmp/ztfinal')

def split_line(line, language):
    s=line.rstrip('\n')
    base=re.match(r'\s*',s).group(0)
    if not s.strip(): return [line]
    out=[]; start=0; par=br=0; state='n'; esc=False; block=False; i=0
    while i<len(s):
        c=s[i]; n=s[i+1] if i+1<len(s) else ''
        if block:
            if c=='*' and n=='/': block=False; i+=2; continue
            i+=1; continue
        if state=='line': break
        if state in ('str','char','template'):
            if esc: esc=False
            elif c=='\\': esc=True
            elif (state=='str' and c=='"') or (state=='char' and c=="'") or (state=='template' and c=='`'): state='n'
            i+=1; continue
        if c=='/' and n=='*': block=True; i+=2; continue
        if c=='/' and n=='/' : state='line'; i+=2; continue
        if c=='"': state='str'; i+=1; continue
        if c=="'": state='char'; i+=1; continue
        if c=='`' and language in ('ts','tsx'): state='template'; i+=1; continue
        if c=='(': par+=1
        elif c==')': par=max(0,par-1)
        elif c=='[': br+=1
        elif c==']': br=max(0,br-1)
        elif language=='java' and c in '{}':
            cut=i+1
            seg=s[start:cut].strip()
            if seg:
                out.append(seg); start=cut
                while start<len(s) and s[start].isspace(): start+=1
        elif c==';' and par==0 and br==0:
            cut=i+1; seg=s[start:cut].strip()
            if seg:
                out.append(seg); start=cut
                while start<len(s) and s[start].isspace(): start+=1
        i+=1
    tail=s[start:].strip()
    if tail: out.append(tail)
    if len(out)==1:return [line]
    # Preserve a sane base indent; nested brace indentation is handled simply.
    depth=0; result=[]
    for seg in out:
        if seg.startswith('}'): depth=max(0,depth-1)
        result.append(base+'    '*depth+seg)
        depth=max(0,depth+seg.count('{')-seg.count('}'))
    return [x+'\n' for x in result]

for root, ext_lang in [
    (ROOT/'apps', {'.java':'java','.rs':'rs','.sql':'sql'}),
    (ROOT/'infra', {'.ts':'ts'}),
    (ROOT/'dashboard', {'.ts':'ts','.tsx':'tsx'}),
]:
    for p in root.rglob('*'):
        if not p.is_file() or p.suffix.lower() not in ext_lang: continue
        lang=ext_lang[p.suffix.lower()]
        lines=p.read_text(errors='ignore').splitlines(True); out=[]
        for line in lines:
            # Structural split only for lines that contain multiple statements/blocks.
            if (';' in line or (lang=='java' and ('{' in line or '}' in line))):
                out.extend(split_line(line,lang))
            else: out.append(line)
        p.write_text(''.join(out))
