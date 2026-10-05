"""Import safeguards; optional integration proves rollback using the real catalog.

Run with MYSQL_PWD and --integration before applying the remaining manifest.
Only catalog, book-profile and topic tables are read or updated inside a rollback.
"""
import copy, importlib.util, json, pathlib, sys, types, unittest

spec=importlib.util.spec_from_file_location('reviewed_profiles',pathlib.Path(__file__).with_name('apply-reviewed-book-profiles.py'))
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
manifest=pathlib.Path(__file__).resolve().parents[1]/'docs/book-knowledge/profiles-reviewed-remaining-2026-10-05.json'
data=json.loads(manifest.read_text(encoding='utf-8'))

class ImportGuards(unittest.TestCase):
    def test_manifest_accepts_primary_sources(self): module.validate(data)

    def test_unverified_edition_fact_is_rejected(self):
        changed=copy.deepcopy(data);changed['books'][0]['fields']['isbn']='unverified'
        with self.assertRaises(ValueError):module.validate(changed)

    def test_unsourced_catalog_author_is_rejected(self):
        changed=copy.deepcopy(data);b=changed['books'][0];b['catalog']['author']='Unverified author'
        with self.assertRaises(ValueError):module.validate(changed)

    def test_source_host_lookalike_is_rejected(self):
        changed=copy.deepcopy(data);changed['books'][0]['sources'][0]['url']='https://nxbctqg.org.vn.example.com/book'
        with self.assertRaises(ValueError):module.validate(changed)

    def test_external_or_traversing_placeholder_is_rejected(self):
        for path in ('https://example.com/image.svg','/assets/../secret.svg'):
            changed=copy.deepcopy(data);changed['books'][0]['catalog']['image']=path
            with self.assertRaises(ValueError):module.validate(changed)

def integration():
    args=types.SimpleNamespace(mysql=r'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',host='127.0.0.1',port=3306,user='root',database='book')
    expr='JSON_OBJECT('+','.join("'%s',b.%s"%(k,k) for k in module.PROFILE_COLUMNS)+')'
    rows=[json.loads(line) for line in module.mysql(args,"SELECT JSON_OBJECT('product_id',p.product_id,'name',p.name,'author',p.author,'description',p.description,'image',p.image,'category_id',p.category_id,'category',c.category_name,'profile',"+expr+") FROM products p JOIN book_profiles b USING(product_id) LEFT JOIN categories c ON c.category_id=p.category_id WHERE p.status=1 AND b.approved=1 ORDER BY p.product_id;").splitlines()]
    links=[json.loads(line) for line in module.mysql(args,"SELECT JSON_OBJECT('product_id',l.product_id,'topic_id',l.topic_id,'name',t.name,'evidence',l.evidence) FROM book_topic_links l JOIN book_topics t ON t.id=l.topic_id ORDER BY l.id;").splitlines()]
    plan=module.prepare(data,rows,links)
    if len(plan)<2:raise RuntimeError('Integration check must run before importing this manifest')
    for item in plan:item['oldLinks']=[l for l in links if l['product_id']==item['row']['product_id']]
    # A catalog identity correction must never attach to a different product ID.
    wrong=copy.deepcopy(data);wrong['books'][0]['productId']=999999
    try:module.prepare(wrong,rows,links)
    except ValueError:pass
    else:raise AssertionError('Wrong catalog ID was accepted')
    # First product/profile/topic update succeeds, second stale row fails.
    # All tables must retain their prior contents after the connection closes.
    snapshots=["SELECT CONCAT(product_id,':',SHA2(CONCAT_WS('|',COALESCE(name,''),COALESCE(author,''),COALESCE(description,''),COALESCE(image,'')),256)) FROM products ORDER BY product_id;",
        'SELECT '+expr+' FROM book_profiles b ORDER BY b.product_id;',
        'SELECT id,name FROM book_topics ORDER BY id;',
        'SELECT id,product_id,topic_id,evidence FROM book_topic_links ORDER BY id;']
    before=[module.mysql(args,s) for s in snapshots]
    stale=copy.deepcopy(plan[:2]);stale[1]['row']['name']='Concurrent edit invalidates this plan'
    try:module.mysql(args,module.sql_for(stale))
    except RuntimeError:pass
    else:raise AssertionError('Stale plan was committed')
    after=[module.mysql(args,s) for s in snapshots]
    if before!=after:raise AssertionError('Failed transaction changed persistent catalog/profile/topic data')
    print('Integration: incorrect product ID rejected; stale transaction rolled back catalog, profile and topics.')

if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    run_integration='--integration' in sys.argv
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(ImportGuards))
    if not result.wasSuccessful():sys.exit(1)
    if run_integration:integration()
