#!/usr/bin/env python3
"""Smoke tests for the local Matchday fault-injection server."""
from __future__ import annotations

import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer

from server import Handler


class FaultServerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.httpd = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        cls.port = cls.httpd.server_port
        cls.thread = threading.Thread(target=cls.httpd.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls) -> None:
        cls.httpd.shutdown()
        cls.httpd.server_close()

    def get(self, path: str) -> tuple[int, bytes]:
        with urllib.request.urlopen(f"http://127.0.0.1:{self.port}{path}", timeout=4) as response:
            return response.status, response.read()

    def test_health(self) -> None:
        status, body = self.get("/health")
        self.assertEqual(200, status)
        self.assertIn(b"matchday fault server ok", body)

    def test_playlist_has_media_endpoints(self) -> None:
        status, body = self.get("/playlist.m3u")
        self.assertEqual(200, status)
        self.assertIn(b"/live/ok.m3u8", body)
        self.assertIn(b"/live/ok.ts", body)

    def test_fault_playlist_points_at_broken_first_variant(self) -> None:
        status, body = self.get("/fault-playlist.m3u")
        self.assertEqual(200, status)
        self.assertIn(b"/fault/404", body)
        self.assertIn(b"/live/ok.ts", body)

    def test_injected_404(self) -> None:
        with self.assertRaises(urllib.error.HTTPError) as raised:
            urllib.request.urlopen(f"http://127.0.0.1:{self.port}/fault/404", timeout=4)
        self.assertEqual(404, raised.exception.code)

    def test_guide_contains_programmes(self) -> None:
        status, body = self.get("/guide.xml")
        self.assertEqual(200, status)
        self.assertIn(b"<programme", body)
        self.assertIn(b"matchday.test.hls", body)


if __name__ == "__main__":
    unittest.main()
