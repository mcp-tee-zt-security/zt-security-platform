from pathlib import Path
import re
ROOT=Path('/tmp/ztfinal')

def split_commas(s):
 parts=[]; start=0; state=None; esc=False; par=br=0
 for i,c in enumerate(s):
  if state:
   if esc:esc=False
   elif c=='\\':esc=True
   elif c==state:state=None
   continue
  if c in ('"',"'",'`'):state=c;continue
  if c=='(':par+=1
  elif c==')':par=max(0,par-1)
  elif c=='[':br+=1
  elif c==']':br=max(0,br-1)
  elif c==',' and (par==0 or True):
   if i-start>55:
    parts.append(s[start:i+1].strip());start=i+1
 if parts:
  tail=s[start:].strip()
  if tail:parts.append(tail)
  return parts
 return [s]

for root in [ROOT/'infra',ROOT/'dashboard',ROOT/'apps']:
 for p in root.rglob('*'):
  if not p.is_file():continue
  lines=p.read_text(errors='ignore').splitlines();out=[]
  for line in lines:
   if len(line)<=120:out.append(line);continue
   parts=split_commas(line)
   if len(parts)>1:
    base=re.match(r'\s*',line).group(0)
    for part in parts:out.append(base+part)
   else:out.append(line)
  p.write_text('\n'.join(out)+'\n')
