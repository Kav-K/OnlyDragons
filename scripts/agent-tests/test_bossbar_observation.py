import copy
import json
import unittest
import uuid
import bossbar_observation as ui
from player_actions import ActionValidationError

class BossBarReplayTests(unittest.TestCase):
    def fixture(self):
        ids={g:str(uuid.uuid4()) for g in ('main','reset','ghost')}
        markers=[('full',1000,'main'),('veto',1000,'main'),('damaged',900,'main'),('reconnected',900,'main'),('outside',900,'main'),('returned',900,'main'),('animation',0,'main'),('retired',0,''),('new-generation',1000,'reset'),('reset',0,''),('ghost-full',1000,'ghost'),('ghost-credit',900,'ghost'),('ghost-stable',900,'ghost'),('closed',0,'')]
        checks=[];actors=[]
        for actor in ('alpha','beta'):
            sessions=[]
            for session in (('s1',) if actor=='alpha' else ('s1','s2')):
                events=[];samples=[];active=None
                for i,(marker,hp,generation) in enumerate(markers):
                    if actor=='beta' and (session=='s1')!=(i<3):continue
                    if actor=='alpha' and marker=='outside':generation='';hp=0
                    title=f'Dragon  |  {hp} HP  ({hp/10:g}%)' if generation else ''
                    checks.append(dict(actor=actor,session=session,marker=marker,generation=generation,title=title,percent=hp/1000))
                    if active and (not generation or ids[generation]!=active['id']):
                        events.append(dict(id=active['id'],action='REMOVE'));active=None
                    if generation:
                        state=dict(id=ids[generation],title=title,titleJson=json.dumps({'text':'Dragon','color':'gold','bold':True,'extra':[{'text':'  |  ','color':'dark_gray'},{'text':f'{hp} HP','color':'red'},{'text':f'  ({hp/10:g}%)','color':'white'}]}),percent=hp/1000,color='RED',division='NONE',darkenSky=False,music=False,fog=False)
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
