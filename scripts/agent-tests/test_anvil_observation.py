"""Mutation tests for received anvil identity, settlement and native transaction evidence.

All reports are synthetic; actual result extraction and XP behavior require the
real-Paper anvil scenarios. Window-0 inventory restrictions remain independent.
"""
import copy
import unittest
import anvil_observation as anvil
from player_actions import ActionValidationError, validate_arguments

class AnvilReplayTest(unittest.TestCase):
    """Exercise stale window/state rejection and preview/input/output/XP evidence reconciliation."""
    def session(self):
        """Build a positive-window synthetic extraction with a fresh post-click snapshot before quit."""
        snap={'openSequence':1,'containerId':3,'stateId':5,'slotCount':39,'slotSha256':['a'*64]*39,'cursorSha256':'b'*64,'amounts':[0]*39,'cursorAmount':0,'receivedAtEpochMs':120,'sha256':'c'*64}
        step={'id':'collect','action':'anvilClick','args':{'slot':2,'button':'left'},'submittedAtEpochMs':130,'anvilOpenSequence':1,'containerId':3,'stateId':5,'anvilSnapshotSequence':1,'anvilSnapshotSha256':'c'*64}
        session={'steps':[step,{'id':'quit','action':'disconnect','args':{},'submittedAtEpochMs':150}],'anvil':{'opens':[{'openSequence':1,'containerId':3,'menuType':'ANVIL','receivedAtEpochMs':110,'sha256':'a'*64}],'snapshots':[snap,dict(snap,receivedAtEpochMs=140)],'costs':[],'xp':[],'closes':[]}}
        expected={'steps':[{'action':'anvilClick'},{'action':'disconnect'}]}
        return session,expected
    def test_fresh_observed_window_settles_before_disconnect(self):
        s,e=self.session();anvil.validate(s,e,100,200)
    def test_snapshot_after_terminal_cannot_repair_unsettled_click(self):
        s,e=self.session();s['anvil']['snapshots'][1]['receivedAtEpochMs']=160
        with self.assertRaises(ActionValidationError):anvil.validate(s,e,100,200)
    def test_wrong_window_state_shape_and_reopened_sessions_reject(self):
        for field,value in [('containerId',7),('stateId',4),('anvilSnapshotSha256','d'*64)]:
            s,e=self.session();s['steps'][0][field]=value
            with self.assertRaises(ActionValidationError):anvil.validate(s,e,100,200)
        s,e=self.session();s['anvil']['snapshots'][0]['slotCount']=46
        with self.assertRaises(ActionValidationError):anvil.validate(s,e,100,200)
        s,e=self.session();s['anvil']['opens'].append(dict(s['anvil']['opens'][0],openSequence=2,containerId=4,receivedAtEpochMs=125))
        with self.assertRaises(ActionValidationError):anvil.validate(s,e,100,200)
    def test_integer_identities_reject_boolean_and_float_aliases(self):
        for section, field in [('opens','openSequence'),('opens','containerId'),('snapshots','containerId'),('snapshots','slotCount')]:
            for convert in (float, lambda _: True):
                s,e=self.session();s['anvil'][section][0][field]=convert(s['anvil'][section][0][field])
                with self.subTest(section=section,field=field,convert=convert), self.assertRaises(ActionValidationError):anvil.validate(s,e,100,200)
        for field in ('anvilOpenSequence','containerId','stateId'):
            for convert in (float, lambda _: True):
                s,e=self.session();s['steps'][0][field]=convert(s['steps'][0][field])
                with self.subTest(field=field,convert=convert), self.assertRaises(ActionValidationError):anvil.validate(s,e,100,200)
    def test_received_feature_requires_cost_destination_and_fractional_xp(self):
        s,e=self.session();before,after=s['anvil']['snapshots'];before['amounts']=[1,1,1]+[0]*36
        after['amounts']=[0]*39;after['cursorAmount']=1;after['cursorSha256']=before['slotSha256'][2]
        s['anvil']['costs']=[{'openSequence':1,'cost':2,'receivedAtEpochMs':125}]
        s['anvil']['xp']=[{'level':28,'fraction':.375,'receivedAtEpochMs':140}]
        scenario={'scenarioId':'anvil-boundaries','observations':{'anvilBoundaryTransactions':[{'step':'collect','success':True,'cost':2,'beforeLevel':30,'afterLevel':28,'fraction':.375}]}}
        report={'actors':[{'sessions':[s]}]};anvil.validate_feature(scenario,report)
        for section,field,value in [('costs','cost',3),('xp','fraction',0),('snapshots','cursorSha256','d'*64)]:
            broken=copy.deepcopy(report);rows=broken['actors'][0]['sessions'][0]['anvil'][section];rows[-1][field]=value
            with self.subTest(section=section),self.assertRaises(ActionValidationError):anvil.validate_feature(scenario,broken)
    def test_narrow_action_arguments_and_window_zero_remain_strict(self):
        for button in ['left','right','shift-left','shift-right']:validate_arguments('anvilClick',{'slot':2,'button':button})
        for args in [{'slot':39,'button':'left'},{'slot':-1,'button':'left'},{'slot':2,'button':'creative'}]:
            with self.assertRaises(ActionValidationError):validate_arguments('anvilClick',args)
        with self.assertRaises(ActionValidationError):validate_arguments('inventoryClick',{'slot':2,'button':'left'})
        with self.assertRaises(ActionValidationError):validate_arguments('anvilRename',{'name':'x'*51})

if __name__=='__main__':unittest.main()
