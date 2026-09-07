"""Two-boot failure boundaries; synthetic records here are never runtime acceptance."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import paper_restart as restart
import paper_test as runner
import player_actions
import ci_paper

ROOT = Path(__file__).resolve().parents[2]


class RestartContracts(unittest.TestCase):
    """Mutate synthetic two-boot envelopes to reject nonce, world/config and process-lineage drift.

    The fixture uses real temporary files but invented process identities and timing.
    These tests certify continuity validation behavior, not a completed Paper restart.
    """
    def setUp(self):
        """Construct two nonoverlapping synthetic phases with linked config snapshots and distinct nonces."""
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.descriptor = runner.strict_json(ROOT / 'dev/game-tests/scenarios.json')['same-profile-restart']
        self.parent_id = 'a' * 32
        self.parent = {'catalogMode': restart.MODE, 'runId': self.parent_id, 'passed': True, 'error': None,
                       'startedAtEpochMs': 100, 'completedAtEpochMs': 1000,
                       'pins': {'minecraftVersion': '26.2'}, 'profile': {'world': 'agent-world-' + self.parent_id},
                       'artifacts': {'productionSha256': 'c' * 64}, 'playerBuild': {'jars': {'client.jar': 'd' * 64}},
                       'build': {'wrapperInvoked': True, 'unitTests': {'tests': 1, 'failures': 0, 'errors': 0, 'skipped': 0},
                                 'companionUnitTests': {'tests': 2, 'failures': 0, 'errors': 0, 'skipped': 0}},
                       'stagedArtifacts': {'server.jar': 'e' * 64}, 'phases': []}
        (self.root / 'config-initial.yml').write_text('initial')
        self.parent['initialConfigSha256'] = runner.sha256(self.root / 'config-initial.yml')
        for index in (1, 2):
            root = self.root / f'phase-{index}'; root.mkdir()
            nonce = self.parent_id[:10] + str(index) * 22
            context = {'schemaVersion': 1, 'mode': restart.MODE, 'parentRunId': self.parent_id, 'index': index,
                       'nonce': nonce, 'previousNonce': self.parent_id[:10] + '1' * 22 if index == 2 else None,
                       'initialConfigPath': str(self.root / 'config-initial.yml'),
                       'previousReportPath': str(self.root / 'phase-1/scenario.json') if index == 2 else None}
            runner.atomic_json(root / 'context.json', context)
            runner.atomic_json(root / 'scenario.json', {'observations': {'restartWorld':
                {'uuid': '11111111-1111-1111-1111-111111111111', 'name': self.parent['profile']['world']}}})
            first = index * 200
            phase = {'index': index, 'parentRunId': self.parent_id, 'runId': nonce, 'context': context,
                     'startedAtEpochMs': first, 'completedAtEpochMs': first + 100,
                     'serverProcess': {'pid': index, 'startTicks': first, 'startedAtEpochMs': first + 1},
                     'clientProcess': {'pid': index + 10, 'startTicks': first + 1, 'startedAtEpochMs': first + 2},
                     'stopStartedAtEpochMs': first + 80, 'stopCompletedAtEpochMs': first + 90,
                     'playerStopStartedAtEpochMs': first + 60, 'playerStopCompletedAtEpochMs': first + 70,
                     'cleanup': {'exitCode': 0, 'forced': False, 'clean': True},
                     'passed': True, 'error': None, 'status': {'version': {'name': '26.2'}}}
            for key in ('profile', 'artifacts', 'playerBuild', 'build'):
                phase[key] = copy.deepcopy(self.parent[key])
            phase['artifactsBefore'] = phase['artifactsAfter'] = copy.deepcopy(self.parent['stagedArtifacts'])
            for side, text in [('before', 'initial' if index == 1 else 'saved'), ('after', 'saved' if index == 1 else 'initial')]:
                path = root / f'config-{side}.yml'; path.write_text(text)
                phase['config' + side.title() + 'Sha256'] = runner.sha256(path)
            self.parent['phases'].append(phase)

    def verify(self):
        """Invoke the real continuity validator on the current test-owned envelope and snapshots."""
        restart.verify_continuity(runner, self.parent, self.descriptor, self.root)

    def test_initial_seed_default_valid_and_rejections(self):
        import subprocess
        self.assertIsNone(restart.initial_seed(self.root, {}))
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        relative = 'dev/game-tests/config-seeds/legacy.yml'
        path = self.root / relative
        path.parent.mkdir(parents=True)
        path.write_text('legacy: retained\n', encoding='utf-8')
        subprocess.run(['git', 'add', relative], cwd=self.root, check=True)
        seed = {'path': relative, 'sha256': runner.sha256(path)}
        self.assertEqual(path.read_bytes(), restart.initial_seed(self.root, {'initialConfig': seed}))
        for bad in [dict(seed, path='../legacy.yml'), dict(seed, sha256='0' * 64), dict(seed, extra=True)]:
            with self.assertRaises(runner.ValidationError): restart.initial_seed(self.root, {'initialConfig': bad})
        for data in [b'changed', b'\xff', b'a' * 65537]:
            path.write_bytes(data)
            candidate = dict(seed, sha256=runner.sha256(path)) if data != b'changed' else seed
            with self.assertRaises(runner.ValidationError): restart.initial_seed(self.root, {'initialConfig': candidate})

    def test_two_sequential_boots_preserve_config_and_world(self):
        self.verify()
        restart.validate_descriptor(ROOT, self.descriptor)
        plans = [player_actions.load_plan(ROOT, d)[2] for d in self.descriptor['phases']]
        self.assertNotEqual(plans[0]['planId'], plans[1]['planId'])
        self.assertEqual(player_actions.identities(self.parent['phases'][0]['runId'], plans[0]),
                         player_actions.identities(self.parent['phases'][1]['runId'], plans[1]))

    def test_missing_duplicate_reordered_phases_reject(self):
        original = copy.deepcopy(self.parent['phases'])
        for phases in [[], original[:1], original + original[:1], original[::-1], [original[0], original[0]]]:
            with self.subTest(phases=len(phases)), self.assertRaises(runner.ValidationError):
                self.parent['phases'] = phases; self.verify()

    def test_stale_wrong_nonce_reject(self):
        for nonce in [self.parent_id, self.parent['phases'][0]['runId'], 'f' * 32, 'invalid']:
            with self.subTest(nonce=nonce), self.assertRaises(runner.ValidationError):
                self.parent['phases'][1]['runId'] = nonce; self.verify()

    def test_failed_shutdown_startup_process_reuse_and_arbitrary_errors_reject(self):
        original = copy.deepcopy(self.parent)
        mutations = [lambda p: p['phases'][0]['cleanup'].update(clean=False),
                     lambda p: p['phases'][0]['cleanup'].update(forced=True),
                     lambda p: p['phases'][0]['cleanup'].update(exitCode=1),
                     lambda p: p['phases'][1].pop('status'),
                     lambda p: p['phases'][1].update(serverProcess=p['phases'][0]['serverProcess']),
                     lambda p: p['phases'][1].update(startedAtEpochMs=250),
                     lambda p: p['phases'][0].update(error='first boot failed'),
                     lambda p: p.update(error='arbitrary', passed=False),
                     lambda p: p['phases'][1].update(index=True)]
        for mutation in mutations:
            self.parent = copy.deepcopy(original); mutation(self.parent)
            with self.subTest(mutation=mutation), self.assertRaises(runner.ValidationError): self.verify()

    def test_changed_saved_config_artifacts_and_world_reject(self):
        original = copy.deepcopy(self.parent)
        for key in ['configBeforeSha256', 'configAfterSha256', 'artifactsBefore', 'artifactsAfter', 'profile']:
            self.parent = copy.deepcopy(original); self.parent['phases'][1][key] = 'mutated'
            with self.subTest(key=key), self.assertRaises(runner.ValidationError): self.verify()
        self.parent = original
        (self.root / 'phase-2/config-before.yml').write_text('changed on disk')
        with self.assertRaises(runner.ValidationError): self.verify()

    def test_wrong_world_uuid_even_with_same_world_name_rejects(self):
        path = self.root / 'phase-2/scenario.json'; d=runner.strict_json(path)
        d['observations']['restartWorld']['uuid'] = '22222222-2222-2222-2222-222222222222'
        runner.atomic_json(path, d)
        with self.assertRaisesRegex(runner.ValidationError, 'World UUID'): self.verify()

    def test_abort_accepts_only_exact_second_phase_reason(self):
        self.descriptor['phases'][1]['expectation'] = 'cleanup-abort'
        self.parent.update(passed=False, error='Failed scenario assertions: scenario_exception')
        self.parent['phases'][1].update(passed=False, error=self.parent['error'])
        self.verify()
        self.parent['phases'][1]['error'] = self.parent['error'] = 'Timed out waiting for a scenario report'
        with self.assertRaises(runner.ValidationError): self.verify()

    def test_typed_bounded_descriptor_and_wrong_actors_reject(self):
        original = copy.deepcopy(self.descriptor)
        for index in [True, 1.0, '1', 2]:
            d = copy.deepcopy(original); d['phases'][0]['index'] = index
            with self.subTest(index=index), self.assertRaises(runner.ValidationError): restart.validate_descriptor(ROOT, d)
        for value in [[], [1], ['same', 'same'], ['bad assertion']]:
            d = copy.deepcopy(original); d['phases'][1]['requiredAssertions'] = value
            with self.subTest(value=value), self.assertRaises(runner.ValidationError): restart.validate_descriptor(ROOT, d)
        d = copy.deepcopy(original); d['phases'][1]['requiredActorMessages'][0]['actor'] = 'intruder'
        with self.assertRaises(player_actions.ActionValidationError): restart.validate_descriptor(ROOT, d)
        real_loader = player_actions.load_plan
        def changed(project, descriptor):
            path, digest, plan = real_loader(project, descriptor)
            if descriptor['index'] == 2: plan['actors'].reverse()
            return path, digest, plan
        with patch.object(player_actions, 'load_plan', side_effect=changed), self.assertRaisesRegex(runner.ValidationError, 'roster'):
            restart.validate_descriptor(ROOT, original)

    def test_second_boot_missing_wrong_or_wrong_actor_received_text_rejects(self):
        phase = self.descriptor['phases'][1]
        messages = ['OnlyDragons ready | version 1', 'Restart calibration parent hello player']
        valid = {'actors': [{'id': actor, 'sessions': [{'id': 's1', 'messages': messages.copy()}]} for actor in ['alpha', 'beta']]}
        player_actions.validate_messages(valid, phase)
        for text in [[], ['wrong'], ['OnlyDragons ready | version 1']]:
            report = copy.deepcopy(valid); report['actors'][1]['sessions'][0]['messages'] = text
            with self.subTest(text=text), self.assertRaises(player_actions.ActionValidationError):
                player_actions.validate_messages(report, phase)

    def test_export_only_declared_restart_final_config(self):
        profile = self.root / 'run/agent-tests' / self.parent_id
        config = profile / restart.CONFIG; config.parent.mkdir(parents=True); config.write_text('initial')
        (config.parent / 'secret.yml').write_text('excluded')
        (profile / 'world').mkdir(); (profile / 'world/level.dat').write_text('excluded')
        self.assertNotIn(config.relative_to(self.root).as_posix(), ci_paper.evidence_files(self.root))
        result = self.root / 'build/reports/agent-paper' / self.parent_id / 'result.json'
        result.parent.mkdir(parents=True); runner.atomic_json(result, {'catalogMode': restart.MODE})
        exported = ci_paper.evidence_files(self.root)
        self.assertIn(config.relative_to(self.root).as_posix(), exported)
        self.assertFalse(any('secret.yml' in p or 'level.dat' in p for p in exported))



class FullRestartReplayTests(unittest.TestCase):
    """Exercise the actual suite replay entry point with a complete synthetic two-phase archive.

    Extend ReceiptFixture using tiny fake protocol/plugin artifacts and copied tracked
    plans. Deliberately forged test pins match those bytes; they are never production
    artifact identity. Mutations must fail nested phase/plan/path continuity checks.
    """
    def setUp(self):
        """Build the test-only restart artifact, config, actor and evidence graph for strict replay."""
        import io
        import zipfile
        import paper_suite as suite
        from test_paper_suite import ReceiptFixture
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.f = f = ReceiptFixture(Path(self.temp.name).resolve())
        self.suite = suite
        self.name = 'same-profile-restart'
        self.descriptor = runner.strict_json(ROOT / 'dev/game-tests/scenarios.json')[self.name]
        for phase in self.descriptor['phases']:
            f.write(phase['playerActionPlan'], (ROOT / phase['playerActionPlan']).read_bytes())
        pins = runner.properties(ROOT / 'versions.properties')
        # Tiny synthetic protocol JAR bytes, with an explicitly matching test-only pin.
        pins['paperSha256'] = f.result['pins']['paperSha256']
        pins['testPlayerProtocolLibSha256'] = runner.hashlib.sha256(b'protocol').hexdigest()
        f.write('versions.properties', ''.join(k+'='+v+'\n' for k,v in pins.items()))
        pinned = runner.player_pins(pins)
        f.write(f.profile/'player-client'/pinned['jarName'], b'protocol')
        f.write(f.profile/'player-client/OnlyDragonsPlayerClient.jar', b'client')
        for path in ['dev/player-client/gradle.lockfile','dev/player-client/gradle/verification-metadata.xml']: f.write(path,'fixture')
        data = io.BytesIO()
        with zipfile.ZipFile(data,'w') as jar: jar.writestr('config.yml','initial')
        f.write(f.profile/'plugins/OnlyDragons.jar',data.getvalue())
        artifacts = {'productionSha256':runner.sha256(f.profile/'plugins/OnlyDragons.jar'),
                     'gameTestsSha256':runner.sha256(f.profile/'plugins/OnlyDragonsGameTests.jar')}
        parent = copy.deepcopy(f.result)
        parent.update(catalogMode=restart.MODE,scenarioId=self.name, pins=pins, artifacts=artifacts, phases=[],
                      startedAtEpochMs=f.start, completedAtEpochMs=f.end,
                      lease={'acquiredAtEpochMs':f.start+1,'releasedAtEpochMs':f.end-1})
        parent['profile'].update(authentication='offline-disposable-loopback',testPlayerMode='protocol-actions-v1')
        settings=runner.test_settings({},f.run_id,45678,True,2)
        f.write(f.profile/'server.properties',''.join(k+'='+v+'\n' for k,v in settings.items()))
        plan = player_actions.load_plan(f.root,self.descriptor['phases'][0])[2]
        f.json(f.profile/'whitelist.json',player_actions.identities(f.run_id,plan))
        parent['playerBuild']={'artifact':pinned['artifact'],'jars':{p.name:runner.sha256(p) for p in (f.profile/'player-client').glob('*.jar')},
                              'lockSha256':runner.sha256(f.root/'dev/player-client/gradle.lockfile'),
                              'verificationMetadataSha256':runner.sha256(f.root/'dev/player-client/gradle/verification-metadata.xml'),
                              'unitTests':f.result['build']['unitTests']}
        parent['stagedArtifacts']=restart.artifact_hashes(runner,f.profile)
        f.write(f.reports/'config-initial.yml','initial');f.write(f.profile/restart.CONFIG,'initial')
        parent['initialConfigSha256']=runner.sha256(f.reports/'config-initial.yml')
        for index,definition in enumerate(self.descriptor['phases'],1):
            root=f.reports/f'phase-{index}'; nonce=f.run_id[:10]+str(index)*22
            first=f.start+index*2000
            plan_path,plan_hash,plan=player_actions.load_plan(f.root,definition)
            f.write(root/'player-plan.json',plan_path.read_bytes())
            context={'schemaVersion':1,'mode':restart.MODE,'parentRunId':f.run_id,'index':index,'nonce':nonce,
                     'previousNonce':f.run_id[:10]+'1'*22 if index==2 else None,
                     'initialConfigPath':str(f.reports/'config-initial.yml'),
                     'previousReportPath':str(f.reports/'phase-1/scenario.json') if index==2 else None}
            phase={key:copy.deepcopy(parent[key]) for key in ('schemaVersion','scenarioId','revision','worktreeDirty','pins','javaVersion','build','artifacts','playerBuild','bootstrap','profile')}
            phase.update(runId=nonce,parentRunId=f.run_id,index=index,context=context,startedAtEpochMs=first,completedAtEpochMs=first+1000,
                         issuedAtEpochMs=first+50,deadlineAtEpochMs=first+60050,passed=True,error=None,
                         status={'version':{'name':'26.2'}},cleanup={'clean':True,'forced':False,'exitCode':0},
                         playerCleanup={'clean':True,'forced':False,'exitCode':0,'successfulExit':True},
                         serverProcess={'pid':index,'startTicks':index,'startedAtEpochMs':first+1},
                         clientProcess={'pid':index+10,'startTicks':index+10,'startedAtEpochMs':first+51},
                         playerStopStartedAtEpochMs=first+700,playerStopCompletedAtEpochMs=first+710,
                         stopStartedAtEpochMs=first+800,stopCompletedAtEpochMs=first+900,
                         memory=runner.assess_memory(1536,{'MemAvailable':5000},client_memory_mib=256),
                         artifactsBefore=parent['stagedArtifacts'],artifactsAfter=parent['stagedArtifacts'],
                         playerPlan={'sha256':plan_hash,'planId':plan['planId']})
            for side,text in [('before','initial' if index==1 else 'saved'),('after','saved' if index==1 else 'initial')]:
                f.write(root/f'config-{side}.yml',text);phase['config'+side.title()+'Sha256']=runner.sha256(root/f'config-{side}.yml')
            scenario=copy.deepcopy(f.scenario)
            scenario.update(runId=nonce,scenarioId=self.name,mechanicRevision=definition['mechanicRevision'],startedAtEpochMs=first+60,completedAtEpochMs=first+600,
                            assertions=[{'id':a,'expected':0 if a in suite.CLEANUP else True,'observed':0 if a in suite.CLEANUP else True,'passed':True} for a in definition['requiredAssertions']])
            actors=[];journal=[]
            for actor,identity in zip(plan['actors'],player_actions.identities(nonce,plan)):
                session=actor['sessions'][0]
                messages=[' '.join(m.get('containsAll',[])) if 'containsAll' in m else m['exact'] for m in definition['requiredActorMessages'] if m['actor']==actor['id']]
                steps=[dict(copy.deepcopy(step),submittedAtEpochMs=first+200+i*10,packetTypes=player_actions.PACKETS[step['action']]) for i,step in enumerate(session['steps'])]
                actors.append({'id':actor['id'],'username':identity['name'],'uuid':identity['uuid'],'sessions':[{
                    'id':session['id'],'startedAtEpochMs':first+100,'completedAtEpochMs':first+500,'loginReceived':True,'playerLoadedSent':True,
                    'teleportsAcknowledged':1,'steps':steps,'messages':messages,'bindings':[],'inventorySnapshots':[],'inventoryConfirmations':[],
                    'disconnected':True,'passed':True,'error':''}]})
                for kind,detail in [('join',{'uuid':identity['uuid'],'sessionOrdinal':1}), *[('request',{'session':session['id'],'step':step['id']}) for step in session['steps']],('quit',{'uuid':identity['uuid']})]:
                    journal.append({'kind':kind,'actor':actor['id'],'tick':100+len(journal),'detail':detail})
            player={'schemaVersion':2,'runId':nonce,'planId':plan['planId'],'planSha256':plan_hash,'authentication':'offline-disposable-loopback',
                    'artifact':pinned['artifact'],'minecraftVersion':'26.2','protocolVersion':pinned['protocolVersion'],
                    'startedAtEpochMs':first+80,'completedAtEpochMs':first+650,'actors':actors,'passed':True,'error':''}
            scenario['observations']={'playerFixture':journal,'restart':{'parentRunId':f.run_id,'index':index,'nonce':nonce},
                                      'restartWorld':{'uuid':'11111111-1111-1111-1111-111111111111','name':parent['profile']['world']},
                                      'configOnBootSha256':phase['configBeforeSha256']}
            phase.update(scenario=scenario,player=player)
            for filename,value in [('context',context),('scenario',scenario),('player',player),('result',phase)]:f.json(root/(filename+'.json'),value)
            f.write(root/'server.log','OnlyDragons enabled\nOnlyDragons disabled\nStopping server\n')
            parent['phases'].append(phase)
        self.parent = parent
        f.json(f.reports/'result.json',parent)
        self.record=copy.deepcopy(f.record);self.record.update(caseId=self.name,scenarioId=self.name)
        restart.capture(suite,f.root,f.reports,self.record)
        for kind in ['production','companion','player']:
            path=f.suite_root/self.name/kind/'TEST-Sample.xml'
            f.write(path, f.COMPANION_XML if kind == 'companion' else '<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase classname="Sample" name="boundary"/></testsuite>')
        self.record['testEvidence']=[{'kind':kind,'path':(f.suite_root/self.name/kind/'TEST-Sample.xml').relative_to(f.root).as_posix(),'sha256':runner.sha256(f.suite_root/self.name/kind/'TEST-Sample.xml')} for kind in ['production','companion','player']]
        self.case={'scenarioId':self.name,'expectation':'positive','testPlayer':'protocol-actions-v1','scenarioTimeout':60}

    def verify(self):
        with patch.object(self.suite,'process_cleanup'):
            return self.suite.verify_case(self.f.root,self.record,self.case,self.descriptor,self.f.source,self.f.suite_root)

    def test_full_nested_replay_passes(self):
        verified = self.verify()
        self.assertGreater(verified['assertions'], 0)
        self.assertEqual(verified['unitTests']['tests'], 1)
        self.assertEqual(verified['companionUnitTests']['tests'], 2)
        self.assertEqual([phase['companionUnitTests']['tests'] for phase in verified['phases']], [2, 2])

    def test_restart_replays_shared_companion_evidence_for_both_boots(self):
        original = copy.deepcopy(self.parent)
        for index in (0, 1):
            with self.subTest(phase=index + 1):
                self.parent = copy.deepcopy(original)
                self.parent['phases'][index]['build']['companionUnitTests']['tests'] = 3
                self.refresh()
                with self.assertRaisesRegex(runner.ValidationError, 'restart build evidence'):
                    self.verify()
        self.parent = copy.deepcopy(original)
        self.parent['build'].pop('companionUnitTests')
        for phase in self.parent['phases']:
            phase['build'].pop('companionUnitTests')
        self.refresh()
        with self.assertRaisesRegex(runner.ValidationError, 'Companion JUnit totals'):
            self.verify()
        self.parent = original
        self.refresh()
        item = next(item for item in self.record['testEvidence'] if item['kind'] == 'companion')
        (self.f.root / item['path']).write_bytes(b'changed XML after capture')
        with self.assertRaisesRegex(runner.ValidationError, 'Missing/mutated evidence'):
            self.verify()

    def refresh(self):
        f = self.f
        for phase in self.parent['phases']:
            root = f.reports / f"phase-{phase['index']}"
            for filename, value in [('result', phase), ('player', phase['player']), ('scenario', phase['scenario'])]:
                f.json(root / (filename + '.json'), value)
        f.json(f.reports / 'result.json', self.parent)
        restart.capture(self.suite, f.root, f.reports, self.record)

    def test_corrupt_phase_provenance_and_lifetimes_fail_full_replay(self):
        original = copy.deepcopy(self.parent)
        changes = [lambda p: p['phases'].reverse(),
                   lambda p: p['phases'].pop(),
                   lambda p: p['phases'][1].update(runId=p['phases'][0]['runId']),
                   lambda p: p['phases'][0]['cleanup'].update(clean=False),
                   lambda p: p['phases'][1].pop('status'),
                   lambda p: p['phases'][1]['player']['actors'][0].update(username='wrong_actor'),
                   lambda p: p['phases'][1]['player']['actors'][1]['sessions'][0].update(messages=[]),
                   lambda p: p['phases'][1].update(artifactsBefore={}),
                   lambda p: p.update(error='arbitrary error')]
        for change in changes:
            self.parent = copy.deepcopy(original); change(self.parent); self.refresh()
            with self.subTest(change=change), self.assertRaises((runner.ValidationError, player_actions.ActionValidationError)):
                self.verify()
        self.parent = original; self.refresh(); self.verify()

    def test_same_content_evidence_leaf_symlinks_fail_full_replay(self):
        self.verify()
        paths=[self.f.reports/'phase-2/player-plan.json',self.f.reports/'phase-2/context.json',
               self.f.reports/'config-initial.yml',self.f.reports/'phase-2/config-before.yml',
               self.f.reports/'phase-1/config-after.yml',self.f.profile/restart.CONFIG]
        for number,path in enumerate(paths):
            data=path.read_bytes();external=self.f.root.parent/(self.f.root.name+'-outside-'+str(number));external.write_bytes(data)
            self.addCleanup(lambda p=external:p.unlink(missing_ok=True))
            path.unlink()
            try:
                try: path.symlink_to(external)
                except OSError as error:
                    self.skipTest('Platform cannot create test symlinks: '+str(error))
                with self.subTest(path=path), self.assertRaisesRegex(runner.ValidationError,'[Ss]ymlink'):
                    self.verify()
            finally:
                path.unlink(missing_ok=True);path.write_bytes(data)


if __name__ == '__main__': unittest.main()
