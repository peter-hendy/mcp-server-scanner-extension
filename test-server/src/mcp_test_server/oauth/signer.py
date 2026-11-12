from __future__ import annotations

import base64
import time
import uuid
from typing import Any

import jwt
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

KEY_ID = "test-server-key-1"
ACCESS_TOKEN_SECONDS = 3600


class JwtSigner:
    def __init__(self, issuer: str, audience: str) -> None:
        self.issuer = issuer
        self.audience = audience
        self._private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)

    @property
    def public_key(self) -> rsa.RSAPublicKey:
        return self._private_key.public_key()

    def public_key_pem(self) -> bytes:
        return self.public_key.public_bytes(
            serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo
        )

    def access_token(self, subject: str, scope: str, client_id: str, **overrides: Any) -> str:
        now = int(time.time())
        claims: dict[str, Any] = {
            "iss": self.issuer,
            "aud": self.audience,
            "sub": subject,
            "scope": scope,
            "client_id": client_id,
            "iat": now,
            "exp": now + ACCESS_TOKEN_SECONDS,
            "jti": uuid.uuid4().hex,
        }
        claims.update(overrides)
        return self.sign(claims)

    def sign(self, claims: dict[str, Any]) -> str:
        return jwt.encode(claims, self._private_key, algorithm="RS256", headers={"kid": KEY_ID})

    def jwks(self) -> dict[str, Any]:
        numbers = self.public_key.public_numbers()
        return {
            "keys": [
                {
                    "kty": "RSA",
                    "kid": KEY_ID,
                    "use": "sig",
                    "alg": "RS256",
                    "n": _b64url_uint(numbers.n),
                    "e": _b64url_uint(numbers.e),
                }
            ]
        }


def _b64url_uint(value: int) -> str:
    raw = value.to_bytes((value.bit_length() + 7) // 8, "big")
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()
