from pathlib import Path
import re
ROOT=Path('/tmp/ztfinal')

def string_parts(s,maxlen=55):
 out=''; i=0
 while i<len(s):
  if s[i]!='"': out+=s[i]; i+=1; continue
  j=i+1; esc=False
  while j<len(s):
   c=s[j]
   if esc: esc=False
   elif c=='\\': esc=True
   elif c=='"': break
   j+=1
  if j>=len(s): out+=s[i:]; break
  c=s[i+1:j]
  if len(c)>maxlen:
   parts=[]; pos=0
   while pos<len(c):
    end=min(pos+maxlen,len(c))
    if end<len(c):
     cuts=[c.rfind(' ',pos,end),c.rfind('>',pos,end),c.rfind(',',pos,end),c.rfind(';',pos,end)]
     cut=max(cuts)
     if cut<=pos: cut=end
     elif c[cut-1]=='\\': cut-=1
    else: cut=end
    parts.append(c[pos:cut]); pos=cut
   out+=' +\n'.join('"'+x+'"' for x in parts)
  else: out+='"'+c+'"'
  i=j+1
 return out

def wrap_spaces(s,maxlen=105):
 if len(s)<=120:return [s]
 # scan whitespace positions outside quoted strings; split at nearest whitespace before maxlen.
 chunks=[]; start=0; i=0; state=None; esc=False; last_space=-1
 while i<len(s):
  c=s[i]
  if state:
   if esc:esc=False
   elif c=='\\':esc=True
   elif c==state:state=None
  else:
   if c in ('"',"'"):state=c
   elif c.isspace():
    if i-start>=70:last_space=i
  if i-start>=maxlen and last_space>start:
   chunks.append(s[start:last_space].rstrip()); start=last_space+1; last_space=-1
  i+=1
 if start<len(s):chunks.append(s[start:].strip())
 return chunks

for root, exts in [(ROOT/'infra',{'.ts','.json','.yaml','.yml','.conf'}),(ROOT/'dashboard',{'.tsx','.ts','.css','.json'}),(ROOT/'apps',{'.java','.rs','.sql','.xml'})]:
 for p in root.rglob('*'):
  if not p.is_file() or p.suffix.lower() not in exts:continue
  lines=p.read_text(errors='ignore').splitlines(); out=[]
  for line in lines:
   if len(line)>120:
    line=string_parts(line)
    parts=[]
    for x in line.splitlines(): parts.extend(wrap_spaces(x))
    out.extend(parts)
   else: out.append(line)
  p.write_text('\n'.join(out)+'\n')
