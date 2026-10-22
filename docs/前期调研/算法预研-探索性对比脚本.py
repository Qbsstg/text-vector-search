import hashlib, math, re, unicodedata, json
from collections import Counter

D=256

def canonical(t):
    return ' '.join(unicodedata.normalize('NFKC',t).lower().split())

def chunks(t, ns=(1,2)):
    a=list(canonical(t))
    return Counter(f'{n}|'+''.join(a[i:i+n]) for n in ns for i in range(len(a)-n+1))

def hash256(features):
    raw=[0]*D
    for ft,c in features.items():
        h=hashlib.blake2b(ft.encode('utf8'),digest_size=8).digest()
        idx=int.from_bytes(h[:4], 'big') % D
        sign=1 if h[4]&1 else -1
        raw[idx]+=c*sign
    mag=math.sqrt(sum(k*k for k in raw))
    return [x/mag if mag else 0 for x in raw]

def v12(t): return hash256(chunks(t,(1,2)))
def v23(t): return hash256(chunks(t,(2,3))) if len(canonical(t))>1 else hash256(chunks(t,(1,)))
def v1(t): return hash256(chunks(t,(1,)))
def sha_whole(t):
    if not canonical(t):return [0.]*D
    seed=hashlib.sha256(canonical(t).encode()).digest()
    out=[]
    for i in range(D):
        b=hashlib.sha256(seed+i.to_bytes(2,'big')).digest()
        out.append(1. if b[0]&1 else -1.)
    return out

def simhash256(t):
    f=chunks(t,(1,2));acc=[0]*D
    for k,w in f.items():
        bitset=hashlib.sha256(k.encode()).digest()
        for i in range(D):
            acc[i]+=w*(1 if bitset[i//8] & (1<<(i%8)) else -1)
    return [1. if x>=0 else -1. for x in acc] if f else [0.]*D

def cosine(a,b):
    na=math.sqrt(sum(x*x for x in a)); nb=math.sqrt(sum(x*x for x in b))
    return sum(x*y for x,y in zip(a,b))/na/nb if na and nb else 0

pairs=[
('完全相同','共享充电宝押金退款规则','共享充电宝押金退款规则'),
('局部改写','共享充电宝押金退款规则','共享充电宝押金退还规则'),
('包含关系','代理商分润结算流程','代理商分润结算流程说明'),
('少量词序变化','设备离线故障处理','离线设备故障处理'),
('同义词但字面不同','怎样退钱','如何申请退款'),
('业务类别不同','代理商分润结算流程','设备离线故障处理'),
('极短空文本','', ''),
('英语局部改写','power bank deposit refund','power bank deposit refund process'),
]
rows=[]
for name,a,b in pairs:
    values={key:round(cosine(fn(a),fn(b)),4) for key,fn in [
        ('char_1+2_signed',v12),('char_2+3_signed',v23),('char_1_signed',v1),('simhash_256_cosine',simhash256),('sha_full',sha_whole)
    ]}
    rows.append({'case':name,**values})
assert v12('')==[0.]*D
assert v12('共享充电宝')==v12('共享充电宝')
assert len(v12('共享充电宝'))==D
print(json.dumps(rows,ensure_ascii=False,indent=2))
with open('/mnt/data/algorithm_reports_work/probe_results.json','w',encoding='utf8') as f:json.dump(rows,f,ensure_ascii=False,indent=2)
