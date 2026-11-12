import copy
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("checker", Path(__file__).parents[1] / "check_requests.py")
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class WireContractTest(unittest.TestCase):
    def setUp(self):
        def event(kind, device="before", user=None, props=None):
            return {"event_type": kind, "device_id": device, "session_id": 42, "user_id": user,
                    "library": "amplitude-android-unified/1-amplitude-analytics-android/2",
                    "event_properties": {checker.REPLAY_ID: f"{device}/42", **(props or {})}}

        self.requests = [
            {"path": "/2/httpapi", "body": {"api_key": checker.API_KEY, "events": [
                event("session_start"), event("smoke_event"), event("smoke_event", "after"),
                event("$exposure", user="smoke-user", props={"flag_key": "checkout", "variant": "treatment"}),
            ]}},
            {"path": "/sdk/v2/vardata", "headers": {"authorization": f"Api-Key {checker.DEPLOYMENT_KEY}"},
             "experiment_user": {"user_id": "smoke-user", "device_id": "before"}},
            {"path": "/sdk/v2/vardata", "headers": {"authorization": f"Api-Key {checker.DEPLOYMENT_KEY}"},
             "experiment_user": {"device_id": "after"}},
            {"path": "/sessions/v2/track", "headers": {"authorization": f"Bearer {checker.API_KEY}"},
             "query": {"device_id": "before", "session_id": "42"}},
        ]

    def test_accepts_complete_customer_flow(self):
        self.assertEqual([], checker.validate(self.requests, "1", "2"))

    def test_rejects_missing_traffic_for_each_product(self):
        for path in {r["path"] for r in self.requests}:
            with self.subTest(path=path):
                self.assertTrue(checker.validate([r for r in self.requests if r["path"] != path], "1", "2"))
        self.assertTrue(checker.validate([], "1", "2"))

    def test_rejects_identity_attribution_privacy_and_enrichment_regressions(self):
        mutations = [
            lambda r: r[0]["body"]["events"][1].update(library="old-library"),
            lambda r: r[0]["body"]["events"][2].update(user_id="stale-user"),
            lambda r: r[0]["body"]["events"][2].update(device_id="before"),
            lambda r: r[0]["body"]["events"][3]["event_properties"].pop(checker.REPLAY_ID),
            lambda r: r[0]["body"]["events"].append(dict(r[0]["body"]["events"][1], event_type="smoke_event_opted_out")),
            lambda r: r[1]["headers"].update(authorization="wrong-key"),
            lambda r: r[2]["experiment_user"].update(user_id="stale-user"),
            lambda r: r[3]["query"].update(device_id="unrelated-device"),
        ]
        for index, mutate in enumerate(mutations):
            with self.subTest(mutation=index):
                requests = copy.deepcopy(self.requests)
                mutate(requests)
                self.assertTrue(checker.validate(requests, "1", "2"))
