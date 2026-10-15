import copy, importlib.util, json, pathlib, unittest
BASE=pathlib.Path(__file__).parent
spec=importlib.util.spec_from_file_location('pilot',BASE/'book-label-pilot.py');pilot=importlib.util.module_from_spec(spec);spec.loader.exec_module(pilot)
DATA=BASE.parent/'VinaBookStore/src/test/resources/book-label-pilot'
class LabelPilotTest(unittest.TestCase):
    def setUp(self):
        manifest=BASE/'labels-20-2026-10-10.json' if (BASE/'labels-20-2026-10-10.json').exists() else DATA/'labels.json'
        snapshot=BASE/'label-pilot-before.json' if (BASE/'label-pilot-before.json').exists() else DATA/'catalog-before.json'
        self.manifest=json.loads(manifest.read_text(encoding='utf-8'))
        self.snapshot=json.loads(snapshot.read_text(encoding='utf-8'))
    def test_twenty_unique_books_and_154_evidenced_labels(self):
        plan=pilot.validate(self.manifest,self.snapshot)
        self.assertEqual(20,len(plan));self.assertEqual(154,sum(len(p['added']) for p in plan))
    def test_refuses_a_twenty_first_book(self):
        self.manifest['books'].append(copy.deepcopy(self.manifest['books'][0]))
        with self.assertRaises(ValueError):pilot.validate(self.manifest,self.snapshot)
    def test_refuses_wrong_identity(self):
        self.manifest['books'][0]['author']='Another author'
        with self.assertRaises(ValueError):pilot.validate(self.manifest,self.snapshot)
    def test_refuses_unapproved_profile(self):
        self.snapshot['books'][4]['profile']['approved']=0
        with self.assertRaises(ValueError):pilot.validate(self.manifest,self.snapshot)
    def test_refuses_fabricated_quote(self):
        self.manifest['books'][0]['labels'][0]['evidence']['quote']='Has exercises with answer key'
        with self.assertRaises(ValueError):pilot.validate(self.manifest,self.snapshot)
    def test_refuses_unknown_dimension(self):
        self.manifest['books'][0]['labels'][0]['dimension']='price'
        with self.assertRaises(ValueError):pilot.validate(self.manifest,self.snapshot)
    def test_refuses_unreviewed_source(self):
        self.manifest['books'][0]['labels'][0]['sourceUrls']=['https://example.com/unverified']
        with self.assertRaises(ValueError):pilot.validate(self.manifest,self.snapshot)
    def test_refuses_duplicate_label(self):
        book=self.manifest['books'][0];book['labels'].append(copy.deepcopy(book['labels'][0]))
        with self.assertRaises(ValueError):pilot.validate(self.manifest,self.snapshot)
    def test_idempotent_when_labels_already_applied(self):
        plan=pilot.validate(self.manifest,self.snapshot)
        for item in plan:
            item['row']['topics']+= [{'name':n} for n in item['added']]
            item['row']['profile']['searchText']=item['afterSearchText']
        self.assertEqual([],pilot.validate(self.manifest,self.snapshot))
    def test_only_label_links_search_text_and_embedding_are_written(self):
        sql=pilot.transaction(pilot.validate(self.manifest,self.snapshot))
        self.assertIn('START TRANSACTION;',sql);self.assertIn('CHECK(ok=1)',sql)
        self.assertNotIn('UPDATE products',sql);self.assertNotIn('SET summary',sql)
        self.assertNotIn('UPDATE users',sql);self.assertNotIn('UPDATE orders',sql)
    def test_rollback_checks_current_state_and_restores_only_pilot_links(self):
        plan=pilot.validate(self.manifest,self.snapshot)
        sql=pilot.transaction(plan,True)
        self.assertIn('DELETE l FROM book_topic_links',sql)
        self.assertNotIn('DELETE FROM book_profiles',sql)
        self.assertIn('BINARY b.search_text',sql)
if __name__=='__main__':unittest.main()
