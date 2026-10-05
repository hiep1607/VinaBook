"""Apply a reviewed work-level manifest to existing approved VinaBook profiles.

Requires the MySQL CLI and MYSQL_PWD (never writes credentials). Dry run is default.
Creates a local catalog/profile/topic backup before --apply. No account/order data is read.
"""
import argparse, datetime, hashlib, json, os, pathlib, re, subprocess, sys
from urllib.parse import urlparse

LIMITS={'summary':4000,'audience':160,'readingLevel':80,'readingStyle':160,'learningGoal':200}
COLUMNS={'summary':'summary','audience':'audience','readingLevel':'reading_level','readingStyle':'reading_style','learningGoal':'learning_goal'}
CATALOG_LIMITS={'title':255,'author':255,'description':1000,'image':255}
CATALOG_COLUMNS={'title':'name','author':'author','description':'description','image':'image'}
PROFILE_COLUMNS=['summary','audience','reading_level','reading_style','learning_goal','language','publisher','publication_year','page_count','isbn','source_note','search_text','updated_at']
TRUSTED={'www.informit.com','www.pragprog.com','theleanstartup.com','jareddiamond.org','www.penguinrandomhouse.com','nxbkimdong.com.vn','nhanam.vn','www.simonandschuster.com','firstnews.vn','www.parallax.org','www.lepetitprince.com','www.jimcollins.com','www.ynharari.com','www.jkrowling.com','www.nxbtre.com.vn','us.macmillan.com','www.dalecarnegie.com','paulocoelhoblog.com','freakonomics.com'}
TRUSTED.update({'www.nxbctqg.org.vn','nxbctqg.org.vn','vanban.chinhphu.vn','vbpl.moj.gov.vn',
    'thuvien.donga.edu.vn','nhasachbella.com','lic.haui.edu.vn','thuvien.hiu.vn','nlv.gov.vn',
    'vnuhcmpress.edu.vn','thuvien.stu.edu.vn','hannom.vass.gov.vn','js.vnu.edu.vn','nxbkhcntt.vn','thuquan.ou.edu.vn'})

def literal(value):
    if value is None: return 'NULL'
    return "CONVERT(X'%s' USING utf8mb4)" % str(value).encode('utf-8').hex()

def validate(data):
    if data.get('schemaVersion')!=1 or not re.fullmatch(r'\d{4}-\d{2}-\d{2}',data.get('reviewedAt','')):
        raise ValueError('Invalid manifest version or review date')
    seen=set()
    for book in data['books']:
        if book.get('reviewed') is not True or book.get('scope')!='work': raise ValueError('Only reviewed work-level profiles are allowed')
        identity=(book['title'],book['author'])
        if identity in seen or not all(identity): raise ValueError('Duplicate or missing book identity')
        seen.add(identity)
        if not book['fields'] or set(book['fields'])-set(LIMITS): raise ValueError('Unsupported fields, including edition-specific facts')
        for name,value in book['fields'].items():
            if not isinstance(value,str) or not value.strip() or value!=value.strip() or len(value)>LIMITS[name]:
                raise ValueError(f'{book["title"]}: invalid {name}')
        catalog=book.get('catalog',{})
        if set(catalog)-set(CATALOG_LIMITS): raise ValueError('Unsupported catalog correction')
        for name,value in catalog.items():
            if not isinstance(value,str) or not value.strip() or value!=value.strip() or len(value)>CATALOG_LIMITS[name]:
                raise ValueError(f'{book["title"]}: invalid catalog {name}')
        if catalog and (type(book.get('productId')) is not int or book['productId']<=0):
            raise ValueError('Catalog corrections require an exact product ID and identity')
        if 'image' in catalog and not re.fullmatch(r'/assets/book-cover-unverified-\d+\.svg',catalog['image']):
            raise ValueError('Only reviewed local cover placeholders may replace an unverified image')
        if not book.get('topics') or len(book['topics'])>20 or len(set(book['topics']))!=len(book['topics']): raise ValueError('Invalid topics')
        if any(not isinstance(t,str) or not t.strip() or len(t)>120 for t in book['topics']): raise ValueError('Invalid topic name')
        supported=set()
        for source in book['sources']:
            url=urlparse(source['url'])
            if url.scheme!='https' or url.hostname not in TRUSTED or url.username or url.password: raise ValueError('Source must be a reviewed primary URL')
            supported.update(source['supports'])
        if set(book['fields'])-supported or 'topics' not in supported: raise ValueError('Fields without source review')
        if {'catalog.'+k for k in catalog if k!='image'}-supported: raise ValueError('Catalog correction without source review')
        if 'image' in catalog and not book.get('coverReviewNote'): raise ValueError('Cover correction requires a review note')
        if len(source_note(data,book))>1000: raise ValueError('Source note exceeds service limit')
    if not seen: raise ValueError('Empty manifest')

def source_note(data,book):
    return 'Nguồn biên tập '+data['reviewedAt']+'; phạm vi tác phẩm, không xác nhận ấn bản. '+book['reviewNote']+' Nguồn: '+' | '.join(s['url'] for s in book['sources'])

def mysql(args,sql):
    proc=subprocess.run([args.mysql,'--no-defaults','--host='+args.host,'--port='+str(args.port),'--user='+args.user,
        '--database='+args.database,'--default-character-set=utf8mb4','--batch','--raw','--skip-column-names'],
        input=sql,encoding='utf-8',capture_output=True)
    if proc.returncode:
        # No SQL values or connection credentials in errors.
        raise RuntimeError('MySQL command failed; transaction was not committed. '+proc.stderr[:500])
    return proc.stdout

def equal(column,value,alias='b'):
    if column=='updated_at': return f'{alias}.{column} <=> CAST({literal(value)} AS DATETIME)'
    return f'BINARY {alias}.{column} <=> BINARY {literal(value)}'

def prepare(data,rows,links):
    plan=[]
    for book in data['books']:
        catalog=book.get('catalog',{})
        identities={(book['title'],book['author']),
            (catalog.get('title',book['title']),catalog.get('author',book['author']))}
        matches=[r for r in rows if (r['name'],r['author']) in identities]
        if len(matches)!=1: raise ValueError('Expected one active approved book: '+book['title'])
        row=matches[0]
        if catalog and row['product_id']!=book['productId']: raise ValueError('Catalog correction matched the wrong product ID')
        catalog_changes={CATALOG_COLUMNS[k]:v for k,v in catalog.items() if row[CATALOG_COLUMNS[k]]!=v}
        current=dict(row);current.update(catalog_changes)
        existing=[l['name'] for l in links if l['product_id']==row['product_id']]
        topics=existing+[t for t in book['topics'] if t not in existing]
        profile=dict(row['profile'])
        for field,value in book['fields'].items(): profile[COLUMNS[field]]=value
        profile['source_note']=source_note(data,book)
        # Same field order, limits and separators as BookKnowledgeService.composeText.
        profile['search_text']=' '.join([current['name'] or '',current['author'] or '',current['category'] or '',current['description'] or '',
            profile['summary'] or '',profile['audience'] or '',profile['reading_level'] or '',
            profile['reading_style'] or '',profile['learning_goal'] or '', ' '.join(topics)]).strip()
        changed=bool(catalog_changes) or any(profile[k]!=row['profile'][k] for k in PROFILE_COLUMNS if k!='updated_at') or topics!=existing
        if changed:
            if row['profile']['source_note']!= 'Mô tả sản phẩm hiện có trong catalog':
                raise ValueError('Profile already has editorial changes; review a new manifest instead of overwriting: '+book['title'])
            plan.append(dict(book=book,row=row,profile=profile,catalogChanges=catalog_changes,existingTopics=existing,topics=topics))
    return plan

def sql_for(plan):
    ids=','.join(str(p['row']['product_id']) for p in plan)
    sql=['SET NAMES utf8mb4;','CREATE TEMPORARY TABLE vinabook_profile_import_guard (ok INT NOT NULL CHECK(ok=1));',
        'SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;','START TRANSACTION;',
        f'SELECT p.product_id FROM products p JOIN book_profiles b USING(product_id) WHERE p.product_id IN ({ids}) FOR UPDATE;']
    for item in plan:
        row=item['row']; pid=row['product_id']; old=row['profile']; new=item['profile']
        conditions=[f'b.product_id={pid}','b.approved=1','p.status=1']+[equal(k,row[k],'p') for k in ('name','author','description','image','category_id')]
        conditions += [equal(k,old[k]) for k in PROFILE_COLUMNS]
        # Check old links inside the transaction, including their evidence.
        old_links=item['oldLinks']
        conditions.append(f'(SELECT COUNT(*) FROM book_topic_links WHERE product_id={pid})={len(old_links)}')
        for link in old_links:
            conditions.append(f"EXISTS(SELECT 1 FROM book_topic_links l JOIN book_topics t ON t.id=l.topic_id WHERE l.product_id={pid} AND l.topic_id={link['topic_id']} AND {equal('name',link['name'],'t')} AND {equal('evidence',link['evidence'],'l')})")
        assignments=[f'b.{k}={literal(new[k])}' for k in PROFILE_COLUMNS if k!='updated_at']+['b.embedding_json=NULL','b.updated_at=NOW()']
        sql.append('UPDATE book_profiles b JOIN products p USING(product_id) SET '+','.join(assignments)+' WHERE '+' AND '.join(conditions)+';')
        sql.append('INSERT INTO vinabook_profile_import_guard VALUES(ROW_COUNT());')
        if item['catalogChanges']:
            guards=[f'p.product_id={pid}','p.status=1']+[equal(k,row[k],'p') for k in ('name','author','description','image','category_id')]
            sql.append('UPDATE products p SET '+','.join(f'p.{k}={literal(v)}' for k,v in item['catalogChanges'].items())+' WHERE '+' AND '.join(guards)+';')
            sql.append('INSERT INTO vinabook_profile_import_guard VALUES(ROW_COUNT());')
        evidence='Nguồn biên tập đã đối chiếu: '+item['book']['sources'][0]['url']
        if len(evidence)>250: evidence='Nguồn đã đối chiếu trong book_profiles.source_note'
        for name in item['topics']:
            if name in item['existingTopics']: continue
            sql.append('INSERT INTO book_topics(name) SELECT '+literal(name)+' WHERE NOT EXISTS(SELECT 1 FROM book_topics WHERE name='+literal(name)+');')
            sql.append(f'INSERT INTO book_topic_links(product_id,topic_id,evidence) SELECT {pid},id,{literal(evidence)} FROM book_topics WHERE name={literal(name)};')
            sql.append('INSERT INTO vinabook_profile_import_guard VALUES(ROW_COUNT());')
    sql.extend(['COMMIT;','SELECT \'COMMITTED\';'])
    return '\n'.join(sql)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('manifest',type=pathlib.Path)
    parser.add_argument('--apply',action='store_true')
    parser.add_argument('--mysql',default=r'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe')
    parser.add_argument('--host',default='127.0.0.1');parser.add_argument('--port',type=int,default=3306)
    parser.add_argument('--user',default='root');parser.add_argument('--database',default='book')
    parser.add_argument('--backup-dir',type=pathlib.Path,default=pathlib.Path('.run-logs/book-profile-backups'))
    args=parser.parse_args()
    if not re.fullmatch('[A-Za-z0-9_]+',args.database): raise ValueError('Invalid database name')
    data=json.loads(args.manifest.read_text(encoding='utf-8-sig'));validate(data)
    if not os.environ.get('MYSQL_PWD'): raise ValueError('Set MYSQL_PWD in this process; do not put it in the manifest')
    version=mysql(args,'SELECT VERSION();').strip()
    version_match=re.match(r'^(\d+)\.(\d+)\.(\d+)',version)
    if not version_match or 'MariaDB' in version or tuple(map(int,version_match.groups()))<(8,0,16):
        raise ValueError('MySQL 8.0.16+ is required for enforced transaction CHECK guards')
    profile_expr='JSON_OBJECT('+','.join("'%s',b.%s"%(k,k) for k in PROFILE_COLUMNS)+')'
    rows=[json.loads(line) for line in mysql(args,"SELECT JSON_OBJECT('product_id',p.product_id,'name',p.name,'author',p.author,'description',p.description,'image',p.image,'price',p.price,'quantity',p.quantity,'discount',p.discount,'sold',p.sold,'category_id',p.category_id,'category',c.category_name,'profile',"+profile_expr+") FROM products p JOIN book_profiles b USING(product_id) LEFT JOIN categories c ON c.category_id=p.category_id WHERE p.status=1 AND b.approved=1 ORDER BY p.product_id;").splitlines()]
    links=[json.loads(line) for line in mysql(args,"SELECT JSON_OBJECT('product_id',l.product_id,'topic_id',l.topic_id,'name',t.name,'evidence',l.evidence) FROM book_topic_links l JOIN book_topics t ON t.id=l.topic_id ORDER BY l.id;").splitlines()]
    plan=prepare(data,rows,links)
    for item in plan: item['oldLinks']=[l for l in links if l['product_id']==item['row']['product_id']]
    summary={'mode':'apply' if args.apply else 'dry-run','reviewed':len(data['books']),'changes':len(plan),
        'unchanged':len(data['books'])-len(plan),'fields':{k:sum(k in x['fields'] for x in data['books']) for k in LIMITS},
        'catalogChanges':{k:sum(CATALOG_COLUMNS[k] in x['catalogChanges'] for x in plan) for k in CATALOG_COLUMNS},
        'activeIdsOutsideManifest':[r['product_id'] for r in rows if not any(r['product_id']==b.get('productId') for b in data['books'])]}
    if args.apply and plan:
        args.backup_dir.mkdir(parents=True,exist_ok=True)
        stamp=datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
        backup=args.backup_dir/('before-'+stamp+'.json')
        backup.write_text(json.dumps({'createdAt':datetime.datetime.now().astimezone().isoformat(),'database':args.database,
            'manifestSha256':hashlib.sha256(args.manifest.read_bytes()).hexdigest(),'profiles':plan},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        # Batch mode without --force exits on a failed CHECK/statement and rolls back on disconnect.
        result=mysql(args,sql_for(plan))
        if 'COMMITTED' not in result: raise RuntimeError('Missing commit result')
        summary['backup']=str(backup.resolve());summary['committed']=True
    print(json.dumps(summary,ensure_ascii=False,indent=2))

if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
