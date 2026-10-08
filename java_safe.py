from pathlib import Path
import re
ROOT=Path('/tmp/ztfinal')

def split_long_strings(s):
    i=0; out=''; changed=False
    while i<len(s):
        if s[i]=='"':
            j=i+1; esc=False
            while j<len(s):
                c=s[j]
                if esc: esc=False
                elif c=='\\': esc=True
                elif c=='"': break
                j+=1
            if j>=len(s): out+=s[i:]; break
            content=s[i+1:j]
            if len(content)>90:
                parts=[]; pos=0
                while pos<len(content):
                    end=min(pos+75,len(content))
                    if end<len(content):
                        cuts=[content.rfind(' ',pos,end),content.rfind('>',pos,end),content.rfind(',',pos,end),content.rfind(';',pos,end)]
                        cut=max(cuts)
                        if cut<pos+25: cut=end
                        else: cut+=1
                    else: cut=end
                    while cut>pos and content[cut-1]=='\\': cut-=1
                    parts.append(content[pos:cut]); pos=cut
                out+='"'+'" +\n'.join(parts)+'"' if False else ''
                # Correctly construct: "part" + newline + "part"
                repl=' +\n'.join('"'+x+'"' for x in parts)
                out+=repl; changed=True
            else: out+='"'+content+'"'
            i=j+1
        else:
            out+=s[i]; i+=1
    return out,changed

def wrap_code(s):
    base=re.match(r'\s*',s).group(0); start=0; i=0; state='n'; esc=False; block=False; cuts=[]
    while i<len(s):
        c=s[i]; n=s[i+1] if i+1<len(s) else ''
        if block:
            if c=='*' and n=='/': block=False; i+=2; continue
            i+=1; continue
        if state=='line': break
        if state in ('str','char'):
            if esc: esc=False
            elif c=='\\': esc=True
            elif (state=='str' and c=='"') or (state=='char' and c=="'"): state='n'
            i+=1; continue
        if c=='/' and n=='*': block=True; i+=2; continue
        if c=='/' and n=='/': state='line'; i+=2; continue
        if c=='"': state='str'; i+=1; continue
        if c=="'": state='char'; i+=1; continue
        op=None; oplen=1
        for x in ('&&','||','->','==','!=','>=','<=','+=','-=','*=','/='):
            if s.startswith(x,i): op=x; oplen=len(x); break
        if i-start>=75 and op:
            cuts.append(i+oplen); start=i+oplen
        elif i-start>=75 and c in ',;{}?:':
            cuts.append(i+1); start=i+1
        elif i-start>=90 and c in '+-=':
            cuts.append(i+1); start=i+1
        elif i-start>=100 and c=='.':
            cuts.append(i+1); start=i+1
        i+=1
    if not cuts: return s
    parts=[]; last=0
    for cut in cuts: parts.append(s[last:cut].strip()); last=cut
    parts.append(s[last:].strip())
    depth=0; out=[]
    for part in parts:
        if not part: continue
        if part.startswith('}'): depth=max(0,depth-1)
        out.append(base+'    '*depth+part)
        depth=max(0,depth+part.count('{')-part.count('}'))
    return '\n'.join(out)

for p in (ROOT/'apps').rglob('*.java'):
 lines=p.read_text(errors='ignore').splitlines()
 out=[]
 for line in lines:
  if len(line)>120:
   line,_=split_long_strings(line)
   if '\n' in line:
    out.extend(line.splitlines()); continue
   line=wrap_code(line)
   out.extend(line.splitlines())
  else: out.append(line)
 p.write_text('\n'.join(out)+'\n')
