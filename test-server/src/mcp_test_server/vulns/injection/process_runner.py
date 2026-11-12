from __future__ import annotations

import asyncio
from collections.abc import Sequence


class ProcessRunner:
    def __init__(self, timeout_seconds: float) -> None:
        self._timeout = timeout_seconds

    async def shell(self, command: str) -> str:
        process = await asyncio.create_subprocess_shell(
            command, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.STDOUT
        )
        return await self._collect(process)

    async def exec(self, argv: Sequence[str]) -> str:
        try:
            process = await asyncio.create_subprocess_exec(
                *argv, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.STDOUT
            )
        except FileNotFoundError:
            return f"{argv[0]}: command not found"
        return await self._collect(process)

    async def _collect(self, process: asyncio.subprocess.Process) -> str:
        try:
            output, _ = await asyncio.wait_for(process.communicate(), timeout=self._timeout)
        except TimeoutError:
            process.kill()
            await process.wait()
            return "command timed out"
        return output.decode("utf-8", errors="replace")
