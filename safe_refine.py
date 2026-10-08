from pathlib import Path
import re
ROOT=Path('/tmp/ztfinal')

def jsx(p):
 s=p.read_text(errors='ignore')
 s=re.sub(r'><', '>\n<', s)
 s=re.sub(r'}\s*<', '}\n<', s)
 p.write_text(s)

def css(p):
 s=p.read_text(errors='ignore'); out=[]; buf=[]; indent=0; state=None; esc=False; i=0
 def flush():
  nonlocal buf
  t=''.join(buf).strip()
  if t: out.append('    '*indent+t)
  buf=[]
 while i<len(s):
  c=s[i]; n=s[i+1] if i+1<len(s) else ''
  if state=='comment':
   buf.append(c)
   if c=='*' and n=='/': buf.append(n); i+=2; state=None; flush(); continue
   i+=1; continue
  if state:
   buf.append(c)
   if esc: esc=False
   elif c=='\\': esc=True
   elif c==state: state=None
   i+=1; continue
  if c=='/' and n=='*': buf.extend(['/', '*']); i+=2; state='comment'; continue
  if c in ('"',"'"): state=c; buf.append(c); i+=1; continue
  if c=='{': flush(); indent+=1; out.append('    '*(indent-1)+'{')
  elif c=='}': flush(); indent=max(0,indent-1); out.append('    '*indent+'}')
  elif c==';': buf.append(c); flush()
  else: buf.append(c)
  i+=1
 flush(); p.write_text('\n'.join(out)+'\n')

for p in (ROOT/'dashboard').rglob('*.tsx'): jsx(p)
for p in (ROOT/'dashboard').rglob('*.css'): css(p)
