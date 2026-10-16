import importlib.util
from pathlib import Path
import unittest
from types import SimpleNamespace

spec = importlib.util.spec_from_file_location('publisher', Path(__file__).with_name('play-publish.py'))
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class PlayPublishTest(unittest.TestCase):
    def run_publish(self, uploaded=20, prior=19, fail=False, existing_draft=False):
        calls = []
        def request(method, url, data=None):
            calls.append((method, url, data))
            if url.endswith('/edits'):
                return {'id': 'edit-1'}
            if url.endswith('/tracks'):
                return {'tracks': [{'track': 'internal', 'releases': [
                    {'versionCodes': [str(prior)], 'status': 'draft' if existing_draft else 'completed'}]}]}
            if 'uploadType=media' in url:
                return {'versionCode': uploaded}
            if fail and url.endswith(':validate'):
                raise RuntimeError('rejected')
            return {}
        return publisher.publish, SimpleNamespace(read_bytes=lambda: b'test-bundle'), calls, request

    def exercise(self, **options):
        function, bundle, calls, request = self.run_publish(**options)
        function(bundle, 20, '0.6.2', 'draft', request)
        return calls

    def test_internal_draft_validates_before_commit_without_review(self):
        calls = self.exercise()
        self.assertTrue(calls[-1][1].endswith(':commit?changesNotSentForReview=true'))
        self.assertTrue(calls[-2][1].endswith(':validate'))
        track = next(data for method, url, data in calls if method == 'PUT')
        self.assertEqual(track['track'], 'internal')
        self.assertEqual(track['releases'][-1]['status'], 'draft')
        self.assertEqual(track['releases'][0], {'versionCodes': ['19'], 'status': 'completed'})

    def test_existing_draft_is_not_overwritten_or_uploaded_over(self):
        function, bundle, calls, request = self.run_publish(existing_draft=True)
        with self.assertRaisesRegex(ValueError, 'existing internal draft'):
            function(bundle, 20, '0.6.2', 'draft', request)
        self.assertFalse(any('uploadType' in url or method == 'PUT' or ':commit' in url
                             for method, url, _ in calls))
        self.assertEqual(calls[-1][0], 'DELETE')

    def test_completed_release_replaces_previous_release(self):
        payload = publisher.track_payload(20, '0.6.2', 'completed',
                                          [{'versionCodes': ['19'], 'status': 'completed'}])
        self.assertEqual(payload['releases'], [
            {'name': '0.6.2', 'versionCodes': ['20'], 'status': 'completed'}])

    def test_version_mismatch_or_google_rejection_never_commits(self):
        for options in ({'uploaded': 21}, {'prior': 20}, {'fail': True}):
            function, bundle, calls, request = self.run_publish(**options)
            with self.assertRaises((ValueError, RuntimeError)):
                function(bundle, 20, '0.6.2', 'draft', request)
            self.assertFalse(any(':commit' in url for _, url, _ in calls))
            self.assertEqual(calls[-1][0], 'DELETE')

    def test_invalid_track_status_or_code_rejected(self):
        for code, status in ((0, 'draft'), (20, 'inProgress'), (20, 'production')):
            with self.assertRaises(ValueError):
                publisher.track_payload(code, '0.6.2', status)

    def test_access_probe_never_commits_and_cleans_up_on_failure(self):
        for failed in (False, True):
            calls = []
            def request(method, url, data=None):
                calls.append((method, url))
                if method == 'POST':
                    return {'id': 'probe'}
                if method == 'GET' and failed:
                    raise RuntimeError('permission denied')
                return {}
            if failed:
                with self.assertRaises(RuntimeError):
                    publisher.check_access(request)
            else:
                publisher.check_access(request)
            self.assertEqual([method for method, _ in calls], ['POST', 'GET', 'DELETE'])
            self.assertFalse(any(':commit' in url for _, url in calls))


if __name__ == '__main__':
    unittest.main()
