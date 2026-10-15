"""Apply a named, bounded set of reviewed labels to existing approved profiles.

Uses existing book_topics/book_topic_links; no schema change or second catalog.
Dry run is default. MYSQL_PWD is process-only. Backup contains book data only.
Unknowns/limitations remain in the review manifest, never in the search index.
"""
import argparse, datetime, hashlib, json, os, pathlib, re, subprocess
from urllib.parse import urlparse

DIM={'genre':'Thể loại','topic':'Chủ đề','purpose':'Mục đích','format':'Dạng nội dung','audience':'Độc giả','style':'Cách trình bày','level':'Mức đọc'}
FIELDS={'summary':'summary','audience':'audience','readingLevel':'reading_level','readingStyle':'reading_style','learningGoal':'learning_goal','sourceNote':'source_note','searchText':'search_text'}
SCOPES={
 'pilot20':('pilotId','labels-20-2026-10-10',frozenset((7,9,13,16,17,19,22,26,27,29,32,37,38,43,48,50,51,52,55,57))),
 'remaining36':('expansionId','labels-remaining-36-2026-10-10',frozenset((3,4,5,6,8,10,11,12,14,15,18,20,21,23,24,25,28,30,31,33,34,35,36,39,40,41,42,44,45,46,47,49,53,54,56,58)))
}

def literal(value):
    return 'NULL' if value is None else "CONVERT(X'%s' USING utf8mb4)"%str(value).encode('utf-8').hex()
def tag(label): return DIM[label['dimension']]+': '+label['value']
def validate(manifest, snapshot, scope='pilot20'):
    if scope not in SCOPES: raise ValueError('Unknown import scope')
    key,identity,allowed=SCOPES[scope]
    if manifest.get('schemaVersion')!=1 or manifest.get(key)!=identity or len(manifest.get('books',[]))!=len(allowed):
        raise ValueError('Expected the named bounded label manifest: '+scope)
    if {book.get('productId') for book in manifest['books']}!=allowed:
        raise ValueError('Manifest product IDs do not match the authorized scope')
    rows={b['productId']:b for b in snapshot['books']}; seen=set(); plan=[]
    for book in manifest['books']:
        pid=book['productId']
        if type(pid) is not int or pid in seen: raise ValueError('Invalid or duplicate product ID')
        seen.add(pid);row=rows.get(pid)
        if not row or row['name']!=book['title'] or row['author']!=book['author'] or row['status']!=1 or row['profile']['approved']!=1:
            raise ValueError('Book identity or approved state changed: '+str(pid))
        if not 5<=len(book['labels'])<=14: raise ValueError('Expected 5–14 detailed labels per book')
        names=[]
        for label in book['labels']:
            if label.get('dimension') not in DIM or label.get('status')!='reviewed_editorial': raise ValueError('Unreviewed label or dimension')
            value=label.get('value')
            if not isinstance(value,str) or not value.strip() or value!=value.strip(): raise ValueError('Invalid label value')
            name=tag(label)
            if len(name)>120 or name.casefold() in [n.casefold() for n in names]: raise ValueError('Oversized or duplicate label')
            evidence=label.get('evidence',{});field=evidence.get('field');quote=evidence.get('quote')
            if field not in FIELDS or field in ('sourceNote','searchText') or not isinstance(quote,str) or not quote.strip() or quote not in (row['profile'].get(field) or ''):
                raise ValueError('Label has no exact approved source: '+str(pid))
            if not label.get('sourceUrls'): raise ValueError('Missing reviewed source URLs')
            for url in label['sourceUrls']:
                parsed=urlparse(url)
                if parsed.scheme!='https' or parsed.username or parsed.password or url not in (row['profile'].get('sourceNote') or ''):
                    raise ValueError('Source URL does not match the approved profile')
            names.append(name)
        if not book.get('unknowns') or not book.get('limitations'): raise ValueError('Missing explicit knowledge limitations')
        existing=[t['name'] for t in row['topics'] or []]
        added=[n for n in names if n.casefold() not in {s.casefold() for s in existing}]
        if len(existing)+len(added)>20: raise ValueError('Would exceed the existing editor 20-topic limit')
        search=(row['profile']['searchText'] or '')
        if added:
            values=[label['value'] for label in book['labels'] if tag(label) in added]
            plan.append({'book':book,'row':row,'added':added,'afterSearchText':search+' '+' '.join(values)})
    return plan
def mysql(args, sql):
    proc=subprocess.run([args.mysql,'--no-defaults','--host='+args.host,'--port='+str(args.port),'--user='+args.user,'--database='+args.database,
       '--default-character-set=utf8mb4','--batch','--raw','--skip-column-names'],input=sql,encoding='utf-8',capture_output=True)
    if proc.returncode: raise RuntimeError('MySQL statement failed; transaction not committed. Details suppressed to protect connection data.')
    return proc.stdout
def snapshot(args):
    expr=','.join(literal(k)+',b.'+v for k,v in FIELDS.items())
    sql="SELECT JSON_OBJECT('productId',p.product_id,'name',p.name,'author',p.author,'status',p.status+0,'profile',JSON_OBJECT("+expr+",'approved',b.approved+0),'topics',(SELECT JSON_ARRAYAGG(JSON_OBJECT('name',t.name,'topicId',t.id,'evidence',l.evidence)) FROM book_topic_links l JOIN book_topics t ON t.id=l.topic_id WHERE l.product_id=p.product_id)) FROM products p JOIN book_profiles b USING(product_id) WHERE p.status=1 ORDER BY p.product_id;"
    return {'books':[json.loads(line) for line in mysql(args,sql).splitlines() if line.strip()]}
def guard(row, search_override=None, added=()):
    parts=[f"p.product_id={row['productId']}",'p.status=1','b.approved=1',f"BINARY p.name=BINARY {literal(row['name'])}",f"BINARY p.author=BINARY {literal(row['author'])}"]
    for field,col in FIELDS.items():
        val=search_override if field=='searchText' and search_override is not None else row['profile'].get(field)
        parts.append(f'BINARY b.{col} <=> BINARY {literal(val)}')
    topic_names=[t['name'] for t in row.get('topics') or []]+list(added)
    parts.append(f"(SELECT COUNT(*) FROM book_topic_links WHERE product_id=p.product_id)={len(topic_names)}")
    for name in topic_names:
        parts.append(f'EXISTS(SELECT 1 FROM book_topic_links l JOIN book_topics t ON t.id=l.topic_id WHERE l.product_id=p.product_id AND BINARY t.name=BINARY {literal(name)})')
    return ' AND '.join('('+p+')' for p in parts)
def transaction(plan, rollback=False, scope='pilot20'):
    sql=['SET NAMES utf8mb4;','CREATE TEMPORARY TABLE label_pilot_guard(ok INT NOT NULL CHECK(ok=1));','SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;','START TRANSACTION;']
    ids=','.join(str(p['row']['productId']) for p in plan)
    sql.append(f'SELECT product_id FROM book_profiles WHERE product_id IN ({ids}) FOR UPDATE;')
    for item in plan:
        row=item['row'];pid=row['productId']
        condition=guard(row,item['afterSearchText'] if rollback else None,item['added'] if rollback else ())
        sql.append('INSERT INTO label_pilot_guard SELECT COUNT(*) FROM products p JOIN book_profiles b USING(product_id) WHERE '+condition+';')
        if rollback:
            sql.append(f"UPDATE book_profiles SET search_text={literal(row['profile']['searchText'])},embedding_json={literal(item.get('beforeEmbedding'))},updated_at=NOW() WHERE product_id={pid};")
            for name in item['added']:
                sql.append(f'DELETE l FROM book_topic_links l JOIN book_topics t ON t.id=l.topic_id WHERE l.product_id={pid} AND BINARY t.name=BINARY {literal(name)};')
                sql.append('INSERT INTO label_pilot_guard VALUES(ROW_COUNT());')
        else:
            sql.append(f"UPDATE book_profiles SET search_text={literal(item['afterSearchText'])},embedding_json=NULL,updated_at=NOW() WHERE product_id={pid};")
            for label in item['book']['labels']:
                name=tag(label)
                if name not in item['added']: continue
                evidence=label['evidence']
                note=(('Pilot20' if scope=='pilot20' else 'Labels36')+' 2026-10-10 | '+evidence['field']+' | '+evidence['quote'])[:250]
                sql.append('INSERT INTO book_topics(name) VALUES('+literal(name)+') ON DUPLICATE KEY UPDATE id=id;')
                sql.append(f'INSERT INTO book_topic_links(product_id,topic_id,evidence) SELECT {pid},id,{literal(note)} FROM book_topics WHERE name={literal(name)};')
                sql.append('INSERT INTO label_pilot_guard VALUES(ROW_COUNT());')
    sql+=['COMMIT;',"SELECT 'COMMITTED';"]
    return '\n'.join(sql)
def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('manifest',type=pathlib.Path)
    ap.add_argument('--scope',choices=SCOPES,default='pilot20',help='Explicit bounded import; pilot20 remains the default')
    ap.add_argument('--apply',action='store_true');ap.add_argument('--rollback',type=pathlib.Path)
    ap.add_argument('--expected-snapshot',type=pathlib.Path,required=True)
    ap.add_argument('--mysql',default=r'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe')
    ap.add_argument('--host',default='127.0.0.1');ap.add_argument('--port',type=int,default=3306);ap.add_argument('--user',default='root');ap.add_argument('--database',default='book')
    ap.add_argument('--backup-dir',type=pathlib.Path,default=pathlib.Path('.run-logs/book-label-pilot'))
    args=ap.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_]+',args.database) or 'MYSQL_PWD' not in os.environ: raise ValueError('Invalid database or missing process MYSQL_PWD')
    version=mysql(args,'SELECT VERSION();').strip();v=re.match(r'^(\d+)\.(\d+)\.(\d+)',version)
    if not v or 'MariaDB' in version or tuple(map(int,v.groups()))<(8,0,16): raise ValueError('Enforced MySQL CHECK guards require MySQL 8.0.16+')
    manifest=json.loads(args.manifest.read_text(encoding='utf-8-sig'))
    expected=json.loads(args.expected_snapshot.read_text(encoding='utf-8-sig'))
    current=snapshot(args)
    if args.rollback:
        backup=json.loads(args.rollback.read_text(encoding='utf-8'))
        if backup['database']!=args.database or backup.get('scope','pilot20')!=args.scope or backup['manifestSha256']!=hashlib.sha256(args.manifest.read_bytes()).hexdigest(): raise ValueError('Backup scope mismatch')
        plan=backup['plan']
        if not args.apply: print(json.dumps({'mode':'rollback-dry-run','books':len(plan)}));return
    else:
        validate(manifest,expected,args.scope)
        plan=validate(manifest,current,args.scope)
        old={r['productId']:r for r in expected['books']}
        for item in plan:
            previous=old[item['row']['productId']]
            for field in FIELDS:
                if previous['profile'].get(field)!=item['row']['profile'].get(field): raise ValueError('Profile edited since snapshot; review again')
            if {t['name'] for t in previous['topics'] or []}!={t['name'] for t in item['row']['topics'] or []}: raise ValueError('Topics edited since snapshot; review again')
        if args.apply and plan:
            vectors={int(pid):vector for pid,vector in [line.split('\t',1) for line in mysql(args,"SELECT product_id,COALESCE(embedding_json,'NULL') FROM book_profiles WHERE product_id IN ("+','.join(str(x['row']['productId']) for x in plan)+");").splitlines()]}
            for item in plan: item['beforeEmbedding']=None if vectors[item['row']['productId']]=='NULL' else vectors[item['row']['productId']]
            args.backup_dir.mkdir(parents=True,exist_ok=True)
            backup_path=args.backup_dir/('before-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+'.json')
            backup_path.write_text(json.dumps({'database':args.database,'scope':args.scope,'manifestSha256':hashlib.sha256(args.manifest.read_bytes()).hexdigest(),'plan':plan},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    if args.apply and plan:
        if 'COMMITTED' not in mysql(args,transaction(plan,bool(args.rollback),args.scope)): raise RuntimeError('Missing commit result')
    print(json.dumps({'mode':('rollback-' if args.rollback else '')+('apply' if args.apply else 'dry-run'),'books':len(plan),'labels':sum(len(x['added']) for x in plan),'committed':bool(args.apply and plan)},ensure_ascii=False))
if __name__=='__main__': main()
