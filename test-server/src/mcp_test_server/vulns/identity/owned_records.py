from __future__ import annotations

from dataclasses import dataclass

from mcp_test_server.identity.users import ALICE, BOB, User

ALICE_MARKER = "ALICE-PRIVATE-7f3c"
BOB_MARKER = "BOB-PRIVATE-91ad"


@dataclass(frozen=True)
class OwnedRecord:
    owner: str
    text: str

    def owned_by(self, user: User) -> bool:
        return user.name == self.owner


DOCUMENTS = {
    "d-alice-0001": OwnedRecord(ALICE.name, f"Acquisition shortlist, board eyes only: {ALICE_MARKER}"),
    "d-bob-0042": OwnedRecord(BOB.name, f"Salary review notes for the platform team: {BOB_MARKER}"),
}
PRIVATE_NOTES = {
    "notes://alice/private.md": OwnedRecord(ALICE.name, f"# Alice's private notes\n\nRecovery code {ALICE_MARKER}\n"),
    "notes://bob/private.md": OwnedRecord(BOB.name, f"# Bob's private notes\n\nRecovery code {BOB_MARKER}\n"),
}
