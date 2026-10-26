import csv
import re
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
class LocalizationTest(unittest.TestCase):
    def test_all_seven_languages_have_matching_resources_and_placeholders(self):
        res=ROOT/'app/src/main/res'
        def read(folder):return {s.attrib['name']:s.text for s in ET.parse(res/folder/'strings.xml').getroot()}
        english=read('values')
        for lang in ('es','pt','fr','de','ja','pl'):
            translated=read('values-'+lang)
            self.assertEqual(english.keys(),translated.keys(),lang)
            for key,value in english.items():
                self.assertTrue(translated[key],(lang,key))
                self.assertEqual(re.findall(r'%\d+\$[ds]',value),re.findall(r'%\d+\$[ds]',translated[key]),(lang,key))
    def test_visible_settings_titles_are_translated(self):
        labels={r[0] for r in csv.reader((ROOT/'tools/localization/strings.tsv').open(),delimiter='\t')}
        settings=(ROOT/'app/src/main/java/org/debiannoroot/ui/SettingsActivity.java').read_text()
        for title in re.findall(r'(?:heading|button|flag|choice|number|indexed)\("([^"\\]+)"',settings):
            self.assertIn(title,labels)
