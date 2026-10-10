"""Synthetic Teams-like workspace. IDs and content are intentionally fictional."""
import base64

ALICE = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1'
BOB = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2'
PREFIX = 'sim-stitch-'


def grants(group):
    return [{'principalKind': 'GROUP', 'principalId': group, 'permission': permission, 'effect': 'ALLOW'}
            for permission in ('READ', 'SEARCH')]


def resources(version):
    def item(name, parent, kind, title, content='', group=None, ai='ALLOW', binary=False):
        return {'resourceId': PREFIX + name, 'parentId': PREFIX + parent if parent else None,
                'kind': kind, 'title': title, 'content': content, 'ownerSubject': 'synthetic-workspace-admin',
                'aiAccess': ai, 'sourceVersion': version, 'purgeAfter': None,
                'acl': grants(group) if group else [],
                'chunks': [{'content': content}] if content else [],
                'fileBase64': base64.b64encode(content.encode()).decode() if binary else None}
    return [
        item('team', None, 'FOLDER', 'Stitch Demo Team'),
        item('projects', 'team', 'FOLDER', 'Projects', group='employees'),
        item('handbook', 'projects', 'FILE', 'Project handbook',
             'Project Aurora launches on October 20. Contact the platform team for onboarding. Synthetic demo data.', binary=True),
        item('payroll', 'team', 'FOLDER', 'Payroll', group='finance'),
        item('payroll-file', 'payroll', 'FILE', 'October payroll',
             'Payroll: Alice Demo salary KRW 5,000,000; Bob Demo salary KRW 4,000,000. Entirely fictional.', group='finance', binary=True),
        item('payroll-attachment', 'payroll-file', 'ATTACHMENT', 'Payroll supporting document',
             'Payroll attachment: fictional bank account DEMO-0000. Do not use as real payroll data.', binary=True),
        item('general', 'team', 'CHANNEL', '#general', group='employees'),
        item('message', 'general', 'MESSAGE', 'Aurora announcement',
             'Project Aurora kickoff is Monday at 10:00. Please read the project handbook.'),
        item('message-attachment', 'message', 'ATTACHMENT', 'Kickoff notes',
             'Project Aurora kickoff agenda: onboarding, access review, release plan.', binary=True),
        item('finance', 'team', 'CHANNEL', '#finance', group='finance'),
        item('finance-message', 'finance', 'MESSAGE', 'Payroll discussion',
             'Payroll approval is due Friday. This finance channel is excluded from Stitchy.'),
    ]


def subjects(version):
    return [{'subject': ALICE, 'groups': ['employees', 'finance'], 'active': True, 'sourceVersion': version},
            {'subject': BOB, 'groups': ['employees'], 'active': True, 'sourceVersion': version}]
