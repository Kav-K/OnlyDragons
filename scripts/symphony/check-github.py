#!/usr/bin/env python3
"""Check repository, published main and issue read access with the configured worker token.

This script runs at import/CLI entry and exits on a missing token, network error or
HTTP failure. It makes GET requests only and prints neither credentials nor response
bodies. Successful reads do not prove write permission; token settings remain the
authority for issue and pull-request mutations.
"""
import json
import os
import sys
import urllib.error
import urllib.request

token = os.environ.get('SYMPHONY_GITHUB_TOKEN', '')
if not token:
    sys.exit('MISSING: GitHub token. Run Symphony.cmd set-token.')
headers = {'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'OnlyDragons-Symphony'}
for endpoint in ('/repos/Kav-K/OnlyDragons', '/repos/Kav-K/OnlyDragons/commits/main', '/repos/Kav-K/OnlyDragons/issues?state=open&per_page=1'):
    try:
        with urllib.request.urlopen(urllib.request.Request('https://api.github.com' + endpoint, headers=headers), timeout=20) as response:
            data = json.load(response)
        if endpoint.endswith('/commits/main'):
            print('OK: main branch has been published.')
        elif isinstance(data, dict):
            print('OK: GitHub repository is accessible; default branch:', data.get('default_branch'))
        else:
            print('OK: GitHub Issues read access. Write permissions are configured in the token settings.')
    except urllib.error.HTTPError as error:
        sys.exit(f'GitHub readiness failed: HTTP {error.code}. Check repository existence and token permissions.')
    except urllib.error.URLError:
        sys.exit('GitHub readiness failed: network connection error.')
