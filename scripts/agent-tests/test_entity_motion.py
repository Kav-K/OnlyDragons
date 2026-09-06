"""Received entity motion is optional, but a required UUID-bound witness must be fresh and numeric."""
import copy
import unittest
import entity_motion as motion

class EntityMotionTests(unittest.TestCase):
    def setUp(self):
        self.row={'segment':0,'uuid':'12345678-1234-4234-8234-123456789abc','startedAtEpochMs':100,
                  'endedAtEpochMs':200,'packets':50,'first':[0,100,0],'last':[5,100,0],'path':10,'maxStep':.25}
        self.session={'id':'first','startedAtEpochMs':100,'completedAtEpochMs':300,
                      'bindings':[{'targetRef':'dragon','uuid':self.row['uuid']}],'entityMotion':[self.row]}
        self.report={'actors':[{'id':'alpha','sessions':[self.session]}]}
        self.descriptor={'requiredEntityMotion':[{'id':'moving','actor':'alpha','session':'first','targetRef':'dragon',
                                                  'minPackets':30,'minPath':5,'maxStep':1}]}
    def test_optional_legacy_and_bound_motion(self):
        motion.validate([],100,300);motion.validate_required({},{});motion.validate_required(self.report,self.descriptor)
    def test_missing_wrong_identity_and_insufficient_motion_rejected(self):
        for field,value in [('entityMotion',[]),('bindings',[])]:
            with self.subTest(field=field),self.assertRaises(ValueError):
                report=copy.deepcopy(self.report);report['actors'][0]['sessions'][0][field]=value
                motion.validate_required(report,self.descriptor)
        for field,value in [('uuid','22345678-1234-4234-8234-123456789abc'),('packets',29),('path',4),('maxStep',2)]:
            with self.subTest(field=field),self.assertRaises(ValueError):
                report=copy.deepcopy(self.report);report['actors'][0]['sessions'][0]['entityMotion'][0][field]=value
                motion.validate_required(report,self.descriptor)
    def test_malformed_stale_impossible_and_unbounded_rejected(self):
        for field,value in [('segment',1),('packets',True),('packets',10001),('startedAtEpochMs',99),('endedAtEpochMs',301),
                            ('path',float('nan')),('maxStep',11),('first',[0,float('inf'),0]),('first',[0,0]),
                            ('path',1),('packets',0),('uuid','garbage'),('extra',1)]:
            with self.subTest(field=field,value=value),self.assertRaises(ValueError):
                row=copy.deepcopy(self.row);row[field]=value;motion.validate([row],100,300)
        with self.assertRaises(ValueError):motion.validate([self.row]*129,100,300)
    def test_segment_identity_cannot_be_conflated(self):
        row=copy.deepcopy(self.row);row.update(segment=1,uuid='22345678-1234-4234-8234-123456789abc')
        self.row.update(packets=1,path=5,maxStep=5);self.session['entityMotion'].append(row)
        with self.assertRaises(ValueError):motion.validate_required(self.report,self.descriptor)

if __name__=='__main__':unittest.main()
