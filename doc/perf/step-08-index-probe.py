"""Synthetic SQLite VM work, not phone latency. Run from repository root."""
import json
import sqlite3
import statistics
import time
from pathlib import Path

schema = json.loads(Path('core/data/schemas/com.mantrasiddhi.data.local.MantraSiddhiDatabase/16.json').read_text())['database']
tables = {e['tableName']: e for e in schema['entities']}
db = sqlite3.connect(':memory:')
for name in ['targets', 'japa_entries']:
    e = tables[name]
    db.execute(e['createSql'].replace('${TABLE_NAME}', name))
    for index in e['indices']:
        db.execute(index['createSql'].replace('${TABLE_NAME}', name))

def insert(name, rows):
    fields = tables[name]['fields']
    keys = [f['columnName'] for f in fields]
    values = []
    for row in rows:
        values.append(tuple(row.get(k, None if not f['notNull'] else (0 if f['affinity'] == 'INTEGER' else '')) for k, f in zip(keys, fields)))
    db.executemany(f"INSERT INTO {name} ({','.join(keys)}) VALUES ({','.join('?' for _ in keys)})", values)

insert('targets', [dict(id=u*100+i+1, userId=u, stableId=f't-{i:04}', isActive=int(i==99), isCompleted=0, startedAtMillis=i,
                        targetRecitationCount=100000, setByUserId=u) for i in range(100) for u in range(1,11)])
def entry(i):
    return dict(id=f'e-{i:06}', userId=i%10+1, targetId=None if i%3==0 else 't-0099', vowId='v-1',
                createdAtMillis=(i*7919)%100000, quantity=1, sessionId=None, sessionStableId=None)
insert('japa_entries', [entry(i) for i in range(10000)])
db.commit()
queries = {
    'active_target': ("SELECT * FROM targets WHERE userId=? AND isCompleted=0 AND isActive=1 ORDER BY startedAtMillis DESC,stableId DESC LIMIT 1", (7,)),
    'target_page': ("SELECT * FROM japa_entries WHERE userId=? AND targetId=? AND id>? ORDER BY id LIMIT 200", (7,'t-0099','')),
    'vow_page': ("SELECT * FROM japa_entries WHERE userId=? AND vowId=? AND id>? ORDER BY id LIMIT 200", (7,'v-1','')),
    'unassigned_page': ("SELECT * FROM japa_entries WHERE userId=? AND targetId IS NULL AND createdAtMillis>0 AND id>? ORDER BY id LIMIT 200", (7,'')),
    'ordered_history': ("SELECT * FROM japa_entries WHERE userId=? ORDER BY createdAtMillis,id LIMIT 200", (7,)),
}
def measure():
    result={}
    for name,(sql,params) in queries.items():
        count=0
        def tick():
            nonlocal count
            count+=1
        db.set_progress_handler(tick, 1)
        rows=db.execute(sql,params).fetchall()
        db.set_progress_handler(None,0)
        result[name]=dict(vm_steps=count,rows=len(rows),plan=[r[3] for r in db.execute('EXPLAIN QUERY PLAN '+sql,params)])
    return result
def writes():
    samples=[]
    for _ in range(7):
        db.execute('SAVEPOINT probe')
        start=time.perf_counter()
        insert('japa_entries', [entry(i) for i in range(10000,11000)])
        db.execute('UPDATE japa_entries SET quantity=quantity+1 WHERE userId=7')
        samples.append((time.perf_counter()-start)*1000)
        db.execute('ROLLBACK TO probe');db.execute('RELEASE probe')
    return dict(median_ms=statistics.median(samples),samples_ms=samples)
before=measure();write_before=writes()
ddl=[
    'CREATE INDEX index_targets_userId_isActive_isCompleted_startedAtMillis_stableId ON targets(userId,isActive,isCompleted,startedAtMillis,stableId)',
    'DROP INDEX index_japa_entries_targetId',
    'CREATE INDEX index_japa_entries_userId_targetId_id ON japa_entries(userId,targetId,id)',
    'DROP INDEX index_japa_entries_vowId',
    'CREATE INDEX index_japa_entries_userId_vowId_id ON japa_entries(userId,vowId,id)',
    'CREATE INDEX index_japa_entries_userId_createdAtMillis_id ON japa_entries(userId,createdAtMillis,id)',
]
for sql in ddl: db.execute(sql)
result=dict(sqlite=sqlite3.sqlite_version,fixture='10 interleaved users, 100 targets/user, 1000 entries/user; 7 write samples of 1000 inserts and one user-wide quantity update',
            queries={k:dict(sql=v[0],params=v[1],before=before[k],after=measure()[k]) for k,v in queries.items()},
            ddl=ddl,write_before=write_before,write_after=writes())
Path('doc/perf/step-08-index-results.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
