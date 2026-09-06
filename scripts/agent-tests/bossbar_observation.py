"""Replay the bounded T08d received packet contract; no production formatter dependency."""
import json
import math
import uuid


def validate(scenario, player):
    from player_actions import require
    expected = scenario.get('observations', {}).get('bossBarChecks')
    restart=scenario.get("scenarioId", "").startswith("dragon-restart-")
    require(isinstance(expected, list) and (2 if restart else 10) <= len(expected) <= 64, 'Missing bounded boss bar checks')
    markers=('full','veto','damaged','reconnected','outside','returned','animation','retired','new-generation','reset','ghost-full','ghost-credit','ghost-stable','closed','standard-full','standard-reset','training-full','training-reset')
    required={(actor, 's1' if actor=='alpha' or marker in markers[:3] else 's2', marker) for actor in ('alpha','beta') for marker in markers}
    if restart:
        index=scenario['observations']['restart']['index']
        required={('alpha','s1',marker) for marker in (('restart-idle','restart-active') if index==1 else ('restart-idle','restart-active','restart-reset'))}
    require({(row.get('actor'),row.get('session'),row.get('marker')) for row in expected}==required and len(expected)==len(required),'Incomplete boss bar lifecycle oracles')
    sessions = {(a['id'], s['id']): s for a in player['actors'] for s in a['sessions']}
    samples = {}
    identities = {}
    for key, session in sessions.items():
        receipt = session.get('bossBars')
        require(isinstance(receipt, dict) and set(receipt) == {'events', 'samples'}, 'Missing boss bar receipt')
        events = receipt['events']
        require(isinstance(events, list) and len(events) <= 512, 'Boss event bound')
        active = {}
        states = [dict(active)]
        for event in events:
            require(isinstance(event, dict) and event.get('action') in ('ADD','REMOVE','UPDATE_HEALTH','UPDATE_TITLE','UPDATE_STYLE','UPDATE_FLAGS'), 'Invalid boss event')
            identity = event.get('id')
            require(str(uuid.UUID(identity)) == identity, 'Invalid received bar UUID')
            action = event['action']
            if action == 'ADD':
                require(identity not in active and not active, 'Duplicate bar add')
            else:
                require(identity in active, 'Unknown bar update/remove')
            if action == 'REMOVE':
                require(set(event)=={'id','action'}, 'Unexpected remove fields')
                del active[identity]
            else:
                require(set(event)=={'id','action','state'}, 'Missing bar state')
                state=event['state']
                require(set(state)=={'id','title','titleJson','percent','color','division','darkenSky','music','fog'}, 'Incomplete bar state')
                require(state['id']==identity and type(state['percent']) in (float,int) and math.isfinite(state['percent']) and 0<=state['percent']<=1, 'Invalid bar percent/identity')
                require(isinstance(state['title'],str) and len(state['title'])<=2048 and isinstance(state['titleJson'],str) and len(state['titleJson'])<=8192, 'Invalid bar title')
                json.loads(state['titleJson'])
                if action != 'ADD':
                    changes={'UPDATE_HEALTH':{'percent'},'UPDATE_TITLE':{'title','titleJson'},'UPDATE_STYLE':{'color','division'},'UPDATE_FLAGS':{'darkenSky','music','fog'}}[action]
                    require(all(state[k]==v for k,v in active[identity].items() if k not in changes), 'Unobserved bar state mutation')
                active[identity]=state
            states.append(dict(active))
        require(isinstance(receipt['samples'],list) and len(receipt['samples'])<=64,'Boss sample bound')
        last=0
        for sample in receipt['samples']:
            require(set(sample)=={'marker','eventCount','bars'},'Invalid sample')
            count=sample['eventCount']; require(type(count) is int and last<=count<=len(events),'Invalid sample order')
            last=count
            require(sample['bars']==list(states[count].values()),'Sample differs from received packets')
            sample_key=(*key,sample['marker'])
            require(sample_key not in samples,'Duplicate sample marker')
            samples[sample_key]=sample['bars']
    require(len(samples)==len(expected),'Missing/extra received UI samples')
    seen=set()
    for check in expected:
        require(set(check)=={'actor','session','marker','generation','title','percent'},'Invalid UI oracle')
        key=(check['actor'],check['session'],check['marker'])
        require(key not in seen and key in samples,'Missing/duplicate UI oracle');seen.add(key)
        bars=samples[key]
        if not check['generation']:
            require(bars==[], 'Stale/unexpected boss bar')
            continue
        require(len(bars)==1,'Missing/duplicate visible boss bar')
        bar=bars[0]
        require(bar['title']==check['title'] and abs(bar['percent']-check['percent'])<1e-6,'Received HP/title mismatch')
        require(bar['color']=='RED' and bar['division']=='NONE' and bar['darkenSky'] is False and bar['music'] is False and bar['fog'] is False,'Unexpected native bar effects/style')
        title_component=json.loads(bar['titleJson'])
        require(isinstance(title_component,dict) and title_component.get('color')=='gold' and title_component.get('bold') is True,'Missing formatted title heading')
        children=title_component.get('extra')
        require(isinstance(children,list) and len(children)==3 and [x.get('color') for x in children]==['dark_gray','red','white'],'Missing formatted title colors')
        require(title_component.get('text','')+''.join(x.get('text','') for x in children)==bar['title'],'Full received title component differs from title')
        generation=check['generation']
        if generation in identities: require(identities[generation]==bar['id'],'Bar identity changed within generation/across viewers')
        else:
            require(bar['id'] not in identities.values(),'Bar reused across generations')
            identities[generation]=bar['id']
    # Reject transient extra bars or incorrect HP/title updates between sampled states.
    for key,session in sessions.items():
        allowed={}
        for check in expected:
            if (check['actor'],check['session'])==key and check['generation']:
                identity=identities[check['generation']]
                allowed.setdefault(identity,[]).append(check)
                if restart and scenario['scenarioId']=='dragon-restart-animation' and scenario['observations']['restart']['index']==1:
                    allowed[identity].append(dict(percent=0,title='Test Dragon (Calibration)  |  0 / 1,000 HP  (0%)'))
        for event in session['bossBars']['events']:
            require(event['id'] in allowed,'Unrequested transient boss bar')
            if 'state' in event:
                state=event['state']
                # Name and health travel in separate packets; intermediate combinations are valid.
                require(any(abs(state['percent']-c['percent'])<1e-6 for c in allowed[event['id']]),'Unobserved HP between UI samples')
                require(any(state['title']==c['title'] for c in allowed[event['id']]),'Unobserved title between UI samples')
    # The strings themselves remain independently required by the scenario catalog.
    for key,session in sessions.items():
        styled=session.get('styledMessages')
        require(isinstance(styled,list) and len(styled)<=128,'Missing received styled output')
        require([r['text'] for r in styled]==session['messages'],'Styled/plain received output differs')
        for row in styled:
            require(set(row)=={'text','json'} and isinstance(row['json'],str) and len(row['json'])<=16384,'Invalid styled output')
            json.loads(row['json'])
    if restart: return
    alpha=sessions[('alpha','s1')]['styledMessages']
    require(any(r['text']=='Your stats' and isinstance(json.loads(r['json']),dict) and json.loads(r['json']).get('color')=='gold' and json.loads(r['json']).get('bold') is True for r in alpha),'Missing styled stats heading')
    require(any('Weapon Damage: 100' in r['text'] and 'aqua' in r['json'] for r in alpha),'Missing styled stat value')
    beta=sessions[('beta','s2')]['styledMessages']
    require(any(r['text'].startswith('Dragon defeated!') and isinstance(json.loads(r['json']),dict) and json.loads(r['json']).get('color')=='gold' for r in beta),'Missing styled received result')
