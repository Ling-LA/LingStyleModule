"""Add one non-exported foreground service to an existing binary Android manifest.
Preserves every existing XML node and string index. Requires existing FGS permissions.
"""
import struct,sys,zipfile
from pathlib import Path
u32=lambda b,o:struct.unpack_from('<I',b,o)[0]
with zipfile.ZipFile(sys.argv[1]) as z:data=z.read('AndroidManifest.xml')
assert struct.unpack_from('<HH',data)==(3,8)
chunks=[];offset=8
while offset<len(data):
 typ,header,size=struct.unpack_from('<HHI',data,offset);assert size>=header
 chunks.append([typ,data[offset:offset+size]]);offset+=size
pool=next(b for t,b in chunks if t==1)
count,styles,flags,start,style_start=struct.unpack_from('<IIIII',pool,8)
assert styles==0 and flags&256 and not flags&1
indices=list(struct.unpack_from('<'+'I'*count,pool,28));strings=[]
def length(b,o):
 a=b[o];return (((a&127)<<8)|b[o+1],o+2) if a&128 else (a,o+1)
for i in indices:
 _,p=length(pool,start+i);n,p=length(pool,p);strings.append(pool[p:p+n].decode('utf-8'))
assert 'android.permission.FOREGROUND_SERVICE_SPECIAL_USE' in strings
service='io.github.ling.style.service.HostReplyService'
assert service not in strings,'Service already declared; use ordinary module update'
raw=bytearray(pool[start:]);new_indices=list(indices)
def put(s):
 if s in strings:return strings.index(s)
 b=s.encode('utf-8');n=len(s.encode('utf-16le'))//2
 def enc(v):return bytes([(v>>8)|128,v&255]) if v>127 else bytes([v])
 new_indices.append(len(raw));raw.extend(enc(n)+enc(len(b))+b+b'\0');strings.append(s);return len(strings)-1
resource=next(b for t,b in chunks if t==0x180)
resources=list(struct.unpack_from('<'+'I'*((len(resource)-8)//4),resource,8))
def attr(key,value,kind=3):
 ids={'name':0x01010003,'exported':0x01010010,'value':0x01010024,'foregroundServiceType':0x01010599}
 name=resources.index(ids[key]);assert strings[name]==key
 v=put(value) if kind==3 else value
 return struct.pack('<IIIHBBI',put('http://schemas.android.com/apk/res/android'),name,v if kind==3 else 0xffffffff,8,0,kind,v)
def element(name,attributes):
 return struct.pack('<HHIII',0x102,16,36+20*len(attributes),0,0xffffffff)+struct.pack('<IIHHHHHH',0xffffffff,put(name),20,20,len(attributes),0,0,0)+b''.join(attributes)
def end(name):return struct.pack('<HHIIIII',0x103,16,24,0,0xffffffff,0xffffffff,put(name))
node=element('service',[attr('name',service),attr('exported',0,0x12),attr('foregroundServiceType',0x40000000,0x11)])
node+=element('property',[attr('name','android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE'),attr('value','User-enabled QQ automatic reply: continuous incoming message processing and network generation.')])+end('property')+end('service')
while len(raw)%4:raw.append(0)
new_start=28+4*len(strings);new_pool=struct.pack('<HHIIIIII',1,28,new_start+len(raw),len(strings),0,flags,new_start,0)+struct.pack('<'+'I'*len(strings),*new_indices)+raw
result=[];injected=False
for typ,b in chunks:
 if typ==1:b=new_pool
 if typ==0x103 and strings[u32(b,20)]=='application':result.append(node);injected=True
 result.append(b)
assert injected
out=b''.join(result);Path(sys.argv[2]).write_bytes(struct.pack('<HHI',3,8,8+len(out))+out)
print('Added one non-exported specialUse service; original nodes and permissions preserved')
