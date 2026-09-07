"""Mutation tests for packet-derived boss-bar lifecycle and styled chat replay.

Synthetic histories establish validator sensitivity, not rendered client appearance.
"""
import copy
import json
import unittest
import uuid
import bossbar_observation as ui
from player_actions import ActionValidationError

class BossBarReplayTests(unittest.TestCase):
    """Require complete generation/viewer lifecycle coverage and reject hidden transient UI changes."""
    def fixture(self):
        """Construct independent expected markers plus matching synthetic event/sample/chat histories.

        Distinct generated UUIDs model encounter generations; health fractions and styled
        titles are explicit test oracles rather than calls to production formatters.
        """
        ids={g:str(uuid.uuid4()) for g in ('main','reset','ghost','standard','training')}
        markers=[('full',1000,'main'),('veto',1000,'main'),('damaged',900,'main'),('reconnected',900,'main'),('outside',900,'main'),('returned',900,'main'),('animation',0,'main'),('retired',0,''),('new-generation',1000,'reset'),('reset',0,''),('ghost-full',1000,'ghost'),('ghost-credit',900,'ghost'),('ghost-stable',900,'ghost'),('closed',0,'')]
        markers += [('standard-full',1000,'standard'),('standard-reset',0,''),('training-full',100000,'training'),('training-reset',0,'')]
        checks=[];actors=[]
        for actor in ('alpha','beta'):
            sessions=[]
            for session in (('s1',) if actor=='alpha' else ('s1','s2')):
                events=[];samples=[];active=None
                for i,(marker,hp,generation) in enumerate(markers):
                    if actor=='beta' and (session=='s1')!=(i<3):continue
                    if actor=='alpha' and marker=='outside':generation='';hp=0
                    maximum=100000 if generation=='training' else 1000
                    name='Test Dragon (Training)' if generation=='training' else 'Test Dragon' if generation=='standard' else 'Dragon'
                    health=f'{hp:,} / {maximum:,} HP'
                    percent=hp/maximum
                    title=f'{name}  |  {health}  ({percent*100:g}%)' if generation else ''
                    checks.append(dict(actor=actor,session=session,marker=marker,generation=generation,title=title,percent=percent))
                    if active and (not generation or ids[generation]!=active['id']):
                        events.append(dict(id=active['id'],action='REMOVE'));active=None
                    if generation:
                        state=dict(id=ids[generation],title=title,titleJson=json.dumps({'text':name,'color':'gold','bold':True,'extra':[{'text':'  |  ','color':'dark_gray'},{'text':health,'color':'red'},{'text':f'  ({percent*100:g}%)','color':'white'}]}),percent=percent,color='RED',division='NONE',darkenSky=False,music=False,fog=False)
                        if active is None:events.append(dict(id=state['id'],action='ADD',state=copy.deepcopy(state)))
                        else:
                            if state['percent']!=active['percent']:
                                old=copy.deepcopy(active);old['percent']=state['percent'];events.append(dict(id=old['id'],action='UPDATE_HEALTH',state=old))
                            if state['title']!=active['title']:events.append(dict(id=state['id'],action='UPDATE_TITLE',state=copy.deepcopy(state)))
                        active=state
                    samples.append(dict(marker=marker,eventCount=len(events),bars=[] if active is None else [copy.deepcopy(active)]))
                messages=[{'text':'Your stats','json':'{"text":"Your stats","color":"gold","bold":true}'},{'text':'Weapon Damage: 100','json':'{"text":"Weapon Damage: 100","color":"aqua"}'},{'text':'Dragon defeated!','json':'{"text":"Dragon defeated!","color":"gold"}'}]
                sessions.append(dict(id=session,bossBars=dict(events=events,samples=samples),messages=[r['text'] for r in messages],styledMessages=messages))
            actors.append(dict(id=actor,sessions=sessions))
        return dict(observations=dict(bossBarChecks=checks)),dict(actors=actors)
    def test_ui_receipt_fields_are_required_only_for_named_plans(self):
        from player_actions import _ui_session_fields
        with self.assertRaises(ActionValidationError):_ui_session_fields({}, {'planId':'dragon-presentation-v1'})
        with self.assertRaises(ActionValidationError):_ui_session_fields({'bossBars':{},'styledMessages':[]},{'planId':'primitives-v1'})
        self.assertEqual({'id':'s1'},_ui_session_fields({'id':'s1'},{'planId':'primitives-v1'}))
    def test_complete_packet_history_and_lifecycle(self):ui.validate(*self.fixture())
    def test_both_human_modes_are_required_and_have_literal_full_health_titles(self):
        scenario,player=self.fixture()
        checks=scenario['observations']['bossBarChecks']
        for marker,title in (
                ('standard-full','Test Dragon  |  1,000 / 1,000 HP  (100%)'),
                ('training-full','Test Dragon (Training)  |  100,000 / 100,000 HP  (100%)')):
            rows=[row for row in checks if row['marker']==marker]
            self.assertEqual(2,len(rows))
            self.assertTrue(all(row['title']==title and row['percent']==1 for row in rows))
        for marker in ('standard-full','standard-reset','training-full','training-reset'):
            with self.subTest(missing=marker):
                altered=copy.deepcopy(scenario)
                altered['observations']['bossBarChecks']=[row for row in checks if row['marker']!=marker]
                with self.assertRaisesRegex(ActionValidationError,'Incomplete boss bar lifecycle'):
                    ui.validate(altered,player)

    def test_training_packets_cannot_replay_as_ordinary_hp_or_leave_stale_bar(self):
        for mutation in ('ordinary-hp','half-progress','stale-reset'):
            with self.subTest(mutation=mutation):
                scenario,player=self.fixture();session=player['actors'][0]['sessions'][0]
                receipt=session['bossBars'];sample=next(row for row in receipt['samples'] if row['marker']=='training-full')
                identity=sample['bars'][0]['id']
                if mutation=='stale-reset':
                    reset=next(row for row in receipt['samples'] if row['marker']=='training-reset')
                    reset['eventCount']=sample['eventCount'];reset['bars']=copy.deepcopy(sample['bars'])
                else:
                    states=[event['state'] for event in receipt['events'] if event['id']==identity and 'state' in event]
                    states += [bar for row in receipt['samples'] for bar in row['bars'] if bar['id']==identity]
                    for state in states:
                        if mutation=='half-progress':state['percent']=.5
                        else:
                            state['title']=state['title'].replace('100,000','1,000')
                            state['titleJson']=state['titleJson'].replace('100,000','1,000')
                with self.assertRaisesRegex(ActionValidationError,'Received HP/title mismatch|Stale/unexpected boss bar'):
                    ui.validate(scenario,player)
    def test_missing_oracle_duplicate_identity_stale_samples_and_wrong_hp_fail(self):
        for mutation in ('missing','duplicate','hp','snapshot','color','style','identity','transient'):
            with self.subTest(mutation=mutation):
                scenario,player=self.fixture();session=player['actors'][0]['sessions'][0]
                if mutation=='missing':scenario['observations']['bossBarChecks'].pop()
                elif mutation=='duplicate':session['bossBars']['events'].insert(1,copy.deepcopy(session['bossBars']['events'][0]))
                elif mutation=='hp':scenario['observations']['bossBarChecks'][0]['percent']=.5
                elif mutation=='snapshot':session['bossBars']['samples'][0]['bars']=[]
                elif mutation=='color':session['bossBars']['events'][0]['state']['music']=True
                elif mutation=='style':session['styledMessages'][0]['json']='{"text":"Your stats"}'
                elif mutation=='transient':
                    event=copy.deepcopy(session['bossBars']['events'][0]);identity=str(uuid.uuid4());event['id']=identity;event['state']['id']=identity;session['bossBars']['events'].append(event)
                else:scenario['observations']['bossBarChecks'][0]['generation']='wrong'
                with self.assertRaises((ActionValidationError,ValueError)):ui.validate(scenario,player)
