"""Boundary tests for submission inventory safety; no untrusted JAR is executed here."""
import tempfile
import unittest
from pathlib import Path
from package_submission import collect_files
from verify_submission import validate_names
from process_tools import run
import sys


class SubmissionToolsTest(unittest.TestCase):
    def test_process_exit_and_output_remain_observable(self):
        with tempfile.TemporaryDirectory() as temporary:
            path=Path(temporary)
            result=run([sys.executable,'-c','import sys;print("observed");sys.exit(3)'],path,path/'result.log',5)
            self.assertEqual(result.returncode,3)
            self.assertIn(b'observed',result.stdout)

    def test_timeout_stops_only_the_owned_process(self):
        with tempfile.TemporaryDirectory() as temporary:
            path=Path(temporary)
            with self.assertRaises(RuntimeError):
                run([sys.executable,'-c','import time;time.sleep(10)'],path,path/'timeout.log',.1)
            self.assertIn(b'TIMEOUT',(path/'timeout.log').read_bytes())

    def test_safe_names_remain_usable(self):
        validate_names(['src/main/java/A.java','README.docx','run.jar'])

    def test_unsafe_and_duplicate_names_are_rejected(self):
        for names in [['../outside'],['/absolute'],['C:/absolute'],['src/../outside'],
                      ['src\\..\\outside'],['same','same'],['file:stream'],
                      ['src/A','src/./A'],['src//A'],['./README.docx'],['src\\A']]:
            with self.subTest(names=names), self.assertRaises(ValueError):
                validate_names(names)

    def test_source_tree_binaries_and_old_archives_are_excluded(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary)
            for name in ['src/main/java/A.java','src/main/java/A.class','src/test/resources/script.tsv',
                         'svfrms-1.0.0.jar.zip','src.zip','.review/private.txt','target/run.jar']:
                path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text('test')
            files=collect_files(root)
            self.assertIn('src/main/java/A.java',files)
            self.assertIn('src/test/resources/script.tsv',files)
            for excluded in ['src/main/java/A.class','svfrms-1.0.0.jar.zip','src.zip','.review/private.txt','target/run.jar']:
                self.assertNotIn(excluded,files)


if __name__=='__main__':
    unittest.main()
