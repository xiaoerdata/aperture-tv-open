"""Synthetic audit fixtures; no real credentials, network, build or publication."""
import importlib.util
import io
import json
import subprocess
import tarfile
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / 'scripts/review-public-source.py'
spec = importlib.util.spec_from_file_location('public_review', SCRIPT)
review = importlib.util.module_from_spec(spec)
spec.loader.exec_module(review)


class PublicationInventoryTest(unittest.TestCase):
    def test_deleted_history_and_secondary_branch_are_still_reported_without_values(self):
        with tempfile.TemporaryDirectory(prefix='aperture-public-review-') as directory:
            root = Path(directory)
            def git(*args):
                return subprocess.check_output(['git', '-C', directory, *args], stderr=subprocess.DEVNULL)
            git('init', '-q')
            git('config', 'user.email', 'fixture@example.invalid')
            git('config', 'user.name', 'Synthetic fixture')
            token = 'ghp_' + 'x' * 36
            phrase = 'recent account payments ' + 'have failed'
            (root / 'README.md').write_text(phrase)
            (root / ('credential-' + token + '.txt')).write_text(token)
            git('add', '.')
            git('commit', '-qm', 'synthetic historical fixture')
            git('branch', 'private-fixture')
            (root / 'README.md').write_text('Current public text')
            (root / ('credential-' + token + '.txt')).unlink()
            git('add', '-u')
            git('commit', '-qm', 'remove fixture from current tree')
            # A private data file in another branch must be included in --history.
            git('checkout', '-q', 'private-fixture')
            (root / '.env').write_text('not-a-real-password')
            git('add', '.env')
            git('commit', '-qm', 'private branch fixture')
            git('checkout', '-q', '-')
            current = review.review_git(root, False)
            self.assertEqual(current['patternFindings'], [])
            history = review.review_git(root, True)
            self.assertEqual(history['commitsVisited'], 3)
            self.assertTrue(any(item['path'] == '.env' for item in history['sensitivePaths']))
            self.assertEqual(len(history['patternFindings']), 2)
            encoded = json.dumps(history)
            self.assertNotIn(token, encoded)
            self.assertNotIn(phrase, encoded)
            self.assertIn('<redacted:github-token-pattern>', encoded)

    def test_cli_redacts_content_and_keeps_binary_review_pending(self):
        with tempfile.TemporaryDirectory(prefix='aperture-public-review-') as directory:
            root = Path(directory)
            token = 'sk-' + 'z' * 45
            archive = root / 'fixture.tar.gz'
            with tarfile.open(archive, 'w:gz') as bundle:
                for name, value in [('README.md', token.encode()), ('picture.png', token.encode())]:
                    item = tarfile.TarInfo('aperture-tv/' + name)
                    item.size = len(value)
                    bundle.addfile(item, io.BytesIO(value))
            output = root / 'report.json'
            run = subprocess.run(['python3', str(SCRIPT), '--archive', str(archive), '--output', str(output)], capture_output=True)
            self.assertEqual(run.returncode, 0)
            self.assertNotIn(token.encode(), run.stdout + run.stderr + output.read_bytes())
            report = json.loads(output.read_text())
            self.assertEqual(len(report['patternFindings']), 1)
            self.assertEqual(report['binaryOrLargeEntriesNotTextScanned'], 1)
            self.assertTrue(report['manualAssetReviewRequired'])
            self.assertFalse(report['publicationApproved'])

    def test_archive_links_and_parent_paths_are_rejected(self):
        with tempfile.TemporaryDirectory(prefix='aperture-public-review-') as directory:
            for number, name in enumerate(['aperture-tv/../outside', 'aperture-tv/link']):
                archive = Path(directory) / (str(number) + '.tar.gz')
                with tarfile.open(archive, 'w:gz') as bundle:
                    item = tarfile.TarInfo(name)
                    if number:
                        item.type = tarfile.SYMTYPE
                        item.linkname = '/private-data'
                    bundle.addfile(item)
                with self.assertRaises(ValueError):
                    review.review_archive(archive)


if __name__ == '__main__':
    unittest.main()
