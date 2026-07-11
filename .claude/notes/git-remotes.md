# Git remote setup

- `origin` → https://github.com/alksandr/RSPSi.git (your fork — push work here)
- `upstream` → https://github.com/blurite/RSPSi.git (original repo — pull updates only)

Workflow:
- Push your changes: `git push origin <branch>`
- Pull upstream updates: `git fetch upstream` then `git merge upstream/master` (or rebase) onto your local master
