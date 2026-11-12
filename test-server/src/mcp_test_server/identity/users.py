from __future__ import annotations

from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from enum import StrEnum


class Role(StrEnum):
    ADMIN = "admin"
    MEMBER = "member"
    READER = "reader"


@dataclass(frozen=True)
class User:
    name: str
    role: Role
    token: str


ALICE = User("alice", Role.ADMIN, "alice-token")
BOB = User("bob", Role.MEMBER, "bob-token")
CAROL = User("carol", Role.READER, "carol-token")


class UserDirectory:
    def __init__(self, users: Sequence[User] = (ALICE, BOB, CAROL)) -> None:
        self._users = tuple(users)
        self._by_token = {user.token: user for user in users}

    def users(self) -> tuple[User, ...]:
        return self._users

    def user_for_token(self, token: str) -> User | None:
        return self._by_token.get(token.strip())

    def user_for(self, headers: Mapping[str, str]) -> User | None:
        scheme, _, token = (headers.get("authorization") or "").partition(" ")
        return self.user_for_token(token) if scheme.lower() == "bearer" else None

    def name_for(self, headers: Mapping[str, str]) -> str | None:
        user = self.user_for(headers)
        return user.name if user else None
