import copy,importlib.util,json,pathlib,unittest
BASE=pathlib.Path(__file__).parent
spec=importlib.util.spec_from_file_location('labels',BASE/'book-label-pilot.py');labels=importlib.util.module_from_spec(spec);spec.loader.exec_module(labels)
DATA=BASE.parent/'VinaBookStore/src/test/resources/book-label-expansion'
class LabelExpansionTest(unittest.TestCase):
    def setUp(self):
        self.manifest=json.loads((DATA/'labels.json').read_text(encoding='utf-8'))
        self.snapshot=json.loads((DATA/'catalog-before.json').read_text(encoding='utf-8'))
    def test_36_remaining_books_have_284_evidenced_labels(self):
        plan=labels.validate(self.manifest,self.snapshot,'remaining36')
        self.assertEqual(36,len(plan));self.assertEqual(284,sum(len(p['added']) for p in plan))
        self.assertFalse({p['row']['productId'] for p in plan}&labels.SCOPES['pilot20'][2])
    def test_default_does_not_silently_expand_the_pilot_scope(self):
        with self.assertRaises(ValueError):labels.validate(self.manifest,self.snapshot)
    def test_refuses_out_of_scope_id_even_when_count_matches(self):
        self.manifest['books'][0]['productId']=7
        with self.assertRaises(ValueError):labels.validate(self.manifest,self.snapshot,'remaining36')
    def test_refuses_unknown_or_unsupported_source(self):
        self.manifest['books'][0]['labels'][0]['evidence']['field']='searchText'
        with self.assertRaises(ValueError):labels.validate(self.manifest,self.snapshot,'remaining36')
    def test_refuses_fabricated_source_or_unapproved_book(self):
        self.manifest['books'][0]['labels'][0]['evidence']['quote']='Có SQL, Java 21 và bài tập kèm đáp án'
        with self.assertRaises(ValueError):labels.validate(self.manifest,self.snapshot,'remaining36')
        self.setUp();self.snapshot['books'][0]['profile']['approved']=0
        with self.assertRaises(ValueError):labels.validate(self.manifest,self.snapshot,'remaining36')
    def test_preserves_all_pilot_books_and_is_idempotent(self):
        old=copy.deepcopy([r for r in self.snapshot['books'] if r['productId'] in labels.SCOPES['pilot20'][2]])
        for item in labels.validate(self.manifest,self.snapshot,'remaining36'):
            item['row']['topics'] += [{'name':n} for n in item['added']]
            item['row']['profile']['searchText']=item['afterSearchText']
        self.assertEqual([],labels.validate(self.manifest,self.snapshot,'remaining36'))
        self.assertEqual(old,[r for r in self.snapshot['books'] if r['productId'] in labels.SCOPES['pilot20'][2]])
    def test_unknowns_are_not_labels_and_database_writes_stay_bounded(self):
        plan=labels.validate(self.manifest,self.snapshot,'remaining36');sql=labels.transaction(plan,scope='remaining36')
        self.assertNotIn('UPDATE products',sql);self.assertNotIn('SET summary',sql)
        self.assertNotIn('UPDATE users',sql);self.assertNotIn('UPDATE orders',sql)
        for book in self.manifest['books']:
            values=' '.join(l['value'].lower() for l in book['labels'])
            self.assertNotIn('bài tập',values);self.assertNotIn('đáp án',values)
            self.assertNotIn('hiện hành',values);self.assertNotIn('chữa lành',values)
            if book['productId'] in (20,21,23,44):
                for unknown in ('sql','spring boot','chatgpt','5g','java 21'):self.assertNotIn(unknown,values)
    def test_rollback_removes_only_the_new_links_and_checks_current_state(self):
        sql=labels.transaction(labels.validate(self.manifest,self.snapshot,'remaining36'),True,'remaining36')
        self.assertIn('BINARY b.search_text',sql);self.assertIn('DELETE l FROM book_topic_links',sql)
        self.assertNotIn('DELETE FROM book_profiles',sql);self.assertNotIn('DELETE FROM products',sql)
if __name__=='__main__':unittest.main()
