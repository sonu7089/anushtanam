const { doc, writeBatch, serverTimestamp } = require('firebase/firestore');
const entry = (extra = {}) => ({ id: 'entry-one', quantity: 600, revision: 1, mantraId: 'gayatri',
  operationId: 'save-one', dateKey: '20260910', zoneId: 'Asia/Kolkata', period: 'SAYAM', source: 'SANDHYA',
  createdAtMillis: 1, updatedAtMillis: 10, targetId: null, vowId: null, ...extra });
const goal = (extra = {}) => ({ id: 'goal-one', kind: 'target', mantraId: 'gayatri', startedAtMillis: 1,
  endedAtMillis: null, baselineCount: 32, targetCount: 108, rewardType: 'OTHER', rewardDescription: '', ...extra });
function publish(db, uid, type, value, id = type === 'practice' ? `${value.type}_${value.id}` : value.id) {
  const collections = { entry: 'japaEntries', goal: 'japaGoals', practice: 'practiceRecords', conflict: 'japaConflicts' };
  const batch = writeBatch(db);
  batch.set(doc(db, `users/${uid}/${collections[type]}/${id}`), value);
  batch.set(doc(db, `users/${uid}/backupChanges/${type}_${id}`), { type, id, value, changedAt: serverTimestamp() });
  return batch.commit();
}
module.exports = { entry, goal, publish };
