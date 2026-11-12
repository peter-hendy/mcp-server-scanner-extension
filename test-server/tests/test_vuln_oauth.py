from __future__ import annotations

import asyncio
import base64
import re
from typing import Any
from urllib.parse import parse_qs, urlsplit

import httpx
import pytest

from tests.conftest import ServerFactory
from tests.support import TestServer, pkce_pair, raw_get

REGISTERED = "http://127.0.0.1:49151/scancheck-registered-1"
HTTPS_REGISTERED = "https://client.example/callback"


def _register(server: TestServer, redirect_uris: list[str], **metadata: Any) -> httpx.Response:
    return server.client.post("/register", json={"client_name": "ScanCheck", "redirect_uris": redirect_uris,
                                                 **metadata})


def _authorize(server: TestServer, client_id: str, redirect_uri: str, **extra: str) -> httpx.Response:
    _, challenge = pkce_pair()
    params = {"response_type": "code", "client_id": client_id, "redirect_uri": redirect_uri, "state": "st-1",
              "code_challenge": challenge, "code_challenge_method": "S256", **extra}
    return server.client.get("/authorize", params=params)


def _redirected_with_code(response: httpx.Response, target: str) -> bool:
    location = response.headers.get("location", "")
    return response.status_code == 302 and location.startswith(target) and "code=" in location


def _client(server: TestServer, redirect_uris: list[str]) -> str:
    response = _register(server, redirect_uris)
    assert response.status_code == 201, response.text
    return str(response.json()["client_id"])


REDIRECT_CASES = {
    "redirect_unregistered": ([REGISTERED], "http://127.0.0.1:49151/scancheck-unregistered-1"),
    "redirect_http_downgrade": ([HTTPS_REGISTERED], "http://client.example/callback"),
    "redirect_path_normalisation": ([REGISTERED], REGISTERED + "/../scancheck-bypass-1"),
}


@pytest.mark.parametrize("vuln", REDIRECT_CASES)
def test_authorize_redirect_policy(make_server: ServerFactory, vuln: str) -> None:
    registered, candidate = REDIRECT_CASES[vuln]
    vulnerable = make_server(consent="auto", default_mode="safe", vulns={vuln: "vulnerable"})
    assert _redirected_with_code(_authorize(vulnerable, _client(vulnerable, registered), candidate), candidate)
    safe = make_server(consent="auto", default_mode="safe")
    response = _authorize(safe, _client(safe, registered), candidate)
    assert response.status_code == 400
    assert "location" not in response.headers


@pytest.mark.parametrize("mode", ["vulnerable", "safe"])
def test_registered_callback_control_always_works(make_server: ServerFactory, mode: str) -> None:
    server = make_server(consent="auto", default_mode=mode)
    client_id = _client(server, [REGISTERED])
    response = _authorize(server, client_id, "http://127.0.0.1:60000/scancheck-registered-1")
    assert _redirected_with_code(response, "http://127.0.0.1:60000/scancheck-registered-1")
    assert parse_qs(urlsplit(response.headers["location"]).query)["state"] == ["st-1"]


@pytest.mark.parametrize("uri", ["javascript:void(0)", "data:text/html,hi"])
def test_script_scheme_registration(make_server: ServerFactory, uri: str) -> None:
    vulnerable = make_server(consent="auto", vulns={"redirect_script_scheme_registration": "vulnerable"})
    client_id = _client(vulnerable, [uri])
    _, challenge = pkce_pair()
    status, headers = raw_get(vulnerable, "/authorize", {
        "response_type": "code", "client_id": client_id, "redirect_uri": uri, "state": "s",
        "code_challenge": challenge, "code_challenge_method": "S256"})
    assert status == 302
    assert headers["location"].startswith(uri)
    safe = make_server(vulns={"redirect_script_scheme_registration": "safe"})
    assert _register(safe, [uri]).json()["error"] == "invalid_redirect_uri"


def test_wildcard_registration(make_server: ServerFactory) -> None:
    vulnerable = make_server(consent="auto", vulns={"redirect_wildcard_registration": "vulnerable"})
    client_id = _client(vulnerable, ["https://*.client.example/cb"])
    assert _redirected_with_code(_authorize(vulnerable, client_id, "https://evil.client.example/cb"),
                                 "https://evil.client.example/cb")
    safe = make_server(vulns={"redirect_wildcard_registration": "safe"})
    assert _register(safe, ["https://*.client.example/cb"]).status_code == 400


def test_prompt_none_and_fragment_response_mode(make_server: ServerFactory) -> None:
    server = make_server(vulns={"redirect_unregistered": "vulnerable"})
    client_id = _client(server, [REGISTERED])
    target = "http://127.0.0.1:49151/scancheck-unregistered-2"
    error = _authorize(server, client_id, target, prompt="none")
    assert error.headers["location"].startswith(target + "?") and "error=consent_required" in error.headers["location"]
    fragment = make_server(consent="auto", vulns={"redirect_unregistered": "vulnerable"})
    response = _authorize(fragment, _client(fragment, [REGISTERED]), target, response_mode="fragment")
    assert response.headers["location"].startswith(target + "#code=")


def test_unknown_client_is_not_reflected(server: TestServer) -> None:
    response = _authorize(server, "unknown<scancheck-x>", REGISTERED)
    assert response.status_code == 400
    assert "<scancheck-x>" not in response.text


CLIENT_NAME = "ScanCheck NAME</script><scancheck-html-m1></scancheck-html-m1>"
STATE = 'token"><scancheck-state-m1></scancheck-state-m1>'


def _consent_page(server: TestServer) -> str:
    client_id = str(_register(server, [REGISTERED], client_name=CLIENT_NAME).json()["client_id"])
    response = _authorize(server, client_id, REGISTERED, state=STATE)
    assert response.status_code == 200
    return response.text


def test_client_name_reflection(make_server: ServerFactory) -> None:
    vulnerable = _consent_page(make_server(vulns={"consent_client_name_reflection": "vulnerable",
                                                  "consent_state_reflection": "safe"}))
    assert "<scancheck-html-m1>" in vulnerable
    assert '"client_name": "ScanCheck NAME</script>' in vulnerable
    assert '<scancheck-state-m1>' not in vulnerable
    safe = _consent_page(make_server(vulns={"consent_client_name_reflection": "safe"}))
    assert "<scancheck-html-m1>" not in safe
    assert '"client_name": "ScanCheck NAME\\u003c/script\\u003e' in safe


def test_state_reflection_in_quoted_attribute(make_server: ServerFactory) -> None:
    vulnerable = _consent_page(make_server(vulns={"consent_state_reflection": "vulnerable"}))
    assert f'name="state" value="{STATE}"' in vulnerable
    safe = _consent_page(make_server(vulns={"consent_state_reflection": "safe",
                                            "consent_client_name_reflection": "safe"}))
    assert "<scancheck-state-m1>" not in safe
    assert 'value="token&quot;&gt;&lt;scancheck-state-m1&gt;' in safe


def test_consent_page_has_form_and_approval_issues_code(make_server: ServerFactory) -> None:
    server = make_server()
    page = _consent_page(server)
    assert re.search(r'<form method="post" action="/authorize/consent">', page)
    fields = dict(re.findall(r'<input type="hidden" name="(\w+)" value="([^"]*)"', page))
    approved = server.client.post("/authorize/consent", data={**fields, "state": "st", "decision": "approve"})
    assert _redirected_with_code(approved, REGISTERED)
    denied = server.client.post("/authorize/consent", data={**fields, "state": "st", "decision": "deny"})
    assert "error=access_denied" in denied.headers["location"]


def _tokens(server: TestServer, client_id: str, secret: str | None, redirect_uri: str, scope: str) -> dict[str, Any]:
    verifier, challenge = pkce_pair()
    response = server.client.get("/authorize", params={
        "response_type": "code", "client_id": client_id, "redirect_uri": redirect_uri, "state": "s",
        "code_challenge": challenge, "code_challenge_method": "S256", "scope": scope})
    code = parse_qs(urlsplit(response.headers["location"]).query)["code"][0]
    form = {"grant_type": "authorization_code", "code": code, "redirect_uri": redirect_uri,
            "code_verifier": verifier, "client_id": client_id}
    headers = _basic(client_id, secret) if secret else {}
    issued = server.client.post("/token", data=form, headers=headers)
    assert issued.status_code == 200, issued.text
    result: dict[str, Any] = issued.json()
    return result


def _basic(client_id: str, secret: str) -> dict[str, str]:
    return {"authorization": "Basic " + base64.b64encode(f"{client_id}:{secret}".encode()).decode()}


def _confidential(server: TestServer) -> tuple[str, str]:
    registered = _register(server, [REGISTERED], token_endpoint_auth_method="client_secret_basic").json()
    return registered["client_id"], registered["client_secret"]


def _refresh(server: TestServer, client_id: str, refresh_token: str, headers: dict[str, str],
             **extra: str) -> httpx.Response:
    return server.client.post("/token", data={"grant_type": "refresh_token", "refresh_token": refresh_token,
                                              "client_id": client_id, **extra}, headers=headers)


@pytest.mark.parametrize(
    ("vuln", "secret_for_refresh"),
    [("refresh_missing_client_secret", None), ("refresh_wrong_client_secret", "wrong-secret")],
)
def test_refresh_client_authentication(make_server: ServerFactory, vuln: str, secret_for_refresh: str | None) -> None:
    for mode, expected in (("vulnerable", 200), ("safe", 401)):
        server = make_server(consent="auto", default_mode="safe", vulns={vuln: mode})
        client_id, secret = _confidential(server)
        tokens = _tokens(server, client_id, secret, REGISTERED, "mcp:tools")
        headers = _basic(client_id, secret_for_refresh) if secret_for_refresh else {}
        assert _refresh(server, client_id, tokens["refresh_token"], headers).status_code == expected
        assert _refresh(server, client_id, tokens["refresh_token"], _basic(client_id, secret)).status_code == 200


def test_refresh_scope_expansion(make_server: ServerFactory) -> None:
    for mode, expected in (("vulnerable", 200), ("safe", 400)):
        server = make_server(consent="auto", vulns={"refresh_scope_expansion": mode})
        tokens = _tokens(server, "public-client", None, HTTPS_REGISTERED, "mcp:tools")
        response = _refresh(server, "public-client", tokens["refresh_token"], {}, scope="mcp:tools mcp:admin")
        assert response.status_code == expected
        if expected == 200:
            assert response.json()["scope"] == "mcp:tools mcp:admin"


def test_issued_access_token_is_accepted_by_mcp_endpoint(make_server: ServerFactory) -> None:
    server = make_server(consent="auto", default_mode="safe")
    tokens = _tokens(server, "public-client", None, HTTPS_REGISTERED, "mcp:tools")
    session = server.session({"authorization": f"Bearer {tokens['access_token']}"})
    assert session.result("ping") == {}


def test_pkce_and_code_reuse_rejected(make_server: ServerFactory) -> None:
    server = make_server(consent="auto")
    _, challenge = pkce_pair()
    response = server.client.get("/authorize", params={
        "response_type": "code", "client_id": "public-client", "redirect_uri": HTTPS_REGISTERED,
        "code_challenge": challenge, "code_challenge_method": "S256"})
    code = parse_qs(urlsplit(response.headers["location"]).query)["code"][0]
    form = {"grant_type": "authorization_code", "code": code, "client_id": "public-client",
            "code_verifier": "wrong-verifier"}
    assert server.client.post("/token", data=form).status_code == 400


def test_metadata_documents(server: TestServer) -> None:
    metadata = server.client.get("/.well-known/oauth-authorization-server").json()
    assert metadata["registration_endpoint"] == "http://testserver/register"
    resource = server.client.get("/.well-known/oauth-protected-resource/mcp").json()
    assert resource == {"resource": "http://testserver/mcp", "authorization_servers": ["http://testserver"],
                        "scopes_supported": metadata["scopes_supported"], "bearer_methods_supported": ["header"]}


@pytest.mark.parametrize(
    ("safe_vuln", "registered", "candidate"),
    [
        ("redirect_http_downgrade", [HTTPS_REGISTERED], "http://client.example/callback"),
        ("redirect_path_normalisation", [REGISTERED], REGISTERED + "/../scancheck-bypass-2"),
        ("redirect_wildcard_registration", [REGISTERED], "https://evil.client.example/cb"),
    ],
)
def test_each_redirect_shape_is_decided_only_by_its_own_module(
    make_server: ServerFactory, safe_vuln: str, registered: list[str], candidate: str
) -> None:
    server = make_server(consent="auto", vulns={safe_vuln: "safe"})
    response = _authorize(server, _client(server, registered), candidate)
    if safe_vuln == "redirect_wildcard_registration":
        assert _redirected_with_code(response, candidate)
    else:
        assert response.status_code == 400


def test_script_scheme_authorize_blocked_when_its_module_is_safe(make_server: ServerFactory) -> None:
    server = make_server(consent="auto", vulns={"redirect_script_scheme_registration": "safe"})
    status, _ = raw_get(server, "/authorize", {
        "response_type": "code", "client_id": "public-client", "redirect_uri": "javascript:alert(1)//",
        "code_challenge": pkce_pair()[1], "code_challenge_method": "S256"})
    assert status == 400


def test_empty_secret_is_treated_as_missing(make_server: ServerFactory) -> None:
    server = make_server(consent="auto", vulns={"refresh_missing_client_secret": "safe",
                                                "refresh_wrong_client_secret": "vulnerable"})
    client_id, secret = _confidential(server)
    tokens = _tokens(server, client_id, secret, REGISTERED, "mcp:tools")
    response = _refresh(server, client_id, tokens["refresh_token"], {}, client_secret="")
    assert response.status_code == 401


def _issued_code(server: TestServer) -> tuple[str, str]:
    verifier, challenge = pkce_pair()
    response = server.client.get("/authorize", params={
        "response_type": "code", "client_id": "public-client", "redirect_uri": HTTPS_REGISTERED, "state": "s",
        "code_challenge": challenge, "code_challenge_method": "S256"})
    return parse_qs(urlsplit(response.headers["location"]).query)["code"][0], verifier


def _redemption_form(code: str, verifier: str) -> dict[str, str]:
    return {"grant_type": "authorization_code", "code": code, "client_id": "public-client",
            "redirect_uri": HTTPS_REGISTERED, "code_verifier": verifier}


async def _concurrent_redemption_statuses(server: TestServer, code: str, verifier: str, attempts: int) -> list[int]:
    transport = httpx.ASGITransport(app=server.application)
    async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as client:
        responses = await asyncio.gather(
            *(client.post("/token", data=_redemption_form(code, verifier)) for _ in range(attempts)))
    return [response.status_code for response in responses]


@pytest.mark.parametrize(("mode", "second_status"), [("vulnerable", 200), ("safe", 400)])
def test_code_reuse_redeems_a_code_again_in_sequence(make_server: ServerFactory, mode: str,
                                                     second_status: int) -> None:
    server = make_server(consent="auto", default_mode="safe", vulns={"code_reuse": mode})
    code, verifier = _issued_code(server)
    assert server.client.post("/token", data=_redemption_form(code, verifier)).status_code == 200
    assert server.client.post("/token", data=_redemption_form(code, verifier)).status_code == second_status


@pytest.mark.parametrize(("mode", "issued"), [("vulnerable", 3), ("safe", 1)])
def test_code_redemption_race_admits_only_overlapping_redemptions(make_server: ServerFactory, mode: str,
                                                                  issued: int) -> None:
    server = make_server(consent="auto", default_mode="safe", vulns={"code_redemption_race": mode})
    code, verifier = _issued_code(server)
    statuses = asyncio.run(_concurrent_redemption_statuses(server, code, verifier, 3))
    assert statuses.count(200) == issued
    assert server.client.post("/token", data=_redemption_form(code, verifier)).status_code == 400
